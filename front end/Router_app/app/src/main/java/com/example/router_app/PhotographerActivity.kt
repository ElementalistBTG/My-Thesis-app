package com.example.router_app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.*
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.example.router_app.Helper.*
import com.google.android.gms.location.*
import kotlinx.android.synthetic.main.activity_photographer.*
import org.json.JSONObject
import java.io.*
import java.net.URL
import java.util.*

/**
 * This class is used for photographing routers, tying the access point/router to the photo and uploading to the local database the user
 * has declared at the login screen activity.
 */

@SuppressLint("SetTextI18n")
class PhotographerActivity : AppCompatActivity(), AsyncResponse {

    //helper variables
    private val tag = "myTest"
    //cell variables
    private lateinit var telephonyManager: TelephonyManager
    private var userId: String? = ""
    private var serverUrl: String? = ""
    private var cellSet: MutableSet<Cell> = mutableSetOf()
    private var newCells = mutableMapOf<Int, Int>() //used for uniqueness of cells in case of dual sim phone
    //codes
    private val CAMERA_AND_EXTERNAL_STORAGE_PERMISSION_CODE = 1000
    private val IMAGE_CAPTURE_CODE = 1001
    private val WIFI_NETWORK_PICK = 1002
    private val READ_PHONE_STATE_PERMISSION = 1003
    private val COARSE_AND_FINE_LOCATION_PERMISSION_CODE = 1004
    //wifi variables
    private var wifiSet: MutableSet<Wifi> = mutableSetOf()
    private var routerBssid: String? = ""
    //location variables
    private var locationManager: LocationManager? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var mLocationRequest: LocationRequest
    private val userLocation = Location(LocationManager.NETWORK_PROVIDER)
    //store image variables
    var currentPhotoPath: String = ""
    private lateinit var uri: Uri


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photographer)

        //initialize variables
        telephonyManager =
            applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager?
        if (!locationManager!!.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            buildAlertMessageNoGps()
        }
        //get intent values and store them globally
        userId = intent.getStringExtra("userId")
        serverUrl = intent.getStringExtra("server url")

        view_status.text = "Take a photo"
        view_status.visibility = View.VISIBLE

        checkCameraAndStoragePermissions()

        retake_photo_button.setOnClickListener {
            openCamera()
        }

        pick_wifi_button.setOnClickListener {
            wifiButtonPressed()
        }

        upload_button.setOnClickListener {
            if (!userLocation.latitude.equals(0f)) {
                uploadPhoto()
            } else {
                Toast.makeText(this, "no data from gps yet. Try again later", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun uploadPhoto() {
        //get path of image
        currentPhotoPath = uri.toString()

        val uploadImage = SendImage()
        uploadImage.delegate = this
        uploadImage.context = this
        uploadImage.realPath = currentPhotoPath
        if (serverUrl != "") {
            uploadImage.UPLOAD_SERVER = serverUrl
        }
        uploadImage.execute()
        view_status.text = "Try to upload Image"
        view_status.setTextColor(Color.GRAY)
    }

    //Here you will receive the result fired from async class
    //of onPostExecute(result) method.
    override fun processFinish(output: String?) {
        view_status.text = "Image uploaded, uploading rest of the data"
        uploadData(output)
        //Log.d(tag, "image url is :$output")
    }

    private fun uploadData(imageUrl: String?) {
        //convert something like this
        //https://homedatabase-060e.restdb.io/media?&apikey=5dc5641764e7774913b6ea76
        //to this
        //"https://homedatabase-060e.restdb.io/rest/routers?&apikey=5dc5641764e7774913b6ea76"
        val newUrl = URL(serverUrl)
        val host = newUrl.host
        val apiKey = newUrl.query
        val databaseUrl = "https://$host/rest/routers?$apiKey"

        val queue = Volley.newRequestQueue(this)
        //create the object with the parameters
        val jsonObject: JSONObject = JSONObject()
        jsonObject.put("userId", userId)
        jsonObject.put("GPS-latitude", userLocation.latitude)
        jsonObject.put("GPS-longitude", userLocation.longitude)
        jsonObject.put("router-bssid", routerBssid)
        jsonObject.put("photo", "https://$host/media/" + imageUrl)

        var idFromString: String
        val stringReq =
            JsonObjectRequest(
                Request.Method.POST, databaseUrl, jsonObject, Response.Listener { response ->
                    val strResp = response.toString()
                    //Log.d(tag, "Response from data upload $strResp")
                    idFromString = strResp.split("\"").get(3)
                    //Log.d(tag, "id for use $idFromString")
                    view_status.text = "record successfully created"
                    retake_photo_button.isEnabled = true
                    uploadWifiCellData(idFromString, databaseUrl)
                },
                Response.ErrorListener {
                    if (it.toString() == "com.android.volley.ClientError") {
                        view_status.text = "Already have that access point stored"
                        //deleteImage(imageUrl)
                    } else {
                        view_status.text = "Couldn't post general data"
                    }
                    Toast.makeText(this, "Couldn't post data", Toast.LENGTH_SHORT)
                        .show()
                })
        queue.add(stringReq)
    }

    //not used at the moment but can be used if we want to further upgrade our app
    private fun deleteImage(url: String?) {
        val imageUrl = "https://homedatabase-060e.restdb.io/media/$url"
        val queue = Volley.newRequestQueue(this)
        val stringReq =
            StringRequest(
                Request.Method.DELETE, imageUrl, Response.Listener { response ->
                    val strResp = response.toString()
                    Log.d(tag, "Response from image delete $strResp")
                    //Log.d(tag, "id for use $idFromString")
                },
                Response.ErrorListener {
                    Log.d(tag, "Response from error image delete $it")
                })
        queue.add(stringReq)
    }

    private fun uploadWifiCellData(idFromString: String, databaseUrl: String) {
        val queue2 = Volley.newRequestQueue(this)
        //pass the subvalues for wifis
        val newUrl = URL(databaseUrl)
        val host = newUrl.host
        val apiKey = newUrl.query
        val wifisDatabaseUrl =
            "https://$host/rest/routers/$idFromString/wifis?$apiKey"
        for (wifi in wifiSet) {
            val wifiJsonObject = JSONObject()
            wifiJsonObject.put("Bssid", wifi.bssid)
            wifiJsonObject.put("Ssid", wifi.ssid)
            wifiJsonObject.put("Power", wifi.power)
            val stringReqWifis =
                JsonObjectRequest(
                    Request.Method.POST,
                    wifisDatabaseUrl,
                    wifiJsonObject,
                    Response.Listener { response ->
                        val strResp = response.toString()
                        //Log.d(tag, "Response from data upload wifi $strResp")
                    },
                    Response.ErrorListener {
                        //Log.d(tag,"error with: $it")
                        view_status.text = "Couldn't post wifi data $it"
                    })
            queue2.add(stringReqWifis)
        }

        //pass the subvalues for cells
        val cellsDatabaseUrl =
            "https://$host/rest/routers/$idFromString/cells?$apiKey"
        for (cell in cellSet) {
            val cellJsonObject = JSONObject()
            cellJsonObject.put("Cell-id", cell.id)
            cellJsonObject.put("Power", cell.signal_strength)
            val stringReqCells =
                JsonObjectRequest(
                    Request.Method.POST,
                    cellsDatabaseUrl,
                    cellJsonObject,
                    Response.Listener { response ->
                        val strResp = response.toString()
                        //Log.d(tag, "Response from data upload cell $strResp")
                    },
                    Response.ErrorListener {
                        //Log.d(tag,"error with: $it")
                        view_status.text = "Couldn't post cell data $it"
                    })
            queue2.add(stringReqCells)
        }

    }

    private fun checkCameraAndStoragePermissions() {
        //if system os is Marshmallow or Above, we need to request runtime permission

        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED ||
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED
        ) {
            //permission was not enabled
            val permission =
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            //show popup to request permission
            requestPermissions(permission, CAMERA_AND_EXTERNAL_STORAGE_PERMISSION_CODE)
        } else {
            //permission already granted
            openCamera()
        }
    }

    /*
    the "wifi picking" is handled by another class
     */
    private fun wifiButtonPressed() {
        val intent = Intent(this, WifiNetworksActivity::class.java)
        startActivityForResult(intent, WIFI_NETWORK_PICK)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        //called when user presses ALLOW or DENY from Permission Request Popup
        when (requestCode) {
            CAMERA_AND_EXTERNAL_STORAGE_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    //permission from popup was granted
                    openCamera()
                } else {
                    //permission from popup was denied
                    Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
                }
            }
            READ_PHONE_STATE_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    //permission from popup was granted
                    scanCellular()
                } else {
                    //permission from popup was denied
                    Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
                }
            }
            COARSE_AND_FINE_LOCATION_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] ==
                    PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED
                ) {
                    //permission from popup was granted
                    startLocationUpdates()
                } else {
                    //permission from popup was denied
                    Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openCamera() {
        Toast.makeText(this, "Take a photo of a router", Toast.LENGTH_LONG).show()
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(packageManager)?.also {
                startActivityForResult(takePictureIntent, IMAGE_CAPTURE_CODE)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMAGE_CAPTURE_CODE && resultCode == RESULT_OK) {
            val imageBitmap = data?.extras?.get("data") as Bitmap
            val resizedBitmap = Bitmap.createScaledBitmap(imageBitmap, 800, 800, false)
            explore_imageView.setImageBitmap(resizedBitmap)
            uri = saveImageToExternalStorage(resizedBitmap)
            view_status.text = "Press 'Pick Wifi' to match the router photographed"
            upload_button.isEnabled = false
            pick_wifi_button.isEnabled = true
        } else if (requestCode == WIFI_NETWORK_PICK && resultCode == RESULT_OK) {
            //successfully picked wifi network
            routerBssid = data?.getStringExtra("bssid")
            bssid_photographer_textview.text = "Bssid: $routerBssid of wifi: ${data?.getStringExtra("ssid")}"
            val routers = data?.getStringArrayListExtra("routers")
            wifiSet.clear()
            if (routers != null) {
                for (router in routers) {
                    //get routers values
                    val values = router.split("/".toRegex())
                    wifiSet.add(Wifi(values[0], values[1], values[2].toInt()))
                }
                scanCellular()
                upload_button.isEnabled = true
                view_status.text = "Now you can upload the photo"
            }
        }
    }

    // Method to save an image to external storage
    @Suppress("DEPRECATION")
    private fun saveImageToExternalStorage(bitmap: Bitmap): Uri {
        // Get the external storage directory path
        val path = Environment.getExternalStorageDirectory().toString()

        // Create a file to save the image
        val file = File(path, "${UUID.randomUUID()}.jpg")

        try {
            // Get the file output stream
            val stream: OutputStream = FileOutputStream(file)

            // Compress the bitmap
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)

            // Flush the output stream
            stream.flush()

            // Close the output stream
            stream.close()
        } catch (e: IOException) { // Catch the exception
            e.printStackTrace()
        }

        // Return the saved image path to uri
        return Uri.parse(file.absolutePath)
    }

    //take the readings for the cell towers
    private fun scanCellular() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // Permission is not granted
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_PHONE_STATE),
                READ_PHONE_STATE_PERMISSION
            )
        } else {
            val cellInfo = telephonyManager.allCellInfo
            if (cellInfo != null && cellInfo.isNotEmpty()) {
                cellSet.clear()
                newCells.clear()
                for (cell in cellInfo) {
                    when (cell) {
                        is CellInfoGsm -> {
                            if (!newCells.containsKey(cell.cellIdentity.cid)) {//use this map to prevent double values
                                newCells[cell.cellIdentity.cid] = 1//put random number to value
                                cellSet.add(
                                    Cell(
                                        cell.cellIdentity.cid.toString(),
                                        cell.cellSignalStrength.dbm
                                    )
                                )
                            }
                        }
                        is CellInfoCdma -> {
                            if (!newCells.containsKey(cell.cellIdentity.basestationId)) {
                                newCells[cell.cellIdentity.basestationId] =
                                    1//put random number to value
                                cellSet.add(
                                    Cell(
                                        cell.cellIdentity.basestationId.toString(),
                                        cell.cellSignalStrength.cdmaDbm
                                    )
                                )
                            }
                        }
                        is CellInfoWcdma -> {
                            if (!newCells.containsKey(cell.cellIdentity.cid)) {
                                newCells[cell.cellIdentity.cid] = 1//put random number to value
                                cellSet.add(
                                    Cell(
                                        cell.cellIdentity.cid.toString(),
                                        cell.cellSignalStrength.dbm
                                    )
                                )
                            }
                        }
                        is CellInfoLte -> {
                            if (!newCells.containsKey(cell.cellIdentity.ci)) {
                                newCells[cell.cellIdentity.ci] = 1//put random number to value
                                cellSet.add(
                                    Cell(
                                        cell.cellIdentity.ci.toString(),
                                        cell.cellSignalStrength.dbm
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }//SCAN CELLULAR END

    override fun onResume() {
        super.onResume()
        mLocationRequest = LocationRequest()
        startLocationUpdates()
    }

    private fun startLocationUpdates() {
        // Create the location request to start receiving updates of user location
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
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_DENIED
        ) {
            //permission was not enabled
            val permission =
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            //show popup to request permission
            requestPermissions(permission, COARSE_AND_FINE_LOCATION_PERMISSION_CODE)
        } else {
            //permission already granted
            fusedLocationClient.requestLocationUpdates(
                mLocationRequest, mLocationCallback,
                Looper.myLooper()
            )
        }
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(mLocationCallback)
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

    fun onLocationChanged(location: Location) {
        // New location has now been determined
        userLocation.latitude = location.latitude
        userLocation.longitude = location.longitude

    }

}