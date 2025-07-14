package com.rcaudle.myapplication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import com.bumptech.glide.Glide;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.StorageTask;
import com.google.firebase.storage.UploadTask;
import com.rcaudle.myapplication.adapters.MessageAdapter;
import com.rcaudle.myapplication.fragments.APIService;
import com.rcaudle.myapplication.model.Chat;
import com.rcaudle.myapplication.model.User;
import com.rcaudle.myapplication.notifications.Client;
import com.rcaudle.myapplication.notifications.Data;
import com.rcaudle.myapplication.notifications.MyResponse;
import com.rcaudle.myapplication.notifications.Sender;
import com.rcaudle.myapplication.notifications.Token;
import com.rcaudle.myapplication.util.Constants;


import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Objects;

import de.hdodenhof.circleimageview.CircleImageView;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * created by RCaudle
 */
public class MessageActivity extends AppCompatActivity {

    public static final String TAG = "MessageActivity";

    private CircleImageView profile_image;
    private TextView username;
    private FirebaseUser fuser;
    private DatabaseReference userReference;
    private DatabaseReference chatReference;
    private ImageButton btn_send;
    private EditText text_send;
    private MessageAdapter messageAdapter;
    private ArrayList<Chat> mChat;
    private RecyclerView recyclerView;
    private ValueEventListener readListener;
    private String userid;
    private APIService apiService;
    private boolean notify = false;
    private User recipientUser;

    private static final int IMAGE_REQUEST = 1;
    private static final String LOADING_IMAGE_URL = "https://www.google.com/images/spin-32.gif";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_message);

        setupToolbar();
        
        apiService = Client.getClient("https://fcm.googleapis.com/").create(APIService.class);

        recyclerView = findViewById(R.id.mMessageRecyclerView);
        recyclerView.setHasFixedSize(true);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getApplicationContext());
        linearLayoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(linearLayoutManager);

        profile_image = findViewById(R.id.profile_image);
        username = findViewById(R.id.username);
        btn_send = findViewById(R.id.sendButton);
        text_send = findViewById(R.id.messageEditText);
        ImageButton mAddMessageImageButton = findViewById(R.id.mAddMessageImageButton);

        intent = getIntent();
        userid = intent.getStringExtra(Constants.INTENT_USER_ID);
        fuser = FirebaseAuth.getInstance().getCurrentUser();

        btn_send.setOnClickListener(v -> {
            notify = true;
            String msg = text_send.getText().toString().trim();
            if (!msg.isEmpty()) {
                String time = DateFormat.format("MM-dd-yyyy (HH:mm)", Calendar.getInstance()).toString();
                sendMessage(fuser.getUid(), userid, msg, null, time);
            } else {
                Toast.makeText(MessageActivity.this, "You can't send an empty message!", Toast.LENGTH_SHORT).show();
            }
            text_send.setText("");
        });

        mAddMessageImageButton.setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(intent, IMAGE_REQUEST);
        });

        fetchRecipientAndDisplayMessages();
    }
    
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setTitle("");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> 
            startActivity(new Intent(MessageActivity.this, MainActivity.class)
            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)));
    }
    
    private void fetchRecipientAndDisplayMessages() {
        userReference = FirebaseDatabase.getInstance().getReference(Constants.DB_USERS).child(userid);
        userReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                recipientUser = dataSnapshot.getValue(User.class);
                if (recipientUser != null) {
                    username.setText(recipientUser.getUsername());
                    if ("default".equals(recipientUser.getImageURL())) {
                        profile_image.setImageResource(R.mipmap.ic_launcher);
                    } else {
                        Glide.with(getApplicationContext()).load(recipientUser.getImageURL()).into(profile_image);
                    }
                    readMessages(fuser.getUid(), userid, recipientUser.getImageURL());
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                 Log.e(TAG, "Failed to read user data.", databaseError.toException());
            }
        });
    }


    private void sendMessage(String sender, final String receiver, String message, String imageUrl, String time) {
        DatabaseReference reference = FirebaseDatabase.getInstance().getReference();
        
        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put("sender", sender);
        hashMap.put("receiver", receiver);
        hashMap.put("message", message);
        hashMap.put("isread", false);
        hashMap.put("time", time);
        if (imageUrl != null) {
            hashMap.put("imageUrl", imageUrl);
        }

        reference.child(Constants.DB_CHATS).push().setValue(hashMap);

        final DatabaseReference chatRef = FirebaseDatabase.getInstance().getReference(Constants.DB_CHATLIST)
                .child(fuser.getUid())
                .child(userid);
        chatRef.child("id").setValue(userid);
        
        final String msg = message;
        DatabaseReference senderRef = FirebaseDatabase.getInstance().getReference(Constants.DB_USERS).child(fuser.getUid());
        senderRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                User user = dataSnapshot.getValue(User.class);
                if (notify && user != null) {
                    sendNotification(receiver, user.getUsername(), msg);
                }
                notify = false;
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                 Log.e(TAG, "Failed to read sender data for notification.", databaseError.toException());
            }
        });
    }

   private void sendNotification(String receiver, final String username, final String message) {
        DatabaseReference tokens = FirebaseDatabase.getInstance().getReference(Constants.DB_TOKENS);
        Query query = tokens.orderByKey().equalTo(receiver);
        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Token token = snapshot.getValue(Token.class);
                    // The Data payload has been simplified as the server-side should handle this ideally
                    Data data = new Data(fuser.getUid(), R.mipmap.ic_launcher_round, username + ": " + message, "New Message", userid, null, null, null);

                    if (token != null) {
                        Sender sender = new Sender(data, token.getToken());
                        apiService.sendNotification(sender).enqueue(new Callback<MyResponse>() {
                            @Override
                            public void onResponse(@NonNull Call<MyResponse> call, @NonNull Response<MyResponse> response) {
                                if (response.code() == 200 && response.body() != null && response.body().success != 1) {
                                    Log.w(TAG, "Notification sending failed partially.");
                                }
                            }

                            @Override
                            public void onFailure(@NonNull Call<MyResponse> call, @NonNull Throwable t) {
                                Log.e(TAG, "Notification sending failed.", t);
                            }
                        });
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Database error while sending notification", databaseError.toException());
            }
        });
    }


    private void readMessages(final String myid, final String userid, final String recipientImageUrl) {
        mChat = new ArrayList<>();
        chatReference = FirebaseDatabase.getInstance().getReference(Constants.DB_CHATS);
        readListener = chatReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                mChat.clear();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Chat chat = snapshot.getValue(Chat.class);
                    if (chat != null && (chat.getReceiver().equals(myid) && chat.getSender().equals(userid) ||
                            chat.getReceiver().equals(userid) && chat.getSender().equals(myid))) {
                        mChat.add(chat);
                    }
                }
                if (messageAdapter == null) {
                    messageAdapter = new MessageAdapter(MessageActivity.this, mChat, recipientImageUrl);
                    recyclerView.setAdapter(messageAdapter);
                } else {
                    messageAdapter.notifyDataSetChanged();
                    recyclerView.smoothScrollToPosition(mChat.size());
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                 Log.e(TAG, "Failed to read messages.", databaseError.toException());
            }
        });
    }

    private void setStatus(String status) {
        if(fuser != null){
            userReference = FirebaseDatabase.getInstance().getReference(Constants.DB_USERS).child(fuser.getUid());
            HashMap<String, Object> hashMap = new HashMap<>();
            hashMap.put("status", status);
            userReference.updateChildren(hashMap);
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri imageUri = data.getData();
            uploadImageToFirebase(imageUri);
        }
    }
    
    private void uploadImageToFirebase(Uri imageUri) {
        final StorageReference storageReference = FirebaseStorage.getInstance()
            .getReference(Constants.DB_CHATS)
            .child(System.currentTimeMillis() + "." + getFileExtension(imageUri));
        
        Toast.makeText(this, "Uploading image...", Toast.LENGTH_SHORT).show();

        storageReference.putFile(imageUri).addOnSuccessListener(taskSnapshot -> 
            storageReference.getDownloadUrl().addOnSuccessListener(uri -> {
                String imageUrl = uri.toString();
                String time = DateFormat.format("MM-dd-yyyy (HH:mm)", Calendar.getInstance()).toString();
                sendMessage(fuser.getUid(), userid, "New Picture!", imageUrl, time);
                 Toast.makeText(this, "Image sent!", Toast.LENGTH_SHORT).show();
            })
        ).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to upload image.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Image upload failed", e);
        });
    }
    
    private String getFileExtension(Uri uri) {
        ContentResolver contentResolver = getContentResolver();
        MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
        return mimeTypeMap.getExtensionFromMimeType(contentResolver.getType(uri));
    }

    @Override
    protected void onResume() {
        super.onResume();
        setStatus("Online");
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (readListener != null) {
            chatReference.removeEventListener(readListener);
        }
        setStatus("Offline");
    }
    
    // Other lifecycle methods (onStart, onStop, etc.) and Menu methods remain largely the same.
    // ... (onCreateOptionsMenu, onOptionsItemSelected)
}