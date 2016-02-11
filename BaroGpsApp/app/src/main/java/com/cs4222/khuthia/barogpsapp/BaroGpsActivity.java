package com.cs4222.khuthia.barogpsapp;

import java.io.*;
import java.util.*;
import java.text.*;

import android.app.*;
import android.os.*;
import android.widget.*;
import android.view.*;
import android.content.*;
import android.location.*;
import android.hardware.*;
import android.util.*;

/**
 Activity that logs Location and Barometer readings.

 <p> This activity allows the user to log readings
 from the location and barometer sensors. The source
 of location can be either GPS, network, or both,
 depending on the location settings enabled.

 <p> Google introduced a Fused location API in May 2013
 which internally uses sensors to smooth and adaptively
 duty cycle location values. The new API also
 intelligently switches between location providers
 and filters reported location values.
 This activity makes use of the old Location Manager
 API so that we get the raw unfiltered location
 readings.

 <p> For GPS, to measure the Time-to-first-fix
 properly, we need to tell the Location Manager
 to flush any prior location data it has, so that
 it will determine location from scratch.

 <p> The GPS is sampled at the highest sampling
 rate available, barometer at 1 Hz.
 Note that this app does not use a wake lock, so
 turning off the screen for a few minutes may
 idle the CPU and stop data collection. The
 screen must be on during data collection.

 <p> The sensor readings are logged into the sdcard
 under the folder 'BaroGps' as files 'GPS.csv' and
 'Barometer.csv'. The format is as follows --
 'Barometer.csv':
 Reading Number, Unix timestamp, Human Readable Time,
 Millibar reading, Height in metres,
 Time from last reading (msec),
 Delay for first reading (msec)
 'GPS.csv':
 Reading Number, Unix timestamp, Human Readable Time,
 Provider, Latitude (deg), Longitude (deg), Accuracy (m),
 Altitude (m), Bearing (deg), Speed (m/sec),
 Time from last reading (msec),
 Delay for first reading (msec)
 Note: Depending on the location provider enabled, the
 altitude, bearing, and speed may not be provided.
 In this case, they are logged as '-1'.
 While copying the log files from the phone to the
 laptop, remember that sometimes MTP does not keep in
 sync with the Android file system. Logs may not be
 flushed to sdcard until the phone is rebooted.

 @author  Kartik S
 @date    17th Jan 2016
 */
public class BaroGpsActivity
        extends Activity
        implements SensorEventListener ,
        LocationListener {

    /** Called when the activity is created. */
    @Override
    public void onCreate( Bundle savedInstanceState ) {
        super.onCreate( savedInstanceState );

        // Create a handler to the main thread
        handler = new Handler();

        try {

            // Set up the GUI
            setUpGUI();

            // Open the log files
            openLogFiles();
        }
        catch ( Exception e ) {
            // Log the exception
            Log.e ( TAG , "Unable to create activity" , e );
            // Tell the user
            createToast ( "Unable to create BaroGps Activity: " + e.toString() );
        }
    }

    /** Called when the activity is destroyed. */
    @Override
    public void onDestroy() {
        super.onDestroy();

        try {

            // Close the log files
            closeLogFiles();

            // Stop sensor sampling (if user didn't stop)
            stopBarometerSampling();
            stopLocationSampling();
        }
        catch ( Exception e ) {
            // Log the exception
            Log.e ( TAG , "Unable to destroy activity" , e );
            // Tell the user
            createToast ( "Unable to destroy BaroGps Activity: " + e.toString() );
        }
    }

    /** Helper method that starts Barometer sampling. */
    private void startBarometerSampling() {

        try {

            // Check the flag
            if ( isBarometerOn )
                return;

            // Get the sensor manager
            sensorManager =
                    (SensorManager) getSystemService( Context.SENSOR_SERVICE );
            // Get the barometer sensor
            barometerSensor =
                    (Sensor) sensorManager.getDefaultSensor( Sensor.TYPE_PRESSURE );
            if ( barometerSensor == null ) {
                throw new Exception( "Sensor not available" );
            }

            // Initialise timestamps and count
            prevBarometerTime = System.currentTimeMillis();
            barometerDelayTime = 0;
            numBarometerReadings = 0;

            // Add a sensor change listener
            sensorManager.registerListener( this ,
                    barometerSensor ,
                    BAROMETER_SAMPLING_RATE * 1000 );

            // Set the flag
            isBarometerOn = true;
        }
        catch ( Exception e ) {
            // Log the exception
            Log.e ( TAG , "Unable to start barometer" , e );
            // Tell the user
            createToast ( "Unable to start barometer: " + e.toString() );
        }
    }

    /** Helper method that stops Barometer sampling. */
    private void stopBarometerSampling() {

        try {

            // Check the flag
            if ( ! isBarometerOn )
                return;

            // Set the flag
            isBarometerOn = false;

            // Remove the sensor change listener
            sensorManager.unregisterListener( this ,
                    barometerSensor );
        }
        catch ( Exception e ) {
            // Log the exception
            Log.e ( TAG , "Unable to stop barometer" , e );
            // Tell the user
            createToast ( "Unable to stop barometer: " + e.toString() );
        }
        finally {
            sensorManager = null;
            barometerSensor = null;
        }
    }

    /** Helper method that starts Location sampling. */
    private void startLocationSampling() {

        try {

            // Check the flag
            if ( isLocationOn )
                return;

            // Get the location manager
            locationManager =
                    (LocationManager) getSystemService( Context.LOCATION_SERVICE );
            // Get one of the enabled location providers (ignore passive provider)
            List <String> enabledProviders = locationManager.getProviders( true );
            enabledProviders.remove( LocationManager.PASSIVE_PROVIDER );
            if ( enabledProviders.isEmpty() ) {
                throw new Exception( "No location provider enabled" );
            }

            // Initialise timestamps and count
            prevLocationTime = System.currentTimeMillis();
            locationDelayTime = 0;
            numLocationReadings = 0;

            // Add a location change listener for any one of the enabled providers
            // The provider used (GPS or network) depends on user's Location Settings
            locationManager.requestLocationUpdates( enabledProviders.get( 0 ) ,
                    0 ,       // Min time: 0 (fastest)
                    0.0F ,    // Min distance change: 0 meters
                    this );   // Location Listener to be called

            // Set the flag
            isLocationOn = true;
        }
        catch ( Exception e ) {
            // Log the exception
            Log.e ( TAG , "Unable to start location" , e );
            // Tell the user
            createToast ( "Unable to start location: " + e.toString() );
        }
    }

    /** Helper method that stops Location sampling. */
    private void stopLocationSampling() {

        try {

            // Check the flag
            if ( ! isLocationOn )
                return;

            // Set the flag
            isLocationOn = false;

            // Remove the location change listener
            locationManager.removeUpdates( this );
        }
        catch ( Exception e ) {
            // Log the exception
            Log.e ( TAG , "Unable to stop location" , e );
            // Tell the user
            createToast ( "Unable to stop location: " + e.toString() );
        }
        finally {
            locationManager = null;
        }
    }

    /** Called when the barometer value has changed. */
    @Override
    public void onSensorChanged( SensorEvent event ) {

        // SensorEvent's timestamp is the device uptime,
        //  but for logging we use UTC time
        long barometerTime = System.currentTimeMillis();

        // Validity check: This must be the barometer sensor
        if ( event.sensor.getType() != Sensor.TYPE_PRESSURE )
            return;

        // Calculate the delay for first reading
        if ( barometerDelayTime == 0 ) {
            barometerDelayTime = barometerTime - prevBarometerTime;
        }

        // Although we specified 1 Hz, events usually arrive at
        //  a faster rate. Cap frequency to 1 Hz.
        if ( barometerTime - prevBarometerTime < (long) BAROMETER_SAMPLING_RATE )
            return;
        // Update the count
        ++numBarometerReadings;

        // Convert reported millibar value to meters above sea level
        float height = convertMillibarToMetres( event.values[0] );

        // Log the reading
        logBarometerReading( barometerTime ,
                event.values[0] ,
                height );

        // Update the GUI
        updateBarometerTextView( barometerTime ,
                event.values[0] ,
                height  );

        // Update the last timestamp
        prevBarometerTime = barometerTime;
    }

    /** Helper method to convert millibar to metres. */
    private static float convertMillibarToMetres( float millibar ) {
        // Calculate the altitude in metres above sea level
        float height =
                SensorManager.getAltitude( SensorManager.PRESSURE_STANDARD_ATMOSPHERE ,  // Sea level
                        millibar );                                   // Pressure
        return height;
    }

    /** Called when the barometer accuracy changes. */
    @Override
    public void onAccuracyChanged( Sensor sensor ,
                                   int accuracy ) {
        // Nothing to do here
    }

    /** Called when the location has changed. */
    @Override
    public void onLocationChanged( Location location ) {

        // Calculate the delay for first reading
        long locationTime = location.getTime();
        if ( locationDelayTime == 0 ) {
            locationDelayTime = locationTime - prevLocationTime;
        }

        // Update the count
        ++numLocationReadings;

        // Get the location details
        String provider = location.getProvider();
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();
        float accuracy = location.getAccuracy();
        double altitude = ( location.hasAltitude() ?
                location.getAltitude() : -1.0 );
        float bearing = ( location.hasBearing() ?
                location.getBearing() : -1.0F );
        float speed = ( location.hasSpeed() ?
                location.getSpeed() : -1.0F );

        // Log the reading
        logLocationReading( locationTime ,
                provider ,
                latitude ,
                longitude ,
                accuracy ,
                altitude ,
                bearing ,
                speed );

        // Update the GUI
        updateLocationTextView( locationTime ,
                provider ,
                latitude ,
                longitude ,
                accuracy ,
                altitude ,
                bearing ,
                speed );

        // Update the last timestamp
        prevLocationTime = locationTime;
    }

    /** Called when a location provider is disabled. */
    @Override
    public void onProviderDisabled( String provider ) {
        // Ignore
    }

    /** Called when a location provider is enabled. */
    @Override
    public void onProviderEnabled( String provider ) {
        // Ignore
    }

    /** Called when a location provider's status has changed. */
    @Override
    public void onStatusChanged( String provider ,
                                 int status ,
                                 Bundle extras ) {
        // Ignore
    }

    /** Helper method that sets up the GUI. */
    private void setUpGUI() {

        // Set the GUI content to the XML layout specified
        setContentView( R.layout.main );

        // Get references to GUI widgets
        startBarometerButton =
                (Button) findViewById( R.id.Button_StartBarometer );
        stopBarometerButton =
                (Button) findViewById( R.id.Button_StopBarometer );
        startLocationButton =
                (Button) findViewById( R.id.Button_StartLocation );
        stopLocationButton =
                (Button) findViewById( R.id.Button_StopLocation );
        flushLocationButton =
                (Button) findViewById( R.id.Button_FlushLocation );
        barometerTextView =
                (TextView) findViewById( R.id.TextView_Barometer );
        locationTextView =
                (TextView) findViewById( R.id.TextView_Location );

        // Disable the stop buttons
        stopBarometerButton.setEnabled( false );
        stopLocationButton.setEnabled( false );

        // Set up button listeners
        setUpButtonListeners();
    }

    /** Helper method that sets up button listeners. */
    private void setUpButtonListeners() {

        // Start barometer
        startBarometerButton.setOnClickListener ( new View.OnClickListener() {
            public void onClick ( View v ) {
                // Start barometer sampling
                startBarometerSampling();
                // Disable the start and enable stop button
                startBarometerButton.setEnabled( false );
                stopBarometerButton.setEnabled( true );
                // Inform the user
                barometerTextView.setText( "\nAwaiting Barometer readings...\n" );
                createToast( "Barometer sampling started" );
            }
        } );

        // Stop barometer
        stopBarometerButton.setOnClickListener ( new View.OnClickListener() {
            public void onClick ( View v ) {
                // Stop barometer sampling
                stopBarometerSampling();
                // Disable the stop and enable start button
                startBarometerButton.setEnabled( true );
                stopBarometerButton.setEnabled( false );
                // Inform the user
                createToast( "Barometer sampling stopped" );
            }
        } );

        // Start Location
        startLocationButton.setOnClickListener ( new View.OnClickListener() {
            public void onClick ( View v ) {
                // Start Location sampling
                startLocationSampling();
                // Disable the start and enable stop button
                startLocationButton.setEnabled( false );
                stopLocationButton.setEnabled( true );
                // Inform the user
                locationTextView.setText( "\nAwaiting location readings...\n" );
                createToast( "Location sampling started" );
            }
        } );

        // Stop Location
        stopLocationButton.setOnClickListener ( new View.OnClickListener() {
            public void onClick ( View v ) {
                // Stop Location sampling
                stopLocationSampling();
                // Disable the stop and enable start button
                startLocationButton.setEnabled( true );
                stopLocationButton.setEnabled( false );
                // Inform the user
                createToast( "Location sampling stopped" );
            }
        } );

        // Flush Location
        flushLocationButton.setOnClickListener ( new View.OnClickListener() {
            public void onClick ( View v ) {
                // Ask the location manager to flush cached GPS data and
                //  start from scratch
                // Note: This is only for the assignment, this is not done in real
                //       application code
                try {
                    if ( ! isLocationOn ) {
                        locationManager = (LocationManager)
                                getSystemService( Context.LOCATION_SERVICE );
                    }
                    if ( locationManager.sendExtraCommand( LocationManager.GPS_PROVIDER ,
                            "delete_aiding_data" ,
                            null ) ) {
                        createToast( "Cached GPS data will be flushed" );
                    }
                    else {
                        createToast( "Warning: Unable to flush old GPS data" );
                    }
                }
                catch ( Exception e ) {
                    // Log the exception
                    Log.e( TAG , "Exception while flushing GPS data" , e );
                    // Inform the user
                    createToast( "Exception while flushing GPS data: " + e.toString() );
                }
                finally {
                    if ( ! isLocationOn ) {
                        locationManager = null;
                    }
                }
            }
        } );
    }

    /** Helper method that updates the barometer text view. */
    private void updateBarometerTextView( long barometerTime ,
                                          float millibar ,
                                          float height ) {

        // Barometer details
        final StringBuilder sb = new StringBuilder();
        sb.append( "\nBarometer--" );
        sb.append( "\nNumber of readings: " + numBarometerReadings );
        sb.append( "\nMillibar: " + millibar );
        sb.append( "\nHeight (m): " + height );
        sb.append( "\nTime to get first sensor reading (msec): " + barometerDelayTime );
        sb.append( "\nTime from previous reading (msec): " +
                ( barometerTime - prevBarometerTime ) );

        // Update the text view in the main UI thread
        handler.post ( new Runnable() {
            @Override
            public void run() {
                barometerTextView.setText( sb.toString() );
            }
        } );
    }

    /** Helper method that updates the location text view. */
    private void updateLocationTextView( long locationTime ,
                                         String provider ,
                                         double latitude ,
                                         double longitude ,
                                         float accuracy ,
                                         double altitude ,
                                         float bearing ,
                                         float speed ) {

        // Location details
        final StringBuilder sb = new StringBuilder();
        sb.append( "\nLocation--" );
        sb.append( "\nNumber of readings: " + numLocationReadings );
        sb.append( "\nProvider: " + provider );
        sb.append( "\nLatitude (degrees): " + latitude );
        sb.append( "\nLongitude (degrees): " + longitude );
        sb.append( "\nAccuracy (m): " + accuracy );
        sb.append( "\nAltitude (m): " + altitude );
        sb.append( "\nBearing (degrees): " + bearing );
        sb.append( "\nSpeed (m/sec): " + speed );
        sb.append( "\nTime to get first sensor reading (msec): " + locationDelayTime );
        sb.append( "\nTime from previous reading (msec): " +
                ( locationTime - prevLocationTime ) );

        // Update the text view in the main UI thread
        handler.post ( new Runnable() {
            @Override
            public void run() {
                locationTextView.setText( sb.toString() );
            }
        } );
    }

    /** Helper method to create toasts for the user. */
    private void createToast ( final String toastMessage ) {

        // Post a runnable in the Main UI thread
        handler.post ( new Runnable() {
            @Override
            public void run() {
                Toast.makeText ( getApplicationContext() ,
                        toastMessage ,
                        Toast.LENGTH_SHORT ).show();
            }
        } );
    }

    /** Helper method to make the log files ready for writing. */
    public void openLogFiles()
            throws IOException {

        // First, check if the sdcard is available for writing
        String externalStorageState = Environment.getExternalStorageState();
        if ( ! externalStorageState.equals ( Environment.MEDIA_MOUNTED ) &&
                ! externalStorageState.equals ( Environment.MEDIA_SHARED ) )
            throw new IOException ( "sdcard is not mounted on the filesystem" );

        // Second, create the log directory
        File logDirectory = new File( Environment.getExternalStorageDirectory() ,
                "BaroGps" );
        logDirectory.mkdirs();
        if ( ! logDirectory.isDirectory() )
            throw new IOException( "Unable to create log directory" );

        // Third, create output streams for the log files (APPEND MODE)
        // Barometer log
        File logFile = new File( logDirectory , "Barometer.csv" );
        FileOutputStream fout = new FileOutputStream( logFile , true );
        barometerLogFileOut = new PrintWriter( fout );
        // Location log
        logFile = new File( logDirectory , "GPS.csv" );
        fout = new FileOutputStream( logFile , true );
        locationLogFileOut = new PrintWriter( fout );
    }

    /** Helper method that closes the log files. */
    public void closeLogFiles() {

        // Close the barometer log file
        try {
            barometerLogFileOut.close();
        }
        catch ( Exception e ) {
            Log.e( TAG , "Unable to close barometer log file" , e );
        }
        finally {
            barometerLogFileOut = null;
        }

        // Close the location log file
        try {
            locationLogFileOut.close();
        }
        catch ( Exception e ) {
            Log.e( TAG , "Unable to close location log file" , e );
        }
        finally {
            locationLogFileOut = null;
        }
    }

    /** Helper method that logs the barometer reading. */
    private void logBarometerReading( long barometerTime ,
                                      float millibar ,
                                      float height ) {

        // Barometer details
        final StringBuilder sb = new StringBuilder();
        sb.append( numBarometerReadings + "," );
        sb.append( barometerTime + "," );
        sb.append( getHumanReadableTime( barometerTime ) + "," );
        sb.append( millibar + "," );
        sb.append( height + "," );
        sb.append( ( barometerTime - prevBarometerTime ) + "," );
        sb.append( barometerDelayTime );

        // Log to the file (and flush)
        barometerLogFileOut.println( sb.toString() );
        barometerLogFileOut.flush();
    }

    /** Helper method that logs the location reading. */
    private void logLocationReading( long locationTime ,
                                     String provider ,
                                     double latitude ,
                                     double longitude ,
                                     float accuracy ,
                                     double altitude ,
                                     float bearing ,
                                     float speed ) {

        // Location details
        final StringBuilder sb = new StringBuilder();
        sb.append( numLocationReadings + "," );
        sb.append( locationTime + "," );
        sb.append( getHumanReadableTime( locationTime ) + "," );
        sb.append( provider + "," );
        sb.append( latitude + "," );
        sb.append( longitude + "," );
        sb.append( accuracy + "," );
        sb.append( altitude + "," );
        sb.append( bearing + "," );
        sb.append( speed + "," );
        sb.append( ( locationTime - prevLocationTime ) + "," );
        sb.append( locationDelayTime );

        // Log to the file (and flush)
        locationLogFileOut.println( sb.toString() );
        locationLogFileOut.flush();
    }

    /** Helper method to get the human readable time from unix time. */
    private static String getHumanReadableTime( long unixTime ) {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat( "yyyy-MM-dd-h-mm-ssa" );
        return sdf.format( new Date( unixTime ) );
    }

    // GUI Widgets
    /** Start Barometer button. */
    private Button startBarometerButton;
    /** Stop Barometer button. */
    private Button stopBarometerButton;
    /** Start Location button. */
    private Button startLocationButton;
    /** Stop Location button. */
    private Button stopLocationButton;
    /** Flush Location button. */
    private Button flushLocationButton;
    /** Barometer readings textview. */
    private TextView barometerTextView;
    /** Location readings textview. */
    private TextView locationTextView;

    /** Sensor Manager. */
    private SensorManager sensorManager;
    /** Location Manager. */
    private LocationManager locationManager;

    /** Barometer sensor. */
    private Sensor barometerSensor;
    /** Barometer sampling rate (millisec). */
    private static final int BAROMETER_SAMPLING_RATE = 1000;

    /** Delay for first barometer reading (millisec). */
    private long barometerDelayTime;
    /** Delay for first location reading (millisec). */
    private long locationDelayTime;
    /** Previous barometer reading timestamp (millisec). */
    private long prevBarometerTime;
    /** Previous location reading timestamp (millisec). */
    private long prevLocationTime;

    /** Number of barometer readings so far. */
    private int numBarometerReadings;
    /** Number of location readings so far. */
    private int numLocationReadings;

    /** Flag to indicate that barometer sensing is going on. */
    private boolean isBarometerOn;
    /** Flag to indicate that location sensing is going on. */
    private boolean isLocationOn;

    /** Handler to the main thread. */
    private Handler handler;

    /** Barometer log file output stream. */
    public PrintWriter barometerLogFileOut;
    /** Location log file output stream. */
    public PrintWriter locationLogFileOut;

    /** DDMS Log Tag. */
    private static final String TAG = "BaroGpsActivity";
}