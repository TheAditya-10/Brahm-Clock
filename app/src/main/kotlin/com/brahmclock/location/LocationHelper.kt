package com.brahmclock.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import com.brahmclock.core.Cities
import com.brahmclock.core.GeoPoint
import java.util.Locale

/** Coarse location through the platform LocationManager (no Play Services), labelled from the offline city list. */
object LocationHelper {
    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Calls [onResult] with a fix, or null if location is off/unavailable. Uses a recent cached fix when there is one. */
    @SuppressLint("MissingPermission")
    fun locate(context: Context, onResult: (GeoPoint?) -> Unit) {
        if (!hasPermission(context)) return onResult(null)
        val lm = context.getSystemService(LocationManager::class.java)
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }

        val cached = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if (cached != null && System.currentTimeMillis() - cached.time < 6 * 60 * 60_000L) {
            return onResult(toPoint(cached))
        }
        val provider = providers.firstOrNull { it != LocationManager.PASSIVE_PROVIDER }
            ?: return onResult(cached?.let(::toPoint))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            lm.getCurrentLocation(provider, null, context.mainExecutor) { loc ->
                onResult((loc ?: cached)?.let(::toPoint))
            }
        } else {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) = onResult(toPoint(location))
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = onResult(cached?.let(::toPoint))
            }
            @Suppress("DEPRECATION")
            lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        }
    }

    private fun toPoint(loc: Location): GeoPoint {
        val label = Cities.nearest(loc.latitude, loc.longitude)?.label
            ?: String.format(Locale.ENGLISH, "%.2f, %.2f", loc.latitude, loc.longitude)
        return GeoPoint(loc.latitude, loc.longitude, label)
    }
}
