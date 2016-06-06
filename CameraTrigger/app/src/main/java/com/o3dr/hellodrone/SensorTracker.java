package com.o3dr.hellodrone;

        import android.app.Activity;
        import android.content.Context;
        import android.hardware.Sensor;
        import android.hardware.SensorEvent;
        import android.hardware.SensorEventListener;
        import android.hardware.SensorManager;
        import android.util.Log;

        import java.io.Serializable;

public class SensorTracker extends Activity implements SensorEventListener,Serializable {
    private final Context mContext;
    private SensorManager mSensorManager;
    private Sensor gyro;
    private Sensor accel;
    private Sensor compass;
    private Sensor gravity;
    private Sensor barometer;

    private boolean gyroscopeIsAvailable;
    private boolean accelerometerIsAvailable;
    private boolean magneticFieldIsAvailable;
    private boolean gravityIsAvailable;
    private boolean barometerIsAvailable;

    private float azimuth; // Angle from magnetic north
    private float pitch; // When in portrait, tilting phone towards face
    private float roll; // When in portrait, tilting phone side to side

    private float lastX, lastY, lastZ;
    private boolean ready = false;
    private float[] accelValues = new float[3];
    private float[] compassValues = new float[3];
    private float[] inR = new float[9];
    private float[] inclineMatrix = new float[9];
    private float[] orientationValues = new float[3];
    private float[] prefValues = new float[3];
    private  float pressure;
    private float zeroPressure =0;
    private float zeroAltitude =0;
    private float zeroRoll =0;
    private float zeroPitch=0;

    private float MEAN_SEA_LEVEL =0;

    private float mInclination;
    private int counter = 0;
    private int mRotation;

    public SensorTracker(Context context) {
        this.mContext = context;
        mSensorManager = (SensorManager) mContext.getSystemService(SENSOR_SERVICE);
        getAngles();

    }

    public void getAngles() {
        try {
            getSensorManagers();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void getSensorManagers() {
        if (mSensorManager.getSensorList(Sensor.TYPE_GYROSCOPE).size() > 0) {
            Log.d("Sensors", "Gyroscope enabled");
            gyroscopeIsAvailable = true;
            gyro = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        }

        // Acceleration on device
        if (mSensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER).size() > 0) {
            Log.d("Sensors", "Accelerometer enabled");
            accelerometerIsAvailable = true;
            accel = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        // Finds magnetic north (May be uncalibrated)
        if (mSensorManager.getSensorList(Sensor.TYPE_MAGNETIC_FIELD).size() > 0) {
            Log.d("Sensors", "Magnetic Field enabled");
            magneticFieldIsAvailable = true;
            compass = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        }

        if (mSensorManager.getSensorList(Sensor.TYPE_GRAVITY).size() > 0) {
            Log.d("Sensors", "Gravity accelerometer enabled");
            gravityIsAvailable = true;
            gravity = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        }

        if(mSensorManager.getSensorList(Sensor.TYPE_PRESSURE).size()>0){
            barometerIsAvailable = true;
            barometer = mSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);

        }
    }

    public void startSensors() {
        mSensorManager.registerListener(this, gyro, mSensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(this, accel, mSensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(this, compass, mSensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(this, barometer,mSensorManager.SENSOR_DELAY_UI);
    }

    public void stopSensors() {
        mSensorManager.unregisterListener(this, gyro);
        mSensorManager.unregisterListener(this, accel);
        mSensorManager.unregisterListener(this, compass);
        mSensorManager.unregisterListener(this, barometer);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // Need to get both accelerometer and compass
        // before determine orientationValues
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER ) {
            //Log.d("Accelerometer", "Obtained values");
            for (int i = 0; i < 3; i++) {
                accelValues[i] = event.values[i];
            }
            ready = (compassValues[2] != 0) ? true : false;
        }
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            //Log.d("Magnetic Field", "Obtained values");
            for (int i = 0; i < 3; i++) {
                compassValues[i] = event.values[i];
            }
            ready = (accelValues[2] != 0) ? true : false;
        }

        if(event.sensor.getType() == Sensor.TYPE_PRESSURE){
            pressure = event.values[0];
            //Log.i("zeropressure",zeroPressure+"");
            if(zeroPressure == 0){
                zeroPressure = pressure;
            }
        }

        if (!ready) {
            return;
        }

        // To get rid of random noise
        // The vibrations of actual flight should be more than enough to overcome this
        float deltaX, deltaY, deltaZ;
        deltaX = lastX - accelValues[0];
        deltaY = lastY - accelValues[1];
        deltaZ = lastZ - accelValues[2];

        if (deltaX < 1) deltaX = 0;
        if (deltaY < 1) deltaY = 0;
        if (deltaZ < 1) deltaZ = 0;

        lastX = accelValues[0];
        lastY = accelValues[1];
        lastZ = accelValues[2];

        if (deltaX != 0 || deltaY != 0 || deltaZ != 0) {


        }

        // Move this section into the above if statement to remove noise
        boolean success = mSensorManager.getRotationMatrix(inR, inclineMatrix, accelValues, compassValues);
        if (success) {
            // Got a good rotation matrix
            //Log.d("Rotation matrix", "Success");
            mSensorManager.getOrientation(inR, prefValues); // Loads the matrix into prefValues
            doUpdate();
            mInclination = mSensorManager.getInclination(inclineMatrix);
            // Display every 10th value
            if (counter++ % 10 == 0) {
                doUpdate();
                counter = 1;
            }
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    public void doUpdate() {
        azimuth = (float) prefValues[0];
        azimuth = (float) Math.toDegrees(azimuth);
        pitch = (float) Math.toDegrees(prefValues[1]);
        roll = (float) Math.toDegrees(prefValues[2]);
    }

    public float getAzimuth() {
        return azimuth;
    }

    public float getPitch() {
        return pitch-zeroPitch;
    }

    public float getRoll() {
        return roll-zeroRoll;
    }


    //set zero altitude msl
    public void calibrateAltitude(float currentAltitude,float mslPressure){
        //if given configuration values
        if(mSensorManager!=null && currentAltitude!=-1 && mslPressure!=-1){

        }
        //else do standard configuration
        else if(mSensorManager != null) {
            zeroAltitude = mSensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE,zeroPressure)*(float)3.28084;
            Log.i("ZERO",zeroAltitude+"");
            Log.i("pres",zeroPressure+"");
            Log.i("std",SensorManager.PRESSURE_STANDARD_ATMOSPHERE+"");

        }

    }
    //set zero roll/pitch
    public void calibrateRollPitch(){
        zeroRoll = roll;
        zeroPitch = pitch;


    }


    public float getAltitude(){
        if(mSensorManager != null) {
            return mSensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE,pressure)*(float)3.28084 -zeroAltitude;
        }
        return 0;
    }

    public boolean getGyroscopeIsAvailable() {
        return gyroscopeIsAvailable;
    }

    public boolean getAccelerometerIsAvailable() {
        return accelerometerIsAvailable;
    }

    public boolean getMagneticFieldIsAvailable() {
        return magneticFieldIsAvailable;
    }
}
