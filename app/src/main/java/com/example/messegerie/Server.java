package com.example.messegerie;

import static androidx.fragment.app.FragmentManager.TAG;

import android.annotation.SuppressLint;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
public class Server extends Thread {
    private ServerSocket serverSocket;
    private boolean running = false;
    private MessageListener messageListener;
    private int port;
    private ConcurrentHashMap<String, ClientHandler> connectedClients = new ConcurrentHashMap<>();
    public interface MessageListener {
        void onMessageReceived(String message, String senderIp);
        void onUserConnected(String userIp);
        void onUserDisconnected(String userIp);
    }
    public Server(int port, MessageListener listener) throws IOException {
        this.port = port;
        this.messageListener = listener;
        serverSocket = new ServerSocket(port);
        serverSocket.setReuseAddress(true);
    }
    @SuppressLint("RestrictedApi")
    @Override
    public void run() {
        running = true;
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                String clientIp = clientSocket.getInetAddress().getHostAddress();

                Log.d(TAG, "Client connected: " + clientIp);

                ClientHandler clientHandler = new ClientHandler(clientSocket, clientIp);
                connectedClients.put(clientIp, clientHandler);
                new Thread(clientHandler).start();

                if (messageListener != null) {
                    messageListener.onUserConnected(clientIp);
                }

            } catch (IOException e) {
                if (running) {
                    Log.e(TAG, "Server error: " + e.getMessage());
                }
            }
        }
    }
    private class ClientHandler implements Runnable {
        private Socket clientSocket;
        private String clientIp;
        private BufferedReader in;
        private PrintWriter out;
        private boolean clientRunning = true;
        public ClientHandler(Socket socket, String ip) {
            this.clientSocket = socket;
            this.clientIp = ip;
            try {
                this.in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                this.out = new PrintWriter(clientSocket.getOutputStream(), true);
            } catch (IOException e) {
                clientRunning = false;
            }
        }
        @Override
        public void run() {
            try {
                String inputLine;
                while (clientRunning && (inputLine = in.readLine()) != null) {
                    if (messageListener != null) {
                        messageListener.onMessageReceived(inputLine, clientIp);
                    }
                    //Log.d(TAG,inputLine);
                }
            } catch (IOException e) {
                if (clientRunning) {
                   // Log.e(TAG,e.getMessage());
                }
            } finally {
                cleanup();
            }
        }
        public void sendMessage(String message) {
            if (out != null && clientRunning) {
                out.println(message);
                out.flush();
              //  Log.d(TAG,message);
            }
        }

        private void cleanup() {
            clientRunning = false;
            try {
                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                }
            } catch (IOException e) {
               // Log.e(TAG, e.getMessage());
            }
            connectedClients.remove(clientIp);
            if (messageListener != null) {
                messageListener.onUserDisconnected(clientIp);
            }
           // Log.d(TAG,clientIp);
        }
    }
    public void sendMessageToClient(String clientIp, String message) {
        ClientHandler client = connectedClients.get(clientIp);
        if (client != null) {
            client.sendMessage(message);
        }
    }

    public void stopServer() {
        running = false;
        try {
            for (ClientHandler handler : connectedClients.values()) {
                try {
                    handler.clientSocket.close();
                } catch (IOException e) {
                    //Log.e(TAG, e.getMessage());
                }
            }
            connectedClients.clear();

            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
           // Log.e(TAG, e.getMessage());
        }
    }
}