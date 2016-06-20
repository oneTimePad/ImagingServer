package com.ruautonomous.dronecamera.utils;

import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import com.ruautonomous.dronecamera.DroneActivity;

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
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TimeZone;

/**
 * Created by lie on 6/8/16.
 */
public class PictureStorage {

    //for writing to log files
    private FileWriter wrt =null;
    private BufferedWriter logOut = null;
    private int picNum = 0;
    private Thread pendingPicFetcher = null;
    public DroneActivity context;

    //picture directory on phone
    private File picDir;
    private File logDir;
    private File logFile;
    private final ImageQueue imagePendingQueue = new ImageQueue();
    private BufferedWriter fileWriter;
    private ImageQueue imageUploadQueue;
    public String TAG ="PictureStorage";
    public final long CONSUMER_SLEEP_TIME= 500;
    private boolean allowed = true;




    private void refreshGallery(File file) {
        Intent mediaScanIntent = new Intent( Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        mediaScanIntent.setData(Uri.fromFile(file));
        context.sendBroadcast(mediaScanIntent);
    }



    public PictureStorage() throws IOException{

        this.context = DroneActivity.app.getContext();
        this.imageUploadQueue = DroneActivity.app.getImageQueue();

        //get the sd card
        File sdCard = Environment.getExternalStorageDirectory();
        //create the pic storage directory
        picDir = new File(sdCard.toString() + "/dronePictures");

        //create pic storage directory
        try {
            //if directory doesn't exist, make it
            if (!picDir.exists()) {
                if(!picDir.mkdirs()){

                    context.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            context.alertUser("Storage creation Failed.");
                        }
                    });

                }
            }


        } catch (SecurityException e) {
            context.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    context.alertUser("Storage creation failed. Exiting");
                }
            });


        }

        if(!picDir.exists()){
            throw  new IOException("Failed to create Picture directory");
        }

        //used for setting current time log file was made
        Calendar cal = Calendar.getInstance(TimeZone.getDefault());
        String dateTime = cal.getTime().toLocaleString();

        //create the pic logs directrory
        logDir = new File(sdCard.toString() + "/droneLogs");

        try {

            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            //create new log file
            logFile = new File(logDir, "log " + dateTime + ".log");
            fileWriter = new BufferedWriter(new FileWriter(logFile));

        } catch (SecurityException e) {
            context.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    context.alertUser("Storage creation failed. Exiting");
                }
            });
            throw new IOException("Failed to create logs");
        }
        finally {
        }


        if(pendingPicFetcher!=null)return;
        pendingPicFetcher = new Thread(new Runnable() {
            @Override
            public void run() {
                while(allowed){
                    //Log.i("ALIVE","ALIVE");
                    if(!imagePendingQueue.isEmpty()) {
                        synchronized (imagePendingQueue) {
                            HashMap<String, Object> image = imagePendingQueue.pop();
                            String imageUrl = (String) image.get("pictureName");
                            ImageData imageData = (ImageData) image.get("pictureData");

                            InputStream istream = downloadImage(imageUrl);

                            try {
                                if (istream != null) {
                                    String imageFileName = writeToStorage(istream);
                                    //writeLog(imageData);
                                    Log.i(TAG,imageData.toString());
                                    istream.close();
                                    synchronized (imageUploadQueue) {
                                        imageUploadQueue.push(imageFileName, imageData);
                                    }
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

    public void close(){
        if(pendingPicFetcher!=null) allowed = false;
        if(fileWriter!=null) {
            try {

                fileWriter.flush();
                fileWriter.close();
            }
            catch (IOException e){
                Log.e(TAG,e.toString());
            }
        }

    }

    public File getPictureStorage(){
        return picDir;
    }

    public void writePicture(String imageName,ImageData imageData) throws IOException{
        synchronized (imagePendingQueue){
            try {
                imagePendingQueue.push(imageName, imageData);
            }
            catch (IOException e){
                Log.e(TAG,"Failed to pend image");
                throw new IOException("Failed to pend image");
            }
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

    public void writeLog(ImageData data) throws IOException{
        //create log file
        if(fileWriter == null)return;
        try {

            //log entry
            //write to log
            fileWriter.newLine();
            fileWriter.write("------------------------------");
            fileWriter.newLine();
            Iterator<?> iter = data.keys();
            while(iter.hasNext()){
                String key = (String)iter.next();
                try {
                    fileWriter.write(key + ":" + data.getString(key)+", ");
                }
                catch (JSONException e){
                    Log.e(TAG,e.toString());
                }

            }
            fileWriter.flush();
            Log.i(TAG,"wrote log");


        }
        catch (FileNotFoundException e){
            Log.e(TAG,e.toString());
            throw new IOException("Log file doesn't exist");

        }


    }

}
