package com.o3dr.dronecamera;

import java.io.IOException;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.provider.Settings;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;
import com.o3dr.dronecamera.utils.DroneTelemetry;
import com.o3dr.dronecamera.utils.ImageQueue;
import com.o3dr.dronecamera.utils.PictureStorage;
import android.view.Window;
import android.view.WindowManager;
import java.net.ConnectException;
import java.util.concurrent.TimeUnit;


public class DroneActivity extends ActionBarActivity {





    public final double SERVER_TIMEOUT = 1000;
    public final String TAG = "Main";
    public String server =null;
    private String android_id;
    private QXHandler qxHandler;
    private DroneTelemetry droneTelem;
    private PictureStorage pictureStorage;
    private ImageQueue imageQueue;
    private DroneRemoteApi droneRemoteApi;
    private CameraTriggerHThread cameraTriggerThread;
    private GroundStationHThread groundStationHThread;
    private SensorTracker mSensor;
    public static DroneApplication app;







    @Override
    protected void onCreate(Bundle savedInstanceState) {

        app = (DroneApplication)getApplication();

        app.setContext((DroneActivity)getApplicationContext());

        //settings up windows
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);


        //Here is the important stuff
        super.onCreate(savedInstanceState);

        //set layout
        setContentView(R.layout.activity_main);

        //synchronize time with ground station
        final Resources res= this.getResources();
        final int id = Resources.getSystem().getIdentifier("config_ntpServer","string","android");
        String defaultServer = res.getString(id);
        Log.i("NTP",defaultServer);


        //this is the android devices, id used as cache key in django
        android_id=Settings.Secure.getString(getApplicationContext().getContentResolver(),
                Settings.Secure.ANDROID_ID);

        app.setId(android_id);

        droneTelem = new DroneTelemetry();
        imageQueue = new ImageQueue();

        app.setDroneTelemetry(droneTelem);
        app.setImageQueue(imageQueue);
        try {
            pictureStorage = new PictureStorage();
            app.setPictureStorage(pictureStorage);
        }
        catch (IOException e){
            Log.e(TAG,e.toString()) ;
        }

        //initialize sensor controller
        mSensor = new SensorTracker(getApplicationContext());
        app.setSensorTracker(mSensor);
        mSensor.startSensors();

        if(pictureStorage!=null && server!=null){
            droneRemoteApi = new DroneRemoteApi();
            app.setDroneRemoteApi(droneRemoteApi);
        }


    }




    @Override
    protected void onResume() {
        super.onResume();



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


        findViewById(R.id.droneconnect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(droneTelem.status()){
                    droneTelem.disconnect();;

                }
                else{
                    try {
                        droneTelem.connect();
                        alertUser("Drone connection succeeded");
                    }
                    catch (ConnectException e){
                        alertUser("Drone conection failed");
                    }


                }
            }
        });

        findViewById(R.id.button_searchqx).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                    qxHandler = new QXHandler();
                    app.setQxHandler(qxHandler);
                    qxHandler.searchQx();
            }
        });

        //manual trigger
        findViewById(R.id.button_triggerqx).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final double TRIGGER_TIME = 2000;
                if(qxHandler!=null && cameraTriggerThread == null){
                    cameraTriggerThread = new CameraTriggerHThread();
                    app.setCameraTriggerHThread(cameraTriggerThread);

                }
                else if(qxHandler!=null && !cameraTriggerThread.status()){
                    cameraTriggerThread.setTriggerTime(TRIGGER_TIME);
                    cameraTriggerThread.startCapture();
                }


            }
        });


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

        //clean up drone telem with sanity checks
        if(droneTelem!= null &&droneTelem.status()){
            droneTelem.disconnect();
        }
        //cleam up qxHandler
        if(qxHandler!= null && qxHandler.status()){
            qxHandler.disconnect();
        }

        //clean up camera Trigger thread
        if(cameraTriggerThread!=null && cameraTriggerThread.status()){
            cameraTriggerThread.stopCapture();
        }

        //clean up pic storage
        if(pictureStorage!=null){
            pictureStorage.close();
        }

        //clean up sensors
        if(mSensor!=null){
            mSensor.stopSensors();
        }

        if(groundStationHThread !=null && groundStationHThread.status()){
            groundStationHThread.disconnect();
        }

        Log.i(TAG,"Destroyed");
        super.onDestroy();


    }










    //set up remote GCS commands for trigger and connect to drone
    public void remoteCommunications(View view){

        //new RemoteThread().startThread();
        EditText ed = (EditText) findViewById(R.id.URL);
        server = ed.getText().toString()+":2000";

        if(server.equals("")){
            server = "192.168.2.1:2000";
            ed.setText(server,TextView.BufferType.EDITABLE);
            alertUser("Using Default IP:PORT");
        }
        app.setServer(server);

        String username = ((EditText)findViewById(R.id.username)).getText().toString();
        String password = ((EditText)findViewById(R.id.password)).getText().toString();

        if(username.equals("") || password.equals("")){
            alertUser("Bad login");
            return;
        }

        if(groundStationHThread == null){
            groundStationHThread = new GroundStationHThread();
            groundStationHThread.setTimeout(SERVER_TIMEOUT);
            try {
                groundStationHThread.connect(username, password);
            }
            catch (ConnectException e){
                groundStationHThread = null;
                alertUser("Bad login");
                return;
            }
        }

    }




    private String getTime(){
        long millis =System.currentTimeMillis();
        String hms = String.format("%02d:%02d:%02d", TimeUnit.MILLISECONDS.toHours(millis),
                TimeUnit.MILLISECONDS.toMinutes(millis) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(millis)),
                TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis)));
        return  hms;
    }

    //used for aleting user of messages
    public void alertUser(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }



}
