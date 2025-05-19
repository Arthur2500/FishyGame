package aqua.blatt6.common.msgtypes;

import java.io.Serializable;
import java.net.InetSocketAddress;

public class LocationUpdate implements Serializable {
    private final String fishId;
    private final InetSocketAddress location;

    public LocationUpdate(String fishId, InetSocketAddress location) {
        this.fishId = fishId;
        this.location = location;
    }

    public String getFishId() {
        return fishId;
    }

    public InetSocketAddress getLocation() {
        return location;
    }
}