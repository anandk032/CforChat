package com.squirrel.chatfire_xmpp;

import android.util.Log;

import org.jivesoftware.smack.AbstractConnectionListener;
import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.ConnectionCreationListener;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.ReconnectionManager;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPConnectionRegistry;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.StreamError;
import org.jivesoftware.smack.util.Async;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by Dell on 09-01-2017.
 */

public class MyReconnectionManager {
    private static final String TAG = "MyReconnectionManager";
    private static final Logger LOGGER = Logger.getLogger(MyReconnectionManager.class.getName());

    private static final Map<AbstractXMPPConnection, MyReconnectionManager> INSTANCES = new WeakHashMap<AbstractXMPPConnection, MyReconnectionManager>();
    private final WeakReference<AbstractXMPPConnection> weakRefConnection;
    private final int randomBase = new Random().nextInt(13) + 2; // between 2 and 15 seconds
    private final Runnable reconnectionRunnable;

    private static int defaultFixedDelay = 15;
    private static ReconnectionManager.ReconnectionPolicy defaultReconnectionPolicy = ReconnectionManager.ReconnectionPolicy.RANDOM_INCREASING_DELAY;

    private volatile int fixedDelay = defaultFixedDelay;
    private volatile ReconnectionManager.ReconnectionPolicy reconnectionPolicy = defaultReconnectionPolicy;

    private boolean automaticReconnectEnabled = false;

    boolean done = false;

    private Thread reconnectionThread;

    private static boolean enabledPerDefault = false;

    public static synchronized MyReconnectionManager getInstanceFor(AbstractXMPPConnection connection) {
        MyReconnectionManager reconnectionManager = INSTANCES.get(connection);
        if (reconnectionManager == null) {
            reconnectionManager = new MyReconnectionManager(connection);
            INSTANCES.put(connection, reconnectionManager);
        }
        return reconnectionManager;
    }

    static {
        XMPPConnectionRegistry.addConnectionCreationListener(new ConnectionCreationListener() {
            public void connectionCreated(XMPPConnection connection) {
                if (connection instanceof AbstractXMPPConnection) {
                    MyReconnectionManager.getInstanceFor((AbstractXMPPConnection) connection);
                }
            }
        });
    }

    private MyReconnectionManager(AbstractXMPPConnection connection) {
        weakRefConnection = new WeakReference<>(connection);

        reconnectionRunnable = new Thread() {

            /**
             * Holds the current number of reconnection attempts
             */
            private int attempts = 0;

            /**
             * Returns the number of seconds until the next reconnection attempt.
             *
             * @return the number of seconds until the next reconnection attempt.
             */
            private int timeDelay() {
                attempts++;

                // Delay variable to be assigned
                int delay;
                switch (reconnectionPolicy) {
                    case FIXED_DELAY:
                        delay = fixedDelay;
                        break;
                    case RANDOM_INCREASING_DELAY:
                        if (attempts > 13) {
                            delay = randomBase * 6 * 5; // between 2.5 and 7.5 minutes (~5 minutes)
                        } else if (attempts > 7) {
                            delay = randomBase * 6; // between 30 and 90 seconds (~1 minutes)
                        } else {
                            delay = randomBase; // 10 seconds
                        }
                        break;
                    default:
                        throw new AssertionError("Unknown reconnection policy " + reconnectionPolicy);
                }

                return delay;
            }

            /**
             * The process will try the reconnection until the connection succeed or the user cancel it
             */
            public void run() {
                final AbstractXMPPConnection connection = weakRefConnection.get();
                if (connection == null) {
                    return;
                }
                // The process will try to reconnect until the connection is established or
                // the user cancel the reconnection process AbstractXMPPConnection.disconnect().
                while (isReconnectionPossible(connection)) {
                    // Find how much time we should wait until the next reconnection
                    int remainingSeconds = timeDelay();
                    // Sleep until we're ready for the next reconnection attempt. Notify
                    // listeners once per second about how much time remains before the next
                    // reconnection attempt.
                    while (isReconnectionPossible(connection) && remainingSeconds > 0) {
                        try {
                            Thread.sleep(1000);
                            remainingSeconds--;

                            //for (ConnectionListener listener : connection.connectionListeners) {
                            //    listener.reconnectingIn(remainingSeconds);
                            //}
                            Log.i(TAG, "Connecting in " + remainingSeconds + " & INSTANCES size:" + INSTANCES.size());
                            LOGGER.log(Level.FINE, "Connecting in " + remainingSeconds);
                        } catch (InterruptedException e) {
                            LOGGER.log(Level.FINE, "waiting for reconnection interrupted", e);
                            Log.i(TAG, "Reconnection interrupted " + e.getMessage());
                            break;
                        }
                    }

                    //for (ConnectionListener listener : connection.connectionListeners) {
                    //    listener.reconnectingIn(0);
                    //}

                    // Makes a reconnection attempt
                    try {
                        if (isReconnectionPossible(connection)) {
                            connection.connect();
                        }
                        // TODO Starting with Smack 4.2, connect() will no
                        // longer login automatically. So change this and the
                        // previous lines to connection.connect().login() in the
                        // 4.2, or any later, branch.
                        if (!connection.isAuthenticated()) {
                            connection.login();
                        }
                        // Successfully reconnected.
                        attempts = 0;
                    } catch (SmackException | IOException | XMPPException e) {
                        // Fires the failed reconnection notification
                        //for (ConnectionListener listener : connection.connectionListeners) {
                        //    listener.reconnectionFailed(e);
                        //}
                        LOGGER.log(Level.FINE, "Failed to reconnect", e);
                        Log.i(TAG, "Failed to reconnect: " + e.getMessage());
                    }
                }
            }
        };

        // If the reconnection mechanism is enable per default, enable it for this ReconnectionManager instance
        if (getEnabledPerDefault()) {
            enableAutomaticReconnection();
        }
    }

    public synchronized void enableAutomaticReconnection() {
        if (automaticReconnectEnabled) {
            return;
        }
        XMPPConnection connection = weakRefConnection.get();
        if (connection == null) {
            throw new IllegalStateException("Connection instance no longer available");
        }
        connection.addConnectionListener(connectionListener);
        automaticReconnectEnabled = true;
    }

    private final ConnectionListener connectionListener = new AbstractConnectionListener() {

        @Override
        public void connectionClosed() {
            done = true;
        }

        @Override
        public void authenticated(XMPPConnection connection, boolean resumed) {
            done = false;
        }

        @Override
        public void connectionClosedOnError(Exception e) {
            done = false;
            if (!isAutomaticReconnectEnabled()) {
                return;
            }
            if (e instanceof XMPPException.StreamErrorException) {
                XMPPException.StreamErrorException xmppEx = (XMPPException.StreamErrorException) e;
                StreamError error = xmppEx.getStreamError();

                if (StreamError.Condition.conflict == error.getCondition()) {
                    return;
                }
            }

            reconnect();
        }
    };


    public synchronized void interruptCheckConnection() {
        XMPPConnection connection = this.weakRefConnection.get();
        if (connection == null) {
            return;
        }

        // Since there is no thread running, creates a new one to attempt
        // the reconnection.
        // avoid to run duplicated reconnectionThread -- fd: 16/09/2010
        if (reconnectionThread != null && reconnectionThread.isAlive()) {
            try {
                reconnectionThread.interrupt();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

        reconnectionThread = Async.go(reconnectionRunnable,
                "Smack Reconnection Manager (" + connection.getConnectionCounter() + ')');
    }

    public synchronized void reconnect() {
        XMPPConnection connection = this.weakRefConnection.get();
        if (connection == null) {
            LOGGER.fine("Connection is null, will not reconnect");
            return;
        }

        // Since there is no thread running, creates a new one to attempt
        // the reconnection.
        // avoid to run duplicated reconnectionThread -- fd: 16/09/2010
        if (reconnectionThread != null && reconnectionThread.isAlive()) {
            return;
        }

        reconnectionThread = Async.go(reconnectionRunnable,
                "Smack Reconnection Manager (" + connection.getConnectionCounter() + ')');
    }

    private boolean isReconnectionPossible(XMPPConnection connection) {
        return !done && !connection.isConnected()
                && isAutomaticReconnectEnabled();
    }

    public static boolean getEnabledPerDefault() {
        return enabledPerDefault;
    }

    public boolean isAutomaticReconnectEnabled() {
        return automaticReconnectEnabled;
    }

    public void setReconnectionPolicy(ReconnectionManager.ReconnectionPolicy reconnectionPolicy) {
        this.reconnectionPolicy = reconnectionPolicy;
    }

    public void setFixedDelay(int fixedDelay) {
        this.fixedDelay = fixedDelay;
        setReconnectionPolicy(ReconnectionManager.ReconnectionPolicy.FIXED_DELAY);
    }

    public static void notifyConnectivityChange(AbstractXMPPConnection connection) {
        getInstanceFor(connection);
    }
}
