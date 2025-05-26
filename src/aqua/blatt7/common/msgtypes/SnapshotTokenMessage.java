package aqua.blatt7.common.msgtypes;

import java.io.Serializable;

/**
 * Token message carrying intermediate snapshot sums around the ring.
 */
public class SnapshotTokenMessage implements Serializable {
    private final String initiatorId;
    private final int sum;

    public SnapshotTokenMessage(String initiatorId, int sum) {
        this.initiatorId = initiatorId;
        this.sum = sum;
    }

    public String getInitiatorId() {
        return initiatorId;
    }

    public int getSum() {
        return sum;
    }
}