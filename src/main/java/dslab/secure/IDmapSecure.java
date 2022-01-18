package dslab.secure;

public interface IDmapSecure {

    /**
     * client performs the handshake with the server
     */
    void performHandshakeClient() throws HandshakeException;

    /**
     * server performs handshake with the client
     */
    void performHandshakeServer() throws HandshakeException;

    /**
     * encrypts and sends a message
     * @param message to be sent
     */
    void sendMessage(String message);

    /**
     * receives and decrypts a message
     * @return a human-readable representation of the message
     */
    String readMessage();

}
