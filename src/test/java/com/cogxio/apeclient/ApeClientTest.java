package com.cogxio.apeclient;

import net.sf.json.JSONObject;
import org.java_websocket.WebSocketImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Arrays;
import java.util.Random;

/**
 * Created by kingster on 31/05/14.
 */
public class ApeClientTest {


    @Before
    public void setUp() throws Exception {
    }


    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void testClient() throws Exception {

        WebSocketImpl.DEBUG = false;
        final Logger Log  =  LoggerFactory.getLogger(ApeClientTest.class);

        String wsHost = "ws://ape.ptejada.com:80/6/";
        URI uri = new URI(wsHost);
        String username = ("user_"+ new Random().nextInt(100)).replace("-","");

        ApeClient apeClient = new ApeClient(uri) {
            @Override
            public void action_onMessage(JSONObject jsonObject) {
                Log.info(jsonObject.toString());
                reply("Hey Boss!");
            }
        };
        apeClient.publishKey = "password";


        apeClient.connectBlocking();
        Log.info("Connected Successfully");

        apeClient.startSession(username);
        apeClient.waitJoinedBlocking();
        Log.info("Joined as "+ username);

        apeClient.join(Arrays.asList("music"));
        //apeClient.sendMessage("music", "awesome", "message");
        Thread.sleep(2000);
        apeClient.sendMessage("music", "awesome", "message");

        Thread.sleep(10000); //keep alive 10s.
    }

}
