package dslab.mailbox;

import dslab.entity.Mail;
import dslab.util.Config;
import dslab.util.UserMailBox;
import dslab.util.Validator;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Map;

public class DmtpMailThreadListener extends Thread {

    private final Config config;
    private final Config users;
    private final Map<String, UserMailBox> mailBoxes;
    private final Validator validator;
    private final Socket socket;
    private Mail mail;
    private boolean protocolError = false;

    public DmtpMailThreadListener(Socket socket,Config config, Config users, Map<String, UserMailBox> mailBoxes) {

        this.config = config;
        this.mailBoxes = mailBoxes;
        this.socket = socket;
        this.validator = new Validator();
        this.users = users;

    }

    private void persistMail(String username, Mail mail){
        mailBoxes.get(username).putMail(mail);
    }

    public void run() {

        while (true) {
            try {

                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter writer = new PrintWriter(socket.getOutputStream());

                // response for valid connection
                writer.println("ok DMTP");
                writer.flush();

                String request;
                while ((request = reader.readLine()) != null) {

                    String[] parts = request.split("\\s");
                    String response = "ok";

                    if (parts.length == 0) continue;

                    switch (parts[0]) {

                        case "begin":
                            if (parts.length > 1) protocolError = true;
                            else mail = new Mail();
                            break;
                        case "from":
                            if (parts.length != 2) protocolError = true;
                            else if (mail == null) response = "error no begin";
                            else mail.setFrom(parts[1]);
                            break;
                        case "to":
                            if (parts.length < 2) protocolError = true;
                            else if (mail == null) response = "error no begin";
                            else {
                                String[] data = Arrays.copyOfRange(parts, 1, parts.length);
                                mail.setTo(String.join(" ",data));
                                String[] unknownUsers = validator.checkForUnknownUser(mail, users, config.getString("domain"));
                                if (unknownUsers.length != 0)
                                    response = "error unknown recipient " + unknownUsers[0].split("@")[0];
                                else response += " " + validator.usersInMailBoxList(mail, users, config.getString("domain")).count();
                            }
                            break;
                        case "subject":
                            if (mail == null) response = "error no begin";
                            else {
                                String[] data = Arrays.copyOfRange(parts, 1, parts.length);
                                mail.setSubject(String.join(" ", data));
                            }
                            break;
                        case "data":
                            if (parts.length < 2) protocolError = true;
                            else if (mail == null) response = "error no begin";
                            else {
                                String[] data = Arrays.copyOfRange(parts, 1, parts.length);
                                mail.setData(String.join(" ", data));
                            }
                            break;
                        case "send":
                            String validation = validator.validateMail(mail);
                            if(validation == null) {
                                validator.usersInMailBoxList(mail, users,config.getString("domain")).forEach(domain -> {
                                    String username = domain.split("@")[0];
                                    persistMail(username,mail);
                                });
                                mail = null;
                            }
                            else response = validation;
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

                    if(protocolError){
                        writer.println("error protocol error");
                        writer.flush();
                        socket.close();
                    }

                    writer.println(response);
                    writer.flush();

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
