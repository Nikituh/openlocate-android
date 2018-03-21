/*
 * Copyright (c) 2017 OpenLocate
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.openlocate.android.core;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.PeriodicTask;
import com.google.android.gms.gcm.Task;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.openlocate.android.callbacks.OpenLocateLocationCallback;
import com.openlocate.android.exceptions.InvalidConfigurationException;
import com.openlocate.android.exceptions.LocationDisabledException;
import com.openlocate.android.exceptions.LocationPermissionException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class OpenLocate implements OpenLocateLocationTracker, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener  {

    private static final int LOCATION_PERMISSION_REQUEST = 1001;

    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

    private final static String LOCATION_DISPATCH_TAG = OpenLocate.class.getCanonicalName() + ".location_dispatch_task";

    private static OpenLocate sharedInstance = null;
    private static final String TAG = OpenLocate.class.getSimpleName();

    private Context context;
    private ArrayList<Endpoint> endpoints;
    private Configuration configuration;
    private AdvertisingIdClient.Info advertisingIdInfo;

    private FusedLocationProviderClient fusedLocationProviderClient;

    private long locationInterval = Constants.DEFAULT_LOCATION_INTERVAL_SEC;
    private long transmissionInterval = Constants.DEFAULT_TRANSMISSION_INTERVAL_SEC;
    private LocationAccuracy accuracy = Constants.DEFAULT_LOCATION_ACCURACY;

    private static final long UPDATE_INTERVAL = 10 * 1000;
    private static final long FASTEST_UPDATE_INTERVAL = UPDATE_INTERVAL / 2;
    private static final long MAX_WAIT_TIME = UPDATE_INTERVAL * 3;

    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;
    private GcmNetworkManager networkManager;

    public static final class Configuration implements Parcelable {

        Context context = null;
        private ArrayList<Endpoint> endpoints;

        private String serverUrl;
        private HashMap<String, String> headers;

        private boolean isWifiCollectionDisabled;
        private boolean isDeviceModelCollectionDisabled;
        private boolean isDeviceManufacturerCollectionDisabled;
        private boolean isOperatingSystemCollectionDisbaled;
        private boolean isChargingInfoCollectionDisabled;
        private boolean isCarrierNameCollectionDisabled;
        private boolean isConnectionTypeCollectionDisabled;
        private boolean isLocationMethodCollectionDisabled;
        private boolean isLocationContextCollectionDisabled;

        public static final class Builder {
            private Context context;
            private ArrayList<Endpoint> endpoints;
            private String serverUrl;
            private HashMap<String, String> headers;
            private boolean isWifiCollectionDisabled;
            private boolean isDeviceModelCollectionDisabled;
            private boolean isDeviceManufacturerCollectionDisabled;
            private boolean isOperatingSystemCollectionDisbaled;
            private boolean isChargingInfoCollectionDisabled;
            private boolean isCarrierNameCollectionDisabled;
            private boolean isConnectionTypeCollectionDisabled;
            private boolean isLocationMethodCollectionDisabled;
            private boolean isLocationContextCollectionDisabled;

            public Builder(Context context, ArrayList<Endpoint> endpoints) {
                this.context = context.getApplicationContext();
                this.endpoints = endpoints;
            }

            public Builder(Context context, String serverUrl) {
                this.context = context.getApplicationContext();
                this.serverUrl = serverUrl;
            }

            public Builder setHeaders(HashMap<String, String> headers) {
                this.headers = headers;
                return this;
            }


            public Builder withoutWifiInfo() {
                this.isWifiCollectionDisabled = true;
                return this;
            }

            public Builder withoutDeviceModel() {
                this.isDeviceModelCollectionDisabled = true;
                return this;
            }

            public Builder withoutDeviceManufacturer() {
                this.isDeviceManufacturerCollectionDisabled = true;
                return this;
            }

            public Builder withoutOperatingSystem() {
                this.isOperatingSystemCollectionDisbaled = true;
                return this;
            }

            public Builder withoutChargingInfo() {
                this.isChargingInfoCollectionDisabled = true;
                return this;
            }

            public Builder withoutCarrierName() {
                this.isCarrierNameCollectionDisabled = true;
                return this;
            }

            public Builder withoutConnectionType() {
                this.isConnectionTypeCollectionDisabled = true;
                return this;
            }

            public Builder withoutLocationMethod() {
                this.isLocationMethodCollectionDisabled = true;
                return this;
            }

            public Builder withoutLocationContext() {
                this.isLocationContextCollectionDisabled = true;
                return this;
            }

            public Configuration build() {
                if (serverUrl != null) {
                    Endpoint endpoint = new Endpoint(serverUrl, headers);

                    if (endpoints == null) {
                        endpoints = new ArrayList<>();
                    }

                    endpoints.add(endpoint);
                }

                return new Configuration(this);
            }
        }

        private Configuration(Builder builder) {
            this.context = builder.context;
            this.endpoints = builder.endpoints;
            this.isCarrierNameCollectionDisabled = builder.isCarrierNameCollectionDisabled;
            this.isChargingInfoCollectionDisabled = builder.isChargingInfoCollectionDisabled;
            this.isConnectionTypeCollectionDisabled = builder.isConnectionTypeCollectionDisabled;
            this.isDeviceManufacturerCollectionDisabled = builder.isDeviceManufacturerCollectionDisabled;
            this.isDeviceModelCollectionDisabled = builder.isDeviceModelCollectionDisabled;
            this.isLocationContextCollectionDisabled = builder.isLocationContextCollectionDisabled;
            this.isLocationMethodCollectionDisabled = builder.isLocationMethodCollectionDisabled;
            this.isOperatingSystemCollectionDisbaled = builder.isOperatingSystemCollectionDisbaled;
            this.isWifiCollectionDisabled = builder.isWifiCollectionDisabled;
        }

        public List<Endpoint> getEndpoints() {
            return endpoints;
        }

        public boolean isWifiCollectionDisabled() {
            return isWifiCollectionDisabled;
        }

        public boolean isDeviceModelCollectionDisabled() {
            return isDeviceModelCollectionDisabled;
        }

        public boolean isDeviceManufacturerCollectionDisabled() {
            return isDeviceManufacturerCollectionDisabled;
        }

        public boolean isOperaringSystemCollectionDisbaled() {
            return isOperatingSystemCollectionDisbaled;
        }

        public boolean isChargingInfoCollectionDisabled() {
            return isChargingInfoCollectionDisabled;
        }

        public boolean isCarrierNameCollectionDisabled() {
            return isCarrierNameCollectionDisabled;
        }

        public boolean isConnectionTypeCollectionDisabled() {
            return isConnectionTypeCollectionDisabled;
        }

        public boolean isLocationMethodCollectionDisabled() {
            return isLocationMethodCollectionDisabled;
        }

        public boolean isLocationContextCollectionDisabled() {
            return isLocationContextCollectionDisabled;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeTypedList(this.endpoints);
            dest.writeByte(this.isWifiCollectionDisabled ? (byte) 1 : (byte) 0);
            dest.writeByte(this.isDeviceModelCollectionDisabled ? (byte) 1 : (byte) 0);
            dest.writeByte(this.isDeviceManufacturerCollectionDisabled ? (byte) 1 : (byte) 0);
            dest.writeByte(this.isOperatingSystemCollectionDisbaled ? (byte) 1 : (byte) 0);
            dest.writeByte(this.isChargingInfoCollectionDisabled ? (byte) 1 : (byte) 0);
            dest.writeByte(this.isCarrierNameCollectionDisabled ? (byte) 1 : (byte) 0);
            dest.writeByte(this.isConnectionTypeCollectionDisabled ? (byte) 1 : (byte) 0);
            dest.writeByte(this.isLocationMethodCollectionDisabled ? (byte) 1 : (byte) 0);
            dest.writeByte(this.isLocationContextCollectionDisabled ? (byte) 1 : (byte) 0);
        }

        protected Configuration(Parcel in) {
            this.endpoints = in.createTypedArrayList(Endpoint.CREATOR);
            this.isWifiCollectionDisabled = in.readByte() != 0;
            this.isDeviceModelCollectionDisabled = in.readByte() != 0;
            this.isDeviceManufacturerCollectionDisabled = in.readByte() != 0;
            this.isOperatingSystemCollectionDisbaled = in.readByte() != 0;
            this.isChargingInfoCollectionDisabled = in.readByte() != 0;
            this.isCarrierNameCollectionDisabled = in.readByte() != 0;
            this.isConnectionTypeCollectionDisabled = in.readByte() != 0;
            this.isLocationMethodCollectionDisabled = in.readByte() != 0;
            this.isLocationContextCollectionDisabled = in.readByte() != 0;
        }

        public static final Creator<Configuration> CREATOR = new Creator<Configuration>() {
            @Override
            public Configuration createFromParcel(Parcel source) {
                return new Configuration(source);
            }

            @Override
            public Configuration[] newArray(int size) {
                return new Configuration[size];
            }
        };
    }


    public static class Endpoint implements Parcelable {

        public static final String URL = "url";
        public static final String HEADERS = "headers";
        public static final String HEADERS_KEY = "key";
        public static final String HEADERS_VALUE = "value";

        public static List<Endpoint> fromJson(String json) throws JSONException {

            JSONArray jsonArray = new JSONArray(json);
            List<Endpoint> result = new ArrayList<>(jsonArray.length());

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonEndpoint = jsonArray.getJSONObject(i);
                Builder builder = Endpoint.builder(jsonEndpoint.getString(URL));
                JSONArray headers = jsonEndpoint.getJSONArray(HEADERS);
                for (int j = 0; j < headers.length(); j++) {
                    JSONObject header = headers.getJSONObject(j);
                    builder.withHeader(header.getString(HEADERS_KEY), header.getString(HEADERS_VALUE));
                }
                result.add(builder.build());
            }

            return result;
        }

        public static String toJson(List<Endpoint> endpoints) throws JSONException {
            JSONArray jsonArray = new JSONArray();

            for (Endpoint endpoint : endpoints) {

                JSONObject jsonEndpoint = new JSONObject();
                jsonEndpoint.put(URL, endpoint.url);


                JSONArray jsonHeaders = new JSONArray();

                for (Map.Entry<String, String> entry : endpoint.getHeaders().entrySet()) {
                    JSONObject header = new JSONObject();
                    header.put(HEADERS_KEY, entry.getKey());
                    header.put(HEADERS_VALUE, entry.getValue());
                    jsonHeaders.put(header);
                }

                jsonEndpoint.put(HEADERS, jsonHeaders);
                jsonArray.put(jsonEndpoint);
            }

            return jsonArray.toString();
        }

        private String url;

        private HashMap<String, String> headers;

        public Endpoint(String url, HashMap<String, String> headers) {
            this.url = url;
            this.headers = headers;
        }

        private Endpoint(Builder builder) {
            this.url = builder.url;
            this.headers = builder.headers;
        }

        public String getUrl() {
            return url;
        }

        public HashMap<String, String> getHeaders() {
            return headers;
        }

        public static Builder builder(String url) {
            return new Builder(url);
        }

        public static class Builder {

            private String url;

            private HashMap<String, String> headers;

            public Builder(String url) {
                this.url = url;
            }

            public Builder withHeader(String key, String value) {

                if (headers == null) {
                    headers = new HashMap<>();
                }

                headers.put(key, value);
                return this;
            }

              public Builder withHeaders(Map<String, String> headers) {

                if (this.headers == null) {
                    this.headers = new HashMap<>();
                }

                this.headers.putAll(headers);
                return this;
            }

            public Endpoint build() {
                return new Endpoint(this.url, this.headers);
            }
        }


        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(this.url);
            dest.writeInt(this.headers.size());
            for (Map.Entry<String, String> entry : this.headers.entrySet()) {
                dest.writeString(entry.getKey());
                dest.writeString(entry.getValue());
            }
        }

        protected Endpoint(Parcel in) {
            this.url = in.readString();
            int headersSize = in.readInt();
            this.headers = new HashMap<String, String>(headersSize);
            for (int i = 0; i < headersSize; i++) {
                String key = in.readString();
                String value = in.readString();
                this.headers.put(key, value);
            }
        }

        public static final Creator<Endpoint> CREATOR = new Creator<Endpoint>() {
            @Override
            public Endpoint createFromParcel(Parcel source) {
                return new Endpoint(source);
            }

            @Override
            public Endpoint[] newArray(int size) {
                return new Endpoint[size];
            }
        };

        @Override
        public String toString() {
            return "{url:" + url+"}";
        }
    }

    private OpenLocate(Configuration configuration) {
            this.context = configuration.context;
            this.endpoints = configuration.endpoints;
            this.configuration = configuration;
            setPreferences();
    }

    private void setPreferences() {
        SharedPreferences preferences = context.getSharedPreferences(Constants.OPENLOCATE, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        try {
            editor.putString(Constants.ENDPOINTS_KEY, Endpoint.toJson(configuration.getEndpoints()));

        } catch (JSONException e) {
            e.printStackTrace();
        }
        editor.apply();
    }

    public static OpenLocate initialize(Configuration configuration) {

        saveConfiguration(configuration);

        if (sharedInstance == null) {
            sharedInstance = new OpenLocate(configuration);
        }

        boolean trackingEnabled = SharedPreferenceUtils.getInstance(configuration.context).getBoolanValue(Constants.TRACKING_STATUS, false);

        if (trackingEnabled && LocationService.hasLocationPermission(configuration.context) &&
                sharedInstance.isGooglePlayServicesAvailable() == ConnectionResult.SUCCESS) {
            sharedInstance.onPermissionsGranted();
        }

        return sharedInstance;
    }

    public static OpenLocate getInstance() throws IllegalStateException {
        if (sharedInstance == null) {
            throw new IllegalStateException("OpenLate SDK must be initialized using initialize method");
        }
        return sharedInstance;
    }

    @Override
    public void startTracking(Activity activity)  {

        if (configuration == null) {
            return;
        }

        int resultCode = isGooglePlayServicesAvailable();
        if (resultCode != ConnectionResult.SUCCESS) {
            GoogleApiAvailability.getInstance().getErrorDialog(activity, resultCode, PLAY_SERVICES_RESOLUTION_REQUEST).show();
            return;
        }

        SharedPreferenceUtils.getInstance(context).setValue(Constants.TRACKING_STATUS, true);

        if (LocationService.hasLocationPermission(context)) {
            onPermissionsGranted();
        } else if (activity != null) {
            ActivityCompat.requestPermissions(
                    activity,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
            startCheckingPermissionTask();
        } else {
            Log.w(TAG, "Location Permission has not been accepted or prompted.");
        }
    }

    private int isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        return apiAvailability.isGooglePlayServicesAvailable(context);
    }

    void startCheckingPermissionTask() {

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {

                if (LocationService.hasLocationPermission(context)) {
                    onPermissionsGranted();
                    this.cancel();
                }

            }
        }, 5 * 1000, 5 * 1000);

    }

    void onPermissionsGranted() {

        FetchAdvertisingInfoTask task = new FetchAdvertisingInfoTask(context, new FetchAdvertisingInfoTaskCallback() {
            @Override
            public void onAdvertisingInfoTaskExecute(AdvertisingIdClient.Info info) {
                onFetchAdvertisingInfo(info);
            }
        });
        task.execute();
    }


    @Override
    public void getCurrentLocation(final OpenLocateLocationCallback callback) throws LocationDisabledException, LocationPermissionException {
        validateLocationEnabled();

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context);
        try {
            fusedLocationProviderClient.getLastLocation()
                    .addOnSuccessListener(new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(final Location location) {
                            if (location == null) {
                                callback.onError(new Error("Location cannot be fetched right now."));
                            }

                            onFetchCurrentLocation(location, callback);
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            callback.onError(new Error(e.getMessage()));
                        }
                    });
        } catch (SecurityException e) {
            throw new LocationPermissionException(
                    "Location permission is denied. Please enable location permission."
            );
        }
    }

    private void onFetchCurrentLocation(final Location location, final OpenLocateLocationCallback callback) {
        FetchAdvertisingInfoTask task = new FetchAdvertisingInfoTask(context, new FetchAdvertisingInfoTaskCallback() {
            @Override
            public void onAdvertisingInfoTaskExecute(AdvertisingIdClient.Info info) {

                callback.onLocationFetch(
                        OpenLocateLocation.from(
                                location,
                                info,
                                InformationFieldsFactory.collectInformationFields(context, configuration)
                        )
                );
            }
        });
        task.execute();
    }

    private void onFetchAdvertisingInfo(AdvertisingIdClient.Info info) {

        advertisingIdInfo = info;
        buildGoogleApiClient();
        networkManager = GcmNetworkManager.getInstance(context);

//        Intent intent = new Intent(context, LocationService.class);
//
//        intent.putParcelableArrayListExtra(Constants.ENDPOINTS_KEY, endpoints);
//
//        updateLocationConfigurationInfo(intent);
//        updateFieldsConfigurationInfo(intent);
//
//        if (info != null) {
//            updateAdvertisingInfo(intent, info.getId(), info.isLimitAdTrackingEnabled());
//        }
//
//        try {
//            context.startService(intent);
//            setStartedPreferences();
//        } catch (SecurityException e) {
//            Log.e(TAG, "Could not start location service");
//        }
    }

    private void setStartedPreferences() {
        SharedPreferences preferences = context.getSharedPreferences(Constants.OPENLOCATE, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(Constants.IS_SERVICE_STARTED, true);
        editor.apply();
    }

    private void updateFieldsConfigurationInfo(Intent intent) {
       intent.putExtra(Constants.INTENT_CONFIGURATION,configuration);
    }

    private void updateAdvertisingInfo(Intent intent, String advertisingId, boolean isLimitedAdTrackingEnabled) {
        intent.putExtra(Constants.ADVERTISING_ID_KEY, advertisingId);
        intent.putExtra(Constants.LIMITED_AD_TRACKING_ENABLED_KEY, isLimitedAdTrackingEnabled);
    }

    private void updateLocationConfigurationInfo(Intent intent) {
        intent.putExtra(Constants.LOCATION_ACCURACY_KEY, accuracy);
        intent.putExtra(Constants.LOCATION_INTERVAL_KEY, locationInterval);
        intent.putExtra(Constants.TRANSMISSION_INTERVAL_KEY, transmissionInterval);
    }

    private static void saveConfiguration(Configuration configuration) throws InvalidConfigurationException {
        if (configuration.endpoints.isEmpty()) {
            String message = "Invalid configuration. Please configure a valid urls";

            Log.e(TAG, message);
            throw new InvalidConfigurationException(
                    message
            );
        }

        try {
            String endpoins = Endpoint.toJson(configuration.endpoints);
            SharedPreferenceUtils.getInstance(configuration.context).setValue(Constants.ENDPOINTS_KEY, endpoins);

        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    private void validateLocationEnabled() throws LocationDisabledException {
        if (!LocationService.isLocationEnabled(context)) {
            String message = "Location is switched off in the settings. Please enable it before continuing.";

            Log.e(TAG, message);
            throw new LocationDisabledException(
                    message
            );
        }
    }

    @Override
    public void stopTracking() {
        SharedPreferenceUtils.getInstance(context).setValue(Constants.TRACKING_STATUS, false);
        //Intent intent = new Intent(context, LocationService.class);
        //context.stopService(intent);
        removeLocationUpdates();
        unschedulePeriodicTasks();
    }

    @Override
    public boolean isTracking() {
        return SharedPreferenceUtils.getInstance(context).getBoolanValue(Constants.TRACKING_STATUS, false);
    }

    public long getLocationInterval() {
        return locationInterval;
    }

    public OpenLocate.Configuration getConfiguration() {
        return configuration;
    }

    public AdvertisingIdClient.Info getAdvertisingIdInfo() {
        return advertisingIdInfo;
    }

    public void setLocationInterval(long locationInterval) {
        this.locationInterval = locationInterval;
        broadcastLocationIntervalChanged();
    }

    public long getTransmissionInterval() {
        return transmissionInterval;
    }

    public void setTransmissionInterval(long transmissionInterval) {
        this.transmissionInterval = transmissionInterval;
        broadcastTransmissionIntervalChanged();
    }

    public LocationAccuracy getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(LocationAccuracy accuracy) {
        this.accuracy = accuracy;
        broadcastLocationAccuracyChanged();
    }

    private void broadcastLocationIntervalChanged() {
        Intent intent = new Intent(Constants.LOCATION_INTERVAL_CHANGED);
        intent.putExtra(Constants.LOCATION_INTERVAL_KEY, locationInterval);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    private void broadcastTransmissionIntervalChanged() {
        Intent intent = new Intent(Constants.TRANSMISSION_INTERVAL_CHANGED);
        intent.putExtra(Constants.TRANSMISSION_INTERVAL_KEY, transmissionInterval);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    private void broadcastLocationAccuracyChanged() {
        Intent intent = new Intent(Constants.LOCATION_ACCURACY_CHANGED);
        intent.putExtra(Constants.LOCATION_ACCURACY_KEY, accuracy);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    private void buildGoogleApiClient() {
        if (mGoogleApiClient != null) {
            return;
        }
        mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addConnectionCallbacks(this)
                .addApi(LocationServices.API)
                .build();
        createLocationRequest();
        mGoogleApiClient.connect();
    }

    private void createLocationRequest() {
        mLocationRequest = new LocationRequest();

        mLocationRequest.setInterval(UPDATE_INTERVAL);

        // Sets the fastest rate for active location updates. This interval is exact, and your
        // application will never receive updates faster than this value.
        mLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        // Sets the maximum time when batched location updates are delivered. Updates may be
        // delivered sooner than this interval.
        mLocationRequest.setMaxWaitTime(MAX_WAIT_TIME);
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.i(TAG, "GoogleApiClient connected");

        requestLocationUpdates();
        scheduleDispatchLocationService();
        setStartedPreferences();
    }

    private PendingIntent getPendingIntent() {
        Intent intent = new Intent(context, LocationUpdatesBroadcastReceiver.class);
        intent.setAction(LocationUpdatesBroadcastReceiver.ACTION_PROCESS_UPDATES);
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void onConnectionSuspended(int i) {
        final String text = "Connection suspended";
        Log.w(TAG, text + ": Error code: " + i);
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        final String text = "Exception while connecting to Google Play services";
        Log.w(TAG, text + ": " + connectionResult.getErrorMessage());
    }

    public void requestLocationUpdates() {
        try {
            Log.i(TAG, "Starting location updates");
            LocationServices.FusedLocationApi.requestLocationUpdates(
                    mGoogleApiClient, mLocationRequest, getPendingIntent());
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    public void removeLocationUpdates() {
        Log.i(TAG, "Removing location updates");
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient,
                getPendingIntent());
    }

    private void scheduleDispatchLocationService() {

        if (endpoints == null) {
            return;
        }

        if (networkManager == null) {
            Log.w(TAG, "Network Manager is null");
            return;
        }

        Bundle bundle = new Bundle();
        try {
            bundle.putString(Constants.ENDPOINTS_KEY, OpenLocate.Endpoint.toJson(endpoints));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        PeriodicTask task = new PeriodicTask.Builder()
                .setExtras(bundle)
                .setService(DispatchLocationService.class)
                .setPeriod(getTransmissionInterval())
                .setRequiredNetwork(Task.NETWORK_STATE_CONNECTED)
                .setRequiresCharging(false)
                .setPersisted(true)
                .setUpdateCurrent(true)
                .setTag(LOCATION_DISPATCH_TAG)
                .build();

        try {
            networkManager.schedule(task);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Google Play Services is not up to date.");
            removeLocationUpdates();
        }
    }

    private void unschedulePeriodicTasks() {
        if (networkManager != null) {
            networkManager.cancelAllTasks(DispatchLocationService.class);
        }
    }
}
