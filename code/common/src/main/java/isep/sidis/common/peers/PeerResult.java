package isep.sidis.common.peers;

import java.util.List;

/**
 * Respostas recolhidas das réplicas peer numa agregação.
 *
 * @param responses   corpos das respostas das réplicas que responderam com sucesso
 * @param failedPeers nº de réplicas que não responderam (timeout, erro, circuito aberto)
 * @param totalPeers  nº de réplicas consultadas
 */
public record PeerResult<T>(List<T> responses, int failedPeers, int totalPeers) {

    public static <T> PeerResult<T> empty() {
        return new PeerResult<>(List.of(), 0, 0);
    }

    public boolean isPartial() {
        return failedPeers > 0;
    }
}
