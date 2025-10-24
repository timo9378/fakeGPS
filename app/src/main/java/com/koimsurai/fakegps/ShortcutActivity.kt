package com.koimsurai.fakegps

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast

class ShortcutActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action == "com.koimsurai.fakegps.ACTION_SET_LAST_LOCATION") {
            val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val lat = prefs.getFloat("last_lat", -999f)
            val lon = prefs.getFloat("last_lon", -999f)

            if (lat != -999f && lon != -999f) {
                try {
                    val serviceIntent = Intent(this, MockLocationService::class.java).apply {
                        action = MockLocationService.ACTION_START_MOCK
                        putExtra(MockLocationService.EXTRA_LATITUDE, lat.toDouble())
                        putExtra(MockLocationService.EXTRA_LONGITUDE, lon.toDouble())
                    }
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }
                    
                    Toast.makeText(this, getString(R.string.toast_mock_location_started), Toast.LENGTH_SHORT).show()
                } catch (e: SecurityException) {
                    Toast.makeText(this, getString(R.string.toast_enable_mock_locations), Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, getString(R.string.toast_no_last_location_saved), Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }
}