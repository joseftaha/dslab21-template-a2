package dslab.secure;

public interface IDmapSecure {

    /**
     * client performs the handshake with the server
     */
    void performHandshakeClient();

    /**
     * server performs handshake with the client
     */
    void performHandshakeServer();

    /**
     * encrypts and sends a message
     * @param message
     */
    void sendMessage(String message);

    /**
     * receives and decrypts a message
     * @return a human-readable representation of the message
     */
    String readMessage();

}
