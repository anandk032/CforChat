package com.squirrel.chatfire_xmpp;

import android.util.Log;

import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.filter.StanzaExtensionFilter;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smackx.disco.ServiceDiscoveryManager;
import org.jivesoftware.smackx.receipts.ReceiptReceivedListener;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class ReadReceiptManager implements StanzaListener {
    private static final String TAG = "ReadReceiptManager";

    private static Map<XMPPConnection, ReadReceiptManager> instances = Collections.synchronizedMap(new WeakHashMap<XMPPConnection, ReadReceiptManager>());
    private Set<ReceiptReceivedListener> receiptReceivedListeners = Collections.synchronizedSet(new HashSet<ReceiptReceivedListener>());

    private ReadReceiptManager(XMPPConnection connection) {
        ServiceDiscoveryManager sdm = ServiceDiscoveryManager.getInstanceFor(connection);
        sdm.addFeature(MyXMPP.ReadReceipt.NAMESPACE);
        //connection.addPacketListener(this, new PacketExtensionFilter(ReadReceipt.NAMESPACE));
        connection.addSyncStanzaListener(ReadReceiptManager.this, new StanzaExtensionFilter(MyXMPP.ReadReceipt.NAMESPACE));
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
        MyXMPP.ReadReceipt dr = packet.getExtension(MyXMPP.ReadReceipt.ELEMENT, MyXMPP.ReadReceipt.NAMESPACE);
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
}