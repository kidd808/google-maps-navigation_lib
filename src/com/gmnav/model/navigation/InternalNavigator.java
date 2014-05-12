package com.gmnav.model.navigation;

import java.io.InvalidObjectException;

import com.gmnav.NavigationFragment;
import com.gmnav.model.directions.Direction;
import com.gmnav.model.directions.Directions;
import com.gmnav.model.directions.Point;
import com.gmnav.model.directions.Route;
import com.gmnav.model.directions.Route.DirectionsRetrieved;
import com.gmnav.model.map.NavigationMap;
import com.gmnav.model.map.NavigationMap.MapMode;
import com.gmnav.model.positioning.AbstractSimulatedGps;
import com.gmnav.model.positioning.IGps;
import com.gmnav.model.positioning.Position;
import com.gmnav.model.positioning.IGps.OnTickHandler;
import com.gmnav.model.util.LatLngUtil;
import com.gmnav.model.vehicle.Vehicle;
import com.gmnav.model.vehicle.VehicleOptions;
import com.google.android.gms.maps.model.LatLng;
import android.util.Log;

public class InternalNavigator {
	
	private final int MIN_ARRIVAL_DIST_METERS = 10;
	private final int OFF_PATH_TOLERANCE_METERS = 10;
	private final int OFF_PATH_TOLERANCE_BEARING = 45;
	private final int MAX_TIME_OFF_PATH_MS = 5000;
	
	private NavigationMap map;
	private Vehicle vehicle;
	private IGps gps;
	private INavigatorStateListener navigatorStateListener;
	private Position position;
	private NavigationState navigationState;
	private NavigationState lastNavigationState;
	private LatLng destination;
	
	public InternalNavigator(NavigationFragment navigationFragment, final IGps gps, NavigationMap map, VehicleOptions vehicleMarkerOptions) {
		this.gps = gps;
		this.map = map;
		this.vehicle = new Vehicle(navigationFragment, map, vehicleMarkerOptions.location(gps.getLastLocation()));
		listenToGps();
	}
	
	private void listenToGps() {
		gps.onTick(new OnTickHandler() {
			@Override
			public void invoke(Position position) {
				onGpsTick(position);
			}
		});
		gps.enableTracking();
		gps.forceTick();
	}
	
	public void addNavigatorStateListener(INavigatorStateListener stateListener) {
		navigatorStateListener = stateListener;
	}

	public void go(final LatLng location) {
		Route request = new Route(position.location, location);
		request.getDirections(new DirectionsRetrieved() {
			@Override
			public void invoke(Directions directions) {
				destination = location;
				startNavigation(directions);
			}
		});
	}
	
	public void stop() {
		destination = null;
		navigationState = null;
		lastNavigationState = null;
		map.setMapMode(MapMode.FREE);
		map.removePolylinePath();
	}
	
	public boolean isNavigating() {
		return navigationState != null;
	}
	
	private void startNavigation(Directions directions) {
		if (!isNavigating()) {
			navigationState = new NavigationState(directions);
			map.addPathPolyline(directions.getLatLngPath());
			map.setMapMode(MapMode.FOLLOW);
			
			if (gps instanceof AbstractSimulatedGps) {
				((AbstractSimulatedGps)gps).followPath(directions.getLatLngPath());
			}
			
			navigatorStateListener.OnDeparture();
			navigatorStateListener.OnNewDirection(directions.getDirectionsList().get(1));
		}
	}
	
	private void onGpsTick(Position position) {
		this.position = position;
		if (isNavigating()) {
			try {
				navigationState.update(position);
			} catch (InvalidObjectException e) {
				e.printStackTrace();
				Log.e("Fatal exception in Navigator", e.getMessage());
			}
			checkArrival();
			checkDirectionChanged();
			checkOffPath();
			tickNavigator();
		}
		updateVehicleMarker();
	}
	
	private void checkArrival() {
		if (LatLngUtil.distanceInMeters(navigationState.getLocation(), destination) <= MIN_ARRIVAL_DIST_METERS) {
			stop();
			navigatorStateListener.OnArrival();
		}
	}
	
	private void checkDirectionChanged() {
		if (!isNavigating()) {
			return;
		}
		
		Point currentPoint = navigationState.getCurrentPoint();
		if (lastNavigationState != null) { 
			Point lastPoint = lastNavigationState.getCurrentPoint();
			if (currentPoint != lastPoint) {
				Direction currentDirection = currentPoint.nextDirection;
				if (currentDirection != lastPoint.nextDirection) {
					navigatorStateListener.OnNewDirection(currentDirection);
				}
			}
		}
	}
	
	private void checkOffPath() {
		if (!isNavigating()) {
			return;
		}
		
		if (navigationState.getDistanceOffPath() > OFF_PATH_TOLERANCE_METERS ||
				navigationState.getBearingDifferenceFromPath() > OFF_PATH_TOLERANCE_BEARING) {
			
			if (lastNavigationState != null && lastNavigationState.isOnPath()) {
				navigationState.signalOffPath();
			} else if (navigationState.getTime() - navigationState.getOffPathStartTime() > MAX_TIME_OFF_PATH_MS) {
				navigatorStateListener.OnVehicleOffPath();
			}
		} else {
			navigationState.signalOnPath();
		}
	}
	
	private void tickNavigator() {
		if (!isNavigating()) {
			return;
		}
		
		lastNavigationState = navigationState.snapshot();
		navigatorStateListener.OnNavigatorTick(navigationState);
	}
	
	private void updateVehicleMarker() {
		if (isNavigating()) {
			long timestamp = navigationState.getTime();
			vehicle.setPosition(navigationState.isOnPath() ?
					new Position(navigationState.getLocationOnPath(), navigationState.getBearingOnPath(), timestamp) :
					new Position(navigationState.getLocation(), navigationState.getBearing(), timestamp));
		} else {
			vehicle.setPosition(position);
		}
	}
}
