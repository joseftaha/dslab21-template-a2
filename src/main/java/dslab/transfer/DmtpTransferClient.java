package dslab.transfer;

import dslab.entity.Mail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

public class DmtpTransferClient {


    public DmtpTransferClient (){

    }

    public boolean forwardMail(String domain, Integer port, Mail mailToForward) {
        try{

            Socket socket = new Socket(domain,port);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter writer = new PrintWriter(socket.getOutputStream());

            writer.write("begin");
            if (!reader.readLine().split(" ")[0].equals("ok")) return false;
            writer.write("from " + mailToForward.getFrom());
            if (!reader.readLine().split(" ")[0].equals("ok")) return false;
            writer.write("to " + String.join(" ",mailToForward.getTo()));
            if (!reader.readLine().split(" ")[0].equals("ok")) return false;
            writer.write("subject "+mailToForward.getSubject());
            if (!reader.readLine().split(" ")[0].equals("ok")) return false;
            writer.write("data "+mailToForward.getData());
            if (!reader.readLine().split(" ")[0].equals("ok")) return false;
            writer.write("send");
            if (!reader.readLine().split(" ")[0].equals("ok")) return false;
            writer.write("quit");

            socket.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

        return true;

    }
}
