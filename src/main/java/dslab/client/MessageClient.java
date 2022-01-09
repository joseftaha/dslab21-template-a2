package dslab.client;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.util.Config;

public class MessageClient implements IMessageClient, Runnable {

    private Shell shell;
    private Config config;
    private String loggedInUser = null;


    /**
     * Creates a new client instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public MessageClient(String componentId, Config config, InputStream in, PrintStream out) {
        this.config = config;
        this.shell = new Shell(in, out);
        this.shell.register(this);
        this.shell.setPrompt(componentId + "> ");
        System.out.println(config);
    }

    @Override
    public void run() {
        shell.run();
        System.out.println("Exiting shell");
    }

    @Override
    @Command
    public void inbox() {

    }

    @Override
    @Command
    public void delete(String id) {

    }

    @Override
    @Command
    public void verify(String id) {

    }

    @Override
    @Command
    public void msg(String to, String subject, String data) {

    }

    @Override
    @Command
    public void shutdown() {
        throw new StopShellException();
    }

    static void shutdownAndAwaitTermination(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS))
                    System.err.println("Threadpool could not terminate");
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void connectDMAP() {

    }

    public static void main(String[] args) throws Exception {
        IMessageClient client = ComponentFactory.createMessageClient(args[0], System.in, System.out);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        executor.execute(client);

        shutdownAndAwaitTermination(executor);
        System.out.println("fsdfsadf");
    }
}
