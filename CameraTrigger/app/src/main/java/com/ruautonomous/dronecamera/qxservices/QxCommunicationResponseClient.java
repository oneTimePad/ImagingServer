package com.ruautonomous.dronecamera.qxservices;


import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

import com.ruautonomous.dronecamera.DroneActivity;
import com.ruautonomous.dronecamera.ImageQueue;

import java.io.IOException;
import java.lang.ref.WeakReference;


public class QxCommunicationResponseClient {

    public static  final int IMAGE = 1;
    public static  final int QXSEARCHUPDATE = 2;
    public static final int QXSTATUS = 3;
    public final static String TAG = "ResponseClient";

    private boolean QXStatus = false;
    private ImageQueue imageQueue;
    private DroneActivity context;
    private Messenger mMessenger;



    QxCommunicationResponseClient(){
        this.imageQueue = DroneActivity.app.getImageQueue();
        this.context = DroneActivity.app.getContext();
        this.mMessenger  = new Messenger(new ResponseHandler(this));
    }


    public void setQxStatus(boolean status){
        this.QXStatus = status;

    }

    public boolean getQxStatus(){
        return this.QXStatus;
    }


    private static class ResponseHandler extends Handler {

        private WeakReference<QxCommunicationResponseClient> serviceWeakReference;

        ResponseHandler(QxCommunicationResponseClient imageQueue){
            serviceWeakReference =new WeakReference<>(imageQueue);
        }

        @Override
        public void handleMessage(Message msg){
            QxCommunicationResponseClient responseClient = serviceWeakReference.get();
            switch(msg.what){

                case IMAGE:
                    if(msg.getData()!=null) {
                        String pictureName = msg.getData().getString("pictureName");
                        try {
                           responseClient.imageQueue.pushImage(pictureName);
                        }
                        catch (IOException e){
                            Log.e(TAG,e.toString());
                        }
                    }
                    break;

                case QXSEARCHUPDATE:
                    responseClient.context.setSearchQxStatus(msg.getData().getBoolean("status"));
                    break;

                case QXSTATUS:
                    responseClient.setQxStatus(msg.getData().getBoolean("status"));
                    synchronized (responseClient) {
                        responseClient.notify();
                    }
                    break;

                default:
                    super.handleMessage(msg);

            }
        }
    }


    public Messenger getMessenger(){
        return mMessenger;
    }
}
