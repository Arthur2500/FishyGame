package aqua.blatt6.common.msgtypes;
import java.io.Serializable;

public final class RegisterResponse implements Serializable {
    private final String id;
    private final int leaseDuration;

    public RegisterResponse(String id, int leaseDuration) {
        this.id = id;
        this.leaseDuration = leaseDuration;
    }

    public String getId() {
        return id;
    }

    public int getLeaseDuration() {
        return leaseDuration;
    }
}