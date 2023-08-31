package com.example.model_c;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnSuccessListener;

import java.text.DecimalFormat;

public class MainActivity extends AppCompatActivity implements LocationListener, SensorEventListener{
    //button status
    int stat=0;
    //extra
    int countdownTime = 10;
    private PopupWindow popupWindow;
    private CountDownTimer countDownTimer;
    Button starter;
    //for map
    private GoogleMap mMap;
    private MapView mapView;
    private static final int PERMISSION_REQUEST_LOCATION = 1;
    private FusedLocationProviderClient fusedLocationClient;
//speed cal
TextView speeder;
    LocationManager lm;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    //for shock reading
    private SensorManager sensorManager;
    private Sensor accelerometerSensor;
    private static final float FALL_THRESHOLD = 9.8f * 3; // 3g
    private static final int FALL_SLOP_TIME_MS = 500;
    private long mFallTimestamp;

    private boolean isStarted = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //basic stuff
initial();
//permissions to call
        View popupView = LayoutInflater.from(this).inflate(R.layout.popup, null);

        // Initialize the popup window with the inflated view
        popupWindow = new PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);

        //for showing the map

        mapView = findViewById(R.id.map_view);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap googleMap) {
                mMap = googleMap;

                if (ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    // Permission has been granted
                    mMap.setMyLocationEnabled(true);
                } else {
                    // Permission has not been granted
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_LOCATION);
                }
                // Enable zoom controls and gestures
                mMap.getUiSettings().setZoomControlsEnabled(true);
                //map type
                mMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN);

                googleMap.setMyLocationEnabled(true);
                // Map is ready
                fusedLocationClient = LocationServices.getFusedLocationProviderClient(getApplication());
                fusedLocationClient.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            // Update the map camera to show the user's current location
                            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                            CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, 20f);
                            googleMap.animateCamera(cameraUpdate);
                        } else {
                            Toast.makeText(MainActivity.this, "Location not available", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });

//for the speedcalculator
        {
        speeder = findViewById(R.id.speed);

        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            // Ask for permission
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            // Permission has been granted, start listening for location updates
            getLocation();
        }
        }

        //for shock

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

    }
//for map
    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
        if (isStarted) {
            startFallDetection();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
        stopFallDetection();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }
//for speed
    @Override
    public void onLowMemory() {
        super.onLowMemory();
    }


    @Override
    public void onLocationChanged(Location location) {
        if (location != null) {
            double speedInKmh = location.getSpeed() * 3.6; // convert m/s to km/h
            speeder.setText(" "+String.format("%.1f", speedInKmh) + " km/h");
        }
    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    //overide for shock
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            float acceleration = (float) Math.sqrt(x * x + y * y + z * z);

            if (acceleration > FALL_THRESHOLD) {
                final long now = System.currentTimeMillis();
                if (mFallTimestamp + FALL_SLOP_TIME_MS > now) {
                    return;
                }

                mFallTimestamp = now;
                // Do certain tasks on fall detection
               vibration();
               showPopup();
                Toast.makeText(this, "Fall detected!", Toast.LENGTH_SHORT).show();

            }
        }
    }
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do nothing here
    }

  //extra

  public void vibration() {
      Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
// Vibrate for 500 milliseconds
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          v.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE));
      } else {
          //deprecated in API 26
          v.vibrate(500);
      }
  }
    //buttons

    public void driver(View view){
        if(stat==0){
        Toast.makeText(this, "System Initiated. safe journey!", Toast.LENGTH_SHORT).show();
        startFallDetection();
        starter.setText("STOP");
        stat=1;}
        else {
            stopFallDetection();
            starter.setText("DRIVE");
            stat=0;

        }
    }

    //functionality
    public void initial(){
        starter=findViewById(R.id.start);
    }
    private void getLocation() { if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
        // Permission is granted, request location updates
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
    } else {
        // Permission is not granted
        Toast.makeText(this, "Location permission not granted", Toast.LENGTH_SHORT).show();
    }
    }
    public void caller(){
        String phoneNumber = "tel:7022768975";
        Intent callIntent = new Intent(Intent.ACTION_CALL, Uri.parse(phoneNumber));
        callIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(callIntent);
    }
    public void messager() {
        String mess = "I am in an accident. Please check out!";
        String number = "7022768975";  // The number on which you want to send SMS

        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(number, null, mess, null, null);
            Log.i("SMS Sent", "Message: " + mess + ", Recipient: " + number);
        } catch (Exception e) {
            Log.e("SMS Failed", "Message: " + mess + ", Recipient: " + number, e);
        }
    }
    protected void sendSMS() {
        Log.i("Send SMS", "");
        Intent smsIntent = new Intent(Intent.ACTION_VIEW);

        smsIntent.setData(Uri.parse("smsto:"));
        smsIntent.setType("vnd.android-dir/mms-sms");
        smsIntent.putExtra("address"  , new String ("7022768975"));
        smsIntent.putExtra("sms_body"  , "Test ");

        try {
            startActivity(smsIntent);
            finish();
            Log.i("Finished sending SMS...", "");
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(MainActivity.this,
                    "SMS faild, please try again later.", Toast.LENGTH_SHORT).show();
        }
    }
    private void showPopup() {
        // Inflate the popup layout
        View popupView = getLayoutInflater().inflate(R.layout.popup, null);

        // Create a popup window with the layout
        PopupWindow popupWindow = new PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );

        // Find the close button in the popup layout
        Button closeButton = popupView.findViewById(R.id.stop);

        // Set an OnClickListener on the close button to dismiss the popup and cancel the timer
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                popupWindow.dismiss();
                countDownTimer.cancel(); // cancel the countdown timer
            }
        });

        // Find the countdown timer text view in the popup layout
        TextView countdownTextView = popupView.findViewById(R.id.timer);

        // Set the initial countdown time to 30 seconds
        final int[] time = {countdownTime};

        // Create a countdown timer that updates the countdown time every second
        countDownTimer = new CountDownTimer(time[0]* 1000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                time[0]--;
                countdownTextView.setText( " "+ time[0]) ;
            }

            @Override
            public void onFinish() {
                // If the user did not click the close button within 30 seconds, do something else
                // For example, you can dismiss the popup, show a message, or perform a task
                caller();
               // messager();
                //sendSMS();
                popupWindow.dismiss();
                Toast.makeText(MainActivity.this, "Popup closed automatically", Toast.LENGTH_SHORT).show();
            }
        }.start();

        // Show the popup at the center of the screen
        popupWindow.showAtLocation(
                findViewById(android.R.id.content),
                Gravity.CENTER,
                0,
                0
        );
    }
//acceleration

    private void startFallDetection() {
        sensorManager.registerListener(MainActivity.this, accelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL);

        isStarted = true;
    }

    private void stopFallDetection() {
        sensorManager.unregisterListener(MainActivity.this);
        isStarted = false;
    }

}
