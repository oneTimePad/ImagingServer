package com.ruautonomous.dronecamera;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.ruautonomous.dronecamera.qxservices.QXCommunicationClient;
import com.ruautonomous.dronecamera.qxservices.QXHandler;
import com.ruautonomous.dronecamera.qxservices.QxCommunicationResponseClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.ConnectException;
import java.util.HashMap;

/**
 * Thread handler for contacting drone API
 */
public class GroundStationHThread extends HandlerThread {

    private Handler mHandler = null;

    //shared resources
    private final DroneRemoteApi droneRemoteApi = new DroneRemoteApi();
    private ImageQueue imageQueue;
    private QXCommunicationClient qxCommunicationClient;
    private  CameraTriggerHThread cameraTriggerHThread;
    private  DroneActivity context;
    private String id;

    //status variables
    //continue loop
    private boolean connect = false;
    //whether api connection succeeded
    private boolean connected = false;

    //time between requests
    private double timeout = 0.0;

    public String TAG = "GroundStationHThread";



   public GroundStationHThread(String id,DroneActivity context){

        super("GroundStationHThread");
        start();
        mHandler = new Handler(getLooper());

        this.context = context;
        this.id = id;

    }

    public void set(){
        this.imageQueue = DroneSingleton.imageQueue;

        this.qxCommunicationClient = DroneSingleton.qxCommunicationClient;
        this.cameraTriggerHThread = DroneSingleton.cameraTriggerHThread;
    }
    /**
     * set server IP:PORT
     */
    public void setServer(String server){
        droneRemoteApi.setServer(server);

    }

    /**
     * status for drone api connection
     * @return: boolean status
     */
    boolean status(){
        return connect;
    }

    /**
     * stop thread nicely
     */
    void disconnect(){
        connect = false;
    }

    /**
     * set time between requests
     * @param timeout: double time
     */
    void setTimeout(double timeout) {this.timeout = timeout;}

    /**
     * start connection loop to drone api
     * @param username
     * @param password

     */
    void connect(final String username, final String password) {
        //if already connected
        if(connect) return;
        //if bad timeout
        if(timeout<=0.0)return;

        connect = true;

        Runnable task = new Runnable() {
            @Override
            public void run() {
                try{
                    //login to api
                    droneRemoteApi.getAccess(username,password);
                    connected = true;
                }
                catch (IOException e){
                    connected = false;
                    connect = false;

                }
                //notify main thread that we succeeded
                context.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        context.setGCSConnectionStatus(connected);
                    }
                });

                //main connection loop
                while (connect){
                    HashMap<String,Object> image =null;
                    //attempt to get an imaging waiting to be posted to drone api
                    try{

                        synchronized (imageQueue) {
                            image = imageQueue.pop();
                        }

                    }
                    catch (IndexOutOfBoundsException e){
                        //nothing, there is no image waiting
                    }
                    JSONObject response = null;
                    boolean connectionStatus = false;
                    //get status of qx connection
                    synchronized (qxCommunicationClient) {
                         connectionStatus = qxCommunicationClient.qxStatus();
                    }

                    try {
                        //use droneapi handler to contact api, post
                        synchronized (cameraTriggerHThread) {
                            response = droneRemoteApi.postServerContact(id, connectionStatus, cameraTriggerHThread.status(), cameraTriggerHThread.triggerTime, image);
                        }
                    }
                    catch (IOException e){
                        Log.e(TAG,"failed to post");
                    }

                    //if there is a response and it is not malformed
                    if (response != null && response.has("trigger")){


                        try {
                            //is it telling device to trigger?
                            if (Integer.parseInt(response.getString("trigger")) == 1 && response.has("time")) {
                                //set trigger time and start capture
                                cameraTriggerHThread.setTriggerTime(Double.parseDouble(response.get("time").toString()));
                                try {
                                    cameraTriggerHThread.startCapture(true);
                                }
                                catch (IOException e){
                                    Log.e(TAG, "failed to start remote capture");
                                }
                            }
                            //else stop trigger
                            else if (Integer.parseInt(response.getString("trigger")) == 0)
                               cameraTriggerHThread.stopCapture();

                            if(response.has("fullSize") && !response.getString("fullSize").equals("")){
                                qxCommunicationClient.getFullSizedImage(response.getString("fullSize"));
                            }

                        }
                        catch (JSONException e){
                            Log.e(TAG,e.toString());
                        }
                    }
                    //sleep for timeout
                    try{
                        Thread.sleep((long)timeout);
                    }
                    catch (InterruptedException e){
                        Log.e(TAG, e.toString());
                    }


                }

            }
        };
        mHandler.post(task);

    }

}
