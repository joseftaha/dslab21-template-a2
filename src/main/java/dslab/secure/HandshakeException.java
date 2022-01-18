package dslab.secure;

public class HandshakeException extends Exception {

    public HandshakeException(String message) {
        super(message);
    }

    public HandshakeException(String message, Throwable cause) {
        super(message, cause);
    }
}
