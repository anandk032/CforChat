package com.squirrel.chatfire_xmpp.model;

/**
 * Created by Dharmesh on 12/28/2016.
 */

public class ChatMessage {
    public String body, senderId, receiverId;
    public String msgId;
    public boolean isMine; // Did I send the message.

    public ChatMessage(String senderId, String receiverId, String body,
                       String msgId, boolean isMine) {
        this.body = body;
        this.isMine = isMine;
        this.senderId = senderId;
        this.msgId = msgId;
        this.receiverId = receiverId;
    }
}
