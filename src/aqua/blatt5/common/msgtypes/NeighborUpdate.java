package aqua.blatt5.common.msgtypes;

import java.io.Serializable;
import java.net.InetSocketAddress;

public class NeighborUpdate implements Serializable {
    private final InetSocketAddress leftNeighbor;
    private final InetSocketAddress rightNeighbor;

    public NeighborUpdate(InetSocketAddress left, InetSocketAddress right) {
        this.leftNeighbor = left;
        this.rightNeighbor = right;
    }

    public InetSocketAddress getLeftNeighbor() {
        return leftNeighbor;
    }

    public InetSocketAddress getRightNeighbor() {
        return rightNeighbor;
    }
}