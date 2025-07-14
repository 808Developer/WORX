package com.rcaudle.myapplication.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.rcaudle.myapplication.R;
import com.rcaudle.myapplication.model.Chat;
import java.util.ArrayList;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * created by RCaudle
 */
public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {

    private static final int MSG_TYPE_LEFT = 0;
    private static final int MSG_TYPE_RIGHT = 1;

    private Context mContext;
    private ArrayList<Chat> mChat;
    private String recipientImageUrl;

    public MessageAdapter(Context mContext, ArrayList<Chat> mChat, String recipientImageUrl) {
        this.mContext = mContext;
        this.mChat = mChat;
        this.recipientImageUrl = recipientImageUrl;
    }

    @NonNull
    @Override
    public MessageAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == MSG_TYPE_RIGHT) {
            View view = LayoutInflater.from(mContext).inflate(R.layout.chat_item_right, parent, false);
            return new ViewHolder(view);
        } else {
            View view = LayoutInflater.from(mContext).inflate(R.layout.chat_item_left, parent, false);
            return new ViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull MessageAdapter.ViewHolder holder, int position) {
        Chat chat = mChat.get(position);

        holder.show_message.setText(chat.getMessage());
        holder.message_time.setText(chat.getTime());

        // Handle profile image display
        if (getItemViewType(position) == MSG_TYPE_LEFT) {
            if ("default".equals(recipientImageUrl)) {
                holder.profile_image.setImageResource(R.mipmap.ic_launcher);
            } else {
                Glide.with(mContext).load(recipientImageUrl).into(holder.profile_image);
            }
        }
        
        // Hide profile image on the right for a cleaner UI, similar to popular messengers.
        if (getItemViewType(position) == MSG_TYPE_RIGHT) {
            holder.profile_image.setVisibility(View.GONE);
        }


        // Handle visibility for text vs. image messages
        if (chat.getImageUrl() != null && !chat.getImageUrl().isEmpty() && !"null".equalsIgnoreCase(chat.getImageUrl()) && chat.getMessage().equals("New Picture!")) {
            holder.myImageView.setVisibility(View.VISIBLE);
            holder.show_message.setVisibility(View.GONE);
            Glide.with(mContext)
                    .load(chat.getImageUrl())
                    .placeholder(R.drawable.ic_worx_notification) // Placeholder while loading
                    .into(holder.myImageView);
        } else {
            holder.myImageView.setVisibility(View.GONE);
            holder.show_message.setVisibility(View.VISIBLE);
        }
        
        // Handle read receipts for the last message
        if (position == mChat.size() - 1) {
            holder.txt_read.setVisibility(View.VISIBLE);
            if (chat.isIsread()) {
                holder.txt_read.setText(R.string.message_read);
            } else {
                holder.txt_read.setText(R.string.delivered);
            }
        } else {
            holder.txt_read.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return mChat.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private TextView show_message;
        private TextView txt_read;
        private TextView message_time;
        private CircleImageView profile_image;
        private ImageView myImageView;

        private ViewHolder(View itemView) {
            super(itemView);
            show_message = itemView.findViewById(R.id.show_message);
            txt_read = itemView.findViewById(R.id.txt_read);
            message_time = itemView.findViewById(R.id.message_time);
            profile_image = itemView.findViewById(R.id.profile_image);
            myImageView = itemView.findViewById(R.id.myImageView);
        }
    }

    @Override
    public int getItemViewType(int position) {
        FirebaseUser fuser = FirebaseAuth.getInstance().getCurrentUser();
        if (fuser != null && mChat.get(position).getSender().equals(fuser.getUid())) {
            return MSG_TYPE_RIGHT;
        } else {
            return MSG_TYPE_LEFT;
        }
    }
}
