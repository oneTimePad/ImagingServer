package com.ruautonomous.dronecamera.qxservices;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import com.o3dr.services.android.lib.drone.connection.ConnectionType;
import com.ruautonomous.dronecamera.DroneActivity;
import com.ruautonomous.dronecamera.DroneSingleton;
import com.ruautonomous.dronecamera.ImageQueue;
import com.ruautonomous.dronecamera.utils.ImageData;
import com.ruautonomous.dronecamera.LogStorage;

import java.io.IOException;


/**
 * client for drone app to communicate with qx service
 */
public class QXCommunicationClient {

    //IPC Messenger
    private Messenger serviceBinder;

    //service connection
    private ServiceConnection mServerConn;
    //client to get responses from service
    private QxCommunicationResponseClient responseClient;
    private LogStorage logStorage;
    private DroneActivity context;
    private boolean beepStatus = true;

    public final String TAG = "QXClient";


    public QXCommunicationClient(DroneActivity context){
        this.context = context;



        final DroneActivity finalContext = context;
        this.mServerConn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                Log.d(TAG,"connected!");
                //give server our messenger
                serviceBinder  =new Messenger(iBinder);
                register(responseClient.getMessenger());
                //we are connected so call search qx device
                //finalContext.searchQx();

            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                Log.d(TAG,"disconnected");

            }
        };

        //bind to service
        context.bindService(new Intent(context, QXCommunicationService.class), mServerConn,
                Context.BIND_AUTO_CREATE);

    }

    public void set(){
        ImageQueue imageQueue = DroneSingleton.imageQueue;
        this.responseClient = new QxCommunicationResponseClient(context,imageQueue);
        //initialize handler for righting to log files
        try {
            this.logStorage = new LogStorage(imageQueue);

        }
        catch (IOException e){
            Log.e(TAG,e.toString());
        }
    }

    /**
     * send a message to qx service
     * @param message message number
     * @param replyTo a message object for service to use to reply
     * @param data data to send in bundle
     */
    private void send(int message,Messenger replyTo,Bundle data){
        if(mServerConn!=null){
            Message msg = Message.obtain(null,message,0,0);
            if(replyTo!=null)
                msg.replyTo = replyTo;
            if(data!=null)
                msg.setData(data);

            try {
                serviceBinder.send(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

    }

    /**
     * register with the service to get responses
     * @param messenger client Messenger
     */
    private void register(Messenger messenger){
            send(QXCommunicationService.REGISTER,messenger,null);
    }

    /**
     * request service to fetch a fullsize version of an image
     * @param request contains session token of requesiting user and image url
     */
    public void getFullSizedImage(String request){
        Bundle data = new Bundle();
        String[] splitter = request.split("~");
        data.putString("session",splitter[0]);
        data.putString("url",splitter[1]);
        send(QXCommunicationService.FULLSIZE,null,data);

    }



    /**
     * tell service to search for Qx devices
     */
    public void search(String format){
        Bundle data = new Bundle();
        data.putString("format",format);
       send(QXCommunicationService.SEARCHQX,null,data);

    }




    /**
     * tell service to send status of Qx connection
     */
    private void status(){
        send(QXCommunicationService.STATUSQX,null,null);
    }

    /**
     * wraps around status to tell caller to wait for response (makes synchronous)
     * @return status
     */
    public boolean qxStatus() {

        synchronized (responseClient) {
            status();
            try {
                responseClient.wait();
            } catch (InterruptedException e) {
                Log.e(TAG, e.toString());
            }

            return responseClient.getQxStatus();
        }
    }

    /**
     * set whether to make trigger noise
     * @param mode boolean
     */
    public void setBeepMode(boolean mode){
        Bundle data = new Bundle();
        if(mode){
            data.putString("status","On");
        }
        else{
            data.putString("status","Off");
        }
        beepStatus = mode;
        send(QXCommunicationService.BEEPMODE,null,data);

    }

    /**
     * return beep status
     */
    public boolean getBeepMode(){return beepStatus;}

    /**
     * zoom
     * @param direction in or out
     */
    public void actZoom(String direction){
        Bundle data= new Bundle();
        data.putString("direction",direction);
        send(QXCommunicationService.ZOOM,null,data);
    }

    /**
     * tell service to trigger qx device (one picture)
     */
    public void trigger(){
        //capture time
        long time = System.currentTimeMillis()/1000;
        try {
            //image data and write to logs
            ImageData imageData = new ImageData(time);
            logStorage.writeLog(imageData);

        }
        catch (IOException e){
            Log.e(TAG, e.toString());
        }

        send(QXCommunicationService.TRIGGERQX,null,null);
    }

    /**
     * close connection to service
     */
    public void close(){
        context.unbindService(mServerConn);
        logStorage.close();
    }





}
