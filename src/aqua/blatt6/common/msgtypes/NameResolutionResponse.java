package aqua.blatt6.common.msgtypes;

import java.io.Serializable;
import java.net.InetSocketAddress;

public class NameResolutionResponse implements Serializable {
    private final String requestId;
    private final InetSocketAddress address;

    public NameResolutionResponse(String requestId, InetSocketAddress address) {
        this.requestId = requestId;
        this.address = address;
    }

    public String getRequestId() {
        return requestId;
    }

    public InetSocketAddress getAddress() {
        return address;
    }
}