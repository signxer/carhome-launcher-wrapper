package com.example.carhomewrapper;

/** Connection settings for an already-enabled Android Debug Bridge endpoint. */
public final class AdbConnectionConfig {
    public final String host;
    public final int port;

    public AdbConnectionConfig(String host, int port) {
        this.host = host;
        this.port = port;
    }
}
