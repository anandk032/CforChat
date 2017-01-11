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
import org.jivesoftware.smackx.delay.packet.DelayInformation;
import org.jivesoftware.smackx.iqlast.LastActivityManager;
import org.jivesoftware.smackx.iqlast.packet.LastActivity;
import org.jivesoftware.smackx.ping.PingFailedListener;
import org.jivesoftware.smackx.ping.PingManager;
import org.jivesoftware.smackx.receipts.DeliveryReceipt;
import org.jivesoftware.smackx.xevent.DefaultMessageEventRequestListener;
import org.jivesoftware.smackx.xevent.MessageEventManager;
import org.jivesoftware.smackx.xevent.MessageEventNotificationListener;
import org.jivesoftware.smackx.xevent.packet.MessageEvent;
import org.jivesoftware.smackx.xevent.provider.MessageEventProvider;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;

/**
 * Created by Dharmesh on 12/28/2016.
 */

public class MyXMPP implements StanzaListener, RosterLoadedListener, PingFailedListener {
    private static final String TAG = "MyXMPP";

    private static final int PRIORITY = 24;
    private static String CURRENT_CHAT_JID = "";
    private static String SERVER = "ip-172-31-53-77.ec2.internal";

    public static MyXMPP instance = null;
    private static XMPPTCPConnection connection = null;

    private String serverAddress;
    private static String loginUser;
    private static String passwordUser;
    private Gson gson;
    private Context mContext;

    private ChatManagerListenerImpl mChatManagerListener;
    private MMessageListener mMessageListener;
    private MessageEventManager messageEventManager;
    private MyRosterEventListener myRosterEventListener;
    private DefaultMessageEventRequestListener defaultMessageEventRequestListener = new DefaultMessageEventRequestListener();

    private static boolean isAppFront = false;

    private MyXMPP(Context context, String serverAdress, String logiUser,
                   String passwordser) {
        this.serverAddress = serverAdress;
        loginUser = logiUser;
        passwordUser = passwordser;
        this.mContext = context;
        init();
    }

    synchronized static MyXMPP getInstance(Context context, String server,
                                           String user, String pass) {
        if (instance == null) {
            instance = new MyXMPP(context, server, user, pass);
        }
        return instance;
    }

    synchronized static MyXMPP getInstance() {
        return instance;
    }

    static {
        try {
            Class.forName("org.jivesoftware.smack.ReconnectionManager");
        } catch (ClassNotFoundException ex) {
            // problem loading reconnection manager
        }
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

        //reconnect manager
        MyReconnectionManager reconnectionManager = MyReconnectionManager.getInstanceFor(connection);
        ReconnectionManager.setEnabledPerDefault(false);
        reconnectionManager.enableAutomaticReconnection();
        reconnectionManager.setReconnectionPolicy(ReconnectionManager.ReconnectionPolicy.FIXED_DELAY);
        reconnectionManager.setFixedDelay(10);

        //roster
        Roster.setDefaultSubscriptionMode(Roster.SubscriptionMode.accept_all);

        //Ping manager
        PingManager pingManager = PingManager.getInstanceFor(connection);
        pingManager.setPingInterval(60);
        pingManager.registerPingFailedListener(MyXMPP.this);

        ServerPingWithAlarmManager.getInstanceFor(connection).setEnabled(true);

        //ReadReceiptManager.getInstanceFor(connection);
        //ReadReceiptManager.getInstanceFor(connection).addReadReceivedListener(new ReadReceiptListener());
        //DoubleTickManager.getInstanceFor(connection);
        //DoubleTickManager.getInstanceFor(connection).addReadReceivedListener(new ReadReceiptListener());

        //add read receipt,double tick provider
        //ProviderManager.addExtensionProvider(ReadReceiptManager.ReadReceipt.ELEMENT, ReadReceiptManager.ReadReceipt.NAMESPACE, new ReadReceiptManager.ReadReceiptProvider());
        //ProviderManager.addExtensionProvider(DoubleTickManager.DoubleTickReceipt.ELEMENT, DoubleTickManager.DoubleTickReceipt.NAMESPACE, new DoubleTickManager.DoubleTickProvider());
        ProviderManager.addExtensionProvider(MessageEvent.ELEMENT, MessageEvent.NAMESPACE, new MessageEventProvider());
    }

    @Override
    public void pingFailed() {
        Log.i(TAG, "PingManager : PING FAILED");
        MyReconnectionManager.getInstanceFor(connection).reconnect();
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

            listenDeliveryReports();
        } catch (SmackException | IOException | XMPPException e) {
            e.printStackTrace();
            checkConnection();
        }
    }

    public synchronized void onNetworkChange(final boolean isAvailable) {
        Log.i(TAG, "onNetworkChange ?" + isAvailable);
        if (isAvailable) {
            MyReconnectionManager.getInstanceFor(connection).setFixedDelay(10);
            MyReconnectionManager.getInstanceFor(connection).interruptCheckConnection();
        } else {
            MyReconnectionManager.getInstanceFor(connection).setFixedDelay(10);
            MyReconnectionManager.getInstanceFor(connection).interruptCheckConnection();
        }
    }

    private void listenMessageEvent() {
        if (messageEventManager == null)
            messageEventManager = MessageEventManager.getInstanceFor(connection);
        messageEventManager.removeMessageEventRequestListener(defaultMessageEventRequestListener);
        messageEventManager.addMessageEventRequestListener(defaultMessageEventRequestListener);
        messageEventManager.removeMessageEventNotificationListener(messageEventNotificationListener);
        messageEventManager.addMessageEventNotificationListener(messageEventNotificationListener);
    }

    private MessageEventNotificationListener messageEventNotificationListener = new MessageEventNotificationListener() {
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
            if (mContext != null && CURRENT_CHAT_JID.equals(buildUserJid(from))) {
                Intent intent = new Intent(MyService.UIUpdaterBoradcast.ACTION_XMPP_UI_COMPOSING_MESSAGE);
                mContext.sendBroadcast(intent);
            }
        }

        @Override
        public void offlineNotification(String from, String packetID) {
            Log.e(TAG, "offlineNotification: from" + from);
        }

        @Override
        public void cancelledNotification(String from, String packetID) {
            Log.e(TAG, "cancelledNotification: from" + from);
            if (mContext != null && CURRENT_CHAT_JID.equals(buildUserJid(from))) {
                Intent intent = new Intent(MyService.UIUpdaterBoradcast.ACTION_XMPP_UI_COMPOSING_PAUSE_MESSAGE);
                mContext.sendBroadcast(intent);
            }
        }
    };

    private String buildUserJid(String from) {
        if (from.contains("/")) {
            from = from.split("/")[0];
        }
        return from;
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
            if (connection.isAuthenticated()) {
                Log.i(TAG, "Authorised already, no need to logged in");
            } else {
                connection.login();
                Log.i(TAG, "Logged in successfully");
                setPresence(PRESENCE.AVAILABLE.ordinal());
            }
            return true;
        } catch (SmackException.AlreadyLoggedInException e) {
            return true;
        } catch (SmackException.NotConnectedException e) {
            e.printStackTrace();
            checkConnection();
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
                to = to + "@" + SERVER;
            MessageEventManager.getInstanceFor(connection).sendComposingNotification(to, String.valueOf(System.currentTimeMillis()));
        } catch (SmackException.NotConnectedException e) {
            e.printStackTrace();
            checkConnection();
        }
    }

    public void composingPauseMessage(String to) {
        try {
            if (!to.contains("@"))
                to = to + "@" + SERVER;
            MessageEventManager.getInstanceFor(connection).sendCancelledNotification(to, String.valueOf(System.currentTimeMillis()));
        } catch (SmackException.NotConnectedException e) {
            e.printStackTrace();
            checkConnection();
        }
    }

    public void getPresenceForUser(String to) {
        if (!to.contains("@"))
            to = to + "@" + SERVER;
        getPresence(to);
    }

    private void checkConnection() {
        if (!isConnected())
            MyReconnectionManager.getInstanceFor(connection).reconnect();
    }

    public void setPresence(int presence) {
        if (!isConnected()) return;

        Presence.Type type;
        Presence.Mode mode;
        if (presence == PRESENCE.AVAILABLE.ordinal()) {
            //online
            type = Presence.Type.available;
            mode = Presence.Mode.available;
        } else if (presence == PRESENCE.AWAY.ordinal()) {
            //away
            type = Presence.Type.available;
            mode = Presence.Mode.away;
        } else if (presence == PRESENCE.UN_AVAILABLE.ordinal()) {
            //offline/unavailable
            type = Presence.Type.unavailable;
            mode = Presence.Mode.available;
        } else if (presence == PRESENCE.LOGOUT.ordinal()) {
            //logout
            try {
                connection.disconnect(new Presence(Presence.Type.unavailable, null, PRIORITY, Presence.Mode.away));
            } catch (SmackException.NotConnectedException e) {
                e.printStackTrace();
            }
            return;
        } else {
            //logout
            type = Presence.Type.unavailable;
            mode = Presence.Mode.away;
        }

        Presence presenceStanza = new Presence(type);
        presenceStanza.setMode(mode);
        presenceStanza.setPriority(PRIORITY);
        try {
            connection.sendStanza(presenceStanza);
            Log.i(TAG, "Sending presence to server");
        } catch (SmackException.NotConnectedException e) {
            e.printStackTrace();
            checkConnection();
        }
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

        String to = chatMessage.receiverId + "@" + SERVER;
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
            checkConnection();
        } catch (Exception e) {
            Log.e("xmpp.SendMessage()", "-Exception" +
                    "msg Not sent!" + e.getMessage());
        }
    }

    private void setInitialPresence() {
        if (connection.isAuthenticated()) {
            if (isAppFront()) {
                setPresence(PRESENCE.AVAILABLE.ordinal());
            } else {
                setPresence(PRESENCE.AWAY.ordinal());
            }
        }
    }

    private class XMPPConnectionListener implements ConnectionListener {
        private static final String TAG = "XMPPConnectionListener";

        @Override
        public void connected(XMPPConnection connection) {
            Log.d(TAG, "Connected!");
            listenMessageEvent();
            setInitialPresence();
        }

        @Override
        public void connectionClosed() {
            Log.d(TAG, "ConnectionClosed!");
            if (PingManager.getInstanceFor(connection) != null) {
                PingManager.getInstanceFor(connection).unregisterPingFailedListener(MyXMPP.this);
            }
        }

        @Override
        public void connectionClosedOnError(Exception arg0) {
            Log.d(TAG, "ConnectionClosedOn Error!");
        }

        @Override
        public void reconnectingIn(int arg0) {
            Log.d(TAG, "Reconnectingin " + arg0);
        }

        @Override
        public void reconnectionFailed(Exception arg0) {
            Log.d(TAG, "ReconnectionFailed!");
        }

        @Override
        public void reconnectionSuccessful() {
            Log.d(TAG, "ReconnectionSuccessful");
            listenMessageEvent();
        }

        @Override
        public void authenticated(XMPPConnection conn, boolean arg1) {
            Log.d(TAG, "Authenticated!");

            listenMessageEvent();
            setInitialPresence();

            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(mContext,
                            "Authenticated",
                            Toast.LENGTH_LONG).show();
                }
            });

            try {
                Roster roster = Roster.getInstanceFor(connection);
                roster.addRosterLoadedListener(MyXMPP.this);
            } catch (Exception e) {
                e.printStackTrace();
            }

            ChatManager.getInstanceFor(connection).addChatListener(
                    mChatManagerListener);

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

            DelayInformation inf = null;
            try {
                inf = (DelayInformation) message.getExtension("x", "jabber:x:delay");
            } catch (Exception e) {
                Log.i(TAG, "DelayInformation : ERROR");
            }
            // get offline message timestamp
            if (inf != null) {
                Date date = inf.getStamp();
                Log.i(TAG, "DelayInformation : " + date.toString());
            }

            if (message.getType() == Message.Type.chat
                    && message.getBody() != null) {
                Log.e("MyXMPP_MESSAGE_LISTENER", "Xmpp message received: '"
                        + message.toString() + " & message.getFrom() :" + message.getFrom());

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

    public long getLastSeenInSeconds(String jId) {
        if (!isConnected()) {
            return -1;
        }

        try {
            LastActivity last = LastActivityManager.getInstanceFor(connection).getLastActivity(jId);
            Log.i(TAG, "Last Activity :" + last.getIdleTime());
            return last.getIdleTime();
        } catch (SmackException.NoResponseException e) {
            e.printStackTrace();
        } catch (XMPPException.XMPPErrorException e) {
            e.printStackTrace();
        } catch (SmackException.NotConnectedException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public void getPresence(final String jId) {
        if (mContext == null) return;

        if (!isConnected()) {
            sendPresenceBroadcast(-1, -1, jId);
            checkConnection();
            return;
        }

        new AsyncTask<Void, Void, Object[]>() {
            @Override
            protected Object[] doInBackground(Void... params) {
                if (connection != null && isConnected()) {
                    try {
                        Roster roster = Roster.getInstanceFor(connection);
                        roster.reloadAndWait();
                        //roster.addRosterListener(myRosterEventListener);
                        Presence presence = roster.getPresence(jId);
                        Log.i(TAG, "away?" + presence.isAway() + " & avail:" + presence.isAvailable() + " & mode: " + presence.getMode().toString());
                        int state = retrieveStateMode(presence);

                        long seconds = -1;
                        if (state != 1) {
                            //seconds = getLastSeenInSeconds(jId);
                        }
                        return new Object[]{state, seconds};
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                return new Object[]{-1, -1L};
            }

            @Override
            protected void onPostExecute(Object[] mode) {
                super.onPostExecute(mode);
                sendPresenceBroadcast((int) mode[0], (long) mode[1], jId);
            }
        }.execute();
    }

    private static int retrieveStateMode(final Presence presence) {
        //Log.i(TAG, "presence.isAvailable():" + presence.isAvailable());
        int userState = 0;
        /** -1 for error, 0 for offline, 1 for online, 2 for away, 3 for busy, 4 for unavailable*/
        if (presence.isAvailable() && presence.getMode() == Presence.Mode.dnd) {
            userState = PRESENCE.BUSY.ordinal();
        } else if (presence.isAvailable() && (presence.getMode() == Presence.Mode.away || presence.getMode() == Presence.Mode.xa)) {
            userState = PRESENCE.AWAY.ordinal();
        } else if (presence.isAvailable() && presence.getMode() == Presence.Mode.available) {
            userState = PRESENCE.AVAILABLE.ordinal();
        } else {
            //offline
            userState = PRESENCE.NONE.ordinal();
        }
        return userState;
    }

    private void sendPresenceBroadcast(int presenceMode, long seconds, String to) {
        Log.i(TAG, "Sending broadcast to UI for presence update:" + presenceMode);
        if (mContext == null) return;
        Intent intent = new Intent(MyService.PresenceUiBoradcast.ACTION_XMPP_PRESENCE_UI_UPDATE);
        intent.putExtra(MyService.PresenceUiBoradcast.BUNDLE_PRESENCE_MODE, presenceMode);
        intent.putExtra(MyService.SendMessageBroadcast.BUNDLE_MSG_TO, to);
        intent.putExtra(MyService.PresenceUiBoradcast.BUNDLE_LAST_SEEN, seconds);
        mContext.sendBroadcast(intent);
    }

    @Override
    public void onRosterLoaded(Roster roster) {
        //new roster loaded
        addRosterListener();
    }

    private void addRosterListener() {
        if (connection == null || !isConnected()) return;

        if (myRosterEventListener == null) {
            myRosterEventListener = new MyRosterEventListener();
        }
        try {
            Roster roster = Roster.getInstanceFor(connection);
            roster.addRosterListener(myRosterEventListener);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void removeRosterListener() {
        if (connection == null || !isConnected() || myRosterEventListener == null) return;
        try {
            Roster roster = Roster.getInstanceFor(connection);
            roster.removeRosterListener(myRosterEventListener);
            myRosterEventListener = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
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
            //Log.i(TAG, "MyRosterEventListener: PRESENCE :" + presence.getFrom() + " & status:" + retrieveStateMode(presence));
            Log.i(TAG, "PRESENCE:" + presence.toXML());

            String from = presence.getFrom();
            from = buildUserJid(from);

            if (from.equals(CURRENT_CHAT_JID)) {
                sendPresenceBroadcast(retrieveStateMode(presence), -1L, from);
            }
        }
    }

    public static void setCurrentChatId(String user) {
        if (!user.contains("@"))
            user = user + "@" + SERVER;
        CURRENT_CHAT_JID = user;
    }

    public static boolean isAppFront() {
        return isAppFront;
    }

    public static void setIsAppFront(boolean isAppFront) {
        MyXMPP.isAppFront = isAppFront;
    }

    public static enum PRESENCE {
        NONE,//0
        AVAILABLE,//1
        AWAY,//2
        BUSY,//3
        UN_AVAILABLE,//4
        LOGOUT,//5
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

