package com.squirrel.chatfire_xmpp;

import android.graphics.Color;
import android.support.v7.widget.RecyclerView;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.squirrel.chatfire_xmpp.model.ChatMessage;

import java.util.ArrayList;

/**
 * Created by Dharmesh on 12/28/2016.
 */

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.MyViewHolder> {
    private ArrayList<ChatMessage> messages;

    public ChatAdapter(ArrayList<ChatMessage> chatlist) {
        messages = chatlist;
    }

    @Override
    public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new MyViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.chatbubble, parent, false));
    }

    @Override
    public void onBindViewHolder(MyViewHolder holder, int position) {
        ChatMessage message = (ChatMessage) messages.get(position);
        holder.msg.setText(message.body);
        // if message is mine then align to right
        if (message.isMine) {
            holder.layout.setBackgroundResource(R.drawable.bubble2);
            holder.parent_layout.setGravity(Gravity.RIGHT);
        }
        // If not mine then align to left
        else {
            holder.layout.setBackgroundResource(R.drawable.bubble1);
            holder.parent_layout.setGravity(Gravity.LEFT);
        }
        holder.msg.setTextColor(Color.BLACK);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public class MyViewHolder extends RecyclerView.ViewHolder {
        TextView msg;
        LinearLayout layout, parent_layout;

        public MyViewHolder(View itemView) {
            super(itemView);

            layout = (LinearLayout) itemView.findViewById(R.id.bubble_layout);
            msg = (TextView) itemView.findViewById(R.id.message_text);
            parent_layout = (LinearLayout) itemView.findViewById(R.id.bubble_layout_parent);
        }
    }

    public void add(ChatMessage object) {
        messages.add(object);
    }
}
