package com.squirrel.chatfire_xmpp;


import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * A simple {@link Fragment} subclass.
 */
public class RosterFragment extends Fragment implements UserAdapter.OnItemViewClickListener {


    public RosterFragment() {
        // Required empty public constructor
    }

    private RecyclerView recyclerView;
    public static UserAdapter userAdapter;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_roster, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = (RecyclerView) view.findViewById(R.id.alluser);
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

        userAdapter = new UserAdapter();
        userAdapter.setOnItemViewClickListener(this);
        recyclerView.setAdapter(userAdapter);
    }

    @Override
    public void onClick(String user) {
        ((MainActivity) getActivity()).switchContent(Chats.newInstance(user), false);
    }
}
