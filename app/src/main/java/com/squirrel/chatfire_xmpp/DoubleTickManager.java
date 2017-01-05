package com.squirrel.chatfire_xmpp;

import android.util.Log;

import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.filter.StanzaExtensionFilter;
import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.provider.EmbeddedExtensionProvider;
import org.jivesoftware.smackx.disco.ServiceDiscoveryManager;
import org.jivesoftware.smackx.receipts.ReceiptReceivedListener;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class DoubleTickManager implements StanzaListener {
    private static final String TAG = "DoubleTickManager";

    private static Map<XMPPConnection, DoubleTickManager> instances = Collections.synchronizedMap(new WeakHashMap<XMPPConnection, DoubleTickManager>());
    private Set<ReceiptReceivedListener> receiptReceivedListeners = Collections.synchronizedSet(new HashSet<ReceiptReceivedListener>());

    private DoubleTickManager(XMPPConnection connection) {
        ServiceDiscoveryManager sdm = ServiceDiscoveryManager.getInstanceFor(connection);
        sdm.addFeature(DoubleTickReceipt.NAMESPACE);
        //connection.addPacketListener(this, new PacketExtensionFilter(ReadReceipt.NAMESPACE));
        connection.addSyncStanzaListener(DoubleTickManager.this, new StanzaExtensionFilter(DoubleTickReceipt.NAMESPACE));
    }

    public static synchronized DoubleTickManager getInstanceFor(XMPPConnection connection) {
        DoubleTickManager receiptManager = instances.get(connection);
        if (receiptManager == null) {
            receiptManager = new DoubleTickManager(connection);
        }
        return receiptManager;
    }

    @Override
    public void processPacket(Stanza packet) {
        DoubleTickReceipt dr = packet.getExtension(DoubleTickReceipt.ELEMENT, DoubleTickReceipt.NAMESPACE);
        if (dr != null) {
            for (ReceiptReceivedListener l : receiptReceivedListeners) {
                //l.onReceiptReceived(packet.getFrom(), packet.getTo(), dr.getId());
                Log.i(TAG, "Read receipt from:" + packet.getFrom() + " & to:" + packet.getTo() + " &stanzaId:" + dr.getId());
            }
        }
    }

    public void addReadReceivedListener(ReceiptReceivedListener listener) {
        receiptReceivedListeners.add(listener);
    }

    public void removeRemoveReceivedListener(ReceiptReceivedListener listener) {
        receiptReceivedListeners.remove(listener);
    }

    public static class DoubleTickReceipt implements ExtensionElement {
        public static final String NAMESPACE = "urn:xmpp:double:tick";
        public static final String ELEMENT = "doubletick";

        private String id; /// original ID of the delivered message

        public DoubleTickReceipt(String id) {
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
            return "<" + ELEMENT + " xmlns='" + NAMESPACE + "' id='" + id + "'/>";
        }
    }

    public static class DoubleTickProvider extends EmbeddedExtensionProvider {
        @Override
        protected ExtensionElement createReturnExtension(String currentElement, String currentNamespace, Map attributeMap, List content) {
            return new DoubleTickReceipt((String) attributeMap.get("id"));
        }
    }
}