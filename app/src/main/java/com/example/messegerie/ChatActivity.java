package com.example.messegerie;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatActivity extends AppCompatActivity {

    private ListView lvMessages;
    private EditText etMessage;
    private Button btnSend, btnBack;
    private TextView tvChatTitle;
    private ArrayAdapter<String> messagesAdapter;

    private List<String> messages = new ArrayList<>();
    private Client client;

    private String targetIp, targetName, myIp;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isConnected = false;
    private ExecutorService networkExecutor = Executors.newSingleThreadExecutor();

    //private static final String TAG = "chat";
    private static final int SERVER_PORT = 8080;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        
        // Safely get intent extras with null checks
        targetIp = getIntent().getStringExtra("targetIp");
        targetName = getIntent().getStringExtra("targetName");
        myIp = getIntent().getStringExtra("myIp");
        
        // Validate required data
        if (targetIp == null || targetName == null) {
            Toast.makeText(this, "Error: Missing required chat information", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        MainActivity.setActiveChatActivity(this);
        initializeViews();
        setupListView();
        connectToUser();
    }
    private void initializeViews() {
        tvChatTitle = findViewById(R.id.tvChatTitle);
        btnBack = findViewById(R.id.btnBack);
        lvMessages = findViewById(R.id.lvMessages);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        tvChatTitle.setText("Chat with " + targetName + " (" + targetIp + ")");
        btnBack.setOnClickListener(v -> finish());
        btnSend.setOnClickListener(v -> sendMessage());
        btnSend.setEnabled(false);
       //etMessage.setHint("Connecting");
    }
    private void setupListView() {
        messagesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, messages);
        lvMessages.setAdapter(messagesAdapter);
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        messages.add(time+"connecting to"+targetName+" Ip:"+targetIp);
        messagesAdapter.notifyDataSetChanged();
    }
    private void connectToUser() {
        //Log.d(TAG, "connectToUser: Connecting" + targetIp + ":" + SERVER_PORT);
        networkExecutor.execute(() -> {
            try {
                client = new Client(targetIp, SERVER_PORT, new Client.MessageListener() {
                    @Override
                    public void onMessageReceived(String message) {
                        //Log.d(TAG, "Received message: " + message);
                        handler.post(() -> {
                            String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
                            String formattedMessage =time + " " + targetName + ": " + message;
                            messages.add(formattedMessage);
                            messagesAdapter.notifyDataSetChanged();
                            lvMessages.setSelection(messages.size()-1);
                        });
                    }
                    @Override
                    public void onConnectionEstablished() {
                        handler.post(() -> {
                            isConnected = true;
                            btnSend.setEnabled(true);
                            //etMessage.setHint("typing");
                            String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
                            messages.add(time + "connected to" + targetName);
                            messagesAdapter.notifyDataSetChanged();
                            Toast.makeText(ChatActivity.this, "Connected to " + targetName, Toast.LENGTH_SHORT).show();
                        });
                    }
                    @Override
                    public void onConnectionFailed(String error) {
                        handler.post(() -> {
                            Toast.makeText(ChatActivity.this,error,Toast.LENGTH_LONG).show();
                            String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
                            messages.add(time+error);
                            messagesAdapter.notifyDataSetChanged();
                            btnSend.setEnabled(false);
                            etMessage.setHint("connection failed");
                        });
                    }
                    @Override
                    public void onConnectionLost(String error) {
                        handler.post(() -> {
                            Toast.makeText(ChatActivity.this,error, Toast.LENGTH_LONG).show();
                            messages.add(" connection lost");
                            messagesAdapter.notifyDataSetChanged();
                            isConnected = false;
                            btnSend.setEnabled(false);
                          //  etMessage.setHint("Connection lost");
                        });
                    }
                });
                client.start();
            } catch (Exception e) {
                //Log.e(TAG, "Failed to create client", e);
                handler.post(() -> {
                    Toast.makeText(ChatActivity.this, "Failed to create client: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    private void sendMessage() {
        String messageText = etMessage.getText().toString().trim();
        if (messageText.isEmpty()) {
            Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (client == null || !isConnected) {
            Toast.makeText(this, "Not connected to user. Please wait...", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        String formattedMessage = time + " You: " + messageText;
        messages.add(formattedMessage);
        messagesAdapter.notifyDataSetChanged();
        etMessage.setText("");
        lvMessages.setSelection(messages.size() - 1);
        
        networkExecutor.execute(() -> {
            try {
                client.sendMessage(messageText);
                // Log.d(TAG, "Message sent: " + messageText);
            } catch (IOException e) {
                // Log.e(TAG, "Failed to send message", e);
                handler.post(() -> {
                    Toast.makeText(ChatActivity.this, "Failed to send message: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    // Remove the message from the list if sending failed
                    if (messages.contains(formattedMessage)) {
                        messages.remove(formattedMessage);
                        messagesAdapter.notifyDataSetChanged();
                    }
                });
            }
        });
    }
    public void receiveMessage(String message, String senderIp) {
        //Log.d(TAG, "njeh tebaath" + senderIp + ": " + message);
        if (targetIp.equals(senderIp)) {
            handler.post(() -> {
                String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
                String formattedMessage = time + " " + targetName + ": " + message;
                messages.add(formattedMessage);
                messagesAdapter.notifyDataSetChanged();
                lvMessages.setSelection(messages.size() - 1);
            });
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        MainActivity.clearActiveChatActivity();
        if (client != null) {
            client.stopClient();
        }
        if (networkExecutor != null) {
            networkExecutor.shutdown();
        }
    }
}