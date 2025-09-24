package com.example.messegerie;

public class Message {
    private String text;
    private String sender;
    private String time;
    private boolean isSent;

    public Message(String text, String sender, String time, boolean isSent) {
        this.text = text;
        this.sender = sender;
        this.time = time;
        this.isSent = isSent;
    }

    public String getText() {
        return text;
    }

    public String getSender() {
        return sender;
    }

    public String getTime() {
        return time;
    }

    public boolean isSent() {
        return isSent;
    }
}