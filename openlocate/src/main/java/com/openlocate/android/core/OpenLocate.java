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
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.openlocate.android.callbacks.OpenLocateLocationCallback;
import com.openlocate.android.exceptions.InvalidConfigurationException;
import com.openlocate.android.exceptions.LocationDisabledException;
import com.openlocate.android.exceptions.LocationPermissionException;

import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class OpenLocate implements OpenLocateLocationTracker {

    private static final int LOCATION_PERMISSION_REQUEST = 1001;

    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

    private static OpenLocate sharedInstance = null;
    private static final String TAG = OpenLocate.class.getSimpleName();

    private Context context;
    private String serverUrl;
    private Configuration configuration;
    private HashMap<String, String> headers;

    private FusedLocationProviderClient fusedLocationProviderClient;

    private long locationInterval = Constants.DEFAULT_LOCATION_INTERVAL_SEC;
    private long transmissionInterval = Constants.DEFAULT_TRANSMISSION_INTERVAL_SEC;
    private LocationAccuracy accuracy = Constants.DEFAULT_LOCATION_ACCURACY;

    public static final class Configuration implements Parcelable {

        Context context = null;
        final String serverUrl;
        final HashMap<String, String> headers;

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
                return new Configuration(this);
            }
        }

        private Configuration(Builder builder) {
            this.context = builder.context;
            this.serverUrl = builder.serverUrl;
            this.headers = builder.headers;
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

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(this.serverUrl);
            dest.writeSerializable(this.headers);
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
            this.serverUrl = in.readString();
            this.headers = (HashMap<String, String>) in.readSerializable();
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

        public static final Parcelable.Creator<Configuration> CREATOR = new Parcelable.Creator<Configuration>() {
            @Override
            public Configuration createFromParcel(Parcel source) {
                return new Configuration(source);
            }

            @Override
            public Configuration[] newArray(int size) {
                return new Configuration[size];
            }
        };

        public String getUrl() {
            return serverUrl;
        }

        public HashMap<String, String> getHeaders() {
            return headers;
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
    }

    private OpenLocate(Configuration configuration) {
            this.context = configuration.context;
            this.serverUrl = configuration.serverUrl;
            this.headers = configuration.headers;
            this.configuration = configuration;
            setPreferences();
    }

    private void setPreferences() {
        SharedPreferences preferences = context.getSharedPreferences(Constants.OPENLOCATE, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(Constants.URL_KEY, configuration.getUrl());
        editor.apply();
    }

    public static OpenLocate initialize(Configuration configuration) {

        saveConfiguration(configuration);

        if (sharedInstance == null) {
            sharedInstance = new OpenLocate(configuration);
        }

        boolean trackingEnabled = SharedPreferenceUtils.getInstance(configuration.context).getBoolanValue(Constants.TRACKING_STATUS, false);

        if (trackingEnabled && LocationService.hasLocationPermission(configuration.context)) {
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

        int resultCode = isGooglePlayServicesAvailable();
        if (resultCode != ConnectionResult.SUCCESS) {
            GoogleApiAvailability.getInstance().getErrorDialog(activity, resultCode, PLAY_SERVICES_RESOLUTION_REQUEST).show();
            return;
        }

        SharedPreferenceUtils.getInstance(context).setValue(Constants.TRACKING_STATUS, true);

        if (LocationService.hasLocationPermission(context)) {
            onPermissionsGranted();
        } else {
            ActivityCompat.requestPermissions(
                    activity,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
            startCheckingPermissionTask();
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
        Intent intent = new Intent(context, LocationService.class);

        intent.putExtra(Constants.URL_KEY, serverUrl);
        intent.putExtra(Constants.HEADER_KEY, headers);
        updateLocationConfigurationInfo(intent);
        updateFieldsConfigurationInfo(intent);

        if (info != null) {
            updateAdvertisingInfo(intent, info.getId(), info.isLimitAdTrackingEnabled());
        }

        context.startService(intent);
        setStartedPreferences();
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
        if (TextUtils.isEmpty(configuration.serverUrl)) {
            String message = "Invalid configuration. Please configure a valid url or header.";

            Log.e(TAG, message);
            throw new InvalidConfigurationException(
                    message
            );
        }

        if(!TextUtils.isEmpty(configuration.getUrl())) {
            SharedPreferenceUtils.getInstance(configuration.context).setValue(Constants.URL_KEY, configuration.getUrl());
            SharedPreferenceUtils.getInstance(configuration.context).saveMap(Constants.HEADER_KEY, configuration.getHeaders());
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
        Intent intent = new Intent(context, LocationService.class);
        context.stopService(intent);
    }

    @Override
    public boolean isTracking() {
        return SharedPreferenceUtils.getInstance(context).getBoolanValue(Constants.TRACKING_STATUS, false);
    }

    public long getLocationInterval() {
        return locationInterval;
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
}
