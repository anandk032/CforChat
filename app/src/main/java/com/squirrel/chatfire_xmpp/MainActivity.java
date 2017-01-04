package com.squirrel.chatfire_xmpp;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private MyService mService;
    private boolean mBounded;
    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mService = ((LocalBinder<MyService>) iBinder).getService();
            mBounded = true;
            Log.d(TAG, "onServiceConnected");
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mService = null;
            mBounded = false;
            Log.d(TAG, "onServiceDisconnected");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        doBindService();
        switchContent(Chats.newInstance(""), false);
//        switchContent(new Chats(), false);

        findViewById(R.id.btnGetUser).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // getmService().xmpp.precence();
            }
        });
    }

    public void switchContent(Fragment fragment, boolean isAddBackStack) {
        if (fragment == null)
            return;

        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.container, fragment, fragment.getClass().getSimpleName());
        if (isAddBackStack) ft.addToBackStack(null);
        ft.commit();
    }

    void doBindService() {
        Log.e(TAG, "doBindService: call");
        startService(new Intent(this, MyService.class));
        bindService(new Intent(this, MyService.class), mConnection,
                Context.BIND_AUTO_CREATE);
    }

    void doUnbindService() {
        unbindService(mConnection);
    }

    public MyService getmService() {
        return mService;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        doUnbindService();
    }


}
