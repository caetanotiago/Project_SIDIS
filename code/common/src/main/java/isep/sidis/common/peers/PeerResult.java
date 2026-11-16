package isep.sidis.common.peers;

import java.util.List;

public record PeerResult<T>(List<T> responses, int failedPeers, int totalPeers) {
    public static <T> PeerResult<T> empty() {
        return new PeerResult<>(List.of(), 0, 0);
    }

    public boolean isPartial() {
        return failedPeers > 0;
    }
}
