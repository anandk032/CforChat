package com.squirrel.chatfire_xmpp;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
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
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.RosterListener;
import org.jivesoftware.smack.roster.RosterLoadedListener;
import org.jivesoftware.smack.roster.packet.RosterPacket;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smackx.iqlast.LastActivityManager;
import org.jivesoftware.smackx.iqlast.packet.LastActivity;
import org.jivesoftware.smackx.pubsub.provider.SubscriptionProvider;
import org.jivesoftware.smackx.receipts.DeliveryReceipt;
import org.jivesoftware.smackx.search.UserSearchManager;
import org.jivesoftware.smackx.xevent.DefaultMessageEventRequestListener;
import org.jivesoftware.smackx.xevent.MessageEventManager;
import org.jivesoftware.smackx.xevent.MessageEventNotificationListener;
import org.jivesoftware.smackx.xevent.packet.MessageEvent;
import org.jivesoftware.smackx.xevent.provider.MessageEventProvider;

import java.io.IOException;
import java.util.Collection;

/**
 * Created by Dharmesh on 12/28/2016.
 */

public class MyXMPP implements StanzaListener, RosterLoadedListener {
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
    private MyService mContext;
    private static MyXMPP instance = null;

    private Chat Mychat;

    private ChatManagerListenerImpl mChatManagerListener;
    private MMessageListener mMessageListener;
    private MessageEventManager messageEventManager;
    private MyRosterEventListener myRosterEventListener;

    private static final String[] list = new String[]{"dharmesh@ip-172-31-53-77.ec2.internal", "vijay@ip-172-31-53-77.ec2.internal", "anand@ip-172-31-53-77.ec2.internal", "tapan@ip-172-31-53-77.ec2.internal"};

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
        this.mContext = context;
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
        mMessageListener = new MMessageListener(mContext);
        mChatManagerListener = new ChatManagerListenerImpl();
        myRosterEventListener = new MyRosterEventListener();
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
        config.setSendPresence(false);
        config.setUsernameAndPassword(loginUser, passwordUser);
        XMPPTCPConnection.setUseStreamManagementResumptiodDefault(true);
        XMPPTCPConnection.setUseStreamManagementDefault(true);
        connection = new XMPPTCPConnection(config.build());
        connection.setPacketReplyTimeout(12000);
        XMPPConnectionListener connectionListener = new XMPPConnectionListener();
        connection.addConnectionListener(connectionListener);
        connection.addStanzaAcknowledgedListener(this);

        //roster
        Roster.setDefaultSubscriptionMode(Roster.SubscriptionMode.accept_all);

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
                Intent intent = new Intent(MyService.UIUpdaterBoradcast.ACTION_XMPP_UI_COMPOSING_MESSAGE);
                mContext.sendBroadcast(intent);
            }

            @Override
            public void offlineNotification(String from, String packetID) {
                Log.e(TAG, "offlineNotification: from" + from);
            }

            @Override
            public void cancelledNotification(String from, String packetID) {
                Log.e(TAG, "cancelledNotification: from" + from);
                Intent intent = new Intent(MyService.UIUpdaterBoradcast.ACTION_XMPP_UI_COMPOSING_PAUSE_MESSAGE);
                mContext.sendBroadcast(intent);
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
//            if (connection.isAuthenticated()) {
//                Log.i(TAG, "Authorised already, no need to logged in");
//            } else {
                connection.login();
                Log.i(TAG, "Logged in successfully");
//            }

            //Presence presence = new Presence(Presence.Type.unavailable);
            //presence.setStatus("Gone fishing");
            //connection.sendStanza(presence);
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

    public void composingMessage(String to) {
        try {
            Log.e(TAG, "composingMessage: to " + to);
            if (!to.contains("@"))
                to = to + "@"
                        + mContext.getString(R.string.server);
            MessageEventManager.getInstanceFor(connection).sendComposingNotification(to, String.valueOf(System.currentTimeMillis()));
        } catch (SmackException.NotConnectedException e) {
            e.printStackTrace();
        }
    }

    public void composingPauseMessage(String to) {
        try {
            if (!to.contains("@"))
                to = to + "@"
                        + mContext.getString(R.string.server);
            MessageEventManager.getInstanceFor(connection).sendCancelledNotification(to, String.valueOf(System.currentTimeMillis()));
        } catch (SmackException.NotConnectedException e) {
            e.printStackTrace();
        }
    }

    public void getPresenceForUser(String to) {
        if (!to.contains("@"))
            to = to + "@"
                    + mContext.getString(R.string.server);
        getPresence(to);
    }


    private class ChatManagerListenerImpl implements ChatManagerListener {
        @Override
        public void chatCreated(final Chat chat,
                                final boolean createdLocally) {
            if (!createdLocally)
                chat.addMessageListener(mMessageListener);
        }
    }

    public void sendMessage(ChatMessage chatMessage) {
        String body = gson.toJson(chatMessage);

        String to = chatMessage.receiverId + "@"
                + mContext.getString(R.string.server);
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
                    Toast.makeText(mContext,
                            "Authenticated",
                            Toast.LENGTH_LONG).show();
                }
            });

            Roster roster = Roster.getInstanceFor(connection);
            roster.addRosterLoadedListener(MyXMPP.this);

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


    private class MMessageListener implements ChatMessageListener {
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

                Intent intent = new Intent(MyService.UIUpdaterBoradcast.ACTION_XMPP_UI_COMPOSING_PAUSE_MESSAGE);
                mContext.sendBroadcast(intent);

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

    private void test(String jId) {
        try {
            LastActivity last = LastActivityManager.getInstanceFor(connection).getLastActivity(jId);
            Log.i(TAG, "Last Activity :" + last.getIdleTime());
        } catch (SmackException.NoResponseException e) {
            e.printStackTrace();
        } catch (XMPPException.XMPPErrorException e) {
            e.printStackTrace();
        } catch (SmackException.NotConnectedException e) {
            e.printStackTrace();
        }
    }

    public void getPresence(final String jId) {
        if (mContext == null) return;

        if (!isConnected()) {
            sendPresenceBroadcast(-1, jId);
            return;
        }

        new AsyncTask<Void, Void, Integer>() {
            @Override
            protected Integer doInBackground(Void... params) {
                if (connection != null && isConnected()) {
                    try {
                        Roster roster = Roster.getInstanceFor(connection);
                        roster.reloadAndWait();
                        roster.addRosterListener(myRosterEventListener);
                        Presence presence = roster.getPresence(jId);
                        Log.i(TAG, "away?" + presence.isAway() + " & avail:" + presence.isAvailable() + " & mode: " + presence.getMode().toString());
                        return retrieveState_mode(presence);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                return -1;
            }

            @Override
            protected void onPostExecute(Integer mode) {
                super.onPostExecute(mode);
                sendPresenceBroadcast(mode, jId);
                test(jId);
            }
        }.execute();
    }

    private static int retrieveState_mode(final Presence presence) {
        Log.i(TAG, "presence.isAvailable():" + presence.isAvailable());
        int userState = 0;
        /** -1 for error, 0 for offline, 1 for online, 2 for away, 3 for busy*/
        if (presence.getMode() == Presence.Mode.dnd) {
            userState = 3;
        } else if (presence.getMode() == Presence.Mode.away || presence.getMode() == Presence.Mode.xa) {
            userState = 2;
        } else if (presence.isAvailable()) {
            userState = 1;
        } else {
            //offline
            userState = 0;
        }
        return userState;
    }

    private void sendPresenceBroadcast(int presenceMode, String to) {
        Log.i(TAG, "Sending reply on presence mode:" + presenceMode);
        if (mContext == null) return;
        Intent intent = new Intent(MyService.PresenceUiBoradcast.ACTION_XMPP_PRESENCE_UI_UPDATE);
        intent.putExtra(MyService.PresenceUiBoradcast.BUNDLE_PRESENCE_MODE, presenceMode);
        intent.putExtra(MyService.SendMessageBroadcast.BUNDLE_MSG_TO, to);
        mContext.sendBroadcast(intent);
    }

    @Override
    public void onRosterLoaded(Roster roster) {
        //new roster loaded
        roster.addRosterListener(myRosterEventListener);
    }

    private class MyRosterEventListener implements RosterListener {
        @Override
        public void entriesAdded(Collection<String> addresses) {
        }

        @Override
        public void entriesUpdated(Collection<String> addresses) {
        }

        @Override
        public void entriesDeleted(Collection<String> addresses) {
        }

        @Override
        public void presenceChanged(Presence presence) {
            Log.i(TAG, "MyRosterEventListener: PRESENCE :" + presence.getFrom() + " & status:" + retrieveState_mode(presence));
            sendPresenceBroadcast(retrieveState_mode(presence), presence.getFrom());
        }
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

