package net.nikcain.showmehills2;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

public class LocationResolver implements LocationListener
{
    String provider;
    private RapidGPSLock locationMgrImpl;
    private LocationManager lm;

    public LocationResolver(LocationManager lm, String provider, RapidGPSLock locationMgrImpl){
        this.lm = lm;
        this.provider = provider;
        this.locationMgrImpl = locationMgrImpl;
    }

    public void onLocationChanged(Location location) {
        lm.removeUpdates(this);
        locationMgrImpl.locationCallback(provider);
    }

    public void onProviderDisabled(String provider) {
    }

    public void onProviderEnabled(String provider) {
    }

    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

}
