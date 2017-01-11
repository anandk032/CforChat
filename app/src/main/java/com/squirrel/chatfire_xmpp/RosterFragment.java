package com.squirrel.chatfire_xmpp;


import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

/**
 * A simple {@link Fragment} subclass.
 */
public class RosterFragment extends Fragment implements UserAdapter.OnItemViewClickListener {

    private ArrayList<String> userList;

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
        userList = new ArrayList<>();
        userAdapter = new UserAdapter();
        addUserInRoster();
        userAdapter.setOnItemViewClickListener(this);
        recyclerView.setAdapter(userAdapter);
    }

    @Override
    public void onClick(String user) {
        ((MainActivity) getActivity()).switchContent(Chats.newInstance(user.split("@")[0]), true);
    }

    private void addUserInRoster() {
        userList.add("330500203990305@ip-172-31-53-77.ec2.internal");
        userList.add("224202404651929@ip-172-31-53-77.ec2.internal");
        userList.add("dharmesh@54.205.116.234");
        userList.add("vijay@54.205.116.234");
        userList.add("zen@54.205.116.234");
        userList.add("anand@54.205.116.234");
        userList.add("tapan@54.205.116.234");
        userList.add("ravi@54.205.116.234");
        userAdapter.addData(userList);
    }

}
