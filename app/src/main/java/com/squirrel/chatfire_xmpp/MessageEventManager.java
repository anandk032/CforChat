package com.squirrel.chatfire_xmpp;

import org.jivesoftware.smack.Manager;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.filter.AndFilter;
import org.jivesoftware.smack.filter.MessageTypeFilter;
import org.jivesoftware.smack.filter.NotFilter;
import org.jivesoftware.smack.filter.StanzaExtensionFilter;
import org.jivesoftware.smack.filter.StanzaFilter;
import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Stanza;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by Dharmesh on 1/5/2017.
 */

public class MessageEventManager extends Manager {
    private static final Logger LOGGER = Logger.getLogger(MessageEventManager.class.getName());

    private static final Map<XMPPConnection, MessageEventManager> INSTANCES = new WeakHashMap<>();

    private static final StanzaFilter PACKET_FILTER = new AndFilter(new StanzaExtensionFilter(
            new MessageEvent()), new NotFilter(MessageTypeFilter.ERROR));

    private List<MessageEventNotificationListener> messageEventNotificationListeners = new CopyOnWriteArrayList<MessageEventNotificationListener>();
    private List<MessageEventRequestListener> messageEventRequestListeners = new CopyOnWriteArrayList<MessageEventRequestListener>();

    public synchronized static MessageEventManager getInstanceFor(XMPPConnection connection) {
        MessageEventManager messageEventManager = INSTANCES.get(connection);
        if (messageEventManager == null) {
            messageEventManager = new MessageEventManager(connection);
            INSTANCES.put(connection, messageEventManager);
        }
        return messageEventManager;
    }

    /**
     * Creates a new message event manager.
     *
     * @param connection an XMPPConnection to a XMPP server.
     */
    public MessageEventManager(XMPPConnection connection) {
        super(connection);
        // Listens for all message event packets and fire the proper message event listeners.
        connection.addAsyncStanzaListener(new StanzaListener() {
            public void processPacket(Stanza packet) {
                Message message = (Message) packet;
                MessageEvent messageEvent =
                        (MessageEvent) message.getExtension("x", "jabber:x:event");
                if (messageEvent.isMessageEventRequest()) {
                    // Fire event for requests of message events
                    for (String eventType : messageEvent.getEventTypes())
                        fireMessageEventRequestListeners(
                                message.getFrom(),
                                message.getStanzaId(),
                                eventType.concat("NotificationRequested"));
                } else
                    // Fire event for notifications of message events
                    for (String eventType : messageEvent.getEventTypes())
                        fireMessageEventNotificationListeners(
                                message.getFrom(),
                                messageEvent.getStanzaId(),
                                eventType.concat("Notification"));
            }
        }, PACKET_FILTER);
    }

    /**
     * Adds event notification requests to a message. For each event type that
     * the user wishes event notifications from the message recepient for, <tt>true</tt>
     * should be passed in to this method.
     *
     * @param message   the message to add the requested notifications.
     * @param offline   specifies if the offline event is requested.
     * @param delivered specifies if the delivered event is requested.
     * @param displayed specifies if the displayed event is requested.
     * @param composing specifies if the composing event is requested.
     */
    public static void addNotificationsRequests(Message message, boolean offline,
                                                boolean delivered, boolean displayed, boolean composing) {
        // Create a MessageEvent Package and add it to the message
        MessageEvent messageEvent = new MessageEvent();
        messageEvent.setOffline(offline);
        messageEvent.setDelivered(delivered);
        messageEvent.setDisplayed(displayed);
        messageEvent.setComposing(composing);
        message.addExtension(messageEvent);
    }

    /**
     * Adds a message event request listener. The listener will be fired anytime a request for
     * event notification is received.
     *
     * @param messageEventRequestListener a message event request listener.
     */
    public void addMessageEventRequestListener(MessageEventRequestListener messageEventRequestListener) {
        messageEventRequestListeners.add(messageEventRequestListener);

    }

    /**
     * Removes a message event request listener. The listener will be fired anytime a request for
     * event notification is received.
     *
     * @param messageEventRequestListener a message event request listener.
     */
    public void removeMessageEventRequestListener(MessageEventRequestListener messageEventRequestListener) {
        messageEventRequestListeners.remove(messageEventRequestListener);
    }

    /**
     * Adds a message event notification listener. The listener will be fired anytime a notification
     * event is received.
     *
     * @param messageEventNotificationListener a message event notification listener.
     */
    public void addMessageEventNotificationListener(MessageEventNotificationListener messageEventNotificationListener) {
        messageEventNotificationListeners.add(messageEventNotificationListener);
    }

    /**
     * Removes a message event notification listener. The listener will be fired anytime a notification
     * event is received.
     *
     * @param messageEventNotificationListener a message event notification listener.
     */
    public void removeMessageEventNotificationListener(MessageEventNotificationListener messageEventNotificationListener) {
        messageEventNotificationListeners.remove(messageEventNotificationListener);
    }

    /**
     * Fires message event request listeners.
     */
    private void fireMessageEventRequestListeners(
            String from,
            String packetID,
            String methodName) {
        try {
            Method method =
                    MessageEventRequestListener.class.getDeclaredMethod(
                            methodName,
                            new Class[]{String.class, String.class, MessageEventManager.class});
            for (MessageEventRequestListener listener : messageEventRequestListeners) {
                method.invoke(listener, new Object[]{from, packetID, this});
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error while invoking MessageEventRequestListener", e);
        }
    }

    /**
     * Fires message event notification listeners.
     */
    private void fireMessageEventNotificationListeners(
            String from,
            String packetID,
            String methodName) {
        try {
            Method method =
                    MessageEventNotificationListener.class.getDeclaredMethod(
                            methodName,
                            new Class[]{String.class, String.class});
            for (MessageEventNotificationListener listener : messageEventNotificationListeners) {
                method.invoke(listener, new Object[]{from, packetID});
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error while invoking MessageEventNotificationListener", e);
        }
    }

    /**
     * Sends the notification that the message was delivered to the sender of the original message
     *
     * @param to       the recipient of the notification.
     * @param packetID the id of the message to send.
     * @throws SmackException.NotConnectedException
     */
    public void sendDeliveredNotification(String to, String packetID) throws SmackException.NotConnectedException {
        // Create the message to send
        Message msg = new Message(to);
        // Create a MessageEvent Package and add it to the message
        MessageEvent messageEvent = new MessageEvent();
        messageEvent.setDelivered(true);
        messageEvent.setStanzaId(packetID);
        msg.addExtension(messageEvent);
        // Send the packet
        connection().sendStanza(msg);
    }

    /**
     * Sends the notification that the message was displayed to the sender of the original message
     *
     * @param to       the recipient of the notification.
     * @param packetID the id of the message to send.
     * @throws SmackException.NotConnectedException
     */
    public void sendDisplayedNotification(String to, String packetID) throws SmackException.NotConnectedException {
        // Create the message to send
        Message msg = new Message(to);
        // Create a MessageEvent Package and add it to the message
        MessageEvent messageEvent = new MessageEvent();
        messageEvent.setDisplayed(true);
        messageEvent.setStanzaId(packetID);
        msg.addExtension(messageEvent);
        // Send the packet
        connection().sendStanza(msg);
    }

    /**
     * Sends the notification that the receiver of the message is composing a reply
     *
     * @param to       the recipient of the notification.
     * @param packetID the id of the message to send.
     * @throws SmackException.NotConnectedException
     */
    public void sendComposingNotification(String to, String packetID) throws SmackException.NotConnectedException {
        // Create the message to send
        Message msg = new Message(to);
        // Create a MessageEvent Package and add it to the message
        MessageEvent messageEvent = new MessageEvent();
        messageEvent.setComposing(true);
        messageEvent.setStanzaId(packetID);
        msg.addExtension(messageEvent);
        // Send the packet
        connection().sendStanza(msg);
    }

    /**
     * Sends the notification that the receiver of the message has cancelled composing a reply.
     *
     * @param to       the recipient of the notification.
     * @param packetID the id of the message to send.
     * @throws SmackException.NotConnectedException
     */
    public void sendCancelledNotification(String to, String packetID) throws SmackException.NotConnectedException {
        // Create the message to send
        Message msg = new Message(to);
        // Create a MessageEvent Package and add it to the message
        MessageEvent messageEvent = new MessageEvent();
        messageEvent.setCancelled(true);
        messageEvent.setStanzaId(packetID);
        msg.addExtension(messageEvent);
        // Send the packet
        connection().sendStanza(msg);
    }

    private static class MessageEvent implements ExtensionElement

    {
        public static final String NAMESPACE = "jabber:x:event";
        public static final String ELEMENT = "x";

        public static final String OFFLINE = "offline";
        public static final String COMPOSING = "composing";
        public static final String DISPLAYED = "displayed";
        public static final String DELIVERED = "delivered";
        public static final String CANCELLED = "cancelled";

        private boolean offline = false;
        private boolean delivered = false;
        private boolean displayed = false;
        private boolean composing = false;
        private boolean cancelled = true;

        private String packetID = null;

        /**
         * Returns the XML element name of the extension sub-packet root element.
         * Always returns "x"
         *
         * @return the XML element name of the stanza(/packet) extension.
         */
        public String getElementName() {
            return ELEMENT;
        }

        /**
         * Returns the XML namespace of the extension sub-packet root element.
         * According the specification the namespace is always "jabber:x:event"
         *
         * @return the XML namespace of the stanza(/packet) extension.
         */
        public String getNamespace() {
            return NAMESPACE;
        }

        /**
         * When the message is a request returns if the sender of the message requests to be notified
         * when the receiver is composing a reply.
         * When the message is a notification returns if the receiver of the message is composing a
         * reply.
         *
         * @return true if the sender is requesting to be notified when composing or when notifying
         * that the receiver of the message is composing a reply
         */
        public boolean isComposing() {
            return composing;
        }

        /**
         * When the message is a request returns if the sender of the message requests to be notified
         * when the message is delivered.
         * When the message is a notification returns if the message was delivered or not.
         *
         * @return true if the sender is requesting to be notified when delivered or when notifying
         * that the message was delivered
         */
        public boolean isDelivered() {
            return delivered;
        }

        /**
         * When the message is a request returns if the sender of the message requests to be notified
         * when the message is displayed.
         * When the message is a notification returns if the message was displayed or not.
         *
         * @return true if the sender is requesting to be notified when displayed or when notifying
         * that the message was displayed
         */
        public boolean isDisplayed() {
            return displayed;
        }

        /**
         * When the message is a request returns if the sender of the message requests to be notified
         * when the receiver of the message is offline.
         * When the message is a notification returns if the receiver of the message was offline.
         *
         * @return true if the sender is requesting to be notified when offline or when notifying
         * that the receiver of the message is offline
         */
        public boolean isOffline() {
            return offline;
        }

        /**
         * When the message is a notification returns if the receiver of the message cancelled
         * composing a reply.
         *
         * @return true if the receiver of the message cancelled composing a reply
         */
        public boolean isCancelled() {
            return cancelled;
        }

        /**
         * Returns the unique ID of the message that requested to be notified of the event.
         * The stanza(/packet) id is not used when the message is a request for notifications
         *
         * @return the message id that requested to be notified of the event.
         */
        public String getStanzaId() {
            return packetID;
        }

        /**
         * Returns the types of events. The type of event could be:
         * "offline", "composing","delivered","displayed", "offline"
         *
         * @return a List of all the types of events of the MessageEvent.
         */
        public List<String> getEventTypes() {
            ArrayList<String> allEvents = new ArrayList<String>();
            if (isDelivered()) {
                allEvents.add(MessageEvent.DELIVERED);
            }
            if (!isMessageEventRequest() && isCancelled()) {
                allEvents.add(MessageEvent.CANCELLED);
            }
            if (isComposing()) {
                allEvents.add(MessageEvent.COMPOSING);
            }
            if (isDisplayed()) {
                allEvents.add(MessageEvent.DISPLAYED);
            }
            if (isOffline()) {
                allEvents.add(MessageEvent.OFFLINE);
            }
            return allEvents;
        }

        /**
         * When the message is a request sets if the sender of the message requests to be notified
         * when the receiver is composing a reply.
         * When the message is a notification sets if the receiver of the message is composing a
         * reply.
         *
         * @param composing sets if the sender is requesting to be notified when composing or when
         *                  notifying that the receiver of the message is composing a reply
         */
        public void setComposing(boolean composing) {
            this.composing = composing;
            setCancelled(false);
        }

        /**
         * When the message is a request sets if the sender of the message requests to be notified
         * when the message is delivered.
         * When the message is a notification sets if the message was delivered or not.
         *
         * @param delivered sets if the sender is requesting to be notified when delivered or when
         *                  notifying that the message was delivered
         */
        public void setDelivered(boolean delivered) {
            this.delivered = delivered;
            setCancelled(false);
        }

        /**
         * When the message is a request sets if the sender of the message requests to be notified
         * when the message is displayed.
         * When the message is a notification sets if the message was displayed or not.
         *
         * @param displayed sets if the sender is requesting to be notified when displayed or when
         *                  notifying that the message was displayed
         */
        public void setDisplayed(boolean displayed) {
            this.displayed = displayed;
            setCancelled(false);
        }

        /**
         * When the message is a request sets if the sender of the message requests to be notified
         * when the receiver of the message is offline.
         * When the message is a notification sets if the receiver of the message was offline.
         *
         * @param offline sets if the sender is requesting to be notified when offline or when
         *                notifying that the receiver of the message is offline
         */
        public void setOffline(boolean offline) {
            this.offline = offline;
            setCancelled(false);
        }

        /**
         * When the message is a notification sets if the receiver of the message cancelled
         * composing a reply.
         * The Cancelled event is never requested explicitly. It is requested implicitly when
         * requesting to be notified of the Composing event.
         *
         * @param cancelled sets if the receiver of the message cancelled composing a reply
         */
        public void setCancelled(boolean cancelled) {
            this.cancelled = cancelled;
        }

        /**
         * Sets the unique ID of the message that requested to be notified of the event.
         * The stanza(/packet) id is not used when the message is a request for notifications
         *
         * @param packetID the message id that requested to be notified of the event.
         */
        public void setStanzaId(String packetID) {
            this.packetID = packetID;
        }

        /**
         * Returns true if this MessageEvent is a request for notifications.
         * Returns false if this MessageEvent is a notification of an event.
         *
         * @return true if this message is a request for notifications.
         */
        public boolean isMessageEventRequest() {
            return this.packetID == null;
        }

        /**
         * Returns the XML representation of a Message Event according the specification.
         * <p>
         * Usually the XML representation will be inside of a Message XML representation like
         * in the following examples:<p>
         * <p>
         * Request to be notified when displayed:
         * <pre>
         * &lt;message
         *    to='romeo@montague.net/orchard'
         *    from='juliet@capulet.com/balcony'
         *    id='message22'&gt;
         * &lt;x xmlns='jabber:x:event'&gt;
         *   &lt;displayed/&gt;
         * &lt;/x&gt;
         * &lt;/message&gt;
         * </pre>
         * <p>
         * Notification of displayed:
         * <pre>
         * &lt;message
         *    from='romeo@montague.net/orchard'
         *    to='juliet@capulet.com/balcony'&gt;
         * &lt;x xmlns='jabber:x:event'&gt;
         *   &lt;displayed/&gt;
         *   &lt;id&gt;message22&lt;/id&gt;
         * &lt;/x&gt;
         * &lt;/message&gt;
         * </pre>
         */
        public String toXML() {
            StringBuilder buf = new StringBuilder();
            buf.append("<").append(getElementName()).append(" xmlns=\"").append(getNamespace()).append(
                    "\">");
            // Note: Cancellation events don't specify any tag. They just send the packetID

            // Add the offline tag if the sender requests to be notified of offline events or if
            // the target is offline
            if (isOffline())
                buf.append("<").append(MessageEvent.OFFLINE).append("/>");
            // Add the delivered tag if the sender requests to be notified when the message is
            // delivered or if the target notifies that the message has been delivered
            if (isDelivered())
                buf.append("<").append(MessageEvent.DELIVERED).append("/>");
            // Add the displayed tag if the sender requests to be notified when the message is
            // displayed or if the target notifies that the message has been displayed
            if (isDisplayed())
                buf.append("<").append(MessageEvent.DISPLAYED).append("/>");
            // Add the composing tag if the sender requests to be notified when the target is
            // composing a reply or if the target notifies that he/she is composing a reply
            if (isComposing())
                buf.append("<").append(MessageEvent.COMPOSING).append("/>");
            // Add the id tag only if the MessageEvent is a notification message (not a request)
            if (getStanzaId() != null)
                buf.append("<id>").append(getStanzaId()).append("</id>");
            buf.append("</").append(getElementName()).append(">");
            return buf.toString();
        }

    }

    /**
     * A listener that is fired anytime a message event request is received.
     * Message event requests are received when the received message includes an extension
     * like this:
     * <p>
     * <pre>
     * &lt;x xmlns='jabber:x:event'&gt;
     *  &lt;offline/&gt;
     *  &lt;delivered/&gt;
     *  &lt;composing/&gt;
     * &lt;/x&gt;
     * </pre>
     * <p>
     * In this example you can see that the sender of the message requests to be notified
     * when the user couldn't receive the message because he/she is offline, the message
     * was delivered or when the receiver of the message is composing a reply.
     *
     * @author Gaston Dombiak
     */
    public interface MessageEventRequestListener {

        /**
         * Called when a request for message delivered notification is received.
         *
         * @param from                the user that sent the notification.
         * @param packetID            the id of the message that was sent.
         * @param messageEventManager the messageEventManager that fired the listener.
         * @throws Exception
         */
        public void deliveredNotificationRequested(String from, String packetID,
                                                   MessageEventManager messageEventManager) throws Exception;

        /**
         * Called when a request for message displayed notification is received.
         *
         * @param from                the user that sent the notification.
         * @param packetID            the id of the message that was sent.
         * @param messageEventManager the messageEventManager that fired the listener.
         */
        public void displayedNotificationRequested(String from, String packetID,
                                                   MessageEventManager messageEventManager);

        /**
         * Called when a request that the receiver of the message is composing a reply notification is
         * received.
         *
         * @param from                the user that sent the notification.
         * @param packetID            the id of the message that was sent.
         * @param messageEventManager the messageEventManager that fired the listener.
         */
        public void composingNotificationRequested(String from, String packetID,
                                                   MessageEventManager messageEventManager);

        /**
         * Called when a request that the receiver of the message is offline is received.
         *
         * @param from                the user that sent the notification.
         * @param packetID            the id of the message that was sent.
         * @param messageEventManager the messageEventManager that fired the listener.
         */
        public void offlineNotificationRequested(String from, String packetID,
                                                 MessageEventManager messageEventManager);

    }

    /**
     * A listener that is fired anytime a message event notification is received.
     * Message event notifications are received as a consequence of the request
     * to receive notifications when sending a message.
     *
     * @author Gaston Dombiak
     */
    public interface MessageEventNotificationListener {

        /**
         * Called when a notification of message delivered is received.
         *
         * @param from     the user that sent the notification.
         * @param packetID the id of the message that was sent.
         */
        public void deliveredNotification(String from, String packetID);

        /**
         * Called when a notification of message displayed is received.
         *
         * @param from     the user that sent the notification.
         * @param packetID the id of the message that was sent.
         */
        public void displayedNotification(String from, String packetID);

        /**
         * Called when a notification that the receiver of the message is composing a reply is
         * received.
         *
         * @param from     the user that sent the notification.
         * @param packetID the id of the message that was sent.
         */
        public void composingNotification(String from, String packetID);

        /**
         * Called when a notification that the receiver of the message is offline is received.
         *
         * @param from     the user that sent the notification.
         * @param packetID the id of the message that was sent.
         */
        public void offlineNotification(String from, String packetID);

        /**
         * Called when a notification that the receiver of the message cancelled the reply
         * is received.
         *
         * @param from     the user that sent the notification.
         * @param packetID the id of the message that was sent.
         */
        public void cancelledNotification(String from, String packetID);
    }


}
