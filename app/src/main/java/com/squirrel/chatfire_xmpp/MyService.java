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
        return Service.START_STICKY;
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
        registerReceiver(mSendMessageBroadcast, intentFilter);
    }

    private void unRegister() {
        if (mSendMessageBroadcast != null) {
            unregisterReceiver(mSendMessageBroadcast);
        }
    }

    public class SendMessageBroadcast extends BroadcastReceiver {
        public static final String ACTION_XMPP_SEND_MESSAGE = "com.meetwo.XMPP_SEND_MESSAGE";
        public static final String BUNDLE_MSG_BODY = "msg_body";
        public static final String BUNDLE_MSG_TO = "msg_to";

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null)
                return;

            if (ACTION_XMPP_SEND_MESSAGE.equalsIgnoreCase(intent.getAction())) {
                sendMessage(intent.getStringExtra(BUNDLE_MSG_TO), intent.getStringExtra(BUNDLE_MSG_BODY));
            }
        }

        private void sendMessage(String to, String body) {
            if (xmpp != null) {
                String msgId = String.valueOf(System.currentTimeMillis());
                ChatMessage chatMessage = new ChatMessage(xmpp.getUserId(), to, body, msgId, true);
                xmpp.sendMessage(chatMessage);
            }
        }
    }

}
