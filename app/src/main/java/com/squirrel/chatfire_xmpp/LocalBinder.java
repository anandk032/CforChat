package com.squirrel.chatfire_xmpp;

import android.os.Binder;

import java.lang.ref.WeakReference;

/**
 * Created by Dharmesh on 12/28/2016.
 */

public class LocalBinder<S> extends Binder {
    private final WeakReference<S> mService;

    public LocalBinder(final S service) {
        mService = new WeakReference<S>(service);
    }

    public S getService() {
        return mService.get();
    }
}
