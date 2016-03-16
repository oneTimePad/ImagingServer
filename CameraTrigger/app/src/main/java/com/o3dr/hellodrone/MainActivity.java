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
    private boolean connectLoop = false;
    //continue take pics?
    private static boolean on =false;
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
    //continue to send pics?
    private static boolean stop = false;
    //baud rate
    private final static int DEFAULT_USB_BAUD_RATE = 57600;
    //id of phone
    private String android_id;
    //for picture surface
    private SurfaceView sf;
    private SurfaceHolder sH;
    //for handling camera callbacks
    private static CameraHandlerThread handlerCamerathread = null;

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
                //open camera lock on it
                synchronized (handlerCamerathread) {
                    handlerCamerathread.openCamera();
                }
                //start up camera viewers
                try{
                    if(mCamera!=null){
                        mCamera.setPreviewDisplay(holder);
                        mCamera.startPreview();
                    }

                }
                catch(IOException e){
                    Log.e("surfaceCreate",e.toString());
                }

            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                //nothing
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                //close camera
                synchronized (handlerCamerathread) {
                    handlerCamerathread.closeCamera();
                }
            }
        });

    }




    @Override
    protected void onResume() {
        super.onResume();

        //create the uploader thread
        if(uThread == null){
            uThread = new ServerContactThread();
        }


        //create pic taker thread
        tThread = new CameraTakerThread();
        //connect to tower
        this.controlTower.connect(this);
        //for maeking interval input keyboard dissapear


        //for writing to the log file
        try {
            wrt = new FileWriter(logFile);
        }
        catch(IOException e){
            Log.e("onResume","FileWriter failed");
        }

        logOut = new BufferedWriter(wrt);

        if(mSensor!=null) {
            mSensor.startSensors();
        }

    }



    @Override
    public void onStop() {
        super.onStop();

        on = false;
        stop = false;




        connectLoop = false;


        try {
            if(logOut!=null){
                logOut.close();
            }
        }
        catch(IOException e){

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

    @Override
    public void onDestroy(){
        Log.i("Destroyed","Destroyed");
        super.onDestroy();
        //gcs.release();
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


    //thread for opening camera and running callbacks
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

        synchronized void  setPictureData(String curTime,Data imageData){
            this.curTime = curTime;
            this.imageData=imageData;
        }



        void closeCamera(){
            mHandler.post(new Runnable() {
                @Override
                public void run() {
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

        void openCamera(){
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    try{



                        mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
                        Camera.Parameters params = mCamera.getParameters();
                        params.setPictureSize(300,300);
                        params.setRotation(90);
                    }catch(RuntimeException e){
                        alertUser("Failed to open camera");

                        Log.e("Camera Failed", "failed to open back camera", e);
                    }
                    notifyCameraReady();
                }
            });
            //wait for camera to be opened
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


    //Thread for triggering camera
    public class CameraTakerThread extends HandlerThread{
        Handler mHandler = null;
        double captureTime =0.0;


        CameraTakerThread(){
            super("CamerTakerThread");
            start();
            mHandler = new Handler(getLooper());
        }


        void setCapture(double time){
            captureTime = time;
        }
        void smartTrigger(){
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    //smart Trigger code

                }
            });

        }

        void capture(){
            mHandler.post(new Runnable() {
                @Override
                public void run() {

                    Double timeNum = captureTime;

                    //continue taking pics
                    on = true;

                    while (on) {
                        long time = System.currentTimeMillis();



                        while(Math.abs(mSensor.getPitch())>30 || Math.abs(mSensor.getRoll())>30){
                            try {
                                Thread.sleep(1000);
                            }
                            catch(InterruptedException e){

                            }
                        }
                        if (mCamera != null) {
                            //take pics

                            mCamera.startPreview();

                            mCamera.takePicture(onShutter, null, onPicTake);
                        }
                        //wait given time interval
                        try {

                            synchronized (handlerCamerathread) {
                                handlerCamerathread.wait();


                            }
                            long time2 = System.currentTimeMillis();
                            double timeDelta = (timeNum*1000 - (time2 - time));
                            Log.i("BOTTLEKNECK",time2-time+"");
                            Log.i("BOTTLEKNECK+Delta",timeDelta+"");
                            //compute the correct time
                            Thread.sleep((long) (timeDelta));

                            Log.i("BOTTLEKNECK+Delay", System.currentTimeMillis() - time + "");
                        } catch (InterruptedException e) {
                            Log.e("CameraHandler",e.toString());
                        }
                    }
                }
            });
        }
    }


    private class ServerContactThread extends HandlerThread{

        Handler mHandler = null;


        ServerContactThread(){
            super("ServerContact");
            start();
            mHandler = new Handler(getLooper());
        }



        void sendPicture(final String fileName, final Data imageData){
            mHandler.post(new Runnable() {
                @Override
                public void run() {

                    try {

                        if(stop){
                            mHandler.removeCallbacksAndMessages(null);
                        }
                        File outFile = new File(StoragePic,fileName);
                        FileInputStream fin = new FileInputStream(outFile);
                        DataInputStream dis = new DataInputStream(fin);

                        byte fileContent[] = new byte[(int)outFile.length()];
                        dis.readFully(fileContent);



                        JSONObject requestData = new JSONObject();
                        requestData.put("fileName", fileName);

                        if (imageData != null) {
                            requestData.put("Azimuth", imageData.getAzimuth());
                            requestData.put("Pitch", imageData.getPitch());
                            requestData.put("Roll", imageData.getRoll());
                            requestData.put("GPS", imageData.getLatLonAltJSON());
                        }


                        URL url = new URL("http://" + URL + "/droid/upload");
                        HttpURLConnection con = (HttpURLConnection) url.openConnection();
                        con.setRequestMethod("POST");


                        String jsonDisp = "Content-Disposition: form-data; name=\"jsonData\"";
                        String jsonType = "Content-Type: application/json; charset=UTF-8";

                        String fileDisp = "Content-Disposition: form-data; name=\"Picture\"; filename=\"" + fileName + "\"";
                        String fileType = "Content-Type: image/jpeg";

                        String LINE_FEED = "\r\n";

                        String boundary = "===" + System.currentTimeMillis() + "===";
                        con.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                        con.setRequestProperty("ENCTYPE", "multipart/form-data");

                        con.setRequestProperty("image", fileName);
                        con.setRequestProperty("Connection", "Keep-Aive");

                        con.setDoOutput(true);
                        con.connect();

                        OutputStream out = con.getOutputStream();

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


                        wrt.append("--" + boundary).append(LINE_FEED);
                        wrt.append(jsonDisp).append(LINE_FEED);
                        wrt.append(jsonType).append(LINE_FEED);
                        wrt.append(LINE_FEED);
                        wrt.append(requestData.toString());
                        wrt.append(LINE_FEED);
                        wrt.flush();


                        switch (con.getResponseCode()) {
                            case 200:
                                Log.d("200", "Success");

                                break;
                            case 500:
                                Log.e("500", "Internal Server error");
                                break;
                            case 403:
                                Log.e("403", "Forbidden");
                                break;
                            default:
                                Log.e("#", "Something else");
                        }
                        con = null;
                        requestData = null;

                    } catch (MalformedURLException e) {
                        Log.e("UploadThreadURL", e.toString());
                    } catch (JSONException e) {
                        Log.e("UploadThreadJSON", e.toString());
                    } catch (IOException e) {
                        Log.e("UploadThread", e.toString());
                    }


                }

            });
        }



        void heartbeat(){
            mHandler.post(new Runnable() {

                public void run() {


                    try {
                        //ask server what to do
                        URL url = new URL("http://" + URL + "/droid/droidtrigger");

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
                            json.put("status", "0");
                            json.put("id", android_id);
                            json.put("time", getTime());
                        } catch (JSONException e) {

                        }
                        OutputStream osC = con.getOutputStream();
                        OutputStreamWriter osW = new OutputStreamWriter(osC, "UTF-8");
                        osW.write(json.toString());
                        osW.flush();
                        osW.close();

                        int status = con.getResponseCode();

                        switch (status) {


                            case 200:
                                BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));
                                StringBuilder sb = new StringBuilder();
                                String line;
                                while ((line = br.readLine()) != null) {
                                    sb.append(line);
                                }
                                br.close();
                                //stop triggering
                                if (sb.toString().equals("NO")) {

                                    MainActivity.on = false;
                                    con.disconnect();
                                }
                                //no command
                                else if (sb.toString().equals("NOINFO")) {
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
                                            tThread.setCapture(Double.parseDouble(timeInterval));
                                            //start triggering
                                            MainActivity.on = true;

                                            tThread.capture();
                                        } else if (smartTrigger.equals("1")) {
                                            //start smart Trigger
                                            tThread.smartTrigger();
                                        }

                                        con.disconnect();
                                        //con = null;


                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                }


                        }


                    } catch (IOException e) {
                        e.printStackTrace();
                    }


                }


            });

        }



    }


    //for holding image data
    private class Data{
        private float azimuth;
        private float pitch;
        private float roll;
        private LatLongAlt gpsData = null;



        Data(){
            this.azimuth =mSensor.getAzimuth();
            this.pitch = -1*mSensor.getPitch();
            this.roll = -1*mSensor.getRoll();
            this.gpsData = getLatLonAlt();

        }

        float getAzimuth(){
            return azimuth;
        }
        float getPitch(){
            return pitch;
        }
        float getRoll(){
            return roll;
        }

        LatLongAlt retLatLonAlt(){
            return gpsData;
        }

        JSONObject getLatLonAltJSON(){
            if(gpsData !=null) {
                try {
                    return new JSONObject().put("lat", gpsData.getLatitude()).put("lon", gpsData.getLongitude()).put("alt", gpsData.getAltitude());
                } catch (JSONException e) {
                    Log.e("Dataholder",e.toString());
                }

            }

            return null;
        }

    }

    //shutter callback
    Camera.ShutterCallback onShutter=new Camera.ShutterCallback()

    {
        @Override
        public void onShutter () {

            //make shutter sound
            AudioManager mgr = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            mgr.playSoundEffect(AudioManager.FLAG_PLAY_SOUND);

            Data dataHolder = new Data();
            String time = getTime();
            synchronized (handlerCamerathread) {
                handlerCamerathread.setPictureData(time, dataHolder);
            }
            //create log file
            try {
                String logData;
                if(dataHolder.retLatLonAlt()!=null) {
                    logData = time+picNum + " GPS:" + dataHolder.retLatLonAlt().toString()+" Pitch:"+dataHolder.getPitch()+" Roll:"+dataHolder.getRoll()+" Azimuth:"+dataHolder.getAzimuth();

                }
                else{
                    logData = time+picNum+" "+"GPS NOT CONNECTED"+" Pitch:"+dataHolder.getPitch()+" Roll:"+dataHolder.getRoll()+" Azimuth:"+dataHolder.getAzimuth();
                }
                logOut.write(logData);
                logOut.newLine();
            }
            catch (FileNotFoundException e){
                Log.e("ShutterFile",e.toString());

            }
            catch (IOException e){
                Log.e("Shutter",e.toString());

            }

            final double pitch = dataHolder.getPitch();
            final double roll  = dataHolder.getRoll();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    TextView pitche = (TextView) findViewById(R.id.pitche);
                    TextView rolle = (TextView) findViewById(R.id.rolle);

//                    pitche.setText(String.format("%.2f",pitch));
                    rolle.setText(String.format("%.2f", roll));


                }
            });


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
               fileName= String.format(handlerCamerathread.curTime + "%04d.jpg", ++picNum);
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
            synchronized (pictureQueue) {
                pictureQueue.add(fileName);
            }


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

        Thread connect= new Thread(new Runnable() {
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
        URL = ed.getText().toString();

        if(URL.equals("")){
            URL = "192.168.2.1:2000";
            ed.setText(URL,TextView.BufferType.EDITABLE);
            alertUser("Using Default IP:PORT");
        }
        //start remote connections
        //create uploader
      new Thread(new Runnable() {
          @Override
          public void run() {
              connectLoop=true;
              if(uThread!=null){

                  while(connectLoop){
                      synchronized (uThread) {
                          uThread.heartbeat();
                      }
                      try {
                          Thread.sleep(3000);
                      }

                      catch (InterruptedException e){
                          Log.e("HeartBeatLoop",e.toString());
                      }

                      try{
                          Data data;
                          String image;
                          synchronized (pictureData) {
                              data = pictureData.remove(0);
                          }
                          synchronized (pictureQueue) {
                              image = pictureQueue.remove(0);
                          }

                          synchronized (uThread){
                              Log.i("send","sent");
                              uThread.sendPicture(image,data);
                          }
                      }
                      catch (IndexOutOfBoundsException e){
                          Log.i("Here","Here");
                          continue;
                      }


                  }
              }

          }
      }).start();



    }



    //get lat/lon/alt of image
    @Nullable
    private LatLongAlt getLatLonAlt() {
        //get altitude
        Altitude droneAltitude = drone.getAttribute(AttributeType.ALTITUDE);
        if(droneAltitude==null){
            alertUser("No altitude");
            return null;
        }
        //get the altitude
        final double vehicleAltitude = droneAltitude.getAltitude();
        //update viewer with altitude
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView alt = (TextView) findViewById(R.id.altnum);
                alt.setText(vehicleAltitude + "m");
            }
        });

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

        //update viewer with lat/lon
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView lat = (TextView) findViewById(R.id.latnum);
                TextView lon = (TextView) findViewById(R.id.lonnum);

                lat.setText(latNum + "deg");
                lon.setText(lonNum + "deg");
            }
        });


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
