package com.example.messegerie;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private TextView tvStatus, tvMyDevice, tvNoUsers;
    private ListView lvUsers;
    private ProgressBar progressBar;
    private Button btnRefresh;
    private List<String> availableUsers = new ArrayList<>();
    private ArrayAdapter<String> usersAdapter;
    private Server server;
    private String localIp;
    private Handler handler = new Handler(Looper.getMainLooper());
    private ScheduledExecutorService scheduler;
    private DatagramSocket broadcastSocket;
    private boolean isBroadcasting = false;
    private static ChatActivity activeChatActivity = null;
    private static final String TAG ="main";
    private static final int SERVER_PORT = 8080;
    private static final int BROADCAST_PORT = 8081;
    private static final String DISCOVERY_MESSAGE = "MESSENGERIE_DISCOVERY";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initializeViews();
        setupListView();
        startServer();
    }

    private void initializeViews() {
        tvStatus = findViewById(R.id.tvStatus);
        tvMyDevice = findViewById(R.id.tvMyDevice);
        tvNoUsers = findViewById(R.id.tvNoUsers);
        lvUsers = findViewById(R.id.lvUsers);
        progressBar = findViewById(R.id.progressBar);
        btnRefresh = findViewById(R.id.btnRefresh);
        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> {
                //Log.d(TAG, "Manual refresh triggered");
                Toast.makeText(this, "refreshing...", Toast.LENGTH_SHORT).show();
                availableUsers.clear();
                usersAdapter.notifyDataSetChanged();
                updateUI();
                sendUDPBroadcast();
            });
        }
    }
    private void setupListView() {
        usersAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, availableUsers);
        lvUsers.setAdapter(usersAdapter);
        lvUsers.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String selectedUser = availableUsers.get(position);
                openChatActivity(selectedUser);
            }
        });
    }
    private void openChatActivity(String userInfo) {
        try {
            String ipAddress = userInfo.substring(userInfo.indexOf("(") + 1, userInfo.indexOf(")"));
            String deviceName = userInfo.substring(0, userInfo.indexOf("(")).trim();
            Intent intent = new Intent(MainActivity.this, ChatActivity.class);
            intent.putExtra("targetIp", ipAddress);
            intent.putExtra("targetName", deviceName);
            intent.putExtra("myIp", localIp);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Error opening chat: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    private String getLocalIpAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                if (!intf.isUp() || intf.isLoopback()) {
                    continue;
                }
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress() && addr.getAddress().length == 4) {
                        String ip = addr.getHostAddress();
                        if (ip.split("\\.").length == 4) {
                            return ip;
                        }
                    }
                }
            }
        } catch (Exception ex) {
            Log.e(TAG, "error", ex);
        }
        return "192.168.1.100"; //fallback
    }
    private void startServer() {
        new Thread(() -> {
            try {
                server = new Server(SERVER_PORT, new Server.MessageListener() {
                    @Override
                    public void onMessageReceived(String message, String senderIp) {
                        routeMessageToChat(message, senderIp);
                    }
                    @Override
                    public void onUserConnected(String userIp) {
                        handler.post(() -> {
                            String userInfo = getDeviceNameFromIp(userIp) + " (" + userIp + ")";
                            if (!availableUsers.contains(userInfo)) {
                                availableUsers.add(userInfo);
                                usersAdapter.notifyDataSetChanged();
                                updateUI();
                            }
                        });
                    }
                    @Override
                    public void onUserDisconnected(String userIp) {
                        Log.d(TAG, "user disconnected" + userIp);
                        handler.post(() -> {
                            String userInfo = getDeviceNameFromIp(userIp) + " (" + userIp + ")";
                            availableUsers.remove(userInfo);
                            usersAdapter.notifyDataSetChanged();
                            updateUI();
                        });
                    }
                });
                server.start();

                localIp = getLocalIpAddress();
                handler.post(() -> {
                    tvStatus.setText("Your IP" + localIp + "port " + SERVER_PORT);
                    tvMyDevice.setText("Device" + android.os.Build.MODEL);
                });
                startUDPBroadcastDiscovery();
            } catch (IOException e) {
                handler.post(() -> {
                    Toast.makeText(MainActivity.this, "error" + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    private String getDeviceNameFromIp(String ip) {
        try {
            String lastOctet = ip.substring(ip.lastIndexOf(".") + 1);
            return "Device"+lastOctet;
        } catch (Exception e) {
            return "Mouch maarouf";
        }
    }

    private void startUDPBroadcastDiscovery() {
        try {
            startUDPListener();
            scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.scheduleWithFixedDelay(() -> {
                if (localIp != null && !isBroadcasting) {
                    sendUDPBroadcast();
                }
            }, 0, 5, TimeUnit.SECONDS);
        } catch (IOException e) {
            handler.post(() -> {
                Toast.makeText(this, "error" + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        }
    }
    
    private void startUDPListener() throws IOException {
        broadcastSocket = new DatagramSocket(BROADCAST_PORT);
        broadcastSocket.setBroadcast(true);
        new Thread(() -> {
            byte[] buffer = new byte[1024];
            while (!broadcastSocket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    broadcastSocket.receive(packet);
                    String message = new String(packet.getData(), 0, packet.getLength());
                    String senderIp = packet.getAddress().getHostAddress();
                    if (DISCOVERY_MESSAGE.equals(message.trim()) && !localIp.equals(senderIp)) {
                        handler.post(() -> {
                            String deviceName = getDeviceNameFromIp(senderIp);
                            String userInfo = deviceName + "("+senderIp+")";
                            if (!availableUsers.contains(userInfo)) {
                                availableUsers.add(userInfo);
                                usersAdapter.notifyDataSetChanged();
                                updateUI();
                            }
                        });
                        sendUDPResponse(senderIp);
                    }
                } catch (IOException e) {
                    if (!broadcastSocket.isClosed()) {
                        Log.e(TAG, "error" + e.getMessage());
                        // Don't throw RuntimeException, just continue listening
                    }
                }
            }
        }).start();
    }
    private void sendUDPBroadcast() {
        new Thread(() -> {
            try {
                isBroadcasting = true;
                DatagramSocket socket = new DatagramSocket();
                socket.setBroadcast(true);
                String networkPrefix = localIp.substring(0, localIp.lastIndexOf(".") + 1);
                String broadcastIp = networkPrefix + "255";
                byte[] message = DISCOVERY_MESSAGE.getBytes();
                DatagramPacket packet = new DatagramPacket(
                    message, message.length, 
                    InetAddress.getByName(broadcastIp), BROADCAST_PORT
                );
                socket.send(packet);
                socket.close();
            } catch (IOException e) {
            } finally {
                isBroadcasting = false;
            }
        }).start();
    }
    
    private void sendUDPResponse(String targetIp) {
        new Thread(() -> {
            try {
                DatagramSocket socket = new DatagramSocket();
                byte[] message = DISCOVERY_MESSAGE.getBytes();
                DatagramPacket packet = new DatagramPacket(
                    message, message.length, 
                    InetAddress.getByName(targetIp), BROADCAST_PORT
                );
                socket.send(packet);
                socket.close();
            } catch (IOException e) {
                System.out.println("error");
            }
        }).start();
    }

    public static void setActiveChatActivity(ChatActivity chatActivity) {
        activeChatActivity=chatActivity;
    }
    public static void clearActiveChatActivity() {
        activeChatActivity = null;
    }
    
    private void routeMessageToChat(String message, String senderIp) {
        if (activeChatActivity != null) {
            handler.post(() -> {
                activeChatActivity.receiveMessage(message, senderIp);
            });
        } else {
            Log.d(TAG, "thamech active chat");
        }
    }
    private void updateUI() {
        runOnUiThread(() -> {
            if (availableUsers.isEmpty()) {
                progressBar.setVisibility(View.VISIBLE);
                tvNoUsers.setVisibility(View.VISIBLE);
                lvUsers.setVisibility(View.GONE);
            } else {
                progressBar.setVisibility(View.GONE);
                tvNoUsers.setVisibility(View.GONE);
               // lvMessages.setVisibility(View.VISIBLE);
                tvStatus.setText("Your IP: " + localIp + " - Users found: " + availableUsers.size());
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (scheduler != null) {
            scheduler.shutdown();
        }
        if (server != null) {
            server.stopServer();
        }
        if (broadcastSocket != null && !broadcastSocket.isClosed()) {
            broadcastSocket.close();
        }
    }
}