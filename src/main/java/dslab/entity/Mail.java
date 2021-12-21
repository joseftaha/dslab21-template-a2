package dslab.entity;

import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Mail {

    private String from;
    private String to;
    private String subject;
    private String data;


    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getFromDomain(){
        Pattern pattern = Pattern.compile("(?<=@)([a-z.]*)");
        Matcher matcher = pattern.matcher(from);
        return matcher.group();
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }


    @Override
    public String toString() {
        return "from " + from + "\n" +
                "to " + to + "\n" +
                "subject " + subject + "\n" +
                "data " + data;
    }

}