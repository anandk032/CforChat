package com.squirrel.chatfire_xmpp;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private MyService mService;
    private boolean mBounded;
    private SharedPreferences prefs;
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
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        doBindService();
        switchContent(new RosterFragment(), false);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_log_out) {
            doUnbindService();
            mService.stop();
            prefs.edit().clear().commit();
            finish();
            startActivity(new Intent(this, LoginActivity.class));
            return true;
        }

        if (id == R.id.available) {
            Intent intent = new Intent(MyService.SendMessageBroadcast.ACTION_XMPP_SET_PRESENCE_MODE);
            intent.putExtra(MyService.SendMessageBroadcast.BUNDLE_PRESENCE_MODE, 1);
            sendBroadcast(intent);
            return true;
        }
        if (id == R.id.unavailable) {
            Intent intent = new Intent(MyService.SendMessageBroadcast.ACTION_XMPP_SET_PRESENCE_MODE);
            intent.putExtra(MyService.SendMessageBroadcast.BUNDLE_PRESENCE_MODE, 4);
            sendBroadcast(intent);
            return true;
        }
        if (id == R.id.away) {
            Intent intent = new Intent(MyService.SendMessageBroadcast.ACTION_XMPP_SET_PRESENCE_MODE);
            intent.putExtra(MyService.SendMessageBroadcast.BUNDLE_PRESENCE_MODE, 2);
            sendBroadcast(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    public void updateTyping(boolean isTyping) {
        if (isTyping) {
            getSupportActionBar().setSubtitle("typing...");
        } else {
            getSupportActionBar().setSubtitle("online");
        }
    }

    public void updatePresence(int presenceMode, long seconds) {
        Log.i(TAG, "Presence Mode:" + presenceMode);
        if (presenceMode == 1) {
            getSupportActionBar().setSubtitle("online");
        } else if (presenceMode == 2 || presenceMode == 3) {
            getSupportActionBar().setSubtitle("");
        } else if (presenceMode == 0 || presenceMode == -1) {
            getSupportActionBar().setSubtitle("");
        }
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
//        bindService(new Intent(this, MyService.class), mConnection,
//                Context.BIND_AUTO_CREATE);
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
        MyXMPP.setIsAppFront(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        MyXMPP.setIsAppFront(true);
    }

    @Override
    public void onBackPressed() {

        Fragment f = getSupportFragmentManager().findFragmentByTag(Chats.class.getSimpleName());
        if (f != null && !((Chats) f).onBackPressed()) {
            return;
        }

        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            super.onBackPressed();
            return;
        }

    }

    public void goBack() {
        if (getSupportFragmentManager() != null) {
            getSupportFragmentManager().popBackStack();
        }
    }
}
