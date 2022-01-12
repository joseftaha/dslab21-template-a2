package dslab.client;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.Buffer;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.secure.DmapSecure;
import dslab.util.Config;
import dslab.util.Keys;

import javax.crypto.spec.SecretKeySpec;

public class MessageClient implements IMessageClient, Runnable {

    private Shell shell;
    private Config config;
    private SecretKeySpec hmac;
    private String componentId;

/*    private Socket socketDMAP;
    BufferedReader readerDMAP;
    PrintWriter writerDMAP;*/

    private Socket socketDMTP;
    BufferedReader readerDMTP;
    PrintWriter writerDMTP;

    private DmapSecure dmapSecure;

    /**
     * Creates a new client instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config      the component config
     * @param in          the input stream to read console input from
     * @param out         the output stream to write console output to
     */
    public MessageClient(String componentId, Config config, InputStream in, PrintStream out) {
        this.componentId = componentId;
        this.config = config;
        try {
            this.hmac = Keys.readSecretKey(new File("keys/hmac.key"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.shell = new Shell(in, out);
        this.shell.register(this);
        this.shell.setPrompt(componentId + "> ");

        if (!connectDMAP()) shell.out().println("Could not connect to mailbox server");
    }

    @Override
    public void run() {
        shell.run();
        shell.out().println("Exiting shell");
    }

    @Override
    @Command
    public void startsecure() {

    }

    @Override
    @Command
    public void inbox() {
        dmapSecure.sendMessage("list");
        ArrayList<String> ids = new ArrayList<>();

        String[] response = dmapSecure.readMessage().split("\n");
        for (String line : response) {
            if (line.equals("none")) {
                shell.out().println("No messages in inbox");
                break;
            } else if (line.equals("ok")) {
                break;
            } else ids.add(line.split(" ")[0]);
        }

        // Formatting
        if(!ids.isEmpty()) {
            shell.out().println();
            shell.out().println("=========================");
            shell.out().println("_,.-#*' I N B O X '*#-.,_");
            shell.out().println("=========================");
            shell.out().println();
        }

        for (String id : ids) {
            dmapSecure.sendMessage(String.format("show %s", id));
            String[] answer = dmapSecure.readMessage().split("\n");
            if (answer[0].equals("error unknown message id")) {
                shell.out().println("Error unknown message ID: " + id);
                return;
            }
            for (String line : answer) {
                if (line.equals("ok")) {
                    shell.out().println();
                    break;
                } else if (line.startsWith("from")) {
                    shell.out().println("ID: " + id);
                    shell.out().println(line);
                } else {
                    shell.out().println(line);
                }
            }
        }
    }

    @Override
    @Command
    public void delete(String id) {
        dmapSecure.sendMessage(String.format("delete %s", id));
        String response = dmapSecure.readMessage();
        if (!response.split(" ")[0].equals("ok")) shell.out().println("ok");
        else shell.out().println(response);
    }

    @Override
    @Command
    public void verify(String id) {
        System.out.println();
    }

    @Override
    @Command
    public void msg(String to, String subject, String data) {
        if (!connectDMTP()) shell.out().println("Could not connect to transfer server");

        try {
            writerDMTP.println("begin");
            writerDMTP.flush();
            String response = readerDMTP.readLine();
            if (!response.split(" ")[0].equals("ok")) {
                shell.out().println("error");
                return;
            }

            writerDMTP.println(String.format("from %s", config.getString("transfer.email")));
            writerDMTP.flush();
            response = readerDMTP.readLine();
            if (!response.split(" ")[0].equals("ok")) {
                shell.out().println("error");
                return;
            }

            writerDMTP.println(String.format("to %s", to));
            writerDMTP.flush();
            response = readerDMTP.readLine();
            if (!response.split(" ")[0].equals("ok")) {
                shell.out().println("error");
                return;
            }

            writerDMTP.println(String.format("subject %s", subject));
            writerDMTP.flush();
            response = readerDMTP.readLine();
            if (!response.split(" ")[0].equals("ok")) {
                shell.out().println("error");
                return;
            }

            writerDMTP.println(String.format("data %s", data));
            writerDMTP.flush();
            response = readerDMTP.readLine();
            if (!response.split(" ")[0].equals("ok")) {
                shell.out().println("error");
                return;
            }

            writerDMTP.println("send");
            writerDMTP.flush();
            response = readerDMTP.readLine();
            if (!response.split(" ")[0].equals("ok")) {
                shell.out().println("error");
                return;
            }

            writerDMTP.println("quit");
            writerDMTP.flush();
            response = readerDMTP.readLine();
            if (!response.split(" ")[0].equals("ok")) {
                shell.out().println("error");
                return;
            }

            shell.out().println("ok");
            socketDMTP.close();

        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    @Override
    @Command
    public void shutdown() {
        //socketDMAP.close();
        throw new StopShellException();
    }

    public boolean connectDMAP() {
        try {
            Socket socketDMAP = new Socket(config.getString("mailbox.host"), config.getInt("mailbox.port"));
            BufferedReader readerDMAP = new BufferedReader(new InputStreamReader(socketDMAP.getInputStream()));
            PrintWriter writerDMAP = new PrintWriter(socketDMAP.getOutputStream());

            if (readerDMAP.readLine().equals("ok DMAP2.0")) ;
            dmapSecure = new DmapSecure(readerDMAP, writerDMAP, this.componentId);
            dmapSecure.performHandshakeClient();
            if (!dmapSecure.readMessage().equals("ok")) {
                shell.out().println("Error: Handshake failed");
            }


            //perform login
            String loginMessage = String.format("login %s %s", config.getString("mailbox.user"), config.getString("mailbox.password"));
            dmapSecure.sendMessage(loginMessage);

            String response = dmapSecure.readMessage();
            return response.split(" ").length == 1 && response.split(" ")[0].equals("ok");

        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean connectDMTP() {
        try {
            socketDMTP = new Socket(config.getString("transfer.host"), config.getInt("transfer.port"));
            readerDMTP = new BufferedReader(new InputStreamReader(socketDMTP.getInputStream()));
            writerDMTP = new PrintWriter(socketDMTP.getOutputStream());
            return readerDMTP.readLine().split(" ")[0].equals("ok");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static void main(String[] args) throws Exception {
        IMessageClient client = ComponentFactory.createMessageClient(args[0], System.in, System.out);
        client.run();
    }
}
