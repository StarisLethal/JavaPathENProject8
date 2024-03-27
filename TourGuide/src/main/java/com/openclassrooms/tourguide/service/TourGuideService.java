package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.DTO.NearbyAttractionsDTO;
import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.tracker.Tracker;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;
import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import rewardCentral.RewardCentral;
import tripPricer.Provider;
import tripPricer.TripPricer;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class TourGuideService {
	private Logger logger = LoggerFactory.getLogger(TourGuideService.class);
	private final GpsUtil gpsUtil;
	private final RewardsService rewardsService;
	private final RewardCentral rewardCentral;
	private final TripPricer tripPricer = new TripPricer();
	public final Tracker tracker;
	boolean testMode = true;

	public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsService = rewardsService;
		this.rewardCentral = rewardCentral;

		Locale.setDefault(Locale.US);

		if (testMode) {
			logger.info("TestMode enabled");
			logger.debug("Initializing users");
			initializeInternalUsers();
			logger.debug("Finished initializing users");
		}
		tracker = new Tracker(this);
		addShutDownHook();
	}

	public List<UserReward> getUserRewards(User user) {
		return user.getUserRewards();
	}

	public VisitedLocation getUserLocation(User user) {
		VisitedLocation visitedLocation = (user.getVisitedLocations().size() > 0) ? user.getLastVisitedLocation()
				: trackUserLocation(user);
		return visitedLocation;
	}

	public User getUser(String userName) {
		return internalUserMap.get(userName);
	}

	public List<User> getAllUsers() {
		return internalUserMap.values().stream().collect(Collectors.toList());
	}

	public void addUser(User user) {
		if (!internalUserMap.containsKey(user.getUserName())) {
			internalUserMap.put(user.getUserName(), user);
		}
	}

	public List<Provider> getTripDeals(User user) {
		int cumulatativeRewardPoints = user.getUserRewards().stream().mapToInt(i -> i.getRewardPoints()).sum();
		List<Provider> providers = tripPricer.getPrice(tripPricerApiKey, user.getUserId(),
				user.getUserPreferences().getNumberOfAdults(), user.getUserPreferences().getNumberOfChildren(),
				user.getUserPreferences().getTripDuration(), cumulatativeRewardPoints);
		user.setTripDeals(providers);
		return providers;
	}

	public void trackUserLocations(List<User> users) {
		users.parallelStream().forEach(this::trackUserLocation);
	}

	public VisitedLocation trackUserLocation(User user) {

		int numberOfCores = Runtime.getRuntime().availableProcessors();
		int poolSize = numberOfCores + 1;
		ExecutorService executor = Executors.newFixedThreadPool(poolSize);

		Callable<VisitedLocation> task = () -> {
			try {
				VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
				user.addToVisitedLocations(visitedLocation);
				CompletableFuture<Void> future = rewardsService.calculateRewards(user);
				future.join();
				return visitedLocation;
			} catch (Exception e) {
				throw new RuntimeException("Failed to track user location", e);
			}
		};
		Future<VisitedLocation> future = executor.submit(task);
		try {
			return future.get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Thread was interrupted", e);
		} catch (ExecutionException e) {
			throw new RuntimeException("Error executing task", e);
		}
	}

	public List<NearbyAttractionsDTO> getNearByAttractions(VisitedLocation visitedLocation) {
		List<NearbyAttractionsDTO> nearbyAttractions = new ArrayList<>();
		for (Attraction attraction : gpsUtil.getAttractions()) {
			NearbyAttractionsDTO nearbyAttractionsDTO = new NearbyAttractionsDTO();
			Location location = new Location(attraction.latitude, attraction.longitude);
			double distance = rewardsService.getDistance(visitedLocation.location, attraction);
			nearbyAttractionsDTO.setAttractionName(attraction.attractionName);
			nearbyAttractionsDTO.setAttractionLocation(location);
			nearbyAttractionsDTO.setUserLocation(visitedLocation.location);
			nearbyAttractionsDTO.setDistanceBetweenAttraction(distance);
			nearbyAttractionsDTO.setRewardPointForAttraction(rewardCentral.getAttractionRewardPoints(attraction.attractionId, visitedLocation.userId));
			nearbyAttractions.add(nearbyAttractionsDTO);

		}
		List<NearbyAttractionsDTO> nearbyAttractionsSorted = nearbyAttractions.stream()
				.sorted(Comparator.comparingDouble(NearbyAttractionsDTO::getDistanceBetweenAttraction))
				.limit(5)
				.collect(Collectors.toList());
		return nearbyAttractionsSorted;
	}


	private void addShutDownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				tracker.stopTracking();
			}
		});
	}

	/**********************************************************************************
	 * 
	 * Methods Below: For Internal Testing
	 * 
	 **********************************************************************************/
	private static final String tripPricerApiKey = "test-server-api-key";
	// Database connection will be used for external users, but for testing purposes
	// internal users are provided and stored in memory
	private final Map<String, User> internalUserMap = new HashMap<>();

	private void initializeInternalUsers() {
		IntStream.range(0, InternalTestHelper.getInternalUserNumber()).forEach(i -> {
			String userName = "internalUser" + i;
			String phone = "000";
			String email = userName + "@tourGuide.com";
			User user = new User(UUID.randomUUID(), userName, phone, email);
			generateUserLocationHistory(user);

			internalUserMap.put(userName, user);
		});
		logger.debug("Created " + InternalTestHelper.getInternalUserNumber() + " internal test users.");
	}

	private void generateUserLocationHistory(User user) {
		IntStream.range(0, 3).forEach(i -> {
			user.addToVisitedLocations(new VisitedLocation(user.getUserId(),
					new Location(generateRandomLatitude(), generateRandomLongitude()), getRandomTime()));
		});
	}

	private double generateRandomLongitude() {
		double leftLimit = -180;
		double rightLimit = 180;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private double generateRandomLatitude() {
		double leftLimit = -85.05112878;
		double rightLimit = 85.05112878;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private Date getRandomTime() {
		LocalDateTime localDateTime = LocalDateTime.now().minusDays(new Random().nextInt(30));
		return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
	}

}
