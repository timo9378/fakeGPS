// Author: koimsurai

package com.koimsurai.fakegps

import android.Manifest
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val permissionsRequestCode = 1

    private var isBound = false
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            isBound = true
            viewModel.setServiceState(true)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            viewModel.setServiceState(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle permissions
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            "android.permission.ACCESS_MOCK_LOCATION",
            Manifest.permission.POST_NOTIFICATIONS
        )
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), permissionsRequestCode)
        }

        // Load osmdroid configuration
        Configuration.getInstance().load(applicationContext, getSharedPreferences("osmdroid", MODE_PRIVATE))

        viewModel.loadFavorites(this)
        viewModel.loadLastLocation(this)

        setContent {
            MaterialTheme {
                MainScreen(viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val isRunning = isServiceRunning()
        if (isRunning) {
            Intent(this, MockLocationService::class.java).also { intent ->
                bindService(intent, serviceConnection, 0)
            }
        }
        viewModel.setServiceState(isRunning)
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.saveLastLocation(this)
    }

    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (MockLocationService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }
    var showAddFavoriteDialog by remember { mutableStateOf(false) }

    if (showAddFavoriteDialog) {
        AddFavoriteDialog(
            onDismiss = { showAddFavoriteDialog = false },
            onConfirm = { name ->
                viewModel.addFavorite(name, context)
                showAddFavoriteDialog = false
            }
        )
    }

    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState
        ) {
            FavoritesSheetContent(
                favorites = uiState.favorites,
                onFavoriteSelected = { geoPoint ->
                    viewModel.selectPoint(geoPoint)
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            showBottomSheet = false
                        }
                    }
                },
                onFavoriteDeleted = { name ->
                    viewModel.deleteFavorite(name, context)
                }
            )
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (uiState.isMocking) {
                    viewModel.stopMockLocation(context)
                } else {
                    viewModel.startMockLocation(context)
                }
            }) {
                Icon(
                    if (uiState.isMocking) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (uiState.isMocking) stringResource(R.string.stop_mock_location) else stringResource(R.string.start_mock_location)
                )
            }
        },
        bottomBar = {
            BottomAppBar {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconButton(onClick = { showBottomSheet = true }) {
                        Icon(Icons.Default.Favorite, contentDescription = stringResource(R.string.show_favorites))
                    }
                    IconButton(onClick = { viewModel.toggleMapVisibility() }) {
                        Icon(Icons.Default.Map, contentDescription = stringResource(R.string.toggle_map_visibility))
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.mapVisible) {
                MapViewContainer(
                    selectedPoint = uiState.selectedPoint,
                    onPointSelected = { viewModel.selectPoint(it) }
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.map_hidden_message))
                }
            }

            SearchBar(
                value = uiState.searchInput,
                onValueChange = { viewModel.setSearchInput(it) },
                onSearch = { viewModel.searchAddress(context) },
                onAddFavorite = {
                    if (uiState.selectedPoint != null) {
                        showAddFavoriteDialog = true
                    }
                }
            )
        }
    }
}

@Composable
fun FavoritesSheetContent(
    favorites: Map<String, GeoPoint>,
    onFavoriteSelected: (GeoPoint) -> Unit,
    onFavoriteDeleted: (String) -> Unit
) {
    // LazyColumn needs a fixed height inside a ModalBottomSheet, padding at the bottom is a good practice.
    LazyColumn(modifier = Modifier.padding(bottom = 56.dp)) {
        item {
            Text(
                text = stringResource(R.string.favorites_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(16.dp)
            )
        }
        items(favorites.toList()) { (name, geoPoint) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onFavoriteSelected(geoPoint) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = { onFavoriteDeleted(name) }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_favorite))
                }
            }
        }
    }
}

@Composable
fun AddFavoriteDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_favorite_dialog_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.favorite_name_hint)) },
                singleLine = true,
                isError = name.isBlank()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank() // Only enable button if name is not blank
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun SearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onAddFavorite: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.search_address_hint)) },
            singleLine = true
        )
        IconButton(onClick = onAddFavorite) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_to_favorites))
        }
        Button(onClick = onSearch) {
            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
        }
    }
}

@Composable
fun MapViewContainer(
    selectedPoint: GeoPoint?,
    onPointSelected: (GeoPoint) -> Unit
) {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }
    // Marker needs to be remembered as well
    val marker = remember { Marker(mapView) }

    AndroidView(
        factory = {
            mapView.apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(18.0)

                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                marker.setOnMarkerClickListener { _, _ -> true } // Disable the info window
                // Using a built-in icon as a placeholder for R.drawable.ic_marker
                // IMPORTANT: Replace this with your own drawable when available
                marker.icon = ContextCompat.getDrawable(context, android.R.drawable.ic_menu_myplaces)
                overlays.add(marker)

                val mapEventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                        onPointSelected(p)
                        return true
                    }

                    override fun longPressHelper(p: GeoPoint): Boolean {
                        return false
                    }
                })
                overlays.add(0, mapEventsOverlay)
            }
        },
        update = { view ->
            selectedPoint?.let {
                view.controller.animateTo(it)
                marker.position = it
            }
            view.invalidate()
        },
        modifier = Modifier.fillMaxSize()
    )

    // Set initial center using LaunchedEffect
    LaunchedEffect(Unit) {
        if (selectedPoint != null) {
            mapView.controller.setCenter(selectedPoint)
            marker.position = selectedPoint
        } else {
            // Default to Taipei 101 if no last location
            val defaultPoint = GeoPoint(25.0330, 121.5654)
            mapView.controller.setCenter(defaultPoint)
            marker.position = defaultPoint
            onPointSelected(defaultPoint)
        }
    }
}
