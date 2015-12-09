package com.o3dr.hellodrone;


import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.IntegerRes;
import android.util.Log;

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
        //DroidTrigger droidTrigger = new DroidTrigger();
        //droidTrigger.trigger();
    }


    //loops waiting for drone connect command
    private class DroidConnect extends HandlerThread{

        Handler mHandler = null;
        HttpURLConnection con;
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

                           URL url = new URL("http://"+URL+"/droid/droidconnect");
                           Log.d("url",URL);
                           con = (HttpURLConnection) url.openConnection();
                           con.setRequestMethod("GET");
                           //String csrf_token_string = "sss";
                           //sets the csrf token cookie
                           //con.setRequestProperty("Cookie", "csrftoken=" + csrf_token_string);
                           //con.setRequestProperty("Content-length", "0");
                           //The boundary string that we will be using.
                           //String boundary_for_multipart_post = "===" + System.currentTimeMillis() + "===";
                           //Make the post request a multipart/form data post request, and pass in the boundary string
                           //con.setRequestProperty("Content-Type",
                           //       "multipart/form-data; boundary=" + boundary_for_multipart_post);
                           con.setUseCaches(false);
                           //initialize the output stream that will write to the body of the http post request
                           //OutputStream out = con.getOutputStream();



                           //following paragraph writes the csrf token into the body as a key-value pair
                           //out.write( ("--" + boundary_for_multipart_post + "\r\n").getBytes() );
                           //out.write( ("Content-Disposition: form-data; name=\"csrfmiddlewaretoken\"\r\n").getBytes() );
                           //out.write( ("\r\n").getBytes() );
                           //out.write( (csrf_token_string).getBytes() );
                           //out.flush();


                       con.connect();

                       int status = con.getResponseCode();



                       switch(status){


                           case 200:
                               BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));
                               StringBuilder sb = new StringBuilder();
                               String line;
                               while((line= br.readLine())!=null){
                                   sb.append(line);
                               }
                               br.close();
                               Log.d("string",""+ sb.toString().equals("NOINFO"));
                               //if server said to connect
                               Log.d("string", sb.toString());
                               if(sb.toString().equals("YES")){
                                   Log.d("Yes","Yes");
                                   if(!MainActivity.drone.isConnected()) {
                                       conT.connect();
                                   }
                                   con.disconnect();



                               }
                               //if server said to disconnect
                               else if(sb.toString().equals("NO")){
                                   if(MainActivity.drone.isConnected()) {
                                       conT.connect();
                                   }
                                   con.disconnect();


                               }
                               //else no command has be sent

                               else if(sb.toString().equals("NOINFO")){



                                   con.disconnect();



                               }
                               try {
                                       Thread.sleep(4000);
                                   }
                               catch (InterruptedException e){
                                   Log.e("Error","error");

                               }



                           }


                       }

                       catch(MalformedURLException e){

                       }
                       catch(ProtocolException e){

                       }
                       catch(IOException e){

                       }
                       catch(NullPointerException e){
                           Log.e("Connection Error","Invalid address");
                       }




                   }

               }
           });
       }
    }

    //used to process server trigger command
    private class DroidTrigger extends HandlerThread{
        HttpURLConnection con;
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
                        URL url = new URL("http://"+URL+"/droid/droidtrigger");
                        con = (HttpURLConnection) url.openConnection();
                        con.setRequestMethod("POST");
                        String csrf_token_string = "sss";
                        //sets the csrf token cookie
                        con.setRequestProperty("Cookie", "csrftoken=" + csrf_token_string);
                        con.setRequestProperty("Content-length", "0");
                        con.setUseCaches(false);
                        //set doOutput to true so that we can write bytes to the body of the http post request
                        con.setDoOutput(true);
                        //initialize the output stream that will write to the body of the http post request
                        OutputStream out = con.getOutputStream();

                        //The boundary string that we will be using.
                        String boundary_for_multipart_post = "===" + System.currentTimeMillis() + "===";
                        //Make the post request a multipart/form data post request, and pass in the boundary string
                        con.setRequestProperty("Content-Type",
                                "multipart/form-data; boundary=" + boundary_for_multipart_post);
                        //following paragraph writes the csrf token into the body as a key-value pair
                        out.write( ("--" + boundary_for_multipart_post + "\r\n").getBytes() );
                        out.write( ("Content-Disposition: form-data; name=\"csrfmiddlewaretoken\"\r\n").getBytes() );
                        out.write( ("\r\n").getBytes() );
                        out.write( (csrf_token_string).getBytes() );
                        out.flush();


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
                            con.disconnect();
                        }
                        catch(IOException e){

                        }


                    }


            }
        });

        }
    }


}
