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
import org.json.JSONObject;

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
 * used by qx service to storage images to fs
 */
public class PictureStorageServer {


    private int picNum = 0;
    private Thread pendingPicFetcher = null;
    private boolean allowed = true;
    private Messenger responseClient;
    //picture directory on phone
    private File picDir;
    //pending images to fetch from qx
    private final ArrayList<String> imagePendingQueue = new ArrayList<>();
    private final ArrayList<HashMap<String,String>> fulleSizePendingQueue = new ArrayList<>();

    public Context context;
    public String TAG ="PictureStorageServer";
    public final long CONSUMER_SLEEP_TIME= 500;





    private void refreshGallery(File file) {
        Intent mediaScanIntent = new Intent( Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        mediaScanIntent.setData(Uri.fromFile(file));
       context.sendBroadcast(mediaScanIntent);
    }

    public void fetchFullSize(String session,String url){
        synchronized (fulleSizePendingQueue){
            HashMap<String,String> hash = new HashMap<>();
            hash.put("url",url);
            hash.put("session",session);
            fulleSizePendingQueue.add(hash);
        }
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
                picDir.mkdirs();



            }


        } catch (SecurityException e) {
            Log.e(TAG,"Failed to create Picture Storage");
        }


        //loop to fetch picture urls from queue and download them from qx
        if(pendingPicFetcher!=null)return;
        pendingPicFetcher = new Thread(new Runnable() {
            @Override
            public void run() {

                while(allowed){
                    //full sized images are a priority
                    if(!fulleSizePendingQueue.isEmpty()){
                        synchronized (fulleSizePendingQueue){
                            HashMap<String,String> image = fulleSizePendingQueue.remove(0);
                            JSONObject response = QXCommunicationService.qx.setPostViewSize("Original");


                            InputStream istream = downloadImage(image.get("url"));
                            Log.i("FULL",image.get("url"));

                            try {
                                if (istream != null) {
                                    //write the image to storage
                                    String imageFileName = writeToStorage(istream,"Original");
                                    istream.close();
                                    //push image to client (drone app)
                                    Log.i("FULL","finished");
                                    pushFullImage(imageFileName,image.get("session"));

                                }
                            } catch (IOException e) {
                                Log.e(TAG,"consumer died");
                                //noting really to do
                            }

                        }
                    }


                    //then get 2M sized images
                    if(!imagePendingQueue.isEmpty()) {
                        synchronized (imagePendingQueue) {

                            if(QXCommunicationService.qx.status()) {

                                JSONObject response = QXCommunicationService.qx.setPostViewSize("2M");
                                Log.i(TAG,response.toString());
                            }

                            //get image url
                            String image = imagePendingQueue.remove(0);

                            //download
                            InputStream istream = downloadImage(image);

                            try {
                                if (istream != null) {
                                    //write the image to storage
                                    String imageFileName = writeToStorage(istream,"2M");
                                    istream.close();
                                    //push image to client (drone app)
                                    pushImage(imageFileName,image);

                                }
                            } catch (IOException e) {
                                Log.e(TAG,"consumer died");
                                //noting really to do
                            }
                        }
                        }
                        else{
                        //if nothing in queue just sleep for a little
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

    /**
     * push image to client (drone app)
     * @param image : image name in fs
     */
    private void pushImage(String image,String url){
        //use Messenger IPC to send image name in fs
        if(responseClient!=null){
            //send it
            Message msg = Message.obtain(null, QxCommunicationResponseClient.IMAGE,0,0);
            Bundle data = new Bundle();
            data.putString("pictureName",image);
            data.putString("url",url);
            msg.setData(data);

            try {
                responseClient.send(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * push full sized image to client (drone app)
     * @param image name in fs
     */
    private void pushFullImage(String image,String session){
        //use Messenger IPC to send image name in fs
        if(responseClient!=null){
            //send it
            Message msg = Message.obtain(null, QxCommunicationResponseClient.FULLIMAGE,0,0);
            Bundle data = new Bundle();
            data.putString("pictureName",image);
            data.putString("session",session);
            msg.setData(data);

            try {
                responseClient.send(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * clean up
     */
    public void close(){
        if(pendingPicFetcher!=null) allowed = false;

    }

    /**
     * push picture to queue
     * @param imageName image url
     * @throws IOException
     */
    public void writePicture(String imageName) throws IOException{
        synchronized (imagePendingQueue){
                imagePendingQueue.add(imageName);

        }
    }

    /**
     * download image from qx
     * @param imageUrl url for image
     * @return input stream for download image to get bytes
     */
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

    /**
     * write dowloaded image to fs
     * @param image input stream for image bytes
     * @return name of image in fs
     * @throws IOException
     */
    private String writeToStorage(InputStream image,String size) throws IOException{



        FileOutputStream outStream = null;
        String fileName = null;
        try{
            //write image to disk
            fileName= String.format(size+":"+System.currentTimeMillis()/1000 + "%04d.jpg", ++picNum);
            File outFile = new File(picDir,fileName);
            outStream = new FileOutputStream(outFile);

            int bufferSize = 2048;
            byte[] buffer = new byte[bufferSize];
            int len =0;
            //read and write loop
            while((len = image.read(buffer))!=-1){

                outStream.write(buffer,0,len);

            }
            refreshGallery(outFile);
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
