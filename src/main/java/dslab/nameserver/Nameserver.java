package dslab.nameserver;

import java.io.InputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.util.Config;

public class Nameserver implements INameserver, INameserverRemote, Serializable {

    private Registry registry;

    private final Map<String,INameserverRemote> zonesTable;
    private final Map<String, String> mailTable;

    private String domain;
    private String rootId;
    private int registryPort;

    private final Shell shell;

    private final SimpleDateFormat formatter= new SimpleDateFormat("HH:mm:ss");

    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public Nameserver(String componentId, Config config, InputStream in, PrintStream out) {

        zonesTable = new ConcurrentHashMap<>();
        mailTable = new ConcurrentHashMap<>();

        this.domain = config.containsKey("domain") ? config.getString("domain") : null;
        this.rootId = config.getString("root_id");
        this.registryPort = config.getInt("registry.port");
        String host = config.getString("registry.host");

        try {
            if (this.domain == null) {
                // bind root ns in registry
                registry = LocateRegistry.createRegistry(this.registryPort);
                INameserverRemote remote = (INameserverRemote) UnicastRemoteObject.exportObject(this, 0);
                registry.rebind(this.rootId, remote);

            } else {
                // register zone-ns
                registry = LocateRegistry.getRegistry(host,this.registryPort);
                INameserverRemote rootRemoteObject = (INameserverRemote) registry.lookup(this.rootId);
                rootRemoteObject.registerNameserver(this.domain, this);
            }

        } catch (RemoteException | NotBoundException | InvalidDomainException | AlreadyRegisteredException e) {
            e.printStackTrace();
        }

        // instantiate and setup I/O-Shell
        this.shell = new Shell(in, out);
        shell.register(this);
        shell.setPrompt(componentId + "> ");

    }

    @Override
    public void run() {

        //TODO: Threading
        shell.run();
    }

    @Override
    public void registerNameserver(String domain, INameserverRemote nameserver) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
        List<String> subdomains = new ArrayList(Arrays.asList(domain.split("\\.")));
        if (subdomains.size() == 1) {
            addNewNameserver(domain, nameserver);
        } else {
            int pos = subdomains.size()-1;
            INameserverRemote subdomain = this.getNameserver(subdomains.get(pos));
            if (subdomains != null) {
                subdomains.remove(pos);
                subdomain.registerNameserver(String.join(".",subdomains),nameserver);
            } else {
                throw new InvalidDomainException(
                        String.format("There does not exist a subdomain '%s' in ns-%s", domain, this.domain)
                );
            }
        }
    }

    @Override
    public void registerMailboxServer(String domain, String address) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
        List<String> subdomains = new ArrayList(Arrays.asList(domain.split("\\.")));
        if (subdomains.size() == 1) {
            addNewMailbox(domain, address);
        } else {
            int pos = subdomains.size()-1;
            INameserverRemote subdomain = this.getNameserver(subdomains.get(pos));
            if (subdomains != null) {
                subdomains.remove(pos);
                subdomain.registerMailboxServer(String.join(".",subdomains),address);
            } else {
                throw new InvalidDomainException(
                        String.format("There does not exist a subdomain '%s' in ns-%s", domain, this.domain)
                );
            }
        }
    }

    @Override
    public INameserverRemote getNameserver(String zone) throws RemoteException {
        return zonesTable.get(zone);
    }

    @Override
    public String lookup(String username) throws RemoteException {
        Date date = new Date(System.currentTimeMillis());
        shell.out().println(
                String.format("%s : Nameserver for '%s' requested by transfer server",formatter.format(date),username)
        );
        return mailTable.get(username);
    }

    private void addNewNameserver(String domain, INameserverRemote nameserver) throws AlreadyRegisteredException {
        if (zonesTable.putIfAbsent(domain, nameserver) != null) {
            throw new AlreadyRegisteredException(String.format("Nameserver with domain '%s' already exists.",domain));
        }
        Date date = new Date(System.currentTimeMillis());
        shell.out().println(String.format("%s : Registering nameserver for zone '%s'",formatter.format(date),domain));
    }

    private void addNewMailbox(String domain, String address) throws AlreadyRegisteredException {
        if (mailTable.putIfAbsent(domain, address) != null) {
            throw new AlreadyRegisteredException(String.format("MailBox with domain '%s' already exists.",domain));
        }
    }

    @Command
    @Override
    public void nameservers() {
        for (String domain: zonesTable.keySet().stream().sorted().collect(Collectors.toList())) {
            shell.out().println(domain);
        }
    }

    @Command
    @Override
    public void addresses() {
        for (String domain: mailTable.keySet().stream().sorted().collect(Collectors.toList())) {
            shell.out().println(domain + " " + mailTable.get(domain));
        }
    }

    @Command
    @Override
    public void shutdown() {

            try {

                //TODO: müssen zone RMIs exported werden, weil sonst ist die Zeile sinnlos
                //UnicastRemoteObject.unexportObject(this,true);
                if(this.domain == null) {
                    this.registry.unbind(this.rootId);
                    UnicastRemoteObject.unexportObject(this.registry,true);
                }

            } catch (RemoteException | NotBoundException e) {
                e.printStackTrace();
            }
    }

    public static void main(String[] args) throws Exception {
        INameserver component = ComponentFactory.createNameserver(args[0], System.in, System.out);
        component.run();
    }

}
