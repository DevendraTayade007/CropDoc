package com.example.cropdoc.crop_suggestion.presentation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.example.cropdoc.crop_suggestion.data.models.LocationData
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class LocationUtils(val context : Context) {

    private val _fusedLocationClient: FusedLocationProviderClient
            = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    fun requestCurrentLocation(onLocation: (LocationData?) -> Unit) {
        _fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                onLocation(LocationData(location.latitude, location.longitude))
            } else {
                val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                    .setMaxUpdates(1).build()
                val callback = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        _fusedLocationClient.removeLocationUpdates(this)
                        onLocation(result.lastLocation?.let { LocationData(it.latitude, it.longitude) })
                    }
                }
                _fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            }
        }
    }

    fun hasLocationPermission(context : Context):Boolean{

        return ContextCompat.checkSelfPermission(context,
            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                ||
                ContextCompat.checkSelfPermission(context,
                    Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun reverseGeocodeLocation(location: LocationData): String? {
        val geocoder = Geocoder(context, Locale.getDefault())
        return geocoder.getAddress(location)?.getAddressLine(0)
    }

    private suspend fun Geocoder.getAddress(
        location: LocationData
    ): Address? = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCoroutine { cont ->
                    getFromLocation(location.latitude, location.longitude, 1) {
                        cont.resume(it.firstOrNull())
                    }
                }
            } else {
                suspendCoroutine { cont ->
                    @Suppress("DEPRECATION")
                    val address = getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()
                    cont.resume(address)
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
