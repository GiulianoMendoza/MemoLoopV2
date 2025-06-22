package com.example.memoloop

import android.app.Activity
import android.content.Context // Importar Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.OnMapReadyCallback
import org.maplibre.android.maps.Style
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

class MapPickerActivity : BaseActivity(), OnMapReadyCallback {

    private lateinit var mapView: MapView
    private var mapLibreMap: MapLibreMap? = null
    private var selectedLatLng: LatLng? = null
    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(LanguageManager.updateBaseContextLocale(newBase!!))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        setContentView(R.layout.activity_map_picker)

        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)
        findViewById<Button>(R.id.btn_confirm_location).setOnClickListener {
            selectedLatLng?.let {
                Intent().apply {
                    putExtra("latitude", it.latitude)
                    putExtra("longitude", it.longitude)
                }.also { intent ->
                    setResult(Activity.RESULT_OK, intent)
                    finish()
                }
            }
        }
    }

    override fun onMapReady(map: MapLibreMap) {
        mapLibreMap = map

        val styleUrl = "https://basemaps.cartocdn.com/gl/positron-gl-style/style.json"

        map.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
            val sourceId = "marker-source"
            val layerId = "marker-layer"
            val geoJsonSource = GeoJsonSource(sourceId)
            style.addSource(geoJsonSource)

            val symbolLayer = SymbolLayer(layerId, sourceId).withProperties(
                iconImage("marker-icon"),
                iconAllowOverlap(true),
                iconIgnorePlacement(true)
            )
            style.addLayer(symbolLayer)

            val bitmap = BitmapFactory.decodeResource(resources, R.drawable.marker_icon)
            style.addImage("marker-icon", bitmap)

            map.cameraPosition = CameraPosition.Builder()
                .target(LatLng(-34.6037, -58.3816))
                .zoom(12.0)
                .build()

            map.addOnMapClickListener { point ->
                selectedLatLng = point

                val feature = Feature.fromGeometry(
                    Point.fromLngLat(point.longitude, point.latitude)
                )
                val featureCollection = FeatureCollection.fromFeatures(arrayOf(feature))
                geoJsonSource.setGeoJson(featureCollection)

                true
            }
        }
    }

    override fun onStart() = super.onStart().also { mapView.onStart() }
    override fun onResume() = super.onResume().also { mapView.onResume() }
    override fun onPause() = super.onPause().also { mapView.onPause() }
    override fun onStop() = super.onStop().also { mapView.onStop() }
    override fun onLowMemory() = super.onLowMemory().also { mapView.onLowMemory() }
    override fun onDestroy() = super.onDestroy().also { mapView.onDestroy() }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
}
