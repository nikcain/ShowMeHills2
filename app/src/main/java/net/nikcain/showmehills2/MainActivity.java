package net.nikcain.showmehills2;

import android.Manifest;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.util.SizeF;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity implements IShowMeHillsActivity, SensorEventListener, View.OnTouchListener {
    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final int COARSE_LOCATION_PERMISSION_CODE = 101;
    private static final int FINE_LOCATION_PERMISSION_CODE = 102;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    public float hfov = (float) 82.5; // 50.2;
    public float vfov = (float) 40.0;
    private SensorManager mSensorManager;
    RapidGPSLock mGPS;
    Sensor accelerometer;
    Sensor magnetometer;
    float[] mGravity;
    float[] mGeomagnetic;

    boolean lockcompass = false;

    Timer timer;
    private final int GPSretryTime = 60;
    private final int CompassSmoothingWindow = 50;

    //private Location curLocation;
    String acc = "";
    boolean badsensor = false;
    double calibrationStep = -1;
    float compassAdjustment = 0;
    float[] mRotationMatrixA = new float[9];
    float[] mRotationMatrixB = new float[9];
    float mDeclination = 0;
    private boolean mHasAccurateGravity = false;
    private boolean mHasAccurateAccelerometer = false;
    public int scrwidth = 10;
    public int scrheight = 10;
    public HillCanvas hc;
    public HillDatabase myDbHelper;
    public filteredDirection fd = new filteredDirection();
    public filteredElevation fe = new filteredElevation();

    // preferences
    boolean typeunits = false; // true for metric, false for imperial
    boolean showheight = false;


    public int GetRotation()
    {
        //Display display = this.getApplicationContext().getDisplay();// context.getDisplay();
        Display display = getWindowManager().getDefaultDisplay();
        int rot = display.getRotation();
        return rot;
    }

    @Override
    protected void onResume() {
        Log.d("showmehills", "onResume");

        fd = new filteredDirection();
        fe = new filteredElevation();
        super.onResume();

        mSensorManager.registerListener((SensorEventListener) this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener((SensorEventListener) this, magnetometer, SensorManager.SENSOR_DELAY_GAME);
        mGPS.switchOn();
        if (timer != null)
        {
            timer.cancel();
            timer = null;
        }
        timer = new Timer();
        timer.scheduleAtFixedRate(new LocationTimerTask(),GPSretryTime* 1000,GPSretryTime* 1000);
        UpdateMarkers();
        myDbHelper.checkDataBase();
    }

    @Override
    protected void onPause() {
        Log.d("showmehills", "onPause");
        timer.cancel();
        timer = null;
        mGPS.switchOff();
        mSensorManager.unregisterListener((SensorEventListener) this);

        super.onPause();
        myDbHelper.close();

    }
    @Override
    protected void onStop()
    {
        mGPS.switchOff();
        if (timer != null)
        {
            timer.cancel();
            timer = null;
        }
        mSensorManager.unregisterListener((SensorEventListener)this);
        myDbHelper.close();
        super.onStop();
    }

    private void calculateFOV(CameraManager cManager) {
        try {
            for (final String cameraId : cManager.getCameraIdList()) {
                CameraCharacteristics characteristics = cManager.getCameraCharacteristics(cameraId);
                int cOrientation = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (cOrientation == CameraCharacteristics.LENS_FACING_BACK) {
                    float[] maxFocus = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                    SizeF size = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
                    assert size != null;
                    float w = size.getWidth();
                    float h = size.getHeight();
                    assert maxFocus != null;
                    hfov = (float) ((2*Math.atan(w/(maxFocus[0]*2))) * (180.0/Math.PI));
                    vfov = (float) ((2*Math.atan(h/(maxFocus[0]*2))) * (180.0/Math.PI));
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                // No errors need to be handled for this Future.
                // This should never be reached.
            }
        }, ContextCompat.getMainExecutor(this));

        checkPermission(Manifest.permission.CAMERA, CAMERA_PERMISSION_CODE);
        checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION, COARSE_LOCATION_PERMISSION_CODE);
        checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, FINE_LOCATION_PERMISSION_CODE);

        mGPS = new RapidGPSLock((IShowMeHillsActivity) this);
        mGPS.switchOn();
        mGPS.findLocation();

        if (timer != null)
        {
            timer.cancel();
            timer = null;
        }
        timer = new Timer();
        timer.scheduleAtFixedRate(new LocationTimerTask(),GPSretryTime* 1000,GPSretryTime* 1000);

        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);

        accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        myDbHelper = new HillDatabase(this, getString(R.string.dbname), getString(R.string.dbpath));
        myDbHelper.createDataBase();

        Display display = getWindowManager().getDefaultDisplay();
        scrwidth = display.getWidth();
        scrheight = display.getHeight();

        hc = findViewById(R.id.canvas_overlay);
        hc.setvars(this);
        hc.setOnTouchListener(this);
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        calculateFOV( manager );

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate menu from XML resource
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.preferences_menu, menu);

        return super.onCreateOptionsMenu(menu);
    }

    public void UpdateMarkers()
    {
        Location curLocation = mGPS.getCurrentLocation();
        if (curLocation != null)
        {
            myDbHelper.SetDirections(curLocation);
        }
    }

    class filteredDirection
    {
        double dir;
        double[] sinevalues = new double[CompassSmoothingWindow];
        double[] cosvalues = new double[CompassSmoothingWindow];
        int index = 0;
        int outlierCount = 0;

        void AddLatest( double d )
        {
            sinevalues[index] = Math.sin(d);
            cosvalues[index] = Math.cos(d);
            index++;
            if (index > CompassSmoothingWindow - 1) index = 0;
            double sumc = 0;
            double sums = 0;
            for (int a = 0; a < CompassSmoothingWindow; a++)
            {
                sumc += cosvalues[a];
                sums += sinevalues[a];
            }
            dir = Math.atan2(sums/CompassSmoothingWindow,sumc/CompassSmoothingWindow);
        }

        double getDirection()
        {
            // Allow for (possibly large) negative direction and/or compass adjustment by adding
            // two full circles before applying modulus to force a value between 0 and 360.
            return (Math.toDegrees(dir) + compassAdjustment + 720) % 360;
        }

        int GetVariation()
        {
            double Q = 0;
            double sumc = 0;
            double sums = 0;
            for (int a = 0; a < CompassSmoothingWindow; a++)
            {
                sumc += cosvalues[a];
                sums += sinevalues[a];
            }
            double avgc = sumc/CompassSmoothingWindow;
            double avgs = sums/CompassSmoothingWindow;

            sumc = 0;
            sums = 0;
            for (int a = 0; a < CompassSmoothingWindow; a++)
            {
                sumc += Math.pow(cosvalues[a] - avgc, 2);
                sums += Math.pow(sinevalues[a] - avgs, 2);
            }
            Q = (sumc/(CompassSmoothingWindow-1)) + (sums/(CompassSmoothingWindow-1));

            return (int)(Q*1000);
        }
    }

    static class filteredElevation
    {
        int AVERAGINGWINDOW = 10;
        double dir;
        double[] sinevalues = new double[AVERAGINGWINDOW];
        double[] cosvalues = new double[AVERAGINGWINDOW];
        int index = 0;
        void AddLatest( double d )
        {
            sinevalues[index] = Math.sin(d);
            cosvalues[index] = Math.cos(d);
            index++;
            if (index > AVERAGINGWINDOW - 1) index = 0;
            double sumc = 0;
            double sums = 0;
            for (int a = 0; a < AVERAGINGWINDOW; a++)
            {
                sumc += cosvalues[a];
                sums += sinevalues[a];
            }
            dir = Math.atan2(sums/AVERAGINGWINDOW,sumc/AVERAGINGWINDOW);
        }
        double getDirection() { return dir; }
    }


    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    public void onSensorChanged(SensorEvent event) {
        // some phones never set the sensormanager as reliable, even when readings are ok
        // That means if we try to block it, those phones will never get a compass reading.
        // So we let any readings through until we know we can get accurate readings. Once We know that
        // we'll block the inaccurate ones
        if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER && mHasAccurateAccelerometer) return;
            if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD && mHasAccurateGravity) return;
        }
        else
        {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) mHasAccurateAccelerometer = true;
            if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) mHasAccurateGravity = true;
        }


        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)  mGravity = event.values;
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) mGeomagnetic = event.values;

        if (mGravity != null && mGeomagnetic != null) {

            float[] rotationMatrixA = mRotationMatrixA;
            if (SensorManager.getRotationMatrix(rotationMatrixA, null, mGravity, mGeomagnetic)) {
                Matrix tmpA = new Matrix();
                tmpA.setValues(rotationMatrixA);
                tmpA.postRotate( -mDeclination );
                tmpA.getValues(rotationMatrixA);

                float[] rotationMatrixB = mRotationMatrixB;

                switch (GetRotation())
                {
                    // portrait - normal
                    case Surface.ROTATION_0: SensorManager.remapCoordinateSystem(rotationMatrixA,
                            SensorManager.AXIS_X, SensorManager.AXIS_Z,
                            rotationMatrixB);
                        break;
                    // rotated left (landscape)
                    case Surface.ROTATION_90: SensorManager.remapCoordinateSystem(rotationMatrixA,
                            //SensorManager.AXIS_Z, SensorManager.AXIS_MINUS_X,
                            SensorManager.AXIS_X, SensorManager.AXIS_Z,
                            rotationMatrixB);
                        break;
                    // upside down
                    case Surface.ROTATION_180: SensorManager.remapCoordinateSystem(rotationMatrixA,
                            SensorManager.AXIS_X, SensorManager.AXIS_Z,
                            rotationMatrixB);
                        break;
                    // rotated right (landscape)
                    case Surface.ROTATION_270: SensorManager.remapCoordinateSystem(rotationMatrixA,
                            SensorManager.AXIS_MINUS_Z, SensorManager.AXIS_X,
                            rotationMatrixB);
                        break;

                    default:  break;
                }

                float[] dv = new float[3];
                SensorManager.getOrientation(rotationMatrixB, dv);

                if (!lockcompass) {
                    fd.AddLatest(dv[0]);
                    fe.AddLatest((double) dv[1]);
                }
            }
            hc.invalidate();
        }
    }

    public boolean onTouch(View v, MotionEvent event) {
        if (event.getX() < (float) scrwidth / 8 &&
                event.getY() < (float) scrheight / 8)
        {
            openOptionsMenu();
        }
        else if (event.getX() > scrwidth - (float) scrwidth / 8 &&
                event.getY() < (float) scrheight / 8)
        {
            lockcompass = !lockcompass;
        }
    /*    else {
            Iterator<HillMarker> itr = mMarkers.iterator();
            while (itr.hasNext()) {
                HillMarker m = itr.next();
                if (m.location.contains((int)event.getX(), (int)event.getY()))
                {
                    Intent infoActivity = new Intent(getBaseContext(),HillInfo.class);
                    Bundle b = new Bundle();

                    b.putInt("key", m.hillid);

                    infoActivity.putExtras(b);
                    startActivity(infoActivity);
                }
            }
        } */
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        super.onKeyDown(keyCode, event);
        switch(keyCode)
        {
            case KeyEvent.KEYCODE_VOLUME_UP:
                compassAdjustment+= 0.1F;
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                compassAdjustment-= 0.1F;
                return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    class LocationTimerTask extends TimerTask
    {
        @Override
        public void run()
        {
            Log.d("showmehills", "renew GPS search");
            runOnUiThread(new Runnable() {
                public void run() {
                    mGPS.RenewLocation();
                }
            });
        }
    }


    public LocationManager GetLocationManager() {
        return (LocationManager)getSystemService(Context.LOCATION_SERVICE);
    }

    String distanceAsImperialOrMetric(double distance) {
        if (typeunits) return (int)distance + "m";
        else return (int)(distance*3.2808399) + "ft";
    }

    void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();
        PreviewView previewView = findViewById(R.id.previewView);
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        cameraProvider.bindToLifecycle((LifecycleOwner)this, cameraSelector, preview);
    }
    public void checkPermission(String permission, int requestCode)
    {
        // Checking if permission is not granted
        if (ContextCompat.checkSelfPermission(MainActivity.this, permission) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[] { permission }, requestCode);
        }
    }
    // This function is called when user accept or decline the permission.
// Request Code is used to check which permission called this function.
// This request code is provided when user is prompt for permission.
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_CODE) {
            // Checking whether user granted the permission or not.
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                // Showing the toast message
                Toast.makeText(MainActivity.this, "Camera Permission Granted", Toast.LENGTH_SHORT).show();
            }
            else {
                Toast.makeText(MainActivity.this, "Camera Permission Denied", Toast.LENGTH_SHORT).show();
            }
        }
        if (requestCode == COARSE_LOCATION_PERMISSION_CODE) {
        // Checking whether user granted the permission or not.
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            // Showing the toast message
            Toast.makeText(MainActivity.this, "Coarse location Permission Granted", Toast.LENGTH_SHORT).show();
        }
        else {
            Toast.makeText(MainActivity.this, "Coarse location Permission Denied", Toast.LENGTH_SHORT).show();
        }
    }

        if (requestCode == FINE_LOCATION_PERMISSION_CODE) {
            // Checking whether user granted the permission or not.
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                // Showing the toast message
                Toast.makeText(MainActivity.this, "Fine location Permission Granted", Toast.LENGTH_SHORT).show();
            }
            else {
                Toast.makeText(MainActivity.this, "Fine location Permission Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

}