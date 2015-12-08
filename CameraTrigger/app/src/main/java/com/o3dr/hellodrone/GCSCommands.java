package com.o3dr.hellodrone;


import android.os.Handler;
import android.os.HandlerThread;

import com.o3dr.android.client.Drone;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.MalformedInputException;
import com.o3dr.hellodrone.MainActivity.CameraTakerThread;
import com.o3dr.hellodrone.MainActivity.ConnectThread;


//used to communicate with GCS, waiting for commands
public class GCSCommands {
    //GCS url
    String URL="";
    //httpcon
    HttpURLConnection con;
    //threads that control connections and triggering
    ConnectThread conT;
    CameraTakerThread camT;




    public GCSCommands(String url, ConnectThread conT, CameraTakerThread camT) throws IllegalAccessException{
        if(url==null){
            throw new IllegalAccessError("No URL") ;
        }
        this.URL=url;
        this.conT = conT;
        this.camT= camT;


    }


    //wait for drone connect command
    public void droneConnect(){
        DroidConnect droidConnect = new DroidConnect();
        droidConnect.connect();

    }
    //wait for trigger command
    public void droidTrigger(){
        DroidTrigger droidTrigger = new DroidTrigger();
        droidTrigger.trigger();
    }


    //loops waiting for drone connect command
    private class DroidConnect extends HandlerThread{

        Handler mHandler = null;

        DroidConnect(){
            super("DroidConnect");
            start();
            mHandler= new Handler(getLooper());
        }

       void connect(){
           mHandler.post(new Runnable(){
               public void run(){
                   try {
                       URL url = new URL(URL+"/droid/droidconnect");
                       con = (HttpURLConnection) url.openConnection();
                       con.setRequestMethod("POST");
                       String csrf_token_string = "sss";
                       //sets the csrf token cookie
                       con.setRequestProperty("Cookie", "csrftoken=" + csrf_token_string);
                       con.setRequestProperty("Content-length", "0");
                       con.setUseCaches(false);

                   }
                   catch(MalformedURLException e){

                   }
                   catch(ProtocolException e){

                   }
                   catch(IOException e){

                   }





                   while(true){
                       try {
                           con.connect();
                           int status = con.getResponseCode();

                           switch(status){

                               case 200:
                               case 201:
                                   BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));
                                   StringBuilder sb = new StringBuilder();
                                   String line;
                                   while((line= br.readLine())!=null){
                                       sb.append(line);
                                   }
                                   br.close();
                                   //if server said to connect
                                   if(sb.toString()=="YES"){
                                       if(!MainActivity.drone.isConnected()) {
                                           conT.connect();
                                       }


                                   }
                                   //if server said to disconnect
                                   else if(sb.toString()=="NO"){
                                       if(MainActivity.drone.isConnected()) {
                                           conT.connect();
                                       }

                                   }
                                   //else no command has be sent
                                   else if(sb.toString()=="NO INFO"){
                                       continue;
                                   }

                           }
                       }
                       catch(IOException e){

                       }



                   }

               }
           });
       }
    }

    //used to process server trigger command
    private class DroidTrigger extends HandlerThread{

        Handler mHandler = null;
        DroidTrigger(){
            super("DroidTrigger");
            start();
            mHandler = new Handler(getLooper());
        }

        void trigger(){
            mHandler.post(new Runnable(){

                public void run(){

                    try {
                        URL url = new URL(URL+"/droid/droidtrigger");
                        con = (HttpURLConnection) url.openConnection();
                        con.setRequestMethod("POST");
                        String csrf_token_string = "sss";
                        //sets the csrf token cookie
                        con.setRequestProperty("Cookie", "csrftoken=" + csrf_token_string);
                        con.setRequestProperty("Content-length", "0");
                        con.setUseCaches(false);

                    }
                    catch(MalformedURLException e){

                    }
                    catch(ProtocolException e){

                    }
                    catch(IOException e){

                    }

                    while(true){
                        try {
                            con.connect();
                            int status = con.getResponseCode();

                            switch(status){

                                case 200:
                                case 201:
                                    BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));
                                    StringBuilder sb = new StringBuilder();
                                    String line;
                                    while((line= br.readLine())!=null){
                                        sb.append(line+"\n");
                                    }
                                    br.close();
                                    //stop triggering
                                    if(sb.toString()=="NO"){
                                        MainActivity.on =false;

                                    }
                                    //no command
                                    else if(sb.toString()=="NO INFO"){
                                        continue;
                                    }
                                    //trigger
                                    else{
                                        //parse JSON options
                                        String[] json = sb.toString().split("\\n");
                                        String triggerOn = json[0];
                                        if(triggerOn!="1"){
                                            continue;
                                        }
                                        //interval
                                        String timeInterval = json[1];
                                        //or smartTrigger
                                        String smartTrigger = json[2];
                                        if(smartTrigger=="0"){
                                            camT.setCapture(Double.parseDouble(timeInterval));
                                        }
                                        else if(smartTrigger=="1"){
                                            //do smartTrigger stuff
                                        }
                                        //start triggering
                                        MainActivity.on = true;
                                        camT.capture();

                                    }

                            }
                        }
                        catch(IOException e){

                        }


                    }


            }
        });

        }
    }


}
