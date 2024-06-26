package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;
import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import org.springframework.stereotype.Service;
import rewardCentral.RewardCentral;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Service
public class RewardsService {
    private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;
    private final GpsUtil gpsUtil;
    private final RewardCentral rewardsCentral;
    // proximity in miles
    private int defaultProximityBuffer = 10;
    private int proximityBuffer = defaultProximityBuffer;
    private int attractionProximityRange = 1000000;

    private List<Attraction> attractionsCache = new CopyOnWriteArrayList<>();

    public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
        this.gpsUtil = gpsUtil;
        this.rewardsCentral = rewardCentral;
    }

    public List<Attraction> getAttractions() {

        if (attractionsCache.isEmpty()) {
            synchronized (this) {
                if (attractionsCache.isEmpty()) {
                    List<Attraction> attractions = gpsUtil.getAttractions();
                    attractionsCache.addAll(attractions);
                }
            }
        }
        return new ArrayList<>(attractionsCache);
    }

    public void setProximityBuffer(int proximityBuffer) {
        this.proximityBuffer = proximityBuffer;
    }

    public void setDefaultProximityBuffer() {
        proximityBuffer = defaultProximityBuffer;
    }

/*    public void calculateRewards(User user) {
        List<VisitedLocation> userLocations = user.getVisitedLocations();
        List<Attraction> attractions = this.getAttractions();


        userLocations.forEach(visitedLocation -> attractions.forEach(attraction -> {
            if (user.getUserRewards().stream().noneMatch(r -> r.attraction.attractionName.equals(attraction.attractionName))) {
                if (nearAttraction(visitedLocation, attraction)) {
                    int rewardPoints = getRewardPoints(attraction, user);
                    user.addUserReward(new UserReward(visitedLocation, attraction, rewardPoints));
                }
            }
        }));
    }*/

/*    public void calculateRewards(User user) {
        List<VisitedLocation> userLocations = user.getVisitedLocations();
        List<Attraction> attractions = this.getAttractions();
        ExecutorService executorService = Executors.newFixedThreadPool(10);

        userLocations.forEach(visitedLocation -> attractions.forEach(attraction -> {
            Runnable task = () -> {
                if (user.getUserRewards().stream().noneMatch(r -> r.attraction.attractionName.equals(attraction.attractionName))) {
                    if (nearAttraction(visitedLocation, attraction)) {
                        int rewardPoints = getRewardPoints(attraction, user);
                            user.addUserReward(new UserReward(visitedLocation, attraction, rewardPoints));
                    }
                }
            };
            executorService.submit(task);
        }));

        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }*/

    public CompletableFuture<Void> calculateRewards(User user) {
        List<VisitedLocation> userLocations = user.getVisitedLocations();
        List<Attraction> attractions = this.getAttractions();
        ExecutorService executorService = Executors.newFixedThreadPool(10);

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        userLocations.forEach(visitedLocation -> attractions.forEach(attraction -> {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                if (user.getUserRewards().stream().noneMatch(r -> r.attraction.attractionName.equals(attraction.attractionName))) {
                    if (nearAttraction(visitedLocation, attraction)) {
                        int rewardPoints = getRewardPoints(attraction, user);
                        user.addUserReward(new UserReward(visitedLocation, attraction, rewardPoints));
                    }
                }
            }, executorService);
            futures.add(future);
        }));

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(executorService::shutdown);
    }



    public static void visitedLocationCheck() {

    }

    public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
        return getDistance(attraction, location) > attractionProximityRange ? false : true;
    }

    private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
        return getDistance(attraction, visitedLocation.location) > proximityBuffer ? false : true;
    }

    private int getRewardPoints(Attraction attraction, User user) {
        return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
    }

    public double getDistance(Location loc1, Location loc2) {
        double lat1 = Math.toRadians(loc1.latitude);
        double lon1 = Math.toRadians(loc1.longitude);
        double lat2 = Math.toRadians(loc2.latitude);
        double lon2 = Math.toRadians(loc2.longitude);

        double angle = Math.acos(Math.sin(lat1) * Math.sin(lat2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.cos(lon1 - lon2));

        double nauticalMiles = 60 * Math.toDegrees(angle);
        double statuteMiles = STATUTE_MILES_PER_NAUTICAL_MILE * nauticalMiles;
        return statuteMiles;
    }

}
