package com.example.messegerie;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class Client extends Thread {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private MessageListener messageListener;
    private String serverIp;
    private int port;
    private boolean running = false;

    public interface MessageListener {
        void onMessageReceived(String message);
        void onConnectionEstablished();
        void onConnectionFailed(String error);
        void onConnectionLost(String error);
    }

    public Client(String serverIp, int port, MessageListener listener) {
        this.serverIp = serverIp;
        this.port = port;
        this.messageListener = listener;
    }

    @Override
    public void run() {
        try {
            socket = new Socket(serverIp, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            running = true;

            if (messageListener != null) {
                messageListener.onConnectionEstablished();
            }
            String message;
            while ((message = in.readLine()) != null && running) {
                if (messageListener != null) {
                    messageListener.onMessageReceived(message);
                }
            }
        } catch (IOException e) {
            if (messageListener != null) {
                if (running) {
                    messageListener.onConnectionFailed(e.getMessage());
                } else {
                    messageListener.onConnectionLost(e.getMessage());
                }
            }
        } finally {
            stopClient();
        }
    }
    public void sendMessage(String message) throws IOException {
        if (out == null) {
            throw new IOException("Output stream is null - connection not established");
        }
        if (!running) {
            throw new IOException("Client is not running");
        }
        if (socket == null || socket.isClosed()) {
            throw new IOException("Socket is closed or null");
        }
        out.println(message);
        out.flush();
    }
    public void stopClient() {
        running = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            if (out != null) {
                out.close();
            }
            if (in != null) {
                in.close();
            }
        } catch (IOException e) {
            System.out.println("error");
        }
    }
    public boolean isConnected() {
        try {
            return running && socket != null && !socket.isClosed() && socket.isConnected();
        } catch (Exception e) {
            return false;
        }
    }
}