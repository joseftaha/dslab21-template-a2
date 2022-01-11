package dslab.secure;

public interface IDmapSecure {

    void performHandshakeClient();

    void performHandshakeServer();

    void sendMessage(String message);

    String readMessage();

}
