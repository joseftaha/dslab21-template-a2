package dslab.util;

import dslab.entity.Mail;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class UserMailBox {

    private AtomicInteger counter;
    private ConcurrentHashMap<Integer, Mail> mails;


    public UserMailBox() {
        this.counter = new AtomicInteger(1);
        this.mails = new ConcurrentHashMap<>();
    }

    public boolean putMail(Mail mail){
        Mail previousMail = mails.put(counter.getAndIncrement(), mail);
        return previousMail == null;
    }

    public boolean deleteMail(Integer id){
        Mail deleted = mails.remove(id);
        return deleted != null;
    }

    public String listMailsAsString(){
        ArrayList<String> mailsList = new ArrayList<>();
        for (Integer id: mails.keySet()) {
            Mail mail = mails.get(id);
            mailsList.add(String.format("%d %s %s",id, mail.getFrom(), mail.getSubject()));
        }
        return mailsList.isEmpty() ? "none" : String.join("\n",mailsList);
    }

    public Mail getMail(Integer id){
        return mails.get(id);
    }



}
