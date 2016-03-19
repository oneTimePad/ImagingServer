package com.o3dr.hellodrone;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.media.AudioManager;
import android.net.Uri;

import android.os.Environment;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.os.Handler;

import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;

import android.view.SurfaceHolder;
import android.view.inputmethod.InputMethodManager;

import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;

import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

import com.o3dr.android.client.ControlTower;
import com.o3dr.android.client.Drone;
import com.o3dr.android.client.interfaces.DroneListener;
import com.o3dr.android.client.interfaces.TowerListener;
import com.o3dr.services.android.lib.coordinate.LatLong;
import com.o3dr.services.android.lib.coordinate.LatLongAlt;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.connection.ConnectionParameter;
import com.o3dr.services.android.lib.drone.connection.ConnectionResult;

import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;
import com.o3dr.services.android.lib.drone.connection.ConnectionType;

import com.o3dr.services.android.lib.drone.property.Altitude;
import com.o3dr.services.android.lib.drone.property.Gps;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;
import android.view.ViewGroup.LayoutParams;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;


import java.util.HashMap;
import java.util.Queue;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;


public class MainActivity extends ActionBarActivity implements DroneListener,TowerListener {

    //controls phone sensors
    private SensorTracker mSensor;
    //queue for pictures to uploader
    private static ArrayList<String> pictureQueue = new ArrayList<>();
    //queue for picture data to upload
    private static ArrayList<Data>  pictureData = new ArrayList<>();
    //for drone communication
    private static Drone drone;
    //thread handler
    private final Handler handler = new Handler();
    //for 3dr tower
    private ControlTower controlTower;
    //for storage of pictures
    private File StoragePic;
    //for storage of logs
    private File logFile;
    //current picture
    private int picNum;
    //camera
    Camera mCamera;



    //upload thread
    private ServerContactThread uThread = null;
    //camera trigger thread
    private CameraTakerThread tThread = null;
    //url of server
    private String URL = null;
    //for writing to log files
    private FileWriter wrt =null;
    private BufferedWriter logOut = null;
    //picture directory on phone
    File picDir;

    //baud rate
    private final static int DEFAULT_USB_BAUD_RATE = 57600;
    //id of phone
    private String android_id;
    //for picture surface
    private SurfaceView sf;
    private SurfaceHolder sH;
    //for handling camera callbacks
    private static CameraHandlerThread handlerCamerathread = null;

    private boolean didPause = false;
    private Thread uploader;
    private  Thread connect;
    private final long DEFAULT_TRIGGER_TIME = 500;
    private final long CONNECTION_DELAY = 3000;

    //allows for syncing on boolean's for deciding when to break loops
    private class BooleanObj{

        private boolean on;

        public BooleanObj(boolean def){
            this.on = def;

        }

        public void set(boolean val){
            this.on = val;
        }
        public boolean get(){
            return this.on;
        }
    }
    //continue triggering?
    private final BooleanObj triggerOn = new BooleanObj(false);
    //continue connecting?
    private final BooleanObj connectOn = new BooleanObj(false);

    //for testing
    private String token;
    private long expiration =0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {



        //settings up windows
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);


        //Here is the important stuff
        super.onCreate(savedInstanceState);

        //set layout
        setContentView(R.layout.activity_main);

        //this is the android devices, id used as cache key in django
        android_id=Settings.Secure.getString(getApplicationContext().getContentResolver(),
                Settings.Secure.ANDROID_ID);

        final Context context = getApplicationContext();
        //get the drone
        drone = new Drone(context);

        //initialize picnum
        picNum = 0;

        //get the sd card
        File sdCard = Environment.getExternalStorageDirectory();
        //create the pic storage directory
        picDir = new File(sdCard.toString() + "/picStorage");

        //create pic storage directory
        try {
            //if directory doesn't exist, make it
            if (!picDir.exists()) {
                if(!picDir.mkdirs()){
                    alertUser("Storage creation Failed. Exiting");
                    System.exit(1);
                }
            }
            StoragePic = picDir;

        } catch (SecurityException e) {
            alertUser("Storage creation failed. Exiting");
            System.exit(1);

        }

        //used for setting current time log file was made
        Calendar cal = Calendar.getInstance(TimeZone.getDefault());
        String dateTime = cal.getTime().toLocaleString();

        //create the pic logs directrory
        File logDir = new File(sdCard.toString() + "/PicLogs");

        try {

            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            //create new log file
            logFile = new File(logDir, "logs " + dateTime + ".txt");

        } catch (SecurityException e) {
            alertUser("Storage creation failed. Exiting");
            System.exit(1);
        }

        //initialize sensor controller
        mSensor = new SensorTracker(getApplicationContext());
        mSensor.startSensors();


        //3dr control tower
        controlTower = new ControlTower(getApplicationContext());

        final EditText ipText = (EditText)findViewById(R.id.URL);
        //make keyboard disappear at enter
        ipText.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int i, KeyEvent keyEvent) {
                //on enter
                if ((keyEvent.getAction() == KeyEvent.ACTION_DOWN) && i == KeyEvent.KEYCODE_ENTER) {
                    InputMethodManager mgr = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    //hide the keyboard
                    mgr.hideSoftInputFromWindow(ipText.getWindowToken(), 0);
                    return true;
                }
                return false;
            }
        });

        //initialize camera callback thread
        handlerCamerathread = new CameraHandlerThread();

        //initialize surface
        sf = (SurfaceView)findViewById(R.id.surfaceView);
        //get surface holder
        sH = sf.getHolder();
        sH.setKeepScreenOn(true);
        //surface control callbacks
        sH.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {

                if(!didPause) {
                    //open camera lock on it
                    synchronized (handlerCamerathread) {
                        handlerCamerathread.openCamera();
                    }
                    //start up camera viewers
                    try {
                        if (mCamera != null) {
                            mCamera.setPreviewDisplay(holder);
                            mCamera.startPreview();
                        }

                    } catch (IOException e) {
                        Log.e("surfaceCreate", e.toString());
                    }
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                //nothing
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                didPause = true;
            }
        });

    }




    @Override
    protected void onResume() {
        super.onResume();
        //app was zoomed out. Can't recover from this...just start over
        //in practice this will never happen as long as phone is sleeping
        if(didPause){ alertUser("App was paused. Please close and restart");}
        //connect to tower
        this.controlTower.connect(this);
        //create the uploader thread
        if(uThread == null){
            uThread = new ServerContactThread();
        }
        if(tThread == null){
            //create pic taker thread
            tThread = new CameraTakerThread();
        }
        if(wrt==null) {
            //for writing to the log file
            try {
                wrt = new FileWriter(logFile);
            } catch (IOException e) {
                Log.e("onResume", "FileWriter failed");
            }

            logOut = new BufferedWriter(wrt);
        }


    }

    @Override
    public void onPause(){
        super.onPause();

        //Nothing...phone might have went to sleep...
    }

    @Override
    public void onStop() {
        super.onStop();

        //Nothing...phone might have went to sleep...


    }

    //app stopped
    @Override
    public void onDestroy(){
        Log.i("Destroyed","Destroyed");
        super.onDestroy();

        //stop loops and clean up

        synchronized (triggerOn){
            triggerOn.set(false);
        }

        synchronized (connectOn){
            connectOn.set(false);
        }


        try {
            if(logOut!=null){
                logOut.close();
            }
        }
        catch(IOException e){

        }
        //close camera
        synchronized (handlerCamerathread) {
            handlerCamerathread.closeCamera();
        }
        //stop drone connection
        this.controlTower.disconnect();
        if (this.drone.isConnected()) {
            this.drone.disconnect();
            updateConnectedButton(false);
        }
        //tell it to stop taking pics
        if(mSensor!=null){
            mSensor.stopSensors();
        }


    }


    //3dr tower connection
    @Override
    public void onTowerConnected() {
        alertUser("3DR Services Connected");
        this.controlTower.registerDrone(drone, this.handler);
        drone.registerDroneListener(this);
    }
    //disconnection for 3dr tower
    @Override
    public void onTowerDisconnected() {
        alertUser("3DR Service Interrupted");
    }

    //drone connection error handling
    @Override
    public void onDroneConnectionFailed(ConnectionResult result) {
        alertUser("Connection Failed:" + result.getErrorMessage());
    }

    @Override
    public void onDroneServiceInterrupted(String errorMsg) {
        alertUser(errorMsg);

    }


    /*
    Thread for opening camera and running camera callbacks
     */
    private class CameraHandlerThread extends HandlerThread {
        Handler  mHandler =  null;
        String curTime;
        Data imageData;

        CameraHandlerThread(){
            super("CamerHandlerThread");
            start();
            mHandler = new Handler(getLooper());

        }
        //notfy when camera has been opened
        synchronized void notifyCameraReady(){
            notify();
        }

        //allows for communication of picture data between callbacks
        synchronized void  setPictureData(String curTime,Data imageData){
            this.curTime = curTime;
            this.imageData=imageData;
        }

        //close camera after done
        void closeCamera(){
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    //release camera resources
                    if(mCamera!=null){
                        try {
                            mCamera.release();
                        }
                        catch(RuntimeException e){
                            Log.e("CameraHandler","Fail on camera release");
                        }
                    }
                }
            });


        }
        //open up camera for triggering use
        void openCamera(){
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    try{
                        //open camera and set params
                        mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
                        Camera.Parameters params = mCamera.getParameters();
                        params.setPictureSize(300,300);
                        params.setRotation(90);
                    }catch(RuntimeException e){
                        alertUser("Failed to open camera");

                        Log.e("Camera Failed", "failed to open back camera", e);
                    }
                    //camera us read to use...notify waiting threads
                    notifyCameraReady();
                }
            });
            //wait for camera to be opened...main thread(UI) is waiting
            try{
                synchronized (handlerCamerathread) {
                    handlerCamerathread.wait();
                }
            }
            catch(InterruptedException e){
                Log.w("Wait Failed", "wait was interupted");

            }

        }

    }


    /*
    Thread for taking pictures in fixed interval
     */
    public class CameraTakerThread extends HandlerThread{
        Handler mHandler = null;
        double captureTime =0.0;


        CameraTakerThread(){
            super("CamerTakerThread");
            start();
            mHandler = new Handler(getLooper());
        }

        //setter for telling thread what time to take pictures at
        void setCapture(double time){
            captureTime = time;
        }

        //tell thread to use smarttrigger
        void smartTrigger(){
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    //smart Trigger code

                }
            });

        }
        //tell thread to start capturing
        void capture(){
            mHandler.post(new Runnable() {
                @Override
                public void run() {

                    Double timeNum = captureTime;
                    //capture looop
                    while (true) {
                        //if told to stop triggering
                        synchronized (triggerOn){
                            //break loop
                            if(!triggerOn.get()){
                                break;
                            }
                        }
                        //time at start of loop
                        long time = System.currentTimeMillis();
                        //while phon is rotated pas 30 degrees on any access
                        while(Math.abs(mSensor.getPitch())>30 || Math.abs(mSensor.getRoll())>30){
                            //suspend taking pics
                            try {
                                Thread.sleep(1000);
                            }
                            catch(InterruptedException e){

                            }
                        }
                        //if camera exists
                        if (mCamera != null) {
                            //take pics
                            mCamera.startPreview();
                            mCamera.takePicture(onShutter, null, onPicTake);
                        }
                        //wait given time interval
                        try {
                            //wait till callbacks for last taken pic are done
                            //allows for synchronization among threads
                            synchronized (handlerCamerathread) {
                                handlerCamerathread.wait();

                            }
                            //time at end of loop
                            long time2 = System.currentTimeMillis();
                            //necessary delay
                            double timeDelta = (timeNum*1000 - (time2 - time));

                            Log.i("BOTTLEKNECK",time2-time+"");
                            Log.i("BOTTLEKNECK+Delta",timeDelta+(time2-time)+"");
                            //wait the delay
                            if(timeDelta>=0) {
                                Thread.sleep((long) (timeDelta));
                            }
                            else{
                                //almost never gets executed...to stop crashes
                                Thread.sleep(DEFAULT_TRIGGER_TIME);
                            }

                            Log.i("BOTTLEKNECK+Delay", System.currentTimeMillis() - time + "");
                        } catch (InterruptedException e) {
                            Log.e("CameraHandler",e.toString());
                        }
                    }
                }
            });
        }
    }

    /*
    Thread that contacts server and parses server responses
     */
    private class ServerContactThread extends HandlerThread{

        Handler mHandler = null;


        ServerContactThread(){
            super("ServerContact");
            start();
            mHandler = new Handler(getLooper());
        }
        //allows to obtain new token if old is about to expire
        private void refresh(){
            //get current system time
            long unixTime = System.currentTimeMillis()/1000;
            Log.i("delta",expiration-unixTime+"");
            //if token expiration is close
            if(expiration-unixTime<=3560) {

                try {
                    //request a new one
                    URL url = new URL("http://" + URL + "/drone/refresh");
                    HttpURLConnection con = (HttpURLConnection) url.openConnection();
                    con.setRequestMethod("POST");
                    //send old one
                    JSONObject requestData = new JSONObject();
                    requestData.put("token",token);

                    con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    con.setDoOutput(true);
                    con.setDoInput(true);
                    con.setUseCaches(false);

                    OutputStream osC = con.getOutputStream();
                    OutputStreamWriter osW = new OutputStreamWriter(osC, "UTF-8");
                    osW.write(requestData.toString());
                    osW.flush();
                    osW.close();


                    int status = con.getResponseCode();

                    switch (status){

                        case 200:
                            //obtain response of new token
                            JSONObject response = new JSONEncoder(con.getInputStream()).encodeJSON();

                            token = response.getString("token");
                            //parse expiration date out
                            String[] token_split = token.split("\\.");

                            String token_decode = new String(Base64.decode(token_split[1].getBytes(), Base64.DEFAULT), "UTF-8");
                            JSONObject payload = new JSONObject(token_decode);
                            expiration=Long.parseLong(payload.getString("exp"));
                            break;
                        default:
                            Log.e("Error Response","Status" +status);
                            break;

                    }
                    con.disconnect();



                } catch (JSONException e) {
                    e.printStackTrace();
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (ProtocolException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }

        }

        //login to obtain auth token
        void login(){
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        //login to obtain token
                        URL url = new URL("http://" + URL + "/drone/login");
                        HttpURLConnection con = (HttpURLConnection) url.openConnection();
                        con.setRequestMethod("POST");
                        //send json username and password
                        JSONObject requestData = new JSONObject();
                        requestData.put("username",((EditText)findViewById(R.id.username)).getText().toString());
                        requestData.put("password",((EditText)findViewById(R.id.password)).getText().toString());

                        con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                        con.setDoInput(true);
                        con.setDoOutput(true);
                        con.setUseCaches(false);

                        OutputStream osC = con.getOutputStream();
                        OutputStreamWriter osW = new OutputStreamWriter(osC, "UTF-8");
                        osW.write(requestData.toString());
                        osW.flush();
                        osW.close();


                        int status = con.getResponseCode();

                        switch (status){
                            case 200:
                                //get token response
                                JSONObject response = new JSONEncoder(con.getInputStream()).encodeJSON();
                                token = response.getString("token");
                                //parse out expiration
                                String[] token_split = token.split("\\.");

                                String token_decode = new String(Base64.decode(token_split[1].getBytes(), Base64.DEFAULT), "UTF-8");
                                JSONObject payload = new JSONObject(token_decode);
                                expiration=Long.parseLong(payload.getString("exp"));

                                break;
                            default:
                                Log.e("Error Response","Status"+status);
                                break;
                        }
                        con.disconnect();
                        synchronized (uThread){
                            uThread.notify();
                        }


                    }
                    catch (JSONException e){
                        e.printStackTrace();
                    }
                    catch (MalformedURLException e){
                        e.printStackTrace();
                    }
                    catch (ProtocolException e){
                        e.printStackTrace();
                    }
                    catch (IOException e){
                        e.printStackTrace();
                    }

                }


            });

        }

        //send heartbeat and if ready, send picture
        void contactLoop(){

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        //check if refresh is necessary before posting
                        refresh();
                        //open connection to server
                        URL url = new URL("http://" + URL + "/drone/serverContact");
                        HttpURLConnection con = (HttpURLConnection) url.openConnection();
                        con.setRequestMethod("POST");
                        con.setRequestProperty("Authorization","JWT "+token);

                        //build JSON request picture data
                        JSONObject requestData = new JSONObject();
                        //we are asking to trigger
                        requestData.put("id", android_id);
                        requestData.put("timeCache", getTime());
                        requestData.put("status",triggerOn.get()+"");
                        //attempt to check for an image and send it
                        try{
                            Data imageData;
                            String fileName;
                            //get next image data
                            synchronized (pictureData) {
                                imageData= pictureData.remove(0);
                            }
                            //get next image file name
                            synchronized (pictureQueue) {
                                fileName = pictureQueue.remove(0);
                            }
                            //put image data in json
                            if (imageData != null) {
                                requestData.put("azimuth", imageData.getAzimuth());
                                requestData.put("pitch", imageData.getPitch());
                                requestData.put("roll", imageData.getRoll());
                                requestData.put("lat",imageData.getLat());
                                requestData.put("lon",imageData.getLon());
                                requestData.put("alt",imageData.getAlt());
                            }
                            //fetch image from fs
                            File outFile = new File(StoragePic,fileName);
                            FileInputStream fin = new FileInputStream(outFile);
                            DataInputStream dis = new DataInputStream(fin);
                            //read image at given string
                            byte fileContent[] = new byte[(int)outFile.length()];
                            dis.readFully(fileContent);

                            //dispositions
                            String jsonDisp = "Content-Disposition: form-data; name=\"jsonData\"";
                            String jsonType = "Content-Type: application/json; charset=UTF-8";

                            String fileDisp = "Content-Disposition: form-data; name=\"Picture\"; filename=\"" + fileName + "\"";
                            String fileType = "Content-Type: image/jpeg";

                            String LINE_FEED = "\r\n";

                            //content type
                            String boundary = "===" + System.currentTimeMillis() + "===";
                            con.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                            con.setRequestProperty("ENCTYPE", "multipart/form-data");
                            //add image name
                            con.setRequestProperty("image", fileName);
                            //might wanna not keep-alive
                            con.setRequestProperty("Connection", "Keep-Aive");
                            //we are outputing
                            con.setDoOutput(true);
                            //open connection
                            con.connect();

                            OutputStream out = con.getOutputStream();

                            //write image and image data
                            PrintWriter wrt = new PrintWriter(new OutputStreamWriter(out), true);

                            wrt.append("--" + boundary).append(LINE_FEED);
                            wrt.append(fileDisp).append(LINE_FEED);
                            wrt.append(fileType).append(LINE_FEED);

                            wrt.append(LINE_FEED);
                            wrt.flush();

                            out.write(fileContent);
                            out.flush();

                            wrt.append(LINE_FEED);
                            wrt.flush();

                            //write json to request
                            wrt.append("--" + boundary).append(LINE_FEED);
                            wrt.append(jsonDisp).append(LINE_FEED);
                            wrt.append(jsonType).append(LINE_FEED);
                            wrt.append(LINE_FEED);
                            wrt.append(requestData.toString());
                            wrt.append(LINE_FEED);
                            wrt.flush();


                            switch (con.getResponseCode()){


                            }


                        }
                        //no image to send, just send heartbeat
                        catch (IndexOutOfBoundsException e){

                            con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                            con.setDoOutput(true);
                            con.setUseCaches(false);
                            con.setDoInput(true);
                            con.connect();
                            OutputStream osC = con.getOutputStream();
                            OutputStreamWriter osW = new OutputStreamWriter(osC, "UTF-8");
                            osW.write(requestData.toString());
                            osW.flush();
                            osW.close();
                        }
                        int status = con.getResponseCode();

                        switch (status){

                            case 200:
                                //encode response as a jsonobject
                                JSONObject response = new JSONEncoder(con.getInputStream()).encodeJSON();
                                //if the gcs said to trigger
                                if(response.has("time")){
                                    //tell capture thread to start triggering at 'time'
                                    synchronized (tThread){
                                        tThread.setCapture(Double.parseDouble(response.get("time").toString()));
                                        //start triggering
                                        synchronized (triggerOn){
                                            triggerOn.set(true);
                                        }
                                        tThread.capture();
                                    }

                                }
                                //if gcs said to stop triggering
                                else if(response.has("STOP")){
                                        //end trigger loop
                                        synchronized (triggerOn){
                                            triggerOn.set(false);
                                        }
                                }
                                //just a normal response
                                else if(response.has("NOINFO")){
                                        //do nothing
                                }

                                break;
                            default:
                               Log.e("Error Response", "Received" + status);
                                break;

                        }

                        con.disconnect();

                    }
                    catch (JSONException e){
                        e.printStackTrace();
                    }
                    catch(MalformedURLException e){
                        e.printStackTrace();
                    }
                    catch (ProtocolException e){
                        e.printStackTrace();
                    }
                    catch (IOException e){
                        e.printStackTrace();
                    }

                }
            });

        }




    }


    //for holding image data
    private class Data{
        private float azimuth=0;
        private float pitch=0;
        private float roll=0;
        private double lat=0;
        private double lon=0;
        private double alt=0;
        //private LatLongAlt gpsData = null;



        Data(){
            //fetch data from sensors
            this.azimuth =mSensor.getAzimuth();
            this.pitch = -1*mSensor.getPitch();
            this.roll = -1*mSensor.getRoll();
            LatLongAlt gps = retLatLonAlt();
            if(gps!=null){
                lat = gps.getLatitude();
                lon = gps.getLongitude();
                alt = gps.getAltitude();
            }

        }
        //getter methods to obtain data
        float getAzimuth(){
            return azimuth;
        }
        float getPitch(){
            return pitch;
        }
        float getRoll(){
            return roll;
        }
        double getLat(){return lat;}
        double getLon(){return lon;}
        double getAlt(){return alt;}


    }

    //shutter callback
    Camera.ShutterCallback onShutter=new Camera.ShutterCallback()

    {
        @Override
        public void onShutter () {
            String time = getTime();
            //make shutter sound
            AudioManager mgr = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            mgr.playSoundEffect(AudioManager.FLAG_PLAY_SOUND);
            //get image data
            Data dataHolder = new Data();
            //send it so picture callback can get it
            synchronized (handlerCamerathread) {
                handlerCamerathread.setPictureData(time, dataHolder);
            }
            //create log file
            try {
                String logData;
                //log entry
                logData = time+picNum + "Lat:"+dataHolder.getLat()+"Lon:"+dataHolder.getLon()+"Alt:"+dataHolder.getAlt()+" Pitch:"+dataHolder.getPitch()+" Roll:"+dataHolder.getRoll()+" Azimuth:"+dataHolder.getAzimuth();
                //write to log
                logOut.write(logData);
                logOut.newLine();
            }
            catch (FileNotFoundException e){
                Log.e("ShutterFile",e.toString());

            }
            catch (IOException e){
                Log.e("Shutter",e.toString());

            }




        }
    };


    private void refreshGallery(File file) {
        Intent mediaScanIntent = new Intent( Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        mediaScanIntent.setData(Uri.fromFile(file));
        sendBroadcast(mediaScanIntent);
    }

    //callback for saved jpeg
    PictureCallback onPicTake=new PictureCallback() {


        @Override
        public void onPictureTaken ( byte[] bytes, Camera camera) {

            String fileName;
            synchronized (handlerCamerathread) {
                //create picture filname
               fileName= String.format(handlerCamerathread.curTime + "%04d.jpg", ++picNum);
                //add image data to queue
                synchronized (pictureData) {
                    pictureData.add(handlerCamerathread.imageData);
                    handlerCamerathread.imageData = null;
                }
            }
            try{
            //write image to disk
            File outFile = new File(StoragePic, fileName);
            FileOutputStream outStream = new FileOutputStream(outFile);
            outStream.write(bytes);
            outStream.close();
            refreshGallery(outFile);
            }
            catch (IOException e){
                Log.e("pictake",e.toString());
            }
            //image file name to queue
            synchronized (pictureQueue) {
                pictureQueue.add(fileName);
            }

            //tell picture capture thread we are done
            //it can take the next picture
            synchronized (handlerCamerathread) {
                handlerCamerathread.notify();
            }

        }




    };


    //update connect button upon drone connection
    protected void updateConnectedButton(Boolean isConnected) {

        Button connectButton = (Button)findViewById(R.id.btnconnect);

        connectButton.setText(isConnected ? "AP Disconnect" : "AP Connect");

    }

    //connect to drone
    public void onBtnConnectTap(View view) {

        connect= new Thread(new Runnable() {
            @Override
            public void run() {
                if (drone.isConnected()) {
                    drone.disconnect();
                } else {
                    Bundle extraParams = new Bundle();
                    extraParams.putInt(ConnectionType.EXTRA_USB_BAUD_RATE, DEFAULT_USB_BAUD_RATE); // Set default baud rate to 57600
                    //connect with usb
                    ConnectionParameter connectionParams = new ConnectionParameter(ConnectionType.TYPE_USB, extraParams, null);
                    drone.connect(connectionParams);

                }
                synchronized (this) {
                    this.notify();
                }
            }
        });

        connect.start();

        try{
            synchronized (connect) {
                connect.wait();
                //connect = null;
            }
        }
        catch (InterruptedException e){
            Log.e("Drone Conection", "error on wait");
        }
        ;
        updateConnectedButton(drone.isConnected());

    }


    //set up remote GCS commands for trigger and connect to drone
    public void remoteCommunications(View view){

        //new RemoteThread().startThread();
        EditText ed = (EditText) findViewById(R.id.URL);
        URL = ed.getText().toString()+":2000";

        if(URL.equals("")){
            URL = "192.168.2.1:2000";
            ed.setText(URL,TextView.BufferType.EDITABLE);
            alertUser("Using Default IP:PORT");
        }
        //start remote connections
        //create uploader
      uploader = new Thread(new Runnable() {
          @Override
          public void run() {

              uThread.login();

              synchronized (uThread){
                  try {
                      uThread.wait();
                  }
                  catch (InterruptedException e){
                      e.printStackTrace();
                  }
              }

              synchronized (connectOn){
                  connectOn.set(true);
              }

              if(uThread!=null){

                  while(true){

                      synchronized (connectOn){
                          if(!connectOn.get()){
                              break;
                          }
                      }
                      synchronized (uThread) {
                          uThread.contactLoop();
                      }
                      try {
                          Thread.sleep(CONNECTION_DELAY);
                      }

                      catch (InterruptedException e){
                          Log.e("HeartBeatLoop",e.toString());
                      }

                  }
              }

          }
      });
      uploader.start();



    }



    //get lat/lon/alt of image
    @Nullable
    private LatLongAlt retLatLonAlt() {
        //get altitude
        Altitude droneAltitude = drone.getAttribute(AttributeType.ALTITUDE);
        if(droneAltitude==null){
            alertUser("No altitude");
            return null;
        }
        //get the altitude
        final double vehicleAltitude = droneAltitude.getAltitude();

        //get positon
        Gps droneGps = drone.getAttribute(AttributeType.GPS);

        if(droneGps==null){

            return null;
        }
        //get lon and lat
        LatLong vehiclePosition = droneGps.getPosition();

        if(vehiclePosition==null){

            return null;
        }

        //get actual lat/lon
        final double latNum =vehiclePosition.getLatitude();
        final double lonNum = vehiclePosition.getLongitude();


        return new LatLongAlt(vehicleAltitude, latNum, lonNum);
    }




    private String getTime(){
        long millis =System.currentTimeMillis();
        String hms = String.format("%02d:%02d:%02d", TimeUnit.MILLISECONDS.toHours(millis),
                TimeUnit.MILLISECONDS.toMinutes(millis) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(millis)),
                TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis)));
        return  hms;
    }

    //used for aleting user of messages
    protected void alertUser(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }



    // Drone Listener
    // ==========================================================
    //does this do anything?

    @Override
    public void onDroneEvent(String event, Bundle extras) {

        switch (event) {

            case AttributeEvent.STATE_CONNECTED:
                alertUser("Drone Connected");
                updateConnectedButton(this.drone.isConnected());

                break;

            case AttributeEvent.STATE_DISCONNECTED:
                alertUser("Drone Disconnected");
                updateConnectedButton(this.drone.isConnected());
                break;

            default:
//                Log.i("DRONE_EVENT", event); //Uncomment to see events from the drone
                break;
        }

    }


}
