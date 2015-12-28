package com.o3dr.hellodrone;


import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;
import android.support.annotation.IntegerRes;
import android.util.Log;

import com.o3dr.android.client.Drone;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.MalformedInputException;
import java.util.concurrent.TimeUnit;

import com.o3dr.hellodrone.MainActivity.CameraTakerThread;
import com.o3dr.hellodrone.MainActivity.ConnectThread;

import org.json.JSONException;
import org.json.JSONObject;


//used to communicate with GCS, waiting for commands
public class GCSCommands {
    //GCS url
    String URL="";
    //httpcon

    //threads that control connections and triggering
    ConnectThread conT;
    CameraTakerThread camT;
    private String android_id;


    public GCSCommands(String url, ConnectThread conT, CameraTakerThread camT, String android_id) throws IllegalAccessException{
        if(url==null){
            throw new IllegalAccessError("No URL") ;
        }
        this.URL=url;
        this.conT = conT;
        this.camT= camT;
        this.android_id = android_id;


    }


    private String getTime(){
        long millis =System.currentTimeMillis();
        String hms = String.format("%02d:%02d:%02d", TimeUnit.MILLISECONDS.toHours(millis),
                TimeUnit.MILLISECONDS.toMinutes(millis) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(millis)),
                TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis)));
        return  hms;
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

                   while(true){
                       try {

                           //ask server what to do
                           URL url = new URL("http://"+URL+"/droid/droidconnect");

                           HttpURLConnection con = (HttpURLConnection) url.openConnection();

                           con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                           con.setDoOutput(true);
                           con.setUseCaches(false);
                           con.setRequestMethod("POST");
                           con.connect();

                            //send json that we are asking should we connect drone
                           //not a status update
                           JSONObject json = new JSONObject();
                           try {
                               json.put("id",android_id);
                               json.put("time",getTime());
                               json.put("connect", "1");
                               json.put("status","0");
                           }
                           catch( JSONException e){

                           }
                           OutputStream osC = con.getOutputStream();
                           OutputStreamWriter osW = new OutputStreamWriter(osC,"UTF-8");
                           osW.write(json.toString());
                           osW.flush();
                           osW.close();



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

                               if(sb.toString().equals("YES")){
                                   Log.d("Yes","Yes");
                                   //if drone is not connected
                                   if(!MainActivity.drone.isConnected()) {
                                       //connect
                                       conT.connect();
                                            try {
                                                //wait till connect completes
                                                //just to be safe
                                                synchronized (conT) {
                                                    while (!conT.done) {
                                                        conT.wait(2000);
                                                    }
                                                }
                                            }
                                            catch(InterruptedException e){

                                            }


                                   }
                                   //if connect failed
                                   if(!MainActivity.drone.isConnected()){
                                       con.disconnect();

                                       URL urlC = new URL("http://"+URL+"/droid/droidconnect");
                                       HttpURLConnection conn = (HttpURLConnection) urlC.openConnection();
                                       conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                                       conn.setDoOutput(true);
                                       conn.setUseCaches(false);
                                       conn.setRequestMethod("POST");
                                       conn.connect();
                                       //send json status update
                                       JSONObject json_yes = new JSONObject();
                                       try {
                                           json_yes.put("connect", "0");
                                           json_yes.put("status", "1");
                                           //connection failed
                                           json_yes.put("connected","0");
                                       }
                                       catch( JSONException e){

                                       }

                                       OutputStream os = conn.getOutputStream();
                                       OutputStreamWriter osWr = new OutputStreamWriter(os,"UTF-8");
                                       osWr.write(json_yes.toString());
                                       osWr.flush();
                                       osWr.close();
                                       int response_code = conn.getResponseCode();



                                       try {
                                           Thread.sleep(8000);
                                       }
                                       catch(InterruptedException e) {

                                       }

                                   }





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


    public void sendPicture(JSONObject request_data) throws IOException {
        Log.d("posting","posting");
        URL url = new URL("http://" +URL+"/droid/upload");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");

        String csrf_token_string = "sss";
        //sets the csrf token cookie
        //con.setRequestProperty("Cookie", "csrftoken=" + csrf_token_string);
        con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        /*try {
            request_data.put("csrfmiddlewaretoken", csrf_token_string);
        }
        catch (JSONException e){
            Log.e("csrf","csrf");
        }*/
        //set doOutput to true so that we can write bytes to the body of the http post request
        con.setDoOutput(true);
        con.connect();
        //initialize the output stream that will write to the body of the http post request
        OutputStream out = con.getOutputStream();

        OutputStreamWriter outW = new OutputStreamWriter(out);
        outW.write(request_data.toString());
        outW.flush();
        outW.close();

        switch(con.getResponseCode()){
            case 200:
                Log.d("200","Success");
                break;
            case 500:
                Log.e("500", "Internal Server error");
                break;
            case 403:
                Log.e("403","Forbidden");
                break;
            default:
                Log.e("#","Something else");
        }
    }


    public void sendPicSignal(String dateTime){
        try {
            //ask server what to do
            URL url = new URL("http://" + URL + "/droid/droidtrigger");
            Log.d("url", URL);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();

            con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            con.setDoOutput(true);
            con.setUseCaches(false);
            con.setRequestMethod("POST");
            con.connect();
            //send json that we are asking should we connect drone
            //not a status update

            JSONObject json = new JSONObject();
            try {
                json.put("trigger", "0");
                json.put("status", "1");
                json.put("dateTime",dateTime);

                OutputStream out = con.getOutputStream();

                OutputStreamWriter outW = new OutputStreamWriter(out);
                outW.write(json.toString());
                outW.flush();
                outW.close();
                int status = con.getResponseCode();

                switch(status){
                    case 200:
                       break;
                    default:
                        Log.e("error",""+status);
                }

            } catch (JSONException e) {

            }
        }
        catch(MalformedURLException e){

        }
        catch(ProtocolException e){

        }
        catch(IOException e){

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

                    while(true){
                        try {
                            //ask server what to do
                            URL url = new URL("http://"+URL+"/droid/droidtrigger");
                            Log.d("url", URL);
                            HttpURLConnection con = (HttpURLConnection) url.openConnection();

                            con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                            con.setDoOutput(true);
                            con.setUseCaches(false);
                            con.setRequestMethod("POST");
                            con.connect();
                            //send json that we are asking should we connect drone
                            //not a status update
                            JSONObject json = new JSONObject();
                            try {

                                json.put("trigger", "1");
                                json.put("status","0");
                            }
                            catch( JSONException e){

                            }
                            OutputStream osC = con.getOutputStream();
                            OutputStreamWriter osW = new OutputStreamWriter(osC,"UTF-8");
                            osW.write(json.toString());
                            osW.flush();
                            osW.close();

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
                                    //stop triggering
                                    if(sb.toString().equals("NO")){

                                        MainActivity.on =false;
                                        con.disconnect();
                                    }
                                    //no command
                                    else if(sb.toString().equals("NOINFO")){
                                        con.disconnect();
                                    }
                                    //trigger
                                    else {

                                        JSONObject json_response = null;
                                        try {

                                            json_response = new JSONObject(sb.toString());



                                            //interval
                                            String timeInterval = json_response.get("time").toString();

                                            //or smartTrigger
                                            String smartTrigger = json_response.get("smart_trigger").toString();
                                            if (smartTrigger.equals("0")) {
                                                camT.setCapture(Double.parseDouble(timeInterval));
                                                //start triggering
                                                MainActivity.on = true;
                                                Log.d("Triggering", "now");
                                                camT.capture();
                                            } else if (smartTrigger.equals("1")) {
                                                //start smart Trigger
                                               camT.smartTrigger();
                                            }

                                            con.disconnect();


                                        } catch (JSONException e) {
                                        }
                                    }





                            }
                            try {
                                Thread.sleep(4000);
                            }
                            catch (InterruptedException e){
                                Log.e("Error","error");

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
