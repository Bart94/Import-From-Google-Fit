package com.example.bcoccaro.importfromgooglefit;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.data.Value;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.request.DataSourcesRequest;
import com.google.android.gms.fitness.request.OnDataPointListener;
import com.google.android.gms.fitness.request.SensorRequest;
import com.google.android.gms.fitness.result.DailyTotalResult;
import com.google.android.gms.fitness.result.DataReadResult;
import com.google.android.gms.fitness.result.DataSourcesResult;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_OAUTH = 1;
    private static final String TAG = "Tag";
    private static final String AUTH_PENDING = "auth_state_pending";
    private boolean authInProgress = false;
    private GoogleApiClient mClient = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /*if (savedInstanceState != null) {
            authInProgress = savedInstanceState.getBoolean(AUTH_PENDING);
        }*/
        buildFitnessClient();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Connect to the Fitness API
        Log.i(TAG, "Connecting...");
        mClient.connect();

        getStep();

        SharedPreferences mPref = getSharedPreferences("Steps", MODE_PRIVATE);
        TextView text = findViewById(R.id.textView1);
        text.setText(mPref.getString("step", "0"));
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mClient.isConnected()) {
            mClient.disconnect();
        }
    }

    @Override
    protected void onResume() {
        mClient.connect();

        SharedPreferences mPref = getSharedPreferences("Steps", MODE_PRIVATE);
        TextView text = findViewById(R.id.textView1);
        text.setText(mPref.getString("step", "0"));

        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_OAUTH) {
            authInProgress = false;
            if (resultCode == RESULT_OK) {
                // Make sure the app is not already connected or attempting to connect
                if (!mClient.isConnecting() && !mClient.isConnected()) {
                    mClient.connect();
                }
            }
        }
    }

    public void buildFitnessClient() {
        // Create the Google API Client
        mClient = new GoogleApiClient.Builder(this)
                .addApi(Fitness.SENSORS_API)
                .addApi(Fitness.HISTORY_API)
                .addScope(new Scope(Scopes.FITNESS_ACTIVITY_READ_WRITE))
                .addScope(new Scope(Scopes.FITNESS_BODY_READ_WRITE))
                .addScope(new Scope(Scopes.FITNESS_LOCATION_READ))
                .addConnectionCallbacks(
                        new GoogleApiClient.ConnectionCallbacks() {

                            @Override
                            public void onConnected(Bundle bundle) {
                                Log.i(TAG, "Connected!!!");
                                // Now you can make calls to the Fitness APIs.
                                // Put application specific code here.
                            }

                            @Override
                            public void onConnectionSuspended(int i) {
                                // If your connection to the sensor gets lost at some point,
                                // you'll be able to determine the reason and react to it here.
                                if (i == GoogleApiClient.ConnectionCallbacks.CAUSE_NETWORK_LOST) {
                                    Log.i(TAG, "Connection lost.  Cause: Network Lost.");
                                } else if (i == GoogleApiClient.ConnectionCallbacks.CAUSE_SERVICE_DISCONNECTED) {
                                    Log.i(TAG, "Connection lost.  Reason: Service Disconnected");
                                }
                            }
                        }
                )
                .addOnConnectionFailedListener(
                        new GoogleApiClient.OnConnectionFailedListener() {
                            // Called whenever the API client fails to connect.
                            @Override
                            public void onConnectionFailed(ConnectionResult result) {
                                Log.i(TAG, "Connection failed. Cause: " + result.toString());
                                if (!result.hasResolution()) {
                                    // Show the localized error dialog
                                    return;
                                }
                                // The failure has a resolution. Resolve it.
                                // Called typically when the app is not yet authorized, and an
                                // authorization dialog is displayed to the user.
                                try {
                                    Log.i(TAG, "Attempting to resolve failed connection");
                                    result.startResolutionForResult(MainActivity.this, REQUEST_OAUTH);
                                } catch (IntentSender.SendIntentException e) {
                                    Log.e(TAG, "Exception while starting resolution activity", e);
                                }
                            }
                        }
                )
                .build();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    public void getStep(){
            // [START find_data_sources]
            // Note: Fitness.SensorsApi.findDataSources() requires the ACCESS_FINE_LOCATION permission.
            Fitness.SensorsApi.findDataSources(mClient, new DataSourcesRequest.Builder()
                    // At least one datatype must be specified.
                    .setDataTypes(DataType.TYPE_STEP_COUNT_DELTA)
                    // Can specify whether data type is raw or derived.
                    .build())
                    .setResultCallback(new ResultCallback<DataSourcesResult>() {
                        @Override
                        public void onResult(DataSourcesResult dataSourcesResult) {
                            Log.i(TAG, "Result: " + dataSourcesResult.getStatus().toString());
                            for (DataSource dataSource : dataSourcesResult.getDataSources()) {
                                Log.i(TAG, "Data source found: " + dataSource.toString());
                                Log.i(TAG, "Data Source type: " + dataSource.getDataType().getName());

                                //Let's register a listener to receive Activity data!
                                if (dataSource.getDataType().equals(DataType.TYPE_STEP_COUNT_DELTA)) {
                                    Log.i(TAG, "Data source for LOCATION_SAMPLE found!  Registering.");
                                    registerFitnessDataListener(dataSource, DataType.TYPE_STEP_COUNT_DELTA);
                                }
                            }
                        }
                    });
            // [END find_data_sources]
    }

    private void registerFitnessDataListener(DataSource dataSource, DataType dataType) {
        // [START register_data_listener]
       OnDataPointListener mListener = new OnDataPointListener() {
            @Override
            public void onDataPoint(DataPoint dataPoint) {
                for (Field field : dataPoint.getDataType().getFields()) {
                    Value val = dataPoint.getValue(field);

                    Log.i(TAG, "Detected DataPoint field: " + field.getName());
                    Log.i(TAG, "Detected DataPoint value: " + val);
                }
            }
        };

        Fitness.SensorsApi.add(
                mClient,
                new SensorRequest.Builder()
                        .setDataSource(dataSource) // Optional but recommended for custom data sets.
                        .setDataType(dataType) // Can't be omitted.
                        .setSamplingRate(1, TimeUnit.SECONDS)
                        .build(),
                mListener)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            Log.i(TAG, "Listener registered!");
                        } else {
                            Log.i(TAG, "Listener not registered.");
                        }
                    }
                });
        // [END register_data_listener]
    }

    private class VerifyDataTask extends AsyncTask<Void, Void, Void> {
        TextView text = findViewById(R.id.textView1);
        protected Void doInBackground(Void... params) {

            long total = 0;

            PendingResult<DailyTotalResult> result = Fitness.HistoryApi.readDailyTotal(mClient, DataType.TYPE_STEP_COUNT_DELTA);

            result.setResultCallback(new ResultCallback<DailyTotalResult>() {
                @Override
                public void onResult(@NonNull DailyTotalResult dailyTotalResult) {
                    int steps = dailyTotalResult.getTotal().getDataPoints().get(0).getValue(Field.FIELD_STEPS).asInt();

                    SharedPreferences mPref = getSharedPreferences("Steps", MODE_PRIVATE);
                    mPref.edit().putString("step", String.valueOf(steps)).apply();

                    Log.e("Totale stesps: ", mPref.getString("step", " 12 "));
                }
            });
            return null;
        }
    }

    //Creo la notifica
    public void sendNotification() {

        NotificationCompat.Builder notify_builder = new NotificationCompat.Builder(this);

        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

        VerifyDataTask task1 = new VerifyDataTask();
        task1.doInBackground();

        SharedPreferences mPref = getSharedPreferences("Steps", MODE_PRIVATE);

        notify_builder.setContentIntent(pendingIntent);

        notify_builder.setSmallIcon(R.mipmap.ic_launcher);
        notify_builder.setContentTitle("Passi Totali");
        notify_builder.setContentText(mPref.getString("step", "0"));

        NotificationManager notification = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        notification.notify(002, notify_builder.build());
    }

}

