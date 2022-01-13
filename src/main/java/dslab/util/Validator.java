package dslab.util;

import dslab.entity.Mail;
import dslab.secure.DmtpSecure;

import java.util.Arrays;
import java.util.stream.Stream;

public class Validator {

    private final DmtpSecure dmtpSecure;

    public Validator() {
        this.dmtpSecure = new DmtpSecure();
    }


    public String validateMail(Mail mail){

        if(mail == null){
            return "no begin";
        }

        if(mail.getTo() == null){
            return "error no receiver";
        }

        if(mail.getData() == null){
            return "error no data";
        }

        if(mail.getFrom() == null){
            return "error no sender";
        }

        if(mail.getSubject() == null){
            return "error no subject";
        }

        return null;
    }

    public String[] checkForUnknownUser(Mail mail, Config mailBoxUsers, String domain){
        String[] reciever = mail.getTo().split(" ");
        return Arrays.stream(reciever).filter(x -> {
            String[] split = x.split("@");
            return !mailBoxUsers.listKeys().contains(split[0]) && split[1].equals(domain);
        }).distinct().toArray(String[]::new);
    }

    public Stream<String> usersInMailBoxList(Mail mail, Config mailBoxUsers, String domain) {
        String[] reciever = mail.getTo().split(" ");
        return Arrays.stream(reciever).filter(x -> {
            String[] split = x.split("@");
            return mailBoxUsers.listKeys().contains(split[0]) && split[1].equals(domain);
        }).distinct();
    }

}
