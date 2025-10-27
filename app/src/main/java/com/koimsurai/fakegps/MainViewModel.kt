package com.koimsurai.fakegps

import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import java.io.IOException

data class MainUiState(
    val selectedPoint: GeoPoint? = null,
    val isMocking: Boolean = false,
    val isServiceRunning: Boolean = false,
    val favorites: Map<String, GeoPoint> = emptyMap(),
    val mapVisible: Boolean = true,
    val searchInput: String = ""
)

class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()

    fun setServiceState(isRunning: Boolean) {
        _uiState.update { it.copy(isServiceRunning = isRunning, isMocking = isRunning) }
    }

    fun setSearchInput(input: String) {
        _uiState.update { it.copy(searchInput = input) }
    }

    fun selectPoint(geoPoint: GeoPoint) {
        _uiState.update { it.copy(selectedPoint = geoPoint) }
    }

    fun toggleMapVisibility() {
        _uiState.update { it.copy(mapVisible = !it.mapVisible) }
    }

    fun startMockLocation(context: Context) {
        val point = _uiState.value.selectedPoint
        if (point == null) {
            Toast.makeText(context, context.getString(R.string.toast_select_location_first), Toast.LENGTH_SHORT).show()
            return
        }

        val serviceIntent = Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_START_MOCK
            putExtra(MockLocationService.EXTRA_LATITUDE, point.latitude)
            putExtra(MockLocationService.EXTRA_LONGITUDE, point.longitude)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
        _uiState.update { it.copy(isMocking = true) }
    }

    fun stopMockLocation(context: Context) {
        val serviceIntent = Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_STOP_MOCK
        }
        context.startService(serviceIntent)
        _uiState.update { it.copy(isMocking = false) }
    }

    fun searchAddress(context: Context) {
        val addressString = _uiState.value.searchInput
        if (addressString.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.search_address_hint), Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            try {
                val geocoder = Geocoder(context)
                val addresses = geocoder.getFromLocationName(addressString, 1)
                if (addresses != null && addresses.isNotEmpty()) {
                    val address = addresses[0]
                    val geoPoint = GeoPoint(address.latitude, address.longitude)
                    _uiState.update {
                        it.copy(
                            selectedPoint = geoPoint,
                            searchInput = "${address.latitude}, ${address.longitude}"
                        )
                    }
                } else {
                    Toast.makeText(context, context.getString(R.string.toast_address_not_found), Toast.LENGTH_SHORT).show()
                }
            } catch (e: IOException) {
                Toast.makeText(context, context.getString(R.string.toast_geocoder_not_available), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun addFavorite(name: String, context: Context) {
        if (_uiState.value.selectedPoint == null) {
            Toast.makeText(context, context.getString(R.string.toast_select_location_first), Toast.LENGTH_SHORT).show()
            return
        }
        if (name.isNotEmpty()) {
            val newFavorites = _uiState.value.favorites.toMutableMap()
            newFavorites[name] = _uiState.value.selectedPoint!!
            _uiState.update { it.copy(favorites = newFavorites) }
            saveFavorites(context)
            Toast.makeText(context, context.getString(R.string.toast_favorite_saved), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, context.getString(R.string.toast_name_cannot_be_empty), Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteFavorite(name: String, context: Context) {
        val newFavorites = _uiState.value.favorites.toMutableMap()
        newFavorites.remove(name)
        _uiState.update { it.copy(favorites = newFavorites) }
        saveFavorites(context)
        Toast.makeText(context, context.getString(R.string.toast_favorite_deleted), Toast.LENGTH_SHORT).show()
    }

    fun loadFavorites(context: Context) {
        val prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)
        val favoriteSet = prefs.getStringSet("locations", emptySet()) ?: emptySet()
        val loadedFavorites = mutableMapOf<String, GeoPoint>()
        favoriteSet.forEach {
            val parts = it.split("|")
            if (parts.size == 3) {
                loadedFavorites[parts[0]] = GeoPoint(parts[1].toDouble(), parts[2].toDouble())
            }
        }
        _uiState.update { it.copy(favorites = loadedFavorites) }
    }

    private fun saveFavorites(context: Context) {
        val prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE).edit()
        val favoriteSet = _uiState.value.favorites.map { "${it.key}|${it.value.latitude}|${it.value.longitude}" }.toSet()
        prefs.putStringSet("locations", favoriteSet)
        prefs.apply()
    }

    fun saveLastLocation(context: Context) {
        _uiState.value.selectedPoint?.let {
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit()
            prefs.putFloat("last_lat", it.latitude.toFloat())
            prefs.putFloat("last_lon", it.longitude.toFloat())
            prefs.apply()
        }
    }

    fun loadLastLocation(context: Context) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val lat = prefs.getFloat("last_lat", -999f)
        val lon = prefs.getFloat("last_lon", -999f)
        if (lat != -999f && lon != -999f) {
            _uiState.update { it.copy(selectedPoint = GeoPoint(lat.toDouble(), lon.toDouble())) }
        } else {
            _uiState.update { it.copy(selectedPoint = GeoPoint(25.0330, 121.5654)) } // Default to Taipei 101
        }
    }
}

class MockLocationProvider(providerName: String, context: Context) {
    private val locationManager: LocationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val providerName: String

    init {
        this.providerName = providerName
        try {
            locationManager.addTestProvider(providerName, false, false, false, false, true, true, true, 1, 2)
            locationManager.setTestProviderEnabled(providerName, true)
        } catch (e: SecurityException) {
            throw e
        }
    }

    fun pushLocation(lat: Double, lon: Double) {
        val mockLocation = Location(providerName)
        mockLocation.latitude = lat
        mockLocation.longitude = lon
        mockLocation.altitude = 0.0
        mockLocation.time = System.currentTimeMillis()
        mockLocation.accuracy = 1f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            mockLocation.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        locationManager.setTestProviderLocation(providerName, mockLocation)
    }

    fun shutdown() {
        locationManager.removeTestProvider(providerName)
    }
}