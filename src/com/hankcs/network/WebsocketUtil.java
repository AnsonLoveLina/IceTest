package com.hankcs.network;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;

/**
 * Created by zy-xx on 18/3/16.
 */
public class WebsocketUtil {

    public WebSocketClient client;

    public WebsocketUtil(WebSocketClient webSocketClient) {
        client = webSocketClient;
        client.connect();
    }

}
