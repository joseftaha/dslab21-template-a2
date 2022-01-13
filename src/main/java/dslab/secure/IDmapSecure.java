package dslab.secure;

public interface IDmapSecure {

    void performHandshakeClient() throws HandshakeException;

    void performHandshakeServer() throws HandshakeException;

    void sendMessage(String message);

    String readMessage();

}
