package com.squirrel.chatfire_xmpp;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.RequiresApi;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;

/**
 * Created by Dell on 05-01-2017.
 */

public class ChatEditText extends EditText {

    private Handler handler = new Handler();
    private boolean previousFocusStatus;
    private String chatId = null;
    private long millis = 0;

    public ChatEditText(Context context) {
        super(context);
        init(context);
    }

    public ChatEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ChatEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public ChatEditText(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }

    void init(Context context) {
        setUserTypingListener();
        setUserStoppedTypingListener();
    }

    private void setUserTypingListener() {
        this.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) {
                    //eventListener.userIsTyping();
                    if (System.currentTimeMillis() - millis > 1500) {
                        millis = System.currentTimeMillis();
                        handler.removeCallbacks(runnableSend);
                        handler.postDelayed(runnableSend, 1500);
                        sendBroadcast(true);
                    }
                    handler.removeCallbacks(runnable);
                    handler.postDelayed(runnable, 2000);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            sendBroadcast(false);
        }
    };

    private Runnable runnableSend = new Runnable() {
        @Override
        public void run() {
            millis = 0;
        }
    };

    private void setUserStoppedTypingListener() {
        this.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    previousFocusStatus = true;
                } else if (!hasFocus) {
                    previousFocusStatus = false;
                } else if (previousFocusStatus && !hasFocus) {
                    //eventListener.userHasStoppedTyping();
                    sendBroadcast(false);
                }
            }
        });
    }

    private void sendBroadcast(boolean typing) {
        if (chatId == null) return;

        Intent intent;
        if (typing)
            intent = new Intent(MyService.SendMessageBroadcast.ACTION_XMPP_COMPOSING_MESSAGE);
        else
            intent = new Intent(MyService.SendMessageBroadcast.ACTION_XMPP_COMPOSING_PAUSE_MESSAGE);
        intent.putExtra(MyService.SendMessageBroadcast.BUNDLE_MSG_TO, chatId);
        getContext().sendBroadcast(intent);
    }

    public String getChatId() {
        return chatId;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }
}
