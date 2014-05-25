package com.navidroidgms.model.vehiclemarker;

import android.os.Handler;

import com.navidroid.model.LatLng;
import com.navidroid.model.map.NavigationMap;
import com.navidroid.model.vehicle.IVehicleMarker;
import com.navidroid.model.vehicle.Vehicle;
import com.navidroidgms.Util;
import com.navidroidgms.model.map.GoogleMapWrapper;
import com.navidroidgms.model.map.GoogleMapWrapper.WhenGoogleMapReadyCallback;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

public class VehicleMarker implements IVehicleMarker {
	
	// Animation gets cancelled if we start straight away, due to internal GestureDetector.onSingleTapConfirmed listener.
	private final static int FOLLOW_VEHICLE_DELAY_MS = 500;
	
	private Marker marker;
	private GoogleMapWrapper map;
	private Handler handler;
	
	public VehicleMarker(final Vehicle vehicle, final NavigationMap navigationMap) {
		handler = new Handler();
		map = (GoogleMapWrapper)navigationMap.getMap();
		map.whenGoogleMapReady(new WhenGoogleMapReadyCallback() {
			@Override
			public void invoke(GoogleMap googleMap) {
				marker = googleMap.addMarker(new MarkerOptions()
						.position(Util.toGoogleLatLng(vehicle.getLocation()))
						.icon(BitmapDescriptorFactory.fromBitmap(vehicle.getImage()))
						.flat(true)
						.anchor(0.5f, 0.5f)
						.visible(false));

				googleMap.setOnMarkerClickListener(new OnMarkerClickListener() {
					@Override
					public boolean onMarkerClick(Marker candidateMarker) {
						if (candidateMarker.equals(marker)) {
							handler.postDelayed(new Runnable() {
								@Override
								public void run() {
									navigationMap.followVehicle();
								}
							}, FOLLOW_VEHICLE_DELAY_MS);
							return true;
						}
						return false;
					}
				});
			}
		});
	}

	@Override
	public void show() {
		map.whenGoogleMapReady(new WhenGoogleMapReadyCallback() {
			@Override
			public void invoke(GoogleMap googleMap) {
				marker.setVisible(true);
			}
		});
	}

	@Override
	public void hide() {
		map.whenGoogleMapReady(new WhenGoogleMapReadyCallback() {
			@Override
			public void invoke(GoogleMap googleMap) {
				marker.setVisible(false);
			}
		});
	}
	
	@Override
	public void setLocation(final LatLng location) {
		map.whenGoogleMapReady(new WhenGoogleMapReadyCallback() {
			@Override
			public void invoke(GoogleMap googleMap) {
				marker.setPosition(Util.toGoogleLatLng(location));
			}
		});
	}
	
	@Override
	public void setBearing(final double bearing) {
		map.whenGoogleMapReady(new WhenGoogleMapReadyCallback() {
			@Override
			public void invoke(GoogleMap googleMap) {
				marker.setRotation((float)bearing);
			}
		});
	}
}