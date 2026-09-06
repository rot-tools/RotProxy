package com.rotproxy.config;

public class ProxyProfile {
    public String name = "New Profile";
    public String host = "";
    public int port = 1080;
    public String username = "";
    public String password = "";
    public ProxyType type = ProxyType.SOCKS5;

    public ProxyProfile() {
    }

    public ProxyProfile(String name) {
        this();
        this.name = name;
    }

    public ProxyProfile copy() {
        ProxyProfile profile = new ProxyProfile();
        profile.name = this.name;
        profile.host = this.host;
        profile.port = this.port;
        profile.username = this.username;
        profile.password = this.password;
        profile.type = this.type;
        return profile;
    }

    public boolean isValid() {
        return this.type != null && this.host != null && !this.host.trim().isEmpty()
                && this.host.chars().noneMatch(Character::isWhitespace)
                && !this.host.contains("://") && this.port > 0 && this.port < 65536;
    }

    public String toString() {
        return this.name;
    }

    public static enum ProxyType {
        SOCKS5,
        HTTP,
        HTTPS;

    }
}
