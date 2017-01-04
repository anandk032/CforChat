package com.squirrel.chatfire_xmpp;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

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
import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.provider.EmbeddedExtensionProvider;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smackx.pubsub.provider.SubscriptionProvider;
import org.jivesoftware.smackx.receipts.DeliveryReceiptManager;
import org.jivesoftware.smackx.receipts.DeliveryReceiptRequest;
import org.jivesoftware.smackx.receipts.ReceiptReceivedListener;
import org.jivesoftware.smackx.search.UserSearchManager;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Created by Dharmesh on 12/28/2016.
 */

public class MyXMPP {
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

    private org.jivesoftware.smack.chat.Chat Mychat;

    private ChatManagerListenerImpl mChatManagerListener;
    private MMessageListener mMessageListener;

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
        config.setDebuggerEnabled(true);
        XMPPTCPConnection.setUseStreamManagementResumptiodDefault(true);
        XMPPTCPConnection.setUseStreamManagementDefault(true);
        connection = new XMPPTCPConnection(config.build());
        connection.setPacketReplyTimeout(12000);
        XMPPConnectionListener connectionListener = new XMPPConnectionListener();
        connection.addConnectionListener(connectionListener);
        ReadReceiptManager.getInstanceFor(connection);
        ReadReceiptManager.getInstanceFor(connection).addReadReceivedListener(new ReadReceiptListener());

        //add read receipt provider
        ProviderManager.addExtensionProvider(ReadReceipt.ELEMENT, ReadReceipt.NAMESPACE, new ReadReceiptProvider());
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
            connection.connect();
            if (!login()) return;

            ReconnectionManager reconnectionManager = ReconnectionManager.getInstanceFor(connection);
            ReconnectionManager.setEnabledPerDefault(true);
            reconnectionManager.enableAutomaticReconnection();
            reconnectionManager.setReconnectionPolicy(ReconnectionManager.ReconnectionPolicy.FIXED_DELAY);
            reconnectionManager.setFixedDelay(30);

            listenDeliveryReports();
        } catch (SmackException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (XMPPException e) {
            e.printStackTrace();
        }
    }

    private void listenDeliveryReports() {
        if (connection == null || !connection.isConnected()) {
            return;
        }

        DeliveryReceiptManager dm = DeliveryReceiptManager
                .getInstanceFor(connection);
        dm.setAutoReceiptMode(DeliveryReceiptManager.AutoReceiptMode.always);
        dm.addReceiptReceivedListener(new ReceiptReceivedListener() {
            @Override
            public void onReceiptReceived(final String fromId,
                                          final String toId, final String msgId,
                                          final Stanza packet) {
                Log.i(TAG, "DeliveryReceiptManager: delivered, to:" + toId + " & msgId:" + msgId + " & stanzaId:" + packet.getStanzaId());
            }
        });
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

    private class ChatManagerListenerImpl implements ChatManagerListener {
        @Override
        public void chatCreated(final org.jivesoftware.smack.chat.Chat chat,
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
        }
    }

    void sendMessage(ChatMessage chatMessage) {
        String body = gson.toJson(chatMessage);

        Chat chat = ChatManager.getInstanceFor(connection).createChat(
                chatMessage.receiverId + "@"
                        + context.getString(R.string.server),
                mMessageListener);

        Message message = new Message();
        message.setBody(body);
        message.setStanzaId(chatMessage.msgId);
        message.setType(Message.Type.chat);
        try {
            DeliveryReceiptRequest.addTo(message);
            ReadReceipt read = new ReadReceipt(chatMessage.msgId);
            message.addExtension(read);
            chat.sendMessage(message);
            Log.i(TAG, "Chat message sent.");
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

        MMessageListener(Context contxt) {
        }


        @Override
        public void processMessage(final org.jivesoftware.smack.chat.Chat chat,
                                   final Message message) {
            Log.e("MyXMPP_MESSAGE_LISTENER", "Xmpp message received: '"
                    + message.toString());

            if (message.getType() == Message.Type.chat
                    && message.getBody() != null) {
                final ChatMessage chatMessage = gson.fromJson(
                        message.getBody(), ChatMessage.class);

                processMessage(chatMessage);
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

    public class ReadReceipt implements ExtensionElement {
        public static final String NAMESPACE = "urn:xmpp:read";
        public static final String ELEMENT = "read";

        private String id; /// original ID of the delivered message

        public ReadReceipt(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        @Override
        public String getElementName() {
            return ELEMENT;
        }

        @Override
        public String getNamespace() {
            return NAMESPACE;
        }

        @Override
        public String toXML() {
            return "<read xmlns='" + NAMESPACE + "' id='" + id + "'/>";
        }
    }

    public class ReadReceiptProvider extends EmbeddedExtensionProvider {
        @Override
        protected ExtensionElement createReturnExtension(String currentElement, String currentNamespace, Map attributeMap, List content) {
            return new ReadReceipt((String) attributeMap.get("id"));
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

