package com.applitools.eyes.visualGridClient.model;

import com.applitools.eyes.RectangleSize;
import com.applitools.eyes.Region;

import java.util.List;

public class RenderStatusResults {

    private RenderStatus status = null;

    private String domLocation = null;

    private String userAgent = null;

    private String imageLocation = null;

    private String os = null;

    private String error = null;

    private List<Region> selectorRegions = null;

    private RectangleSize deviceSize = null;

    public RenderStatus getStatus() {
        return status;
    }

    public void setStatus(RenderStatus status) {
        this.status = status;
    }

    public String getDomLocation() {
        return domLocation;
    }

    public void setDomLocation(String domLocation) {
        this.domLocation = domLocation;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getImageLocation() {
        return imageLocation;
    }

    public void setImageLocation(String imageLocation) {
        this.imageLocation = imageLocation;
    }

    public String getOs() {
        return os;
    }

    public void setOs(String os) {
        this.os = os;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public List<Region> getSelectorRegions() {
        return selectorRegions;
    }

    public void setSelectorRegions(List<Region> selectorRegions) {
        this.selectorRegions = selectorRegions;
    }

    public RectangleSize getDeviceSize() {
        return deviceSize;
    }

    public void setDeviceSize(RectangleSize deviceSize) {
        this.deviceSize = deviceSize;
    }

    @Override
    public String toString() {
        return "RenderStatusResults{" +
                "status=" + status +
                ", domLocation='" + domLocation + '\'' +
                ", userAgent='" + userAgent + '\'' +
                ", imageLocation='" + imageLocation + '\'' +
                ", os='" + os + '\'' +
                ", error='" + error + '\'' +
                ", selectorRegions=" + selectorRegions +
                ", deviceSize=" + deviceSize +
                '}';
    }

    public boolean isEmpty() {
        return (selectorRegions == null || selectorRegions.isEmpty()) &&
                status == null &&
                imageLocation == null &&
                error == null &&
                os == null &&
                userAgent == null &&
                deviceSize == null &&
                selectorRegions == null;

    }

}
