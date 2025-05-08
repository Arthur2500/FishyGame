package aqua.blatt4.common.msgtypes;

import java.io.Serializable;
import java.net.InetSocketAddress;
import aqua.blatt1.common.Direction;

public class NeighborUpdate implements Serializable {
    private final Direction direction;
    private final InetSocketAddress neighbor;

    public NeighborUpdate(Direction direction, InetSocketAddress neighbor) {
        this.direction = direction;
        this.neighbor = neighbor;
    }

    public Direction getDirection() {
        return direction;
    }

    public InetSocketAddress getNeighbor() {
        return neighbor;
    }
}