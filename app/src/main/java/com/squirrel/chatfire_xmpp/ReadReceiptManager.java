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

public class ReadReceiptManager implements StanzaListener {
    private static final String TAG = "ReadReceiptManager";

    private static Map<XMPPConnection, ReadReceiptManager> instances = Collections.synchronizedMap(new WeakHashMap<XMPPConnection, ReadReceiptManager>());
    private Set<ReceiptReceivedListener> receiptReceivedListeners = Collections.synchronizedSet(new HashSet<ReceiptReceivedListener>());

    private ReadReceiptManager(XMPPConnection connection) {
        ServiceDiscoveryManager sdm = ServiceDiscoveryManager.getInstanceFor(connection);
        sdm.addFeature(ReadReceipt.NAMESPACE);
        //connection.addPacketListener(this, new PacketExtensionFilter(ReadReceipt.NAMESPACE));
        connection.addSyncStanzaListener(ReadReceiptManager.this, new StanzaExtensionFilter(ReadReceipt.NAMESPACE));
    }

    public static synchronized ReadReceiptManager getInstanceFor(XMPPConnection connection) {
        ReadReceiptManager receiptManager = instances.get(connection);
        if (receiptManager == null) {
            receiptManager = new ReadReceiptManager(connection);
        }
        return receiptManager;
    }

    @Override
    public void processPacket(Stanza packet) {
        ReadReceipt dr = packet.getExtension(ReadReceipt.ELEMENT, ReadReceipt.NAMESPACE);
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

    public static class ReadReceipt implements ExtensionElement {
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

    public static class ReadReceiptProvider extends EmbeddedExtensionProvider {
        @Override
        protected ExtensionElement createReturnExtension(String currentElement, String currentNamespace, Map attributeMap, List content) {
            return new ReadReceipt((String) attributeMap.get("id"));
        }
    }
}