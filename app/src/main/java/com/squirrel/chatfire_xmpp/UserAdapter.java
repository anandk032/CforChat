package com.squirrel.chatfire_xmpp;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.TextView;

import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smack.roster.packet.RosterPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Dharmesh on 12/28/2016.
 */

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.MyViewHolder> {
    private List<RosterEntry> entries = new ArrayList<>();

    @Override
    public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new MyViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.list, parent, false));
    }

    @Override
    public void onBindViewHolder(final MyViewHolder holder, int position) {
        holder.textView.setText(entries.get(position).getName());
//        holder.status.setText(entries.get(position).getStatus().compareTo(RosterPacket.ItemStatus.subscribe));

        holder.textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (onItemViewClickListener != null) {
                    onItemViewClickListener.onClick(entries.get(holder.getAdapterPosition()).getName());
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    public void addData(List<RosterEntry> list) {
        entries.clear();
        entries.addAll(list);
        notifyDataSetChanged();
    }

    public class MyViewHolder extends RecyclerView.ViewHolder {
        TextView textView, status;

        public MyViewHolder(View itemView) {
            super(itemView);
            textView = (TextView) itemView.findViewById(R.id.userName);
            status = (TextView) itemView.findViewById(R.id.user_status);
        }
    }

    public interface OnItemViewClickListener {
        public void onClick(String user);
    }

    public void setOnItemViewClickListener(OnItemViewClickListener onItemViewClickListener) {
        this.onItemViewClickListener = onItemViewClickListener;
    }

    private OnItemViewClickListener onItemViewClickListener;
}
