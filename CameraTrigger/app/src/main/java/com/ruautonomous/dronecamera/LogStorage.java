package com.ruautonomous.dronecamera;

import android.os.Environment;
import android.util.Log;

import com.ruautonomous.dronecamera.DroneActivity;
import com.ruautonomous.dronecamera.ImageQueue;
import com.ruautonomous.dronecamera.utils.ImageData;

import org.json.JSONException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;
import java.util.Iterator;
import java.util.TimeZone;

/**
 * Created by root on 6/25/16.
 */
public class LogStorage {


    private File logDir;
    private File logFile;
    //for writing to log files
    private FileWriter wrt =null;
    private BufferedWriter logOut = null;
    private BufferedWriter fileWriter;
    private ImageQueue imageQueue;
    public final String TAG = "LogStorage";


    public LogStorage() throws IOException{
        this.imageQueue = DroneActivity.app.getImageQueue();

        //used for setting current time log file was made
        Calendar cal = Calendar.getInstance(TimeZone.getDefault());
        String dateTime = cal.getTime().toLocaleString();

        //get the sd card
        File sdCard = Environment.getExternalStorageDirectory();

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
            //context.runOnUiThread(new Runnable() {
              //  @Override
               // public void run() {
                 //   context.alertUser("Storage creation failed. Exiting");
               // }
            //});
            throw new IOException("Failed to create logs");
        }
        finally {
        }

    }

    public void close(){
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


    public void writeLog(ImageData data) throws IOException {

        synchronized (imageQueue){
            imageQueue.pushData(data);
        }
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
