package com.ruautonomous.dronecamera.qxservices;


import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

import com.ruautonomous.dronecamera.DroneActivity;
import com.ruautonomous.dronecamera.ImageQueue;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashMap;

/**
 * wraps around handler for responses from Qx service
 */
public class QxCommunicationResponseClient {

    public static  final int IMAGE = 1;
    public static  final int QXSEARCHUPDATE = 2;
    public static final int QXSTATUS = 3;
    public static final int FULLIMAGE = 4;
    public final static String TAG = "ResponseClient";

    private ImageQueue imageQueue;
    private boolean QXStatus = false;
    private DroneActivity context;
    private Messenger mMessenger;



    QxCommunicationResponseClient(DroneActivity context,ImageQueue imageQueue){
        this.context = context;
        this.imageQueue = imageQueue;
        this.mMessenger  = new Messenger(new ResponseHandler(this));
    }

    /**
     * set the status of Qx connection when we get it from service
     * @param status boolean
     */
    public void setQxStatus(boolean status){
        this.QXStatus = status;

    }

    /**
     * get current qx connection status
     * @return boolean status
     */
    public boolean getQxStatus(){
        return this.QXStatus;
    }


    /**
     * Client Messenger handler
     */
    private static class ResponseHandler extends Handler {

        private WeakReference<QxCommunicationResponseClient> serviceWeakReference;

        ResponseHandler(QxCommunicationResponseClient imageQueue){
            serviceWeakReference =new WeakReference<>(imageQueue);
        }

        @Override
        public void handleMessage(Message msg){
            QxCommunicationResponseClient responseClient = serviceWeakReference.get();
            switch(msg.what){
                //service send an image to get from fs
                case IMAGE:
                    if(msg.getData()!=null) {
                        String pictureName = msg.getData().getString("pictureName");
                        String url = msg.getData().getString("url");

                        try {
                            synchronized (responseClient.imageQueue) {
                                responseClient.imageQueue.pushImage(pictureName, url);
                            }
                        }
                        catch (IOException e){
                            Log.e(TAG,e.toString());
                        }
                    }
                    break;
                //service sent full sized image
                case FULLIMAGE:
                    if(msg.getData()!=null){
                        String picture = msg.getData().getString("pictureName");
                        String session = msg.getData().getString("session");
                        HashMap<String,String> hash = new HashMap<>();
                        hash.put("pictureName",picture);
                        hash.put("session",session);

                        try{
                            synchronized (responseClient.imageQueue){
                                responseClient.imageQueue.pushFullImage(hash);
                            }

                        }
                        catch (IOException e){
                            Log.e(TAG,e.toString());
                        }
                    }
                    break;
                //service is telling us a qx device search update
                case QXSEARCHUPDATE:
                    responseClient.context.setSearchQxStatus(msg.getData().getBoolean("status"));
                    break;

                //qx is telling us a qx connection status update
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


    /**
     * get client Messenger object
     * @return Messenger object
     */
    public Messenger getMessenger(){
        return mMessenger;
    }
}
