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
import com.ruautonomous.dronecamera.utils.ImageData;
import com.ruautonomous.dronecamera.LogStorage;

import java.io.IOException;


public class QXCommunicationClient {


    private Messenger serviceBinder;
    private DroneActivity context;
    private ServiceConnection mServerConn;
    private QxCommunicationResponseClient responseClient;
    private LogStorage logStorage;

    public final String TAG = "QXClient";

    public QXCommunicationClient(){
        this.responseClient = new QxCommunicationResponseClient();
        this.context = DroneActivity.app.getContext();
        this.logStorage = DroneActivity.app.getLogStorage();
        this.mServerConn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                Log.d(TAG,"connected!");
                serviceBinder  =new Messenger(iBinder);
                register(responseClient.getMessenger());
                context.searchQx();

            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                Log.d(TAG,"disconnected");

            }
        };

        context.bindService(new Intent(DroneActivity.app.getContext(), QXCommunicationService.class), mServerConn,
                Context.BIND_AUTO_CREATE);

    }

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


    public QxCommunicationResponseClient getResponseClient(){
        return responseClient;
    }

    private void register(Messenger messenger){
            send(QXCommunicationService.REGISTER,messenger,null);
    }

    public void search(){
       send(QXCommunicationService.SEARCHQX,null,null);

    }

    public void close(){
        context.unbindService(mServerConn);
    }

    private void status(){
        send(QXCommunicationService.STATUSQX,null,null);
    }

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


    public void trigger(){
        long time = System.currentTimeMillis()/1000;
        try {
            ImageData imageData = new ImageData(time);
            DroneActivity.app.getLogStorage().writeLog(imageData);

        }
        catch (IOException e){
            Log.e(TAG, e.toString());
        }


      send(QXCommunicationService.TRIGGERQX,null,null);
    }


}
