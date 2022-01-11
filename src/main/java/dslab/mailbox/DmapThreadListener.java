package dslab.mailbox;

import dslab.entity.Mail;
import dslab.secure.DmapSecure;
import dslab.util.Config;
import dslab.util.UserMailBox;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

public class DmapThreadListener extends Thread {

    private final Map<String, UserMailBox> mailBoxes;

    private final String componentId;
    private final Config users;
    private final Socket socket;

    private String username = null;
    private boolean protocolError = false;

    private DmapSecure dmapSecure;

    public DmapThreadListener(String componentId, Socket socket, Config users, Map<String, UserMailBox> mailBoxes) {

        this.componentId = componentId;
        this.socket = socket;
        this.users = users;
        this.mailBoxes = mailBoxes;

    }

    public void run() {

        while (true) {
            try {

                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter writer = new PrintWriter(socket.getOutputStream());

                // response for valid connection
                writer.println("ok DMAP2.0");
                writer.flush();

                String request;
                while ((request = reader.readLine()) != null) {

                    if (dmapSecure != null) {
                        request = dmapSecure.decryptMessage(request);
                    }

                    String[] parts = request.split("\\s");
                    String response = "ok";
                    if (parts.length == 0) continue;

                    switch (parts[0]) {
                        case "startsecure":
                            dmapSecure = new DmapSecure(reader, writer, componentId);
                            dmapSecure.performHandshakeServer();
                            break;
                        case "login":
                            if (parts.length != 3) protocolError = true;
                            else {
                                if (users.containsKey(parts[1]))
                                    if (users.getString(parts[1]).equals(parts[2]))
                                        username = parts[1];
                                    else response = "error wrong password";
                                else response = "error unknown user";
                            }
                            break;
                        case "list":
                            if (parts.length != 1) protocolError = true;
                            else if (username == null) response = "error not logged in";
                            else response = mailBoxes.get(username).listMailsAsString();
                            break;
                        case "show":
                            if (parts.length != 2) protocolError = true;
                            else if (username == null) response = "error not logged in";
                            else {
                                try {
                                    int id = Integer.parseInt(parts[1]);
                                    Mail mail = mailBoxes.get(username).getMail(id);
                                    if (mail == null) response = "error unknown message id";
                                    else response = mail.toString();
                                    break;
                                } catch (NumberFormatException e) {
                                    protocolError = true;
                                    break;
                                }
                            }
                            break;
                        case "delete":
                            try {
                                if (parts.length != 2) protocolError = true;
                                else if (username == null) response = "error not logged in";
                                else {
                                    int id = Integer.parseInt(parts[1]);
                                    if (!mailBoxes.get(username).deleteMail(id))
                                        response = "error unknown message id";
                                }
                                break;
                            } catch (NumberFormatException e) {
                                protocolError = true;
                                break;
                            }
                        case "logout":
                            if (parts.length != 1) protocolError = true;
                            else if (username == null) response = "error not logged in";
                            else username = null;
                            break;
                        case "quit":
                            writer.println("ok bye");
                            writer.flush();
                            socket.close();
                            break;
                        default:
                            protocolError = true;
                            break;
                    }

                    if (protocolError) {
                        writer.println("error protocol error");
                        writer.flush();
                        socket.close();
                    }

                    if (dmapSecure == null) {
                        writer.println(response);
                        writer.flush();
                    } else {
                        dmapSecure.sendMessage(response);
                    }
                }

            } catch (SocketException e) {
                System.out.println("SocketException while handling socket: " + e.getMessage());
                break;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                if (socket != null && !socket.isClosed()) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        // Ignored because we cannot handle it
                    }
                }

            }

        }
    }
}
