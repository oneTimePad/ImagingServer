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

public class GCSCommands {

    String url;
    HttpURLConnection con;
    ConnectThread conT;
    CameraTakerThread camT;
    DroidConnect connectThread = null;



    public GCSCommands(String url, ConnectThread conT, CameraTakerThread camT){
        this.url=url;
        this.conT = conT;
        this.camT= camT;

    }

    private void initializeConnection(){

        try {
            URL url = new URL(this.url);
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




    }

    public void droneConnect(){
        DroidConnect droidConnect = new DroidConnect();
        droidConnect.connect();

    }



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
                                   if(sb.toString()=="YES"){
                                       conT.connect();


                                   }
                                   else if(sb.toString()=="NO"){
                                       conT.connect();

                                   }
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

                                    if(sb.toString()=="NO"){
                                        conT.connect();

                                    }
                                    else if(sb.toString()=="NO INFO"){
                                        continue;
                                    }
                                    else{
                                        String[] json = sb.toString().split("\\n");
                                        //get trigger part, get time get smart trigger
                                    }

                            }
                        }
                        catch(IOException e){

                        }


                    }


            });
        }
    }


}
