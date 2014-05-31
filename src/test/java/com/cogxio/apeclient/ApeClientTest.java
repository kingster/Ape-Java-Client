package com.cogxio.apeclient;

import net.sf.json.JSONObject;
import org.java_websocket.WebSocketImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Arrays;

/**
 * Created by kingster on 31/05/14.
 */
public class ApeClientTest {

    public static void main( String[] args ) throws  Exception{
        WebSocketImpl.DEBUG = false;
        final Logger Log  =  LoggerFactory.getLogger(ApeClientTest.class);

        String wsHost = "ws://channel.host.com:6969/6/";
        URI uri = new URI(wsHost);

        ApeClient apeClient = new ApeClient(uri) {
            @Override
            public void action_onMessage(JSONObject jsonObject) {
                Log.info(jsonObject.toString());
                reply("Hey Boss!");
            }
        };

        apeClient.connectBlocking();

        apeClient.startSession("username");
        apeClient.waitJoinedBlocking();
        apeClient.join(Arrays.asList("channelId"));

    }

}
