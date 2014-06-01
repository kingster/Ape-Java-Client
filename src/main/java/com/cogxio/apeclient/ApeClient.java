package com.cogxio.apeclient;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_75;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;

/**
 * Created by kingster on 31/05/14.
 */
public abstract class ApeClient extends WebSocketClient{

    private Integer chl = 1;
    private String sessionId = null;
    private String pipeId = null ;
    private JSONObject  user  = null;
    private String userName = null;
    private Long lastCommandTS = new Date().getTime();
    private CountDownLatch sessionCreated = new CountDownLatch( 1 );
    private static final Logger Log  =  LoggerFactory.getLogger(ApeClient.class);
    private final ScheduledExecutorService executor =  Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> scheduledKeepAlive = null;
    public String publishKey = null;

    public ApeClient( URI uri ) {
        super(uri, new Draft_75());
    }

    @Override
    public void onOpen(ServerHandshake serverHandshake) {
        triggerKeepAlive();
    }

    public void triggerKeepAlive(){
        final ApeClient self = this;
        if(scheduledKeepAlive == null || scheduledKeepAlive.isDone())
        {
            scheduledKeepAlive = executor.schedule(new Runnable() {
                public void run() {
                    self.check();
                    triggerKeepAlive();
                }
            }, 30, TimeUnit.SECONDS);
        }
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
        if(scheduledKeepAlive != null && !scheduledKeepAlive.isDone())
            scheduledKeepAlive.cancel(false);
        send(JSONSerializer.toJSON(Arrays.asList(params)).toString());
        triggerKeepAlive();
    }


    @Override
    public void onMessage( ByteBuffer blob ) {
        //nothing to do with bytes for as of now.
        Log.debug("onMessage ByteBuffer NotImplemented");
    }

    @Override
    public void onClose( int code, String reason, boolean remote) {
        Log.info("Closed: " + code + " - " + reason);

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
        this.userName = userName;
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
     * Keep connection alive. ( connection would die after 45 secs of inactivity)
     */
    private void check(){
        Log.debug("Running Check");
        Map<String,Object> options = new HashMap<String, Object>();
        options.put("chl", ++chl);
        options.put("cmd","CHECK");
        options.put("sessid", sessionId);
        options.put("freq", "1");
        send(options);
        scheduledKeepAlive = null;
    }

    /**
     * Process APE Response
     * @param response
     */
    protected void processResponse (JSONArray response){

        JSONObject headObject =  response.getJSONObject(0);
        String raw = headObject.getString("raw");
        lastCommandTS = Long.parseLong(headObject.getString("time"));
        if (raw.equalsIgnoreCase("LOGIN")){
            JSONObject indentObject =  response.getJSONObject(1);
            sessionId =  headObject.getJSONObject("data").getString("sessid");
            user =  indentObject.getJSONObject("data").getJSONObject("user");
            pipeId =  user.getString("pubid");
            sessionCreated.countDown();
        }
        else if(raw.equalsIgnoreCase("ERR")){
            Log.error("ERROR : " + headObject.getString("data"));
            handleAPSError(headObject.getJSONObject("data"));
        }
        else {
            try {
                String methodName = "action_" + raw.toLowerCase().replaceAll("-","_");
                Method method = ApeClient.class.getDeclaredMethod(methodName, JSONObject.class);
                method.setAccessible(true);
                method.invoke(this,headObject.getJSONObject("data"));
            } catch (NoSuchMethodException e) {
                Log.info("Ignoring,Not Implemented Method : " + e.getMessage());
            }   catch (Exception e) {
                Log.error("Exception" ,e);
            }
        }
    }

    /**
     * APS Error Code Handling.
     * @param errorData
     */
    protected void handleAPSError(JSONObject errorData){
        switch (errorData.getInt("code")){
            case 4 :
                //BAD_SESSID, restart app
                this.sessionId = null;
                this.sessionCreated = new CountDownLatch( 1 );
                this.scheduledKeepAlive.cancel(true);
                this.startSession(this.userName);
                break;
            case 7 :
                throw new RuntimeException(errorData.getString("value"));
        }
    }


    /**
     * Inline Push Triggers this event
     * @param jsonObject
     */
    protected void action_event_x(JSONObject jsonObject){
        action_event(jsonObject);
    }

    /**
     * Normal Message Event
     * @param jsonObject
     */
    protected void action_event(JSONObject jsonObject){
        pipeId =  ((JSONObject)jsonObject.get("pipe")).getString("pubid");
        action_onMessage(jsonObject);
    }

    /**
     * Reply to last incomming message
     * @param message
     */
    public void reply(String message){
        Map<String,Object> options = new HashMap<String, Object>();
        options.put("chl", ++chl);
        options.put("cmd","Event");
        options.put("sessid", sessionId);
        options.put("freq", "1");
        Map<String,Object> params = new HashMap<String, Object>();
        params.put("event", "message");
        params.put("sync", false);
        params.put("data", message);
        params.put("pipe", pipeId);
        options.put("params", params );
        send(options);
    }

    public void sendMessage( String channel, Object message, String eventType){
        Map<String,Object> options = new HashMap<String, Object>();
        options.put("password",publishKey);
        options.put("channel", channel);
        options.put("raw",eventType);
        options.put("data", message);

        Map<String,Object> command = new HashMap<String, Object>();
        command.put("cmd","inlinepush");
        command.put("params", options);
        send(command);
    }

    abstract public void action_onMessage(JSONObject jsonObject);
}
