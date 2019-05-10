package com.example.trailxplorer;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;

public class MainActivity extends AppCompatActivity {

    private LocationManager locationManager;

    private Chronometer chronometer;

    private Button recordButton;
    private TextView recordText;

    private int MY_PERMISSION_REQUEST_ACCESS_FINE_LOCATION = 1;
    private int MY_PERMISSION_REQUEST_ACCESS_COARSE_LOCATION = 1;
    private int MY_PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE = 1;
    private int MY_PERMISSION_REQUEST_READ_EXTERNAL_STORAGE = 1;

    private long pauseOffset;
    private boolean running = false;

    Boolean permissionExternalStorage;

    private ArrayList<Location> trackPoints = new ArrayList<>();
    private ArrayList<Integer> speedThroughTime = new ArrayList<>();

    String directoryName = "GPStracks";
    String fileName;
    GPXHelper gpxHelper;
    File dir;
    File file;
    double totatDistance;
    double maxAltitude = 0;
    double minAltitude = 0;
    double averageSpeedKH;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        gpxHelper = new GPXHelper(this);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String text = sdf.format(Calendar.getInstance().getTime());
        fileName = text;

        chronometer = findViewById(R.id.chronometer);
        recordText = findViewById(R.id.txtRecord);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        permissionExternalStorage();
        permissionGPS();
        addLocationListener();

        recordButton = findViewById(R.id.recordButton);

        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!running)
                {
                    //Start the traveler's journey by starting the chronometer,
                    //create a new folder called "GPStracks" if it does not exists,
                    //and create a new .gpx file with the current date and time as title
                    startChronometer();
                    dir = gpxHelper.newDirectory(directoryName);
                    file = gpxHelper.newFile(dir, fileName);
                }
                else
                {
                    //Ends the traveler's journey by stopping the chronometer
                    //write the end of the .gpx file created before
                    //and gather every necessary detail about the traveler's journey
                    gpxHelper.closeFile(file);
                    totatDistance = getDistanceSpeedThroughTimeMaxAltitude(trackPoints);
                    intoRecordActivity();
                }
            }
        });
    }

    //Run the chronometer
    public void startChronometer()
    {
        recordText.setText("Recording your journey!");
        recordButton.setText("End Record");
        chronometer.setBase(SystemClock.elapsedRealtime() - pauseOffset);
        chronometer.start();
        running = true;
    }

    /*Upon end of recording, gather every details of the trail
    And give them to the RecordActivity*/
    public void intoRecordActivity()
    {
        //Stops the chronometer
        recordText.setText("");
        chronometer.stop();
        running = false;

        //Get every details of the journey
        String dst = String.valueOf(totatDistance);
        String chrono = (String) chronometer.getText();
        double elapsedSeconds = (SystemClock.elapsedRealtime() - chronometer.getBase()) * 0.001;
        int j = (int) elapsedSeconds;
        maxAltitude = Math.floor(maxAltitude * 100) / 100;
        minAltitude = Math.floor(minAltitude * 100) / 100;
        averageSpeedKH = Math.floor(((totatDistance/elapsedSeconds) * 3.6) * 100) / 100;
        String averageSpd = String.valueOf(averageSpeedKH);

        //Reset the chronometer to 0
        chronometer.setBase(SystemClock.elapsedRealtime());
        pauseOffset = 0;
        recordButton.setText("Start Record");

        //Start the RecordActivity and send the journey's details to it
        Intent intent = new Intent(this, RecordActivity.class);
        intent.putExtra("Max Altitude", maxAltitude);
        intent.putExtra("Min Altitude", minAltitude);
        intent.putExtra("Chronometer", chrono);
        intent.putExtra("Distance", dst);
        intent.putExtra("Average Speed", averageSpd);
        intent.putExtra("Speed Through Time", speedThroughTime);
        startActivity(intent);
    }

    /*Calculate the distance traveled
    Compare the trackpoints' altitude and get the maximum altitude
    Calculate the speed of the traveler between two points and create an array of them
    This array is used to create the graph of the traveler's speed throughout his journey*/
    public double getDistanceSpeedThroughTimeMaxAltitude(ArrayList<Location> trackpoints)
    {
        ArrayList<Integer> speedTime = new ArrayList<>();
        double total = 0;
        minAltitude = trackpoints.get(0).getAltitude();
        for(int i = 0; i < trackpoints.size() - 1; i++)
        {
            Location loc1 = trackpoints.get(i);
            Location loc2 = trackpoints.get(i + 1);
            double dst = distanceBetweenTwoPoints(loc1.getLatitude(), loc2.getLatitude(), loc1.getLongitude(), loc2.getLongitude());
            total += dst;
            int spd = (int)(((dst / 5) * 3.6 ) * 100) / 100;
            speedTime.add(spd);
            maxAltitude = Math.max(maxAltitude, loc1.getAltitude());
            maxAltitude = Math.max(maxAltitude, loc2.getAltitude());
            minAltitude = Math.min(minAltitude, loc1.getAltitude());
            minAltitude = Math.min(minAltitude, loc2.getAltitude());
        }
        speedThroughTime = speedTime;
        return total;
    }

    //Calculate the distance between two given Locations by using formulas
    public double distanceBetweenTwoPoints(double lat1, double lat2, double lon1, double lon2)
    {
        double earthRadius = 3958.75;
        double distanceLat = Math.toRadians(lat2 - lat1);
        double distanceLon = Math.toRadians(lon2 - lon1);
        double a = (Math.sin(distanceLat / 2) * Math.sin(distanceLat / 2))
                + (Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(distanceLon / 2) * Math.sin(distanceLon / 2));
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = earthRadius * c;
        double meterConversion = 1609;
        return (int) (distance * meterConversion);
    }

    //Method used to get the current Location of the traveler
    //Taken from "Android by Example"
    private void addLocationListener() {

        //Ask the permission to use the GPS
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSION_REQUEST_ACCESS_FINE_LOCATION);
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    MY_PERMISSION_REQUEST_ACCESS_COARSE_LOCATION);
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 0, new LocationListener() {
            @Override
            //The app will get the current Location of the traveler every 5 seconds
            //Each time the app gets a Location, it will add it to an ArrayList and write it in the .gpx file
            //The app starts getting Location once the journey has begun
            public void onLocationChanged(Location location) {
                if(running)
                {
                    trackPoints.add(location);
                    gpxHelper.writeInFile(file, location);
                }
            }

            @Override
            //Nothing to do here
            public void onStatusChanged(String provider, int status, Bundle extras) {

            }

            @Override
            //When the GPS is enabled, this is called
            //Does the same thing as in onLocationChanged
            public void onProviderEnabled(String provider) {
                if (provider == LocationManager.GPS_PROVIDER) {
                    if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                            && ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return;
                    }
                    Location l = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    if(l != null)
                    {
                        if(running)
                        {
                            trackPoints.add(l);
                            gpxHelper.writeInFile(file, l);
                        }
                    }
                }
            }

            @Override
            //When the GPS is disabled, this is called
            //It adds a Location with no longitude, latitude, altitude and time
            public void onProviderDisabled(String provider) {
                if(provider == LocationManager.GPS_PROVIDER)
                {
                    if(running)
                    {
                        Location location = new Location("");
                        location.setAltitude(0);
                        location.setLatitude(0);
                        location.setLongitude(0);
                        location.setTime(0);
                        trackPoints.add(location);
                        gpxHelper.writeInFile(file, location);
                    }
                }
            }
        });
    }

    public void permissionGPS()
    {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSION_REQUEST_ACCESS_FINE_LOCATION);
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    MY_PERMISSION_REQUEST_ACCESS_COARSE_LOCATION);
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
    }

    //Ask for permission to write in the external storage
    public void permissionExternalStorage()
    {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    MY_PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE);
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    MY_PERMISSION_REQUEST_READ_EXTERNAL_STORAGE);
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            permissionExternalStorage = !(ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED);
                return ;
        }
    }
}