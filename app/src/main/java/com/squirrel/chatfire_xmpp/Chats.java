package com.squirrel.chatfire_xmpp;

import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.squirrel.chatfire_xmpp.model.ChatMessage;

import java.util.ArrayList;
import java.util.Random;

/**
 * Created by Dharmesh on 12/28/2016.
 */

public class Chats extends Fragment implements View.OnClickListener {
    private ChatEditText msg_edittext;
    private String user1 = "vijay", user2;// chating with self
    private Random random;
    public static ArrayList<ChatMessage> chatlist = new ArrayList<>();
    public static ChatAdapter chatAdapter = new ChatAdapter(chatlist);
    RecyclerView msgListView;

    public static String PARAM_USER = "user_name";
    private String userName;

    private MyService.UIUpdaterBoradcast uiUpdaterBoradcast;

    public static Chats newInstance(String user) {

        Bundle args = new Bundle();
        args.putString(PARAM_USER, user);
        Chats fragment = new Chats();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            user2 = getArguments().getString(PARAM_USER);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        unRegister();
    }

    @Override
    public void onResume() {
        super.onResume();
        register();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.chat_layout, container, false);
        random = new Random();
        ((MainActivity) getActivity()).getSupportActionBar().setTitle(
                "Chats");
        msg_edittext = (ChatEditText) view.findViewById(R.id.messageEditText);
        msg_edittext.setChatId(user2);
        msgListView = (RecyclerView) view.findViewById(R.id.msgListView);
        msgListView.setLayoutManager(new LinearLayoutManager(getActivity()));
        Button sendButton = (Button) view
                .findViewById(R.id.sendMessageButton);
        sendButton.setOnClickListener(this);

        // ----Set autoscroll of listview when a new message arrives----//
//        msgListView.setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
//        msgListView.setStackFromBottom(true);

        chatlist = new ArrayList<ChatMessage>();
        chatAdapter = new ChatAdapter(chatlist);
        msgListView.setAdapter(chatAdapter);

        ((MainActivity) getActivity()).getSupportActionBar().setTitle(user2);
        return view;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
    }

    public void sendTextMessage(View v) {
        String message = msg_edittext.getEditableText().toString();

        Intent intent = new Intent(MyService.SendMessageBroadcast.ACTION_XMPP_SEND_MESSAGE);
        intent.putExtra(MyService.SendMessageBroadcast.BUNDLE_MSG_TO, user2);
        intent.putExtra(MyService.SendMessageBroadcast.BUNDLE_MSG_BODY, "" + message);
        getActivity().sendBroadcast(intent);

//        if (!message.equalsIgnoreCase("")) {
//            final ChatMessage chatMessage = new ChatMessage(user1, user2,
//                    message, "" + random.nextInt(1000), true);
//            chatMessage.setMsgID();
//            chatMessage.body = message;
//            chatMessage.Date = CommonMethods.getCurrentDate();
//            chatMessage.Time = CommonMethods.getCurrentTime();
        msg_edittext.setText("");
        ChatMessage chatMessage = new ChatMessage(user1, user2, message, String.valueOf(System.currentTimeMillis()), true);
        chatAdapter.add(chatMessage);
        chatAdapter.notifyDataSetChanged();
//            MainActivity activity = ((MainActivity) getActivity());
//            activity.getmService().xmpp.sendMessage(chatMessage);
//        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.sendMessageButton:
                sendTextMessage(v);
        }
    }

    public boolean onBackPressed() {
        ((MainActivity) getActivity()).goBack();
        return false;
    }


    private void register() {
        if (uiUpdaterBoradcast == null)
            uiUpdaterBoradcast = new MyService.UIUpdaterBoradcast();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(MyService.UIUpdaterBoradcast.ACTION_XMPP_UI_COMPOSING_MESSAGE);
        intentFilter.addAction(MyService.UIUpdaterBoradcast.ACTION_XMPP_UI_COMPOSING_PAUSE_MESSAGE);
        getActivity().registerReceiver(uiUpdaterBoradcast, intentFilter);
    }

    private void unRegister() {
        if (uiUpdaterBoradcast != null) {
            getActivity().unregisterReceiver(uiUpdaterBoradcast);
        }
    }
}
