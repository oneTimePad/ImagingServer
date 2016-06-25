package com.ruautonomous.dronecamera.qxservices;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.MotionEvent;

import com.ruautonomous.dronecamera.DroneActivity;
import com.ruautonomous.dronecamera.ImageQueue;
import com.ruautonomous.dronecamera.qxservices.QxCommunicationResponseClient;

import org.json.JSONException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TimeZone;

/**
 * Created by lie on 6/8/16.
 */
public class PictureStorageServer {


    private int picNum = 0;
    private Thread pendingPicFetcher = null;
    public Context context;

    //picture directory on phone
    private File picDir;
    private final ArrayList<String> imagePendingQueue = new ArrayList<>();

    public String TAG ="PictureStorageServer";
    public final long CONSUMER_SLEEP_TIME= 500;
    private boolean allowed = true;
    private Messenger responseClient;




    private void refreshGallery(File file) {
        Intent mediaScanIntent = new Intent( Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        mediaScanIntent.setData(Uri.fromFile(file));
       context.sendBroadcast(mediaScanIntent);
    }



    public PictureStorageServer(Messenger responseClient, Context context) throws IOException{

        this.context = context;
        //this.imageUploadQueue = DroneActivity.app.getImageQueue();
        this.responseClient = responseClient;

        //get the sd card
        File sdCard = Environment.getExternalStorageDirectory();
        //create the pic storage directory
        picDir = new File(sdCard.toString() + "/dronePictures");

        //create pic storage directory
        try {
            //if directory doesn't exist, make it
            if (!picDir.exists()) {
                if(!picDir.mkdirs()){

                   /* context.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            context.alertUser("Storage creation Failed.");
                        }
                    });*/

                }
            }


        } catch (SecurityException e) {
            Log.e(TAG,"Failed to create Picture Storage");

            /*context.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    context.alertUser("Storage creation failed. Exiting");
                }
            });*/


        }

        /*if(!picDir.exists()){
            throw  new IOException("Failed to create Picture directory");
        }*/



        if(pendingPicFetcher!=null)return;
        pendingPicFetcher = new Thread(new Runnable() {
            @Override
            public void run() {
                while(allowed){
                    //Log.i("ALIVE","ALIVE");
                    if(!imagePendingQueue.isEmpty()) {
                        synchronized (imagePendingQueue) {
                            String image = imagePendingQueue.remove(0);


                            InputStream istream = downloadImage(image);

                            try {
                                if (istream != null) {
                                    String imageFileName = writeToStorage(istream);
                                    istream.close();
                                    pushImage(imageFileName);

                                    /*
                                    synchronized (imageUploadQueue) {
                                        imageUploadQueue.push(imageFileName, imageData);
                                    }*/
                                }
                            } catch (IOException e) {
                                Log.e(TAG,"consumer died");
                                //noting really to do
                            }
                        }
                        }
                        else{
                            try {
                                Thread.sleep(CONSUMER_SLEEP_TIME);
                            } catch (InterruptedException e) {
                                Log.e(TAG, "Error in consumer sleep");
                            }
                        }

                }
            }
        });
        pendingPicFetcher.start();

    }

    private void pushImage(String image){
        if(responseClient!=null){
            Message msg = Message.obtain(null, QxCommunicationResponseClient.IMAGE,0,0);
            Bundle data = new Bundle();
            data.putString("pictureName",image);
            msg.setData(data);

            try {
                responseClient.send(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public void close(){
        if(pendingPicFetcher!=null) allowed = false;


    }


    public void writePicture(String imageName) throws IOException{
        synchronized (imagePendingQueue){

                imagePendingQueue.add(imageName);


        }
    }

    private InputStream downloadImage(String imageUrl) {
        InputStream istream = null;
        try {
            Log.i("URL",imageUrl);
           istream = (InputStream) new URL(imageUrl).getContent();


        }
        catch (MalformedURLException e){
            Log.e(TAG,"Failed while downloading image");
        }
        catch (IOException e){
            Log.e(TAG,"Unknown error "+e.toString());
        }
        return istream;

    }


    private String writeToStorage(InputStream image) throws IOException{






        FileOutputStream outStream = null;
        String fileName = null;
        try{
            //write image to disk
            fileName= String.format(System.currentTimeMillis()/1000 + "%04d.jpg", ++picNum);
            File outFile = new File(picDir,fileName);
            outStream = new FileOutputStream(outFile);

            int bufferSize = 2048;
            byte[] buffer = new byte[bufferSize];
            int total =0;
            int len =0;

            while((len = image.read(buffer))!=-1){
                //Log.i("READ",len+"");
                outStream.write(buffer,0,len);
                //Log.i("ENDREAD","ENDREAD");

                total+=len;

                //if(image.available()<=0) break;
            }
            //Log.i("TOTALSIZE","PicNUM"+picNum+"TOTAL"+total+"");
            refreshGallery(outFile);
            //Log.i("CAPTUREWATCH","just saved "+picNum);
        }
        catch (IOException e){
            Log.e(TAG,e.toString());
            throw  new IOException("Failed to save image");
        }
        finally {
            if(outStream !=null) {
                outStream.flush();
                outStream.close();
            }

        }
        return fileName;

    }



}
