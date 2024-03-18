package com.openclassrooms.tourguide.DTO;

import gpsUtil.location.Location;

public class NearbyAttractionsDTO {
    public String attractionName;
    public Location attractionLocation;
    public Location userLocation;
    public double distanceBetweenAttraction;
    public int rewardPointForAttraction;

    public String getAttractionName() {
        return attractionName;
    }

    public void setAttractionName(String attractionName) {
        this.attractionName = attractionName;
    }

    public Location getAttractionLocation() {
        return attractionLocation;
    }

    public void setAttractionLocation(Location attractionLocation) {
        this.attractionLocation = attractionLocation;
    }

    public Location getUserLocation() {
        return userLocation;
    }

    public void setUserLocation(Location userLocation) {
        this.userLocation = userLocation;
    }

    public Double getDistanceBetweenAttraction() {
        return distanceBetweenAttraction;
    }

    public void setDistanceBetweenAttraction(Double distanceBetweenAttraction) {
        this.distanceBetweenAttraction = distanceBetweenAttraction;
    }

    public int getRewardPointForAttraction() {
        return rewardPointForAttraction;
    }

    public void setRewardPointForAttraction(int rewardPointForAttraction) {
        this.rewardPointForAttraction = rewardPointForAttraction;
    }
}
