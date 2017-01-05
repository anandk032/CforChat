package com.squirrel.chatfire_xmpp;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.Gson;
import com.squirrel.chatfire_xmpp.model.ChatMessage;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.ReconnectionManager;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.chat.Chat;
import org.jivesoftware.smack.chat.ChatManager;
import org.jivesoftware.smack.chat.ChatManagerListener;
import org.jivesoftware.smack.chat.ChatMessageListener;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.roster.packet.RosterPacket;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.jivesoftware.smackx.chatstates.ChatStateListener;
import org.jivesoftware.smackx.pubsub.provider.SubscriptionProvider;
import org.jivesoftware.smackx.receipts.DeliveryReceipt;
import org.jivesoftware.smackx.receipts.ReceiptReceivedListener;
import org.jivesoftware.smackx.search.UserSearchManager;
import org.jivesoftware.smackx.xevent.DefaultMessageEventRequestListener;
import org.jivesoftware.smackx.xevent.MessageEventManager;
import org.jivesoftware.smackx.xevent.MessageEventNotificationListener;
import org.jivesoftware.smackx.xevent.packet.MessageEvent;
import org.jivesoftware.smackx.xevent.provider.MessageEventProvider;

import java.io.IOException;

/**
 * Created by Dharmesh on 12/28/2016.
 */

public class MyXMPP implements StanzaListener {
    private static final String TAG = "MyXMPP";

    private static boolean connected = false;
    private boolean loggedin = false;
    private static boolean isconnecting = false;
    private static boolean isToasted = true;
    private boolean chat_created = false;
    private String serverAddress;
    private XMPPTCPConnection connection;
    private static String loginUser;
    private static String passwordUser;
    private Gson gson;
    private MyService context;
    private static MyXMPP instance = null;

    private Chat Mychat;

    private ChatManagerListenerImpl mChatManagerListener;
    private MMessageListener mMessageListener;
    private MessageEventManager messageEventManager;


    static {
        try {
            Class.forName("org.jivesoftware.smack.ReconnectionManager");
        } catch (ClassNotFoundException ex) {
            // problem loading reconnection manager
        }
    }


    private MyXMPP(MyService context, String serverAdress, String logiUser,
                   String passwordser) {
        this.serverAddress = serverAdress;
        loginUser = logiUser;
        passwordUser = passwordser;
        this.context = context;
        init();
    }

    static MyXMPP getInstance(MyService context, String server,
                              String user, String pass) {
        if (instance == null) {
            instance = new MyXMPP(context, server, user, pass);
        }
        return instance;
    }


    private void init() {
        gson = new Gson();
        mMessageListener = new MMessageListener(context);
        mChatManagerListener = new ChatManagerListenerImpl();
        initialiseConnection();
    }

    private void initialiseConnection() {
        XMPPTCPConnectionConfiguration.Builder config = XMPPTCPConnectionConfiguration
                .builder();
        config.setSecurityMode(ConnectionConfiguration.SecurityMode.disabled);
        config.setServiceName(serverAddress);
        config.setHost(serverAddress);
        config.setPort(5222);
        config.setDebuggerEnabled(false);
        config.setResource("Meetwo");
        XMPPTCPConnection.setUseStreamManagementResumptiodDefault(true);
        XMPPTCPConnection.setUseStreamManagementDefault(true);
        connection = new XMPPTCPConnection(config.build());
        connection.setPacketReplyTimeout(12000);
        XMPPConnectionListener connectionListener = new XMPPConnectionListener();
        connection.addConnectionListener(connectionListener);
        connection.addStanzaAcknowledgedListener(this);


        ReadReceiptManager.getInstanceFor(connection);
        //ReadReceiptManager.getInstanceFor(connection).addReadReceivedListener(new ReadReceiptListener());
        DoubleTickManager.getInstanceFor(connection);
        //DoubleTickManager.getInstanceFor(connection).addReadReceivedListener(new ReadReceiptListener());

        //add read receipt,double tick provider
        ProviderManager.addExtensionProvider(ReadReceiptManager.ReadReceipt.ELEMENT, ReadReceiptManager.ReadReceipt.NAMESPACE, new ReadReceiptManager.ReadReceiptProvider());
        ProviderManager.addExtensionProvider(DoubleTickManager.DoubleTickReceipt.ELEMENT, DoubleTickManager.DoubleTickReceipt.NAMESPACE, new DoubleTickManager.DoubleTickProvider());
        ProviderManager.addExtensionProvider(MessageEvent.ELEMENT, MessageEvent.NAMESPACE, new MessageEventProvider());
    }

    public String getUserId() {
        if (connection != null) {
            return connection.getUser();
        }
        return null;
    }

    public boolean isConnected() {
        if (connection != null) {
            return connection.isConnected();
        }
        return false;
    }

    void disconnect() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                connection.disconnect();
            }
        }).start();
    }

    void connect() {
        try {
            if (!connection.isConnected())
                connection.connect();
            if (!login()) return;

            ReconnectionManager reconnectionManager = ReconnectionManager.getInstanceFor(connection);
            ReconnectionManager.setEnabledPerDefault(true);
            reconnectionManager.enableAutomaticReconnection();
            reconnectionManager.setReconnectionPolicy(ReconnectionManager.ReconnectionPolicy.RANDOM_INCREASING_DELAY);
            reconnectionManager.setFixedDelay(5);

            listenDeliveryReports();
            listenMessageEvent();
        } catch (SmackException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (XMPPException e) {
            e.printStackTrace();
        }
    }

    private void listenMessageEvent() {
        messageEventManager = MessageEventManager.getInstanceFor(connection);
        messageEventManager.addMessageEventRequestListener(new DefaultMessageEventRequestListener());
        messageEventManager.addMessageEventNotificationListener(new MessageEventNotificationListener() {
            @Override
            public void deliveredNotification(String from, String packetID) {
                Log.e(TAG, "deliveredNotification: from" + from);
            }

            @Override
            public void displayedNotification(String from, String packetID) {
                Log.e(TAG, "displayedNotification: from" + from);
            }

            @Override
            public void composingNotification(String from, String packetID) {
                Log.e(TAG, "composingNotification: from" + from);

            }

            @Override
            public void offlineNotification(String from, String packetID) {
                Log.e(TAG, "offlineNotification: from" + from);

            }

            @Override
            public void cancelledNotification(String from, String packetID) {
                Log.e(TAG, "cancelledNotification: from" + from);

            }
        });
    }

    private void listenDeliveryReports() {
        if (connection == null || !connection.isConnected()) {
            return;
        }

        // DeliveryReceiptManager dm = DeliveryReceiptManager
        // .getInstanceFor(connection);
        //dm.setAutoReceiptMode(DeliveryReceiptManager.AutoReceiptMode.always);
        //dm.addReceiptReceivedListener(new ReceiptReceivedListener() {
        //    @Override
        //    public void onReceiptReceived(final String fromId,
        //                                  final String toId, final String msgId,
        //                                  final Stanza packet) {
        //        Log.i(TAG, "DeliveryReceiptManager RECEIVED RECEIPT: " + "fromId :" + fromId + " to:" + toId + " & msgId:" + msgId + " & stanzaId:" + packet.getStanzaId());
        //    }
        //});
    }


    private boolean login() {
        try {
            connection.login(loginUser, passwordUser);
            Log.i(TAG, "Logged in successfully");
            return true;
        } catch (SmackException.AlreadyLoggedInException e) {
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.i(TAG, "Logged in ERROR");
        return false;
    }

    /*Stanza acknowledge listener*/
    @Override
    public synchronized void processPacket(Stanza packet) throws SmackException.NotConnectedException {
        if (packet == null) return;

        if (packet instanceof Message) {
            Message message = (Message) packet;
            if (message.getType() == Message.Type.chat) {
                Log.i(TAG, "Stanza ACK CHAT ONE-TICK: id:" + packet.getStanzaId() + " ----->" + packet.toXML());
            }
        } else if (packet instanceof RosterPacket) {
            Log.i(TAG, "Stanza ACK ROSTER_PACKET: id:" + packet.getStanzaId() + " ----->" + packet.toXML());
        } else if (packet instanceof Presence) {
            Log.i(TAG, "Stanza ACK PRESENCE: id:" + packet.getStanzaId() + " ----->" + packet.toXML());
        } else {
            Log.i(TAG, "Stanza ACK : id:" + packet.getStanzaId() + " ----->" + packet.toXML());
        }
    }

    public void composingPauseMessage() {
//        MessageEventManager.getInstanceFor(connection).sendComposingNotification();
    }

    public void composingMessage() {

    }

    private class ChatManagerListenerImpl implements ChatManagerListener {
        @Override
        public void chatCreated(final Chat chat,
                                final boolean createdLocally) {
            if (!createdLocally)
                chat.addMessageListener(mMessageListener);
        }
    }

    private class ReadReceiptListener implements ReceiptReceivedListener {
        private static final String TAG = "ReadReceiptListener";

        @Override
        public void onReceiptReceived(String fromJid, String toJid, String receiptId, Stanza receipt) {
            Log.i(TAG, "Read receipt from:" + fromJid + " & to:" + toJid + " &receiptId:" + receiptId + " &receipt:" + receipt);
            Log.i(TAG, "Message Read Successfully");
        }
    }

    void sendMessage(ChatMessage chatMessage) {

        String body = gson.toJson(chatMessage);

        String to = chatMessage.receiverId + "@"
                + context.getString(R.string.server);
        Chat chat = ChatManager.getInstanceFor(connection).createChat(to, mMessageListener);

        final Message message = new Message();
        message.setBody(body);
        message.setFrom(getUserId());
        message.setTo(to);
        message.setStanzaId(chatMessage.msgId);
        message.setType(Message.Type.chat);
        try {
            MessageEventManager.addNotificationsRequests(message, true, true, true, true);
            chat.sendMessage(message);
            Log.i(TAG, "Chat message sent msgId:" + message.getStanzaId());
        } catch (SmackException.NotConnectedException e) {
            Log.e("xmpp.SendMessage()", "msg Not sent!-Not Connected!");
        } catch (Exception e) {
            Log.e("xmpp.SendMessage()", "-Exception" +
                    "msg Not sent!" + e.getMessage());
        }
    }

    private class XMPPConnectionListener implements ConnectionListener {
        private static final String TAG = "XMPPConnectionListener";

        @Override
        public void connected(final XMPPConnection connection) {
            Log.d(TAG, "Connected!");
            connected = true;
            if (!connection.isAuthenticated()) {
                login();
            }
        }

        @Override
        public void connectionClosed() {
            Log.d(TAG, "ConnectionClosed!");
            connected = false;
            chat_created = false;
            loggedin = false;
        }

        @Override
        public void connectionClosedOnError(Exception arg0) {
            Log.d(TAG, "ConnectionClosedOn Error!");
            connected = false;
            chat_created = false;
            loggedin = false;
        }

        @Override
        public void reconnectingIn(int arg0) {
            Log.d(TAG, "Reconnectingin " + arg0);
            loggedin = false;
        }

        @Override
        public void reconnectionFailed(Exception arg0) {
            Log.d(TAG, "ReconnectionFailed!");
            connected = false;
            chat_created = false;
            loggedin = false;
        }

        @Override
        public void reconnectionSuccessful() {
            Log.d(TAG, "ReconnectionSuccessful");
            connected = true;
            chat_created = false;
            loggedin = false;
        }

        @Override
        public void authenticated(XMPPConnection arg0, boolean arg1) {
            Log.d(TAG, "Authenticated!");
            loggedin = true;

            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context,
                            "Authenticated",
                            Toast.LENGTH_LONG).show();
                }
            });


            ChatManager.getInstanceFor(connection).addChatListener(
                    mChatManagerListener);

            chat_created = false;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }).start();
        }
    }

    private class MMessageListener implements ChatMessageListener, ChatStateListener {
        private static final String TAG = "MMessageListener";

        MMessageListener(Context contxt) {
        }

        @Override
        public void processMessage(Chat chat, final Message message) {
            // Log.i(TAG, "processMessage FROM:" + message.getFrom() + " &TYPE:" + message.getType().toString());
            Log.i(TAG, "Received processMessage details : " + message.toXML());

            if (checkReadReceipt(message)) {
                Log.i(TAG, "Type DELIVERY REPORTS " + message.getStanzaId());
                return;
            }

            if (checkDoubleTickReceipt(message)) {
                Log.i(TAG, "Type DOUBLE TICK Received " + message.getStanzaId());
                return;
            }

            if (message.getType() == Message.Type.chat
                    && message.getBody() != null) {
                Log.e("MyXMPP_MESSAGE_LISTENER", "Xmpp message received: '"
                        + message.toString() + " & message.getFrom() :" + message.getFrom());

                //sendReadReceipt(message);
                //sendDoubleTick(message);


                final ChatMessage chatMessage = gson.fromJson(
                        message.getBody(), ChatMessage.class);
                processMessage(chatMessage);
            }
        }

        private void sendReadReceipt(Message message) {
            Message messageReceipt = new Message(message.getFrom());
            messageReceipt.setStanzaId(message.getStanzaId());
            messageReceipt.setFrom(getUserId());
            messageReceipt.setType(Message.Type.normal);
            ReadReceiptManager.ReadReceipt read = new ReadReceiptManager.ReadReceipt(messageReceipt.getStanzaId());
            messageReceipt.addExtension(read);
            try {
                connection.sendStanza(messageReceipt);
                Log.i(TAG, "Read receipt sent");
            } catch (SmackException.NotConnectedException e) {
                e.printStackTrace();
            }
        }

        private void sendDoubleTick(Message message) {
            Message messageDoubleTick = new Message(message.getFrom());
            messageDoubleTick.setStanzaId(null);
            messageDoubleTick.setFrom(getUserId());
            messageDoubleTick.setType(Message.Type.normal);
            DoubleTickManager.DoubleTickReceipt read = new DoubleTickManager.DoubleTickReceipt(messageDoubleTick.getStanzaId());
            messageDoubleTick.addExtension(read);
            try {
                connection.sendStanza(messageDoubleTick);
                Log.i(TAG, "DoubleTick receipt sent");
            } catch (SmackException.NotConnectedException e) {
                e.printStackTrace();
            }
        }

        private void processMessage(final ChatMessage chatMessage) {
            chatMessage.isMine = false;
            Chats.chatlist.add(chatMessage);
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    Chats.chatAdapter.notifyDataSetChanged();
                }
            });
        }

        @Override
        public void stateChanged(Chat chat, ChatState state) {
            switch (state) {
                case active:
                    Log.i("state", "active");
                    break;
                case composing:
                    Log.i("state", "composing");
                    break;
                case paused:
                    Log.i("state", "paused");
                    break;
                case inactive:
                    Log.i("state", "inactive");
                    break;
                case gone:
                    Log.i("state", "gone");
                    break;
            }
        }
    }


    private boolean checkReadReceipt(Object message) {
        if (message instanceof Message) {
            ReadReceiptManager.ReadReceipt dr = ((Message) message).getExtension(ReadReceiptManager.ReadReceipt.ELEMENT, ReadReceiptManager.ReadReceipt.NAMESPACE);
            if (dr != null) {
                Log.i(TAG, "Type Read Receipt->" + ((Message) message).getStanzaId());
                return true;
            }
        } else if (message instanceof Stanza) {
            DeliveryReceipt dr = ((Stanza) message).getExtension(ReadReceiptManager.ReadReceipt.ELEMENT, ReadReceiptManager.ReadReceipt.NAMESPACE);
            if (dr != null) {
                Log.i(TAG, "Type Read Receipt->" + ((Stanza) message).getStanzaId());
                return true;
            }
        }
        return false;
    }

    private boolean checkDoubleTickReceipt(Object message) {
        if (message instanceof Message) {
            DoubleTickManager.DoubleTickReceipt dtr = ((Message) message).getExtension(DoubleTickManager.DoubleTickReceipt.ELEMENT, DoubleTickManager.DoubleTickReceipt.NAMESPACE);
            if (dtr != null) {
                Log.i(TAG, "Type Double Tick->" + ((Message) message).getStanzaId());
                return true;
            }
        } else if (message instanceof Stanza) {
            DoubleTickManager.DoubleTickReceipt dtr = ((Stanza) message).getExtension(DoubleTickManager.DoubleTickReceipt.ELEMENT, DoubleTickManager.DoubleTickReceipt.NAMESPACE);
            if (dtr != null) {
                Log.i(TAG, "Type Double Tick->" + ((Stanza) message).getStanzaId());
                return true;
            }
        }
        return false;
    }

    public void getSub() {
        UserSearchManager userSearchManager = new UserSearchManager(connection);
        SubscriptionProvider subscriptionProvider = new SubscriptionProvider();
    }

    void precence() {
        Presence presence = new Presence(Presence.Type.available);
        presence.setStatus("Gone fishing");
        presence.setMode(Presence.Mode.dnd);
        try {
            connection.sendStanza(presence);
        } catch (SmackException.NotConnectedException e) {
            e.printStackTrace();
        }

        connection.addStanzaAcknowledgedListener(new StanzaListener() {
            @Override
            public void processPacket(Stanza packet) throws SmackException.NotConnectedException {
            }
        });
    }


//    public void getUser() {
//        if (false) {
//            return;
//        } else {
//            Roster roster = Roster.getInstanceFor(connection);
//            roster.addRosterListener(new RosterListener() {
//                @Override
//                public void entriesAdded(Collection<String> addresses) {
//                    Log.e(TAG, "entriesAdded: " + addresses);
//                    getUser();
//                }
//
//                @Override
//                public void entriesUpdated(Collection<String> addresses) {
//                    Log.e(TAG, "entriesUpdated: " + addresses);
//                    getUser();
//                }
//
//                @Override
//                public void entriesDeleted(Collection<String> addresses) {
//                    getUser();
//
//                }
//
//                @Override
//                public void presenceChanged(Presence presence) {
//                    Log.e(TAG, "presenceChanged: " + presence.getMode());
//                    getUser();
//                }
//            });
//            Set<RosterEntry> entries = roster.getEntries();
//            Log.e(TAG, "getUser: size" + entries.size());
//            for (RosterEntry entry : entries) {
//                Log.e(TAG, "getUser: " + entry);
//            }
//            List<RosterEntry> list = new ArrayList(entries);
//            RosterFragment.userAdapter.addData(list);
//        }
//    }


}

