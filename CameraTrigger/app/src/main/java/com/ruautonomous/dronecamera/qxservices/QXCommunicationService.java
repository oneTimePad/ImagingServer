package com.ruautonomous.dronecamera.qxservices;

import android.app.Service;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.ConnectException;


/**
 * service to communicate with Qx, runs in a different Process than the application
 */
public class QXCommunicationService extends Service {


    public static final String  TAG = "qxservice";
    public static final int SEARCHQX =1;
    public static final int TRIGGERQX=2;
    public static final int STATUSQX=3;
    public static final int REGISTER = 4;
    public static final int FULLSIZE = 5;
    public static final int BEEPMODE = 6;
    public static final int ZOOM =7;
    public static final int CAPTUREMODES =8;

    public static  QXHandler qx;
    private Messenger client;
    private PictureStorageServer pictureStorageServer;






    @Override
    public void  onCreate(){
        super.onCreate();


    }
    @Override
    public void onDestroy(){
        if(qx!=null)
            qx.disconnect();
        if(pictureStorageServer!=null)
            pictureStorageServer.close();
    }

    //Messenger so service can receive IPC
    final Messenger mMessenger = new Messenger(new IncomingHandler(this));


    /**
     * set process NIC to Wi-FI
     */
    public void setInterfaceWIFI(){

        //connectivity Manager to look for Wi-Fi
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        Network etherNetwork = null;

        for (Network network : connectivityManager.getAllNetworks()) {
            NetworkInfo networkInfo = connectivityManager.getNetworkInfo(network);
            if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                etherNetwork = network;

            }
        }
        Network boundNetwork = connectivityManager.getBoundNetworkForProcess();
        if (boundNetwork != null) {
            NetworkInfo boundNetworkInfo = connectivityManager.getNetworkInfo(boundNetwork);
            if(boundNetworkInfo!=null) {
                if (boundNetworkInfo.getType() != ConnectivityManager.TYPE_WIFI) {
                    if (etherNetwork != null) {
                        connectivityManager.bindProcessToNetwork(etherNetwork);

                    }
                }
            }
        }
        if(etherNetwork!=null)
            connectivityManager.bindProcessToNetwork(etherNetwork);
    }

    @Override
    public IBinder onBind(Intent intent) {

        setInterfaceWIFI();
        //return service Messenger
        return mMessenger.getBinder();
    }


    /**
     * search for Qx device on Wi-Fi
     */
    private void serviceSearchQX(){
        if(qx==null) return;

        try{
            qx.searchQx(getApplicationContext());

        }
        catch (ConnectException e){
            Log.e(TAG,"failed");
        }
        //tell client the status
        Bundle data = new Bundle();
        data.putBoolean("status",qx.status());
        send(QxCommunicationResponseClient.QXSEARCHUPDATE,null,data);

    }


    /**
     * trigger Qx for one pic
     */
    public void serviceTriggerQX() {


        new Thread(new Runnable() {
            @Override
            public void run() {
                qx.capture();
                //qx.capture();
               // qx.capture();


            }
        }).start();
    }

    /**
     * change beep
     */
    public void serviceSetBeepMode(final String mode){
        new Thread(new Runnable() {
            @Override
            public void run() {
                qx.setBeepMode(mode);
            }
        }).start();
    }

    public void serviceCaptureModes(){qx.setCaptureModes();}


    public void serviceActZoom(final String direction){
        qx.actZoom(direction);
    }

    /**
     * get Qx connection status
     * @return boolean status
     */
    private boolean serviceStatusQX(){
        return qx.status();
    }

    /**
     * send a message to the client
     * @param message message number
     * @param replyTo reply to messenger
     * @param data bundle
     */
    public void send(int message,Messenger replyTo,Bundle data){
        if(client!=null){
            Message msg = Message.obtain(null,message,0,0);
            if(replyTo!=null)
                msg.replyTo = replyTo;
            if(data!=null)
                msg.setData(data);

            try {
                client.send(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

    }


    /**
     * Handler for client Messages
     */
    private static class  IncomingHandler extends Handler{

        private WeakReference<QXCommunicationService> serviceWeakReference;

        IncomingHandler(QXCommunicationService service){
            serviceWeakReference =new WeakReference<>(service);
        }

        @Override
        public void handleMessage(Message msg){
            QXCommunicationService service = serviceWeakReference.get();
            Log.i(TAG,msg.what+"");
            switch(msg.what){
                //search for a Qx device

                case SEARCHQX:
                    service.setInterfaceWIFI();
                    QXCommunicationService.qx = new QXHandler(service.pictureStorageServer,msg.getData().getString("format"));
                    service.serviceSearchQX();
                    break;
                //trigger qx
                case TRIGGERQX:
                    service.serviceTriggerQX();
                    break;

                //get full size image
                case FULLSIZE:
                    service.pictureStorageServer.fetchFullSize(msg.getData().getString("session"),msg.getData().getString("url"));
                    break;
                //status update for Qx connection
                case STATUSQX:
                    if(QXCommunicationService.qx == null){
                        Bundle data = new Bundle();
                        data.putBoolean("status",false);
                        service.send(QxCommunicationResponseClient.QXSTATUS,null,data);
                    }
                    else {
                        boolean status = service.serviceStatusQX();

                        Bundle data = new Bundle();
                        data.putBoolean("status", status);
                        service.send(QxCommunicationResponseClient.QXSTATUS, null, data);
                    }

                    break;
                //set whether to make trigger sound
                case BEEPMODE:
                    if(QXCommunicationService.qx!=null){
                        String beepStatus = msg.getData().getString("status");
                        service.serviceSetBeepMode(beepStatus);
                    }

                    break;

                //zoom
                case ZOOM:
                    if(QXCommunicationService.qx!=null){
                        String zoomDirection = msg.getData().getString("direction");
                        service.serviceActZoom(zoomDirection);
                    }
                    break;

                case CAPTUREMODES:
                    if(QXCommunicationService.qx!=null){
                        service.serviceCaptureModes();
                    }
                    break;


                //client wants to register
                case REGISTER:
                    service.client = msg.replyTo;
                    try {
                        service.pictureStorageServer = new PictureStorageServer(service.client,service.getApplicationContext());
                        QXCommunicationService.qx = new QXHandler(service.pictureStorageServer,"2M");

                    }
                    catch (IOException e){
                        Log.e(TAG,e.toString()) ;
                    }
                    break;
                default:
                    super.handleMessage(msg);

            }
        }
    }

}
