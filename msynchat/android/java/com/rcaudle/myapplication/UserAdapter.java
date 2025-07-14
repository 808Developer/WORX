package com.rcaudle.myapplication;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import de.hdodenhof.circleimageview.CircleImageView;

import com.bumptech.glide.Glide;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import com.rcaudle.myapplication.MessageActivity;
import com.rcaudle.myapplication.R;
import com.rcaudle.myapplication.model.Chat;
import com.rcaudle.myapplication.model.User;
import com.rcaudle.myapplication.util.Constants;
/**
 * created by RCaudle
 */
public class UserAdapter extends RecyclerView.Adapter<UserAdapter.ViewHolder> {

    private Context mContext;
    private List<User> mUsers;
    private boolean isChat;
    private String theLastMessage;

    public UserAdapter(Context mContext, List<User> mUsers, boolean isChat) {
        this.mUsers = mUsers;
        this.mContext = mContext;
        this.isChat = isChat;
    }
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mContext).inflate(R.layout.user_item, parent, false);
        return new ViewHolder(view);
    }
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final User user = mUsers.get(position);
        holder.username.setText(user.getUsername());
        if ("default".equals(user.getImageURL())) {
            holder.profile_image.setImageResource(R.mipmap.ic_launcher);
        } else {
            Glide.with(mContext).load(user.getImageURL()).into(holder.profile_image);
        }
        if (isChat) {
            lastMessage(user.getId(), holder.last_msg);
        } else {
            holder.last_msg.setVisibility(View.GONE);
        }
        if (isChat) {
            if ("Online".equals(user.getStatus())) {
                holder.img_on.setVisibility(View.VISIBLE);
                holder.img_off.setVisibility(View.GONE);
            } else {
                holder.img_on.setVisibility(View.GONE);
                holder.img_off.setVisibility(View.VISIBLE);
            }
        } else {
            holder.img_on.setVisibility(View.GONE);
            holder.img_off.setVisibility(View.GONE);
        }
        holder.itemView.setOnClickListener(view -> {
            Intent intent = new Intent(mContext, MessageActivity.class);
            intent.putExtra(Constants.INTENT_USER_ID, user.getId());
            mContext.startActivity(intent);
        });
    }
    @Override
    public int getItemCount() {
        return mUsers.size();
    }
    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView username;
        public CircleImageView profile_image;
        private CircleImageView img_on;
        private CircleImageView img_off;
        private ImageView myImageView; // This seems to be a notification icon
        private TextView last_msg;

        ViewHolder(View itemView) {
            super(itemView);
            username = itemView.findViewById(R.id.username);
            profile_image = itemView.findViewById(R.id.profile_image);
            img_on = itemView.findViewById(R.id.img_on);
            img_off = itemView.findViewById(R.id.img_off);
            last_msg = itemView.findViewById(R.id.last_msg);
            myImageView = itemView.findViewById(R.id.myImageView);
        }
    }
    // Check for the last message
    private void lastMessage(final String userid, final TextView last_msg) {
        theLastMessage = "default";
        final FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        DatabaseReference reference = FirebaseDatabase.getInstance().getReference(Constants.DB_CHATS);

        // This listener will iterate through all chats to find the last one for this user.
        // For production apps, this is inefficient and a better DB structure is advised.
        reference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (firebaseUser == null) return;
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Chat chat = snapshot.getValue(Chat.class);
                    if (chat != null && chat.getReceiver() != null && chat.getSender() != null) {
                         if ((chat.getReceiver().equals(firebaseUser.getUid()) && chat.getSender().equals(userid)) ||
                            (chat.getReceiver().equals(userid) && chat.getSender().equals(firebaseUser.getUid()))) {
                             if ("New Picture!".equals(chat.getMessage())) {
                                 theLastMessage = mContext.getString(R.string.new_picture_received);
                             } else {
                                theLastMessage = chat.getMessage();
                             }
                         }
                    }
                }
                if ("default".equals(theLastMessage)) {
                    last_msg.setText(R.string.no_messages);
                } else {
                    last_msg.setText(theLastMessage);
                }
                theLastMessage = "default";
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                 Toast.makeText(mContext, "Failed to load last message.", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
