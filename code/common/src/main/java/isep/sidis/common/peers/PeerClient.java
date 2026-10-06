package isep.sidis.common.peers;

import isep.sidis.common.http.AisafeHeaders;
import isep.sidis.common.http.OutboundHeaders;
import isep.sidis.common.resilience.ResilientExecutor;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Comunicação peer-to-peer entre as réplicas do MESMO microsserviço (Strategy 2 — Instance Replication).
 *
 * <p>Cada réplica tem a sua base de dados, por isso os dados ficam particionados pela réplica que
 * recebeu a escrita. Quando um GET chega a uma réplica:
 * <ol>
 *   <li>a réplica responde com os dados locais;</li>
 *   <li>se lhe faltarem dados, consulta os peers <b>em paralelo</b> (unicast HTTP, com timeout,
 *       retry e circuit breaker por peer);</li>
 *   <li>agrega as respostas e devolve ao cliente uma resposta única.</li>
 * </ol>
 *
 * <p>Os pedidos enviados levam {@code X-Peer-Hops: 1}: a réplica que os recebe responde só com os
 * seus dados locais e não volta a encaminhar (sem forwarding circular). Levam também o JWT do
 * utilizador (mesmo controlo de acessos), o correlation id e a {@code X-Service-Key}.
 *
 * <p>Se algum peer não responder, a resposta ao cliente leva {@code X-Partial-Response: true}
 * (Availability + Partition tolerance: responde-se com o que se tem, em vez de falhar).
 */
public class PeerClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PeerClient.class);

    private final List<String> peers;
    private final RestClient restClient;
    private final ResilientExecutor executor;
    private final OutboundHeaders outboundHeaders;
    private final Duration aggregationTimeout;
    private final ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();

    public PeerClient(List<String> peers, RestClient restClient, ResilientExecutor executor,
                      OutboundHeaders outboundHeaders, Duration aggregationTimeout) {
        this.peers = peers.stream().map(String::trim).filter(s -> !s.isEmpty())
                .map(u -> u.endsWith("/") ? u.substring(0, u.length() - 1) : u).toList();
        this.restClient = restClient;
        this.executor = executor;
        this.outboundHeaders = outboundHeaders;
        this.aggregationTimeout = aggregationTimeout;
    }

    public List<String> getPeers() {
        return peers;
    }

    /** {@code true} se o pedido em curso foi enviado por outra réplica (deve ser respondido só localmente). */
    public boolean isPeerRequest() {
        return OutboundHeaders.currentRequest()
                .map(r -> r.getHeader(AisafeHeaders.PEER_HOPS) != null)
                .orElse(false);
    }

    /** {@code true} se este pedido deve ser propagado aos peers. */
    public boolean shouldForward() {
        return !peers.isEmpty() && !isPeerRequest();
    }

    // ------------------------------------------------------------------ GET: primeira resposta

    /**
     * Pede o recurso a todos os peers em paralelo e devolve a primeira resposta 200.
     * 404 num peer significa "não tenho"; vazio se nenhum o tiver (ou se não se deve encaminhar).
     *
     * @param uri caminho + query já codificados (ex.: "/api/routes/abc")
     */
    public <T> Optional<T> findFirst(String uri, Class<T> type) {
        return findFirst(uri, ParameterizedTypeReference.forType(type));
    }

    public <T> Optional<T> findFirst(String uri, ParameterizedTypeReference<T> type) {
        if (!shouldForward()) {
            return Optional.empty();
        }
        log.info("Not found locally: forwarding GET {} to peers {}", uri, peers);
        Map<String, String> headers = peerHeaders();

        CompletableFuture<Optional<T>> first = new CompletableFuture<>();
        AtomicInteger pending = new AtomicInteger(peers.size());
        AtomicInteger failed = new AtomicInteger();

        for (String peer : peers) {
            CompletableFuture
                    .supplyAsync(withMdc(() -> loggedGet(peer, uri, type, headers)), pool)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            failed.incrementAndGet(); // já registado em loggedGet
                        } else if (result.isPresent()) {
                            first.complete(result);
                        }
                        if (pending.decrementAndGet() == 0) {
                            first.complete(Optional.empty());
                        }
                    });
        }

        Optional<T> result = await(first, Optional.empty(), failed);
        if (result.isEmpty() && failed.get() > 0) {
            markPartial(failed.get());
        }
        return result;
    }

    // ------------------------------------------------------------------ GET: agregação

    /**
     * Pede o mesmo recurso a todos os peers em paralelo e devolve todas as respostas 200
     * (o chamador agrega-as com os dados locais). Peers que falhem são contados em
     * {@link PeerResult#failedPeers()} e marcados na resposta HTTP.
     */
    public <T> PeerResult<T> collect(String uri, ParameterizedTypeReference<T> type) {
        if (!shouldForward()) {
            return PeerResult.empty();
        }
        log.info("Aggregating GET {} from peers {}", uri, peers);
        Map<String, String> headers = peerHeaders();

        List<CompletableFuture<Optional<T>>> futures = new ArrayList<>();
        for (String peer : peers) {
            futures.add(CompletableFuture
                    .supplyAsync(withMdc(() -> loggedGet(peer, uri, type, headers)), pool)
                    .orTimeout(aggregationTimeout.toMillis(), TimeUnit.MILLISECONDS)
                    .exceptionally(error -> null)); // null = falhou (Optional.empty = 404)
        }

        List<T> responses = new ArrayList<>();
        int failed = 0;
        for (CompletableFuture<Optional<T>> f : futures) {
            Optional<T> r = f.join();
            if (r == null) {
                failed++;
            } else {
                r.ifPresent(responses::add);
            }
        }
        if (failed > 0) {
            markPartial(failed);
        }
        return new PeerResult<>(responses, failed, peers.size());
    }

    /** Atalho para endpoints que devolvem listas: junta as listas de todos os peers. */
    public <T> List<T> collectList(String uri, ParameterizedTypeReference<List<T>> type) {
        List<T> all = new ArrayList<>();
        collect(uri, type).responses().forEach(all::addAll);
        return all;
    }

    // ------------------------------------------------------------------ Comandos: réplica dona

    /**
     * Envia um comando (ex.: PATCH) à réplica que é dona do recurso. Os peers são tentados um a um
     * (unicast); 404 = "não é meu", passa ao seguinte. A resposta do dono (sucesso ou erro 4xx de
     * negócio, ex.: 409) é devolvida/propagada tal como veio.
     */
    public <T> Optional<T> forwardToOwner(HttpMethod method, String uri, Object body, Class<T> type) {
        if (!shouldForward()) {
            return Optional.empty();
        }
        log.info("Not found locally: forwarding {} {} to the owner replica", method, uri);
        Map<String, String> headers = peerHeaders();
        int failed = 0;

        for (String peer : peers) {
            try {
                Optional<T> result = executor.execute(peer, () -> send(peer, method, uri, body,
                        ParameterizedTypeReference.forType(type), headers));
                if (result.isPresent()) {
                    log.info("Peer {} handled {} {}", peer, method, uri);
                    return result;
                }
            } catch (HttpClientErrorException e) {
                // O dono respondeu com um erro de negócio (400/409/422...): propaga-o ao cliente
                throw new ResponseStatusException(HttpStatus.valueOf(e.getStatusCode().value()), errorMessage(e), e);
            } catch (RuntimeException e) {
                failed++;
                log.warn("Peer {} did not answer {} {}: {}", peer, method, uri, rootMessage(e));
            }
        }
        if (failed > 0) {
            markPartial(failed);
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------ internos

    // Como get(), mas regista a falha na thread da chamada (com o correlation id no MDC).
    private <T> Optional<T> loggedGet(String peer, String uri, ParameterizedTypeReference<T> type,
                                      Map<String, String> headers) {
        try {
            Optional<T> result = get(peer, uri, type, headers);
            if (result.isPresent()) {
                log.info("Peer {} answered GET {} (200)", peer, uri);
            }
            return result;
        } catch (RuntimeException e) {
            log.warn("Peer {} did not answer GET {}: {}", peer, uri, rootMessage(e));
            throw e;
        }
    }

    private <T> Optional<T> get(String peer, String uri, ParameterizedTypeReference<T> type,
                                Map<String, String> headers) {
        return executor.execute(peer, () -> send(peer, HttpMethod.GET, uri, null, type, headers));
    }

    private <T> Optional<T> send(String peer, HttpMethod method, String uri, Object body,
                                 ParameterizedTypeReference<T> type, Map<String, String> headers) {
        RestClient.RequestBodySpec spec = restClient.method(method)
                .uri(URI.create(peer + uri))
                .accept(MediaType.APPLICATION_JSON)
                .headers(h -> headers.forEach(h::set));
        if (body != null) {
            spec.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        try {
            return Optional.ofNullable(spec.retrieve().body(type));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty(); // a réplica respondeu: não tem o recurso
        }
    }

    /** Copia o MDC (correlation id, instância) para a thread que faz a chamada ao peer. */
    private static <T> Supplier<T> withMdc(Supplier<T> task) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        return () -> {
            if (context != null) {
                MDC.setContextMap(context);
            }
            try {
                return task.get();
            } finally {
                MDC.clear();
            }
        };
    }

    private Map<String, String> peerHeaders() {
        Map<String, String> headers = outboundHeaders.capture();
        headers.put(AisafeHeaders.PEER_HOPS, String.valueOf(currentHops() + 1));
        return headers;
    }

    private int currentHops() {
        return OutboundHeaders.currentRequest()
                .map(r -> r.getHeader(AisafeHeaders.PEER_HOPS))
                .map(v -> {
                    try {
                        return Integer.parseInt(v);
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                })
                .orElse(0);
    }

    private <T> T await(CompletableFuture<T> future, T fallback, AtomicInteger failed) {
        try {
            return future.get(aggregationTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            failed.incrementAndGet();
            log.warn("Peers did not answer within {}", aggregationTimeout);
            return fallback;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    /** Marca a resposta HTTP em curso como parcial (alguns peers não responderam). */
    private static void markPartial(int unavailablePeers) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletResponse response = attrs.getResponse();
            if (response != null && !response.isCommitted()) {
                response.setHeader(AisafeHeaders.PARTIAL_RESPONSE, "true");
                response.setHeader(AisafeHeaders.UNAVAILABLE_PEERS, String.valueOf(unavailablePeers));
            }
        }
    }

    private static String errorMessage(HttpClientErrorException e) {
        try {
            Map<?, ?> body = e.getResponseBodyAs(Map.class);
            if (body != null && body.get("error") != null) {
                return String.valueOf(body.get("error"));
            }
        } catch (RuntimeException ignored) {
            // corpo não é JSON: usa-se o texto
        }
        String text = e.getResponseBodyAsString();
        return text.isBlank() ? e.getStatusText() : text;
    }

    private static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + (root.getMessage() == null ? "" : ": " + root.getMessage());
    }

    @Override
    public void close() {
        pool.shutdownNow();
    }
}
