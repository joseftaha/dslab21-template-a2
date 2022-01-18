package dslab.transfer;

import dslab.entity.Mail;
import dslab.secure.DmtpSecure;
import dslab.util.Validator;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;

public class DmtpTransferThreadListener extends Thread{

    private final BlockingQueue<Mail> mailQueue;
    private final Validator validator;
    private final Socket socket;
    private Mail mail;
    private boolean protocolError = false;

    private final DmtpSecure dmtpSecure;

    public DmtpTransferThreadListener(Socket socket, BlockingQueue<Mail> mailQueue) {

        this.mailQueue = mailQueue;
        this.socket = socket;
        this.validator = new Validator();

        this.dmtpSecure = new DmtpSecure();

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

                    System.out.println(request);

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
                                response += " " + data.length;
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
                        case "hash":
                            if(validator.validateMail(mail) != null) {
                                response = "error please finish the message before calculating hash";
                            } else {
                                String hash = dmtpSecure.signMessage(mail);
                                mail.setHash(hash);
                                response += " " + hash;
                            }
                            break;
                        case "send":
                            String validation = validator.validateMail(mail);
                            if(validation == null) {
                                mailQueue.add(mail);
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
