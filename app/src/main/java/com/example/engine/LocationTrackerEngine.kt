package com.example.engine

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import com.example.data.model.GeoCoordinate
import com.example.data.model.LocationStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LocationTrackerEngine(context: Context) : LocationListener {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _status = MutableStateFlow(LocationStatus())
    val status: StateFlow<LocationStatus> = _status.asStateFlow()

    fun updatePermission(granted: Boolean) {
        _status.value = _status.value.copy(hasPermission = granted)
        if (granted) {
            startTracking()
        } else {
            stopTracking()
        }
    }

    @SuppressLint("MissingPermission")
    fun startTracking() {
        if (!_status.value.hasPermission) return
        if (_status.value.isTracking) return

        val gpsEnabled = runCatching { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
        val netEnabled = runCatching { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)
        val anyProvider = gpsEnabled || netEnabled

        if (!anyProvider) {
            _status.value = _status.value.copy(isProviderAvailable = false, isTracking = false)
            return
        }

        // Seed with last known location immediately if available
        val lastGps = if (gpsEnabled) runCatching { locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull() else null
        val lastNet = if (netEnabled) runCatching { locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull() else null
        val bestInitial = when {
            lastGps != null && lastNet != null -> if (lastGps.time >= lastNet.time) lastGps else lastNet
            lastGps != null -> lastGps
            else -> lastNet
        }
        if (bestInitial != null) {
            onLocationChanged(bestInitial)
        }

        if (gpsEnabled) {
            runCatching {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    3000L,
                    2f,
                    this,
                    Looper.getMainLooper()
                )
            }
        }
        if (netEnabled) {
            runCatching {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    5000L,
                    5f,
                    this,
                    Looper.getMainLooper()
                )
            }
        }

        _status.value = _status.value.copy(isTracking = true, isProviderAvailable = true)
    }

    fun stopTracking() {
        runCatching {
            locationManager.removeUpdates(this)
        }
        _status.value = _status.value.copy(isTracking = false)
    }

    override fun onLocationChanged(location: Location) {
        val coord = GeoCoordinate(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = location.accuracy,
            altitudeMeters = if (location.hasAltitude()) location.altitude else null,
            provider = location.provider ?: "gps",
            updatedAtMs = System.currentTimeMillis()
        )
        _status.value = _status.value.copy(
            isProviderAvailable = true,
            currentCoordinate = coord
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    override fun onProviderEnabled(provider: String) {
        _status.value = _status.value.copy(isProviderAvailable = true)
    }

    override fun onProviderDisabled(provider: String) {}
}
