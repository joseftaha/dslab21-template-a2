package dslab.transfer;

import java.io.*;
import java.net.*;
import java.security.spec.RSAOtherPrimeInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.stream.Stream;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.entity.Mail;
import dslab.util.Config;


public class TransferServer implements ITransferServer, Runnable {

    private BlockingQueue<Mail> mailQueue;

    private final Config config;
    private final Config lookUpTable;
    private final ExecutorService executor;
    private final Shell shell;

    private ServerSocket serverSocket;
    private DatagramSocket datagramSocket;



    /**
     * Creates a new server instance.
     *  @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public TransferServer(String componentId, Config config, Config lookUpTable, InputStream in, PrintStream out) {

        this.config = config;
        this.lookUpTable = lookUpTable;

        // instantiate ThreadPoolExecutor
        this.executor = Executors.newFixedThreadPool(8);

        // instantiate Blocking Queue
        this.mailQueue = new LinkedBlockingDeque<>();

        // instantiate and setup I/O-Shell
        this.shell = new Shell(in, out);
        shell.register(this);
        shell.setPrompt(componentId + "> ");

    }


    @Override
    public void run() {

        // run MTServerListener in new Thread
        executor.execute(()->{
            try {
                serverSocket = new ServerSocket(config.getInt("tcp.port"));
                shell.out().println("TCP Server is up!");

                while(true){
                    Socket socket = serverSocket.accept();
                    executor.execute(new DmtpTransferThreadListener(socket,mailQueue));
                }

            } catch (IOException e) {
                throw new UncheckedIOException("Error while creating server socket", e);
            }
        });

        // run Mail Forwarding in new Thread
        executor.execute(()->{
            try {
                while(true){

                    Mail mail = mailQueue.take();
                    if(mail.getFrom() == null) break;
                    forwardMonitoring(mail);
                    forwardMailBox(mail);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        // run Shell
        shell.run();

    }


    private void forwardMailBox(Mail mail) {

        Stream<String> domains = Arrays.stream(mail.getTo().split(" ")).map(to-> to.split("@")[1]).distinct();

        // save all domain strings of MailBoxes where forwarding was not possible
        ArrayList<String> errorMailBoxes = new ArrayList<>();

        // try sending mail to all mailBox-domains
        domains.forEach(domain -> {
            if (lookUpTable.containsKey(domain)){

                String domainAndPort = lookUpTable.getString(domain);
                String mailBoxDomain = domainAndPort.split(":")[0];
                int mailBoxPort = Integer.parseInt(domainAndPort.split(":")[1]);

                if (!connectDmtpClientAndSend(mailBoxDomain,mailBoxPort, mail)){
                    errorMailBoxes.add(domain);
                }

            } else {
                errorMailBoxes.add(domain);
            }
        });

        // try to send error message to sender
        if(!errorMailBoxes.isEmpty()){
            if(lookUpTable.containsKey(mail.getFrom().split("@")[1])){
                try{
                    Mail errorMail = new Mail();

                    errorMail.setFrom(String.format("mailer@%s",InetAddress.getLocalHost().getHostAddress()));
                    errorMail.setTo(mail.getFrom());
                    errorMail.setSubject(String.format("Error sending mail with subject: %s",mail.getSubject()));
                    errorMail.setData(String.format(
                            "Problem sending to following domain(s): %s",
                            String.join(", ",errorMailBoxes))
                    );

                    String[] domainAndPort = lookUpTable.getString(mail.getFrom().split("@")[1]).split(":");
                    connectDmtpClientAndSend(
                            domainAndPort[0],
                            Integer.parseInt(domainAndPort[1]),
                            errorMail
                    );

                } catch (UnknownHostException e){
                    e.printStackTrace();
                }
            }
        }
    }

    private boolean connectDmtpClientAndSend(String domain, Integer port, Mail mailToForward){

        try{

            Socket socket = new Socket(domain,port);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter writer = new PrintWriter(socket.getOutputStream());

            if (!reader.readLine().split(" ")[0].equals("ok")) return false;

            writer.println("begin");
            writer.flush();
            if (!reader.readLine().split(" ")[0].equals("ok")) return false;
            writer.println("from " + mailToForward.getFrom());
            writer.flush();
            if (!reader.readLine().split(" ")[0].equals("ok")) return false;
            writer.println("to " + String.join(" ",mailToForward.getTo()));
            writer.flush();
            if (!reader.readLine().split(" ")[0].equals("ok")) return false;
            writer.println("subject "+mailToForward.getSubject());
            writer.flush();
            if (!reader.readLine().split(" ")[0].equals("ok")) return false;
            writer.println("data "+mailToForward.getData());
            writer.flush();
            if (!reader.readLine().split(" ")[0].equals("ok")) return false;
            writer.println("send");
            writer.flush();
            if (!reader.readLine().split(" ")[0].equals("ok")) return false;
            writer.println("quit");
            writer.flush();

            socket.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

        return true;

    }

    private void forwardMonitoring(Mail mail) {

        try{

            if(datagramSocket == null){
                datagramSocket = new DatagramSocket();
            }

            String data = String.format("%s:%d %s",
                    InetAddress.getLocalHost().getHostAddress(),
                    config.getInt("tcp.port"), mail.getFrom()
            );

            byte[] buffer = data.getBytes();

            DatagramPacket packet = new DatagramPacket(buffer, buffer.length,
                    InetAddress.getByName(config.getString("monitoring.host")),
                    config.getInt("monitoring.port"));

            // send statistic-packet to monitoring-server
            datagramSocket.send(packet);

        } catch (SocketException | UnknownHostException e){
            System.err.println("Error while opening datagram socket: " + e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    @Override
    @Command
    public void shutdown() {
        if (serverSocket != null) {
            try {

                // close socket-server
                serverSocket.close();
                // close executor
                executor.shutdown();
                // close queue-consumer
                Mail error = new Mail();
                error.setFrom(null);
                mailQueue.add(error);

            } catch (IOException e) {
                System.err.println("Error while closing server socket: " + e.getMessage());
            }
        }

        // close shell
        throw new StopShellException();
    }

    public static void main(String[] args) throws Exception {
        ITransferServer server = ComponentFactory.createTransferServer(args[0], System.in, System.out);
        server.run();
    }

}