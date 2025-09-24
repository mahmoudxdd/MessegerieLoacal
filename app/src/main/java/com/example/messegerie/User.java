package com.example.messegerie;

public class User {
    private String ipAddress;
    private String deviceName;

    public User(String ipAddress, String deviceName) {
        this.ipAddress = ipAddress;
        this.deviceName = deviceName;
    }
    public String getIpAddress() {
        return ipAddress;
    }
    public String getDeviceName() {
        return deviceName;
    }
    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }
    @Override
    public String toString() {
        return deviceName;
    }
}