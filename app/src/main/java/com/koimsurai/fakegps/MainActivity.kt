// Author: koimsurai
//
package com.koimsurai.fakegps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var marker: Marker
    private lateinit var toggleButton: Button
    private lateinit var addressInput: EditText
    private lateinit var searchButton: Button
    private lateinit var favoriteButton: Button
    private lateinit var favoritesSpinner: Spinner
    private var mockLocationProvider: MockLocationProvider? = null
    private var selectedPoint: GeoPoint? = null
    private var isMocking = false

    private val favorites = mutableMapOf<String, GeoPoint>()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var mockLocationRunnable: Runnable

    private val permissionsRequestCode = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle permissions
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            "android.permission.ACCESS_MOCK_LOCATION"
        )
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), permissionsRequestCode)
        }

        // Load osmdroid configuration
        Configuration.getInstance().load(applicationContext, getSharedPreferences("osmdroid", MODE_PRIVATE))

        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.map)
        toggleButton = findViewById(R.id.toggle_mock_location)
        addressInput = findViewById(R.id.address_input)
        searchButton = findViewById(R.id.search_button)
        favoriteButton = findViewById(R.id.favorite_button)
        favoritesSpinner = findViewById(R.id.favorites_spinner)

        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        val mapController = mapView.controller
        mapController.setZoom(18.0)
        val startPoint = GeoPoint(25.0330, 121.5654)
        mapController.setCenter(startPoint)

        marker = Marker(mapView)
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.icon = ContextCompat.getDrawable(this, R.drawable.ic_marker)
        mapView.overlays.add(marker)

        val mapEventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                selectedPoint = p
                marker.position = p
                mapView.invalidate()
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean {
                return false
            }
        })
        mapView.overlays.add(0, mapEventsOverlay)

        toggleButton.setOnClickListener {
            if (isMocking) {
                stopMockLocation()
            } else {
                startMockLocation()
            }
        }

        searchButton.setOnClickListener {
            searchAddress()
        }

        favoriteButton.setOnClickListener {
            addFavorite()
        }

        loadFavorites()
        setupFavoritesSpinner()
    }

    private fun setupFavoritesSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, favorites.keys.toList())
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        favoritesSpinner.adapter = adapter

        favoritesSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                val selectedName = parent.getItemAtPosition(position) as String
                val geoPoint = favorites[selectedName]
                if (geoPoint != null) {
                    selectedPoint = geoPoint
                    marker.position = geoPoint
                    mapView.controller.animateTo(geoPoint)
                    mapView.invalidate()
                    addressInput.setText(selectedName)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun addFavorite() {
        if (selectedPoint == null) {
            Toast.makeText(this, "Please select a location first", Toast.LENGTH_SHORT).show()
            return
        }
        val address = addressInput.text.toString()
        if (address.isEmpty() || address.matches(Regex("-?[0-9]+(\\.[0-9]+)?,-?[0-9]+(\\.[0-9]+)?"))) {
            Toast.makeText(this, "Please search for an address to get a name", Toast.LENGTH_SHORT).show()
            return
        }
        favorites[address] = selectedPoint!!
        saveFavorites()
        setupFavoritesSpinner() // Refresh spinner
        Toast.makeText(this, "Favorite added", Toast.LENGTH_SHORT).show()
    }

    private fun saveFavorites() {
        val prefs = getSharedPreferences("favorites", Context.MODE_PRIVATE).edit()
        val favoriteSet = favorites.map { "${it.key}|${it.value.latitude}|${it.value.longitude}" }.toSet()
        prefs.putStringSet("locations", favoriteSet)
        prefs.apply()
    }

    private fun loadFavorites() {
        val prefs = getSharedPreferences("favorites", Context.MODE_PRIVATE)
        val favoriteSet = prefs.getStringSet("locations", emptySet()) ?: emptySet()
        favorites.clear()
        favoriteSet.forEach {
            val parts = it.split("|")
            if (parts.size == 3) {
                favorites[parts[0]] = GeoPoint(parts[1].toDouble(), parts[2].toDouble())
            }
        }
    }

    private fun searchAddress() {
        val addressString = addressInput.text.toString()
        if (addressString.isEmpty()) {
            Toast.makeText(this, "Please enter an address", Toast.LENGTH_SHORT).show()
            return
        }

        Thread {
            try {
                val geocoder = android.location.Geocoder(this)
                val addresses = geocoder.getFromLocationName(addressString, 1)
                if (addresses != null && addresses.isNotEmpty()) {
                    val address = addresses[0]
                    val geoPoint = GeoPoint(address.latitude, address.longitude)
                    runOnUiThread {
                        selectedPoint = geoPoint
                        marker.position = geoPoint
                        mapView.controller.animateTo(geoPoint)
                        mapView.invalidate()
                        addressInput.setText("${address.latitude}, ${address.longitude}")
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this, "Address not found", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: java.io.IOException) {
                runOnUiThread {
                    Toast.makeText(this, "Geocoder service not available", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun startMockLocation() {
        if (selectedPoint == null) {
            Toast.makeText(this, "Please select a location on the map first", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            mockLocationProvider = MockLocationProvider(LocationManager.GPS_PROVIDER, this)
            isMocking = true
            toggleButton.text = "Stop Mock Location"
            Toast.makeText(this, "Mock location started", Toast.LENGTH_SHORT).show()

            mockLocationRunnable = object : Runnable {
                override fun run() {
                    selectedPoint?.let {
                        mockLocationProvider?.pushLocation(it.latitude, it.longitude)
                    }
                    handler.postDelayed(this, 1000) // Repeat every second
                }
            }
            handler.post(mockLocationRunnable)

        } catch (e: SecurityException) {
            Toast.makeText(this, "Please enable 'Mock Locations' in Developer Options", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopMockLocation() {
        if (::mockLocationRunnable.isInitialized) {
            handler.removeCallbacks(mockLocationRunnable)
        }
        mockLocationProvider?.shutdown()
        mockLocationProvider = null
        isMocking = false
        toggleButton.text = "Start Mock Location"
        Toast.makeText(this, "Mock location stopped", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMockLocation()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Handle permission results if needed
    }
}

class MockLocationProvider(providerName: String, context: Context) {
    private val locationManager: LocationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val providerName: String

    init {
        this.providerName = providerName
        locationManager.addTestProvider(providerName, false, false, false, false, true, true, true, 1, 2)
        locationManager.setTestProviderEnabled(providerName, true)
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