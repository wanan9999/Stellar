package roro.stellar.manager.ui.features.location

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import roro.stellar.manager.BuildConfig
import java.io.File

@Composable
fun LocationMap(
    modifier: Modifier = Modifier,
    lat: Double,
    lng: Double,
    zoom: Double,
    cameraEpoch: Int,
    onUserMoved: (lat: Double, lng: Double, zoom: Double) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    val programmatic = remember { ProgrammaticMove() }

    DisposableEffect(Unit) {
        val base = File(context.cacheDir, "osmdroid")
        Configuration.getInstance().apply {
            osmdroidBasePath = base
            osmdroidTileCache = File(base, "tiles")
            userAgentValue = "Stellar/${BuildConfig.VERSION_NAME}"
        }
        onDispose { }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setDestroyMode(false)
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    minZoomLevel = 3.0
                    maxZoomLevel = 19.0
                    isHorizontalMapRepetitionEnabled = true
                    controller.setZoom(zoom)
                    controller.setCenter(GeoPoint(lat, lng))
                    addMapListener(object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean {
                            if (!programmatic.skip) {
                                val center = mapCenter
                                onUserMoved(center.latitude, center.longitude, zoomLevelDouble)
                            }
                            return false
                        }

                        override fun onZoom(event: ZoomEvent?): Boolean {
                            if (!programmatic.skip) {
                                val center = mapCenter
                                onUserMoved(center.latitude, center.longitude, zoomLevelDouble)
                            }
                            return false
                        }
                    })
                    mapView = this
                }
            },
            update = { map ->
                if (cameraEpoch != programmatic.epoch) {
                    programmatic.epoch = cameraEpoch
                    programmatic.skip = true
                    map.controller.animateTo(GeoPoint(lat, lng), zoom, 350L)
                    map.postDelayed({ programmatic.skip = false }, 400)
                }
            }
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(18.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(8.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        )
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val map = mapView ?: return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        map.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            map.onPause()
            map.onDetach()
        }
    }
}

private class ProgrammaticMove {
    var skip = false
    var epoch = 0
}
