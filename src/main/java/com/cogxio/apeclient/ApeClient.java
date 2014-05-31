package com.cogxio.apeclient;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_76;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CountDownLatch;

/**
 * Created by kingster on 31/05/14.
 */
public class ApeClient extends WebSocketClient{

    private Integer chl = 1;
    private String sessionId = null;
    private String pipeId = null ;
    private JSONObject  user  = null;
    private Long lastCommandTS = new Date().getTime();
    private CountDownLatch sessionCreated = new CountDownLatch( 1 );
    private static final Logger Log  =  LoggerFactory.getLogger(ApeClient.class);

    public ApeClient( URI uri ) {
        super( uri, new Draft_76() );
    }

    @Override
    public void onOpen(ServerHandshake serverHandshake) {

    }



    @Override
    public void onMessage(String message) {
        Log.debug("Received :: "+ message );
        JSONArray json = (JSONArray) JSONSerializer.toJSON(message);
        processResponse(json);
    }

    /**
     * blocked wait till connection is confirmed.
     * @throws Exception
     */
    public void waitJoinedBlocking() throws Exception{
        sessionCreated.await();
    }

    /**
     * Send Notification
     * @param params
     */
    public void send(Map<String,Object> params) {
        send(JSONSerializer.toJSON(Arrays.asList(params)).toString());
    }


    @Override
    public void onMessage( ByteBuffer blob ) {
        //nothing to do with bytes for as of now.
        Log.debug("onMessage ByteBuffer NotImplemented");
    }

    @Override
    public void onClose( int code, String reason, boolean remote) {
        Log.info("Closed: " + code + " " + reason);

    }

    @Override
    public void onError(Exception e) {
        Log.error("Error Occurred", e);
    }

    /**
     * Start a ape session
     * @param userName
     */
    public void startSession(final String userName){
        Map<String,Object> options = new HashMap<String, Object>();
        options.put("chl", chl);
        options.put("cmd","CONNECT");
        Map<String,Object> params = new HashMap<String, Object>();
        params.put("user", new HashMap<String, String>(){{
            put("name",userName);
        }} );
        options.put("params", params );
        send(options);
    }


    /**
     * Join Channels
     * @param channelNames
     */
    public void join(List<String> channelNames){
        Map<String,Object> options = new HashMap<String, Object>();
        options.put("chl", ++chl);
        options.put("cmd","JOIN");
        options.put("sessid", sessionId);
        options.put("freq", "1");
        Map<String,Object> params = new HashMap<String, Object>();
        params.put("channels", channelNames);
        options.put("params", params );
        send(options);
    }

    /**
     * Keep connection alive.
     */
    private void check(){
        Map<String,Object> options = new HashMap<String, Object>();
        options.put("chl", ++chl);
        options.put("cmd","CHECK");
        options.put("sessid", sessionId);
        options.put("freq", "1");
        send(options);
    }

    /**
     * Process APE Response
     * @param response
     */
    protected void processResponse (JSONArray response){

        JSONObject headObject = (JSONObject) response.get(0);
        String raw = headObject.getString("raw");
        lastCommandTS = Long.parseLong(headObject.getString("time"));
        if (raw.equalsIgnoreCase("LOGIN")){
            JSONObject indentObject = (JSONObject) response.get(1);
            sessionId =  ((JSONObject)headObject.get("data")).getString("sessid");
            user =  (JSONObject) ((JSONObject)indentObject.get("data")).get("user");
            pipeId =  user.getString("pubid");
            sessionCreated.countDown();
        }  else if(raw.equalsIgnoreCase("ERR")){
            Log.error(raw);
        }  else if ( raw.equalsIgnoreCase("CHANNEL")){
            Log.info("Not Implemented Channel");
        }
        else {
            Log.info("Not Implemented #Method : " + raw);
        }


    }
}
