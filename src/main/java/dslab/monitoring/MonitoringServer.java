package dslab.monitoring;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.*;
import java.util.Hashtable;
import java.util.Map;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.util.Config;

public class MonitoringServer implements IMonitoringServer {

    private Map<String, Integer> servers;
    private Map<String, Integer> addresses;

    private final Config config;
    private final Shell shell;

    private DatagramSocket datagramSocket;

    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public MonitoringServer(String componentId, Config config, InputStream in, PrintStream out) {

        this.config = config;

        this.servers = new Hashtable<>();
        this.addresses = new Hashtable<>();

        // instantiate and setup I/O-Shell
        this.shell = new Shell(in, out);
        shell.register(this);
        shell.setPrompt(componentId + "> ");

    }

    @Override
    public void run() {

        Thread shellTread = new Thread(shell);
        shellTread.start();

        try {

            datagramSocket = new DatagramSocket(config.getInt("udp.port"));
            byte[] buffer;
            DatagramPacket packet;

            while (true) {

                buffer = new byte[1024];
                packet = new DatagramPacket(buffer, buffer.length);

                // wait for incoming packets from client
                datagramSocket.receive(packet);
                // get the data from the packet
                String request = new String(packet.getData(), 0, packet.getLength());


                String[] parts = request.split("\\s");
                // format error
                if(parts.length != 2){
                    continue;
                }

                String server = parts[0];
                String address = parts[1];

                int count = servers.getOrDefault(server, 0);
                servers.put(server, count + 1);

                count = addresses.getOrDefault(address, 0);
                addresses.put(address, count + 1);

            }

        } catch (SocketException e) {
            System.out.println("SocketException while waiting for/handling packets: " + e.getMessage());
            return;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Command
    @Override
    public void addresses() {
        for (String address: addresses.keySet()) {
            shell.out().println(address + " " + addresses.get(address));
        }
    }

    @Command
    @Override
    public void servers() {
        for (String server: servers.keySet()) {
            shell.out().println(server + " " + servers.get(server));
        }
    }

    @Command
    @Override
    public void shutdown() {
        if (datagramSocket != null && !datagramSocket.isClosed()) {
                datagramSocket.close();
        }
        throw new StopShellException();
    }

    public static void main(String[] args) throws Exception {
        IMonitoringServer server = ComponentFactory.createMonitoringServer(args[0], System.in, System.out);
        server.run();
    }

}
