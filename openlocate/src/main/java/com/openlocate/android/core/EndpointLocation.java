package com.openlocate.android.core;

import java.util.List;

public class EndpointLocation {

    private OpenLocate.Endpoint endPoint;

    private List<OpenLocateLocation> locations;

    public OpenLocate.Endpoint getEndPoint() {
        return endPoint;
    }

    public List<OpenLocateLocation> getLocations() {
        return locations;
    }

    public EndpointLocation(OpenLocate.Endpoint endPoint, List<OpenLocateLocation> locations) {
        this.endPoint = endPoint;
        this.locations = locations;
    }
}
