package isep.sidis.common.http;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Interceptor do RestClient usado para chamar OUTROS microsserviços: acrescenta o JWT do utilizador,
 * o correlation id e a identidade do serviço (ver {@link OutboundHeaders}). Não sobrepõe headers
 * que o pedido já traga (ex.: o PeerClient define-os explicitamente).
 */
public class InterServiceHeadersInterceptor implements ClientHttpRequestInterceptor {

    private final OutboundHeaders outboundHeaders;

    public InterServiceHeadersInterceptor(OutboundHeaders outboundHeaders) {
        this.outboundHeaders = outboundHeaders;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        outboundHeaders.capture().forEach((name, value) -> {
            if (!request.getHeaders().containsHeader(name)) {
                request.getHeaders().set(name, value);
            }
        });
        return execution.execute(request, body);
    }
}
