package com.example.router_app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableException
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.ArFragment
import com.squareup.picasso.Picasso
import com.squareup.picasso.clearCache
import kotlinx.android.synthetic.main.activity_gps_navigate.*
import kotlinx.android.synthetic.main.location_layout_renderable.view.*
import uk.co.appoly.arcorelocation.LocationMarker
import uk.co.appoly.arcorelocation.LocationScene
import java.util.concurrent.CompletableFuture

class GPSNavigateActivity : AppCompatActivity() {

    private val tag = "testDinos"

    private val cameraRequestCode = 1
    private val accessFineLocationCode = 2
    // location specific variables
    private var locationManager: LocationManager? = null
    private val userLocation = Location(LocationManager.NETWORK_PROVIDER)
    private var firstLocationBoolean = true
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var mLocationRequest: LocationRequest
    //these values are used for ArCore calls
    private lateinit var myArFragment: ArFragment
    private lateinit var myArSceneView: ArSceneView
    // Our ARCore-Location scene
    private var locationScene: LocationScene? = null
    private var arHandler = Handler(Looper.getMainLooper())
    //runnable for updates
    private val resumeArElementsTask = Runnable {
        locationScene?.resume()
        myArSceneView.resume()
    }
    //router coordinates
    private var routerLatitude: Double = 0.0
    private var routerLongitude: Double = 0.0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gps_navigate)
        //get intent data and show them on screen
        routerLatitude = intent.getDoubleExtra("latitude", 0.0)
        routerLongitude = intent.getDoubleExtra("longitude", 0.0)
        frame_bssid.text = intent.getStringExtra("bssid")
        Picasso.get().clearCache()
        Picasso.get().load(intent.getStringExtra("imageUrl"))
            .into(gps_imageView)

        //find my position
        //location services
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager?
        if (!locationManager!!.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            buildAlertMessageNoGps()
        }

        //arcore elements settings
        myArFragment = supportFragmentManager.findFragmentById(R.id.ux_fragment) as ArFragment
        myArSceneView = myArFragment.arSceneView
        //disable the instruction hand in screen
        myArFragment.planeDiscoveryController.hide()
        myArFragment.planeDiscoveryController.setInstructionView(null)
        //we disable the plane renderer because we don't want this functionality
        myArSceneView.planeRenderer.isEnabled = false

    }

    override fun onResume() {
        super.onResume()
        checkAndRequestPermissions()
        mLocationRequest = LocationRequest()
        startLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        myArSceneView.session?.let {
            locationScene?.pause()
            myArSceneView.pause()
        }
        stopLocationUpdates()
    }

    private fun checkAndRequestPermissions() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED -> {
                // Permission is not granted
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA),
                    cameraRequestCode
                )
            }
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED -> {
                // Permission is not granted
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    accessFineLocationCode
                )
            }
            else -> {
                setupSession()
            }
        }
    }

    private fun buildAlertMessageNoGps() {
        val builder = AlertDialog.Builder(this)
        builder.setMessage("Your GPS seems to be disabled, you must enable it for the app to run smoothly")
            .setCancelable(false)
            .setPositiveButton("Go to settings") { _, _ ->
                startActivityForResult(
                    Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    , 11
                )
            }
        val alert: AlertDialog = builder.create()
        alert.show()
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            // do work here
            locationResult.lastLocation
            onLocationChanged(locationResult.lastLocation)
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(mLocationCallback)
    }

    private fun startLocationUpdates() {

        // Create the location request to start receiving updates
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        mLocationRequest.interval = 2000
        mLocationRequest.fastestInterval = 1000

        // Create LocationSettingsRequest object using location request
        val builder = LocationSettingsRequest.Builder()
        builder.addLocationRequest(mLocationRequest)
        val locationSettingsRequest = builder.build()

        val settingsClient = LocationServices.getSettingsClient(this)
        settingsClient.checkLocationSettings(locationSettingsRequest)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        // new Google API SDK v11 uses getFusedLocationProviderClient(this)
        fusedLocationClient.requestLocationUpdates(
            mLocationRequest, mLocationCallback,
            Looper.myLooper()
        )
    }

    fun onLocationChanged(location: Location) {
        // New location has now been determined

        if (firstLocationBoolean) {
            userLocation.latitude = location.latitude
            userLocation.longitude = location.longitude
            firstLocationBoolean = false
        }

        val usableRouterLocation = Location(LocationManager.NETWORK_PROVIDER)
        usableRouterLocation.latitude = routerLatitude
        usableRouterLocation.longitude = routerLongitude
        //set new distance to cell
        frame_distancetocell.text =
            getString(R.string.textview_distance_to_router, location.distanceTo(usableRouterLocation))
    }

    private fun configureSession(): Session? {
        val session = Session(this)
        val config = Config(session)
        config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
        // IMPORTANT!!!  ArSceneView requires the `LATEST_CAMERA_IMAGE` non-blocking update mode.

        session.configure(config)
        return session
    }

    private fun setupSession() {
        if (myArSceneView.session == null) {
            try {
                val session = configureSession()
                myArSceneView.setupSession(session)
            } catch (e: UnavailableException) {
                Toast.makeText(this, e.toString(), Toast.LENGTH_SHORT).show()
            }
        }

        if (locationScene == null) {
            locationScene = LocationScene(this, myArSceneView)
//            locationScene!!.setMinimalRefreshing(true)
//            locationScene!!.setOffsetOverlapping(true)
//            locationScene!!.setRemoveOverlapping(true)
            locationScene!!.anchorRefreshInterval = 2000
        }

        try {
            resumeArElementsTask.run()
        } catch (e: CameraNotAvailableException) {
            Toast.makeText(this, "Unable to get camera", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        //create router pin on screen
        renderRouterMarker()
    }

    private fun renderRouterMarker() {
        //Log.d(tag, "renderTowers")
        //clear the markers if any
        locationScene!!.clearMarkers()

        setupAndAttachRouterMarker()
        updateRouterMarker()
    }

    private fun setupAndAttachRouterMarker() {
        Log.d(tag, "setupAndRenderTowersMarkers")

        val completableFutureViewRenderable = ViewRenderable.builder()
            .setView(this, R.layout.location_layout_renderable)
            .build()

        CompletableFuture.anyOf(completableFutureViewRenderable)
            .handle<Any> { _, throwable ->
                //here we know the renderable was built or not
                if (throwable != null) {
                    // handle renderable load fail
                    return@handle null
                }
                try {
                    val routerMarker = LocationMarker(
                        routerLongitude, routerLatitude,
                        setRouterNode(completableFutureViewRenderable)
                    )

                    arHandler.postDelayed({
                        //attach the view markers
                        attachMarkerToScene(routerMarker, completableFutureViewRenderable.get().view)
                        arHandler.post {
                            locationScene?.refreshAnchors()
                        }
                       // areAllMarkersLoaded = true
                    }, 200)

                } catch (ex: Exception) {
                    // showToast(getString(R.string.generic_error_msg))
                }
                null
            }
    }

    private fun setRouterNode(completableFuture: CompletableFuture<ViewRenderable>): Node {
        Log.d(tag, "setTowerNode")
        val node = Node()
        node.renderable = completableFuture.get()

        val nodeLayout = completableFuture.get().view
        val routerName = nodeLayout.name
        val markerLayoutContainer = nodeLayout.pinContainer
        routerName.text = "Router this way"
        markerLayoutContainer.visibility = View.GONE
        nodeLayout.setOnTouchListener { _, _ ->
            Toast.makeText(this, "Follow the pin to get to the cell.", Toast.LENGTH_LONG).show()
            false
        }
        return node
    }

    private fun attachMarkerToScene(locationMarker: LocationMarker, layoutRendarable: View) {
        Log.d(tag, "attachMarkerToScene")
        resumeArElementsTask.run {
            locationMarker.scalingMode = LocationMarker.ScalingMode.FIXED_SIZE_ON_SCREEN
            locationMarker.scaleModifier = 0.5f

            //we attach markers on anchors
            locationScene?.mLocationMarkers?.add(locationMarker)
            locationMarker.anchorNode?.isEnabled = true

            arHandler.post {
                locationScene?.refreshAnchors()
                layoutRendarable.pinContainer.visibility = View.VISIBLE
            }
        }
        locationMarker.setRenderEvent { locationNode ->
            layoutRendarable.distance.text = "${locationNode.distance.toString()} m"
//            resumeArElementsTask.run {
//                computeNewScaleModifierBasedOnDistance(locationMarker, locationNode.distance)
//            }
        }
    }

    private fun updateRouterMarker() {
        Log.d(tag, "updateTowersMarkers")
        myArSceneView.scene.addOnUpdateListener()
        {
//            if (!areAllMarkersLoaded) {
//                return@addOnUpdateListener
//            }

            locationScene?.mLocationMarkers?.forEach { locationMarker ->
                locationMarker.height = 1f
//                    AugmentedRealityLocationUtils.generateRandomHeightBasedOnDistance(
//                        locationMarker?.anchorNode?.distance ?: 0
//                    )
            }

            val frame = myArSceneView.arFrame ?: return@addOnUpdateListener
            if (frame.camera.trackingState != TrackingState.TRACKING) {
                return@addOnUpdateListener
            }
            locationScene!!.processFrame(frame)
        }
    }


}