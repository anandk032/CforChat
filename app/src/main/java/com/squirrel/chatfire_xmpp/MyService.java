package com.squirrel.chatfire_xmpp;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;

import com.squirrel.chatfire_xmpp.model.ChatMessage;

/**
 * Created by Dharmesh on 12/28/2016.
 */

public class MyService extends Service {
    private static final String TAG = "MyService";

    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";

    private static final String DOMAIN = "54.205.116.234";
    private MyXMPP xmpp;
    private SharedPreferences prefs;
    private SendMessageBroadcast mSendMessageBroadcast;
    private Thread mThread;
    private Handler mTHandler;
    private boolean mActive;

    @Override
    public IBinder onBind(final Intent intent) {
        return new LocalBinder<>(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, " Service onCreate()");
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        register();
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags,
                              final int startId) {
        start();
        return Service.START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stop();
        unRegister();
    }

    public void start() {
        Log.i(TAG, " Service Start() function called.");
        if (!mActive) {
            mActive = true;
            if (mThread == null || !mThread.isAlive()) {
                mThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Looper.prepare();
                        mTHandler = new Handler();
                        //THE CODE HERE RUNS IN A BACKGROUND THREAD.
                        connect();
                        Looper.loop();
                    }
                });
                mThread.start();
            }
        }
    }

    public void stop() {
        Log.d(TAG, "stop()");
        mActive = false;
        mTHandler.post(new Runnable() {
            @Override
            public void run() {
                if (xmpp != null) {
                    xmpp.disconnect();
                }
            }
        });

    }

    private void connect() {
        String user = prefs.getString(USERNAME, "");
        String pass = prefs.getString(PASSWORD, "");

        if (xmpp != null && xmpp.isConnected()) {
            return;
        }

        xmpp = MyXMPP.getInstance(MyService.this, DOMAIN, user, pass);
        xmpp.connect();
    }

    private void register() {
        if (mSendMessageBroadcast == null) {
            mSendMessageBroadcast = new SendMessageBroadcast();
        }
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(SendMessageBroadcast.ACTION_XMPP_SEND_MESSAGE);
        intentFilter.addAction(SendMessageBroadcast.ACTION_XMPP_COMPOSING_MESSAGE);
        intentFilter.addAction(SendMessageBroadcast.ACTION_XMPP_COMPOSING_PAUSE_MESSAGE);
        intentFilter.addAction(SendMessageBroadcast.ACTION_XMPP_PRESENCE_UPDATE);
        intentFilter.addAction(SendMessageBroadcast.ACTION_XMPP_SET_PRESENCE_MODE);
        registerReceiver(mSendMessageBroadcast, intentFilter);
    }

    private void unRegister() {
        if (mSendMessageBroadcast != null) {
            unregisterReceiver(mSendMessageBroadcast);
        }
    }

    public class SendMessageBroadcast extends BroadcastReceiver {
        public static final String ACTION_XMPP_SEND_MESSAGE = "com.meetwo.XMPP_SEND_MESSAGE";
        public static final String ACTION_XMPP_COMPOSING_MESSAGE = "com.meetwo.XMPP_COMPOSING_MESSAGE";
        public static final String ACTION_XMPP_COMPOSING_PAUSE_MESSAGE = "com.meetwo.XMPP_COMPOSING_PAUSE_MESSAGE";
        public static final String ACTION_XMPP_PRESENCE_UPDATE = "com.meetwo.XMPP_PRESENCE_UPDATE";
        public static final String ACTION_XMPP_SET_PRESENCE_MODE = "com.meetwo.XMPP_SET_PRESENCE_MODE";
        public static final String BUNDLE_MSG_BODY = "msg_body";
        public static final String BUNDLE_MSG_TO = "msg_to";
        public static final String BUNDLE_PRESENCE_MODE = "presence_mode";

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null)
                return;

            if (ACTION_XMPP_SEND_MESSAGE.equalsIgnoreCase(intent.getAction())) {
                sendMessage(intent.getStringExtra(BUNDLE_MSG_TO), intent.getStringExtra(BUNDLE_MSG_BODY));
            } else if (ACTION_XMPP_COMPOSING_MESSAGE.equalsIgnoreCase(intent.getAction())) {
                composingMessage(intent.getStringExtra(BUNDLE_MSG_TO));
            } else if (ACTION_XMPP_COMPOSING_PAUSE_MESSAGE.equalsIgnoreCase(intent.getAction())) {
                composingPauseMessage(intent.getStringExtra(BUNDLE_MSG_TO));
            } else if (ACTION_XMPP_PRESENCE_UPDATE.equalsIgnoreCase(intent.getAction())) {
                sendPresenceUpdate(intent.getStringExtra(BUNDLE_MSG_TO));
            } else if (ACTION_XMPP_SET_PRESENCE_MODE.equalsIgnoreCase(intent.getAction())) {
                setPresence(intent.getIntExtra(BUNDLE_PRESENCE_MODE, 0));
            }
        }

        private void sendMessage(String to, String body) {
            if (xmpp != null) {
                String msgId = String.valueOf(System.currentTimeMillis());
                ChatMessage chatMessage = new ChatMessage(xmpp.getUserId(), to, body, msgId, true);
                xmpp.sendMessage(chatMessage);
            }
        }

        private void composingPauseMessage(String to) {
            Log.d(TAG, "Composing : PAUSED");
            if (xmpp != null) {
                xmpp.composingPauseMessage(to);
            }
        }

        private void composingMessage(String to) {
            Log.d(TAG, "Composing : COMPOSING");
            if (xmpp != null) {
                xmpp.composingMessage(to);
            }
        }

        private void sendPresenceUpdate(String to) {
            Log.d(TAG, "Sending update soon...");
            if (xmpp != null) {
                xmpp.getPresenceForUser(to);
            }
        }

        private void setPresence(int presence) {
            Log.e(TAG, "setPresence: " + presence);
            if (xmpp != null) {
                xmpp.setPresence(presence);
            }
        }
    }

    public static class UIUpdaterBoradcast extends BroadcastReceiver {
        public static final String ACTION_XMPP_UI_COMPOSING_MESSAGE = "com.meetwo.XMPP_UI_COMPOSING_MESSAGE";
        public static final String ACTION_XMPP_UI_COMPOSING_PAUSE_MESSAGE = "com.meetwo.XMPP_UI_COMPOSING_PAUSE_MESSAGE";

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null)
                return;

            if (ACTION_XMPP_UI_COMPOSING_MESSAGE.equalsIgnoreCase(intent.getAction())) {
                ((MainActivity) context).updateTyping(true);
            } else if (ACTION_XMPP_UI_COMPOSING_PAUSE_MESSAGE.equalsIgnoreCase(intent.getAction())) {
                ((MainActivity) context).updateTyping(false);
            }
        }
    }

    public static class PresenceUiBoradcast extends BroadcastReceiver {
        public static final String ACTION_XMPP_PRESENCE_UI_UPDATE = "com.meetwo.XMPP_PRESENCE_UI_UPDATE";
        public static final String BUNDLE_PRESENCE_MODE = "presence_mode";
        public static final String BUNDLE_LAST_SEEN = "last_seen";

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null)
                return;

            if (ACTION_XMPP_PRESENCE_UI_UPDATE.equalsIgnoreCase(intent.getAction())) {
                ((MainActivity) context).updatePresence(intent.getIntExtra(BUNDLE_PRESENCE_MODE, -1), intent.getLongExtra(BUNDLE_LAST_SEEN, -1));
            }
        }
    }
}
