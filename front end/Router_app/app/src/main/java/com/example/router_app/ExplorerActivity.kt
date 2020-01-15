package com.example.router_app

import android.Manifest
import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.*
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.android.volley.ParseError
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.example.router_app.Helper.Cell
import com.example.router_app.Helper.MyApplication
import com.example.router_app.Helper.Wifi
import com.google.android.gms.location.*
import com.google.ar.core.ArCoreApk
import com.squareup.picasso.Picasso
import com.squareup.picasso.clearCache
import kotlinx.android.synthetic.main.activity_explorer.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.Map
import kotlin.collections.MutableSet
import kotlin.collections.average
import kotlin.collections.forEach
import kotlin.collections.groupBy
import kotlin.collections.isNotEmpty
import kotlin.collections.map
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.collections.set

/**
 * Class for letting the user find either the closest router to him or the one with the best match from the data he has from the wifis/cells
 */

class ExplorerActivity : AppCompatActivity() {

    //helper variables
    private val tag = "myTest"
    var mApp = MyApplication()
    private var userId: String? = ""
    //cell
    private lateinit var telephonyManager: TelephonyManager
    private val READ_PHONE_STATE_PERMISSION = 1003
    private val FINE_LOCATION_PERMISSION_CODE = 1
    private val COARSE_AND_FINE_LOCATION_PERMISSION_CODE = 1004
    private var cellSet: MutableSet<Cell> = mutableSetOf()
    private var aggregateCellList = mutableListOf<Cell>()//List with all the cells from multiple readings
    private val resultCellSet: MutableSet<Cell> = mutableSetOf() //final set to use for the volley request
    private var newCells = mutableMapOf<Int, Int>() //map with values to use for uniqueness of cell data (used for multiple sim cards)
    //wifi
    private lateinit var wifiManager: WifiManager
    var wifiResultList = ArrayList<ScanResult>()//list with the results from the scan
    private var wifiSet: MutableSet<Wifi> = mutableSetOf()
    private var aggregateWifiList = mutableListOf<Wifi>()//List with all the wifis from multiple readings
    private val resultWifiSet: MutableSet<Wifi> = mutableSetOf() //final set to use for the volley request
    private var allWifiDataAcquired = false
    //handler
    private var myHandler = Handler()
    //execute function once when all data are gathered
    private var runFunctionOnce = false
    // location variables
    private var locationManager: LocationManager? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var mLocationRequest: LocationRequest
    private val userLocation = Location(LocationManager.NETWORK_PROVIDER)
    //interval variables
    private var intervalCounter = 1 //default value
    private var intervalTime = 1 //default value
    private var cellCounter = 0
    private var wifiCounter = 0
    private var receiverTriggeredCounter = 0
    //progress dialog
    private lateinit var progressDialog : ProgressDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_explorer)
        //get intent values
        userId = intent.getStringExtra("userId")
        //initialize variables
        progressDialog = ProgressDialog(this)

        telephonyManager =
            applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager?
        if (!locationManager!!.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            buildAlertMessageNoGps()
        }

        explore_button.setOnClickListener {
            locationButtonPressed()
        }

        similarity_button.setOnClickListener {
            similarityButtonPressed()
        }

        parameters_button.setOnClickListener {
            parametersButtonPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        mLocationRequest = LocationRequest()
        startLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        myHandler.removeCallbacks(checkDataRunnable)
        stopLocationUpdates()
    }

    private fun parametersButtonPressed() {
        // inflate the layout of the popup window
        val inflater: LayoutInflater =
            this.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val popupView = inflater.inflate(R.layout.parameters_popup, findViewById(R.id.layout_parameters) )

        // create the popup window
        val width = LinearLayout.LayoutParams.WRAP_CONTENT
        val height = LinearLayout.LayoutParams.WRAP_CONTENT
        val focusable = true
        val popupWindow: PopupWindow = PopupWindow(popupView, width, height, focusable)

        //create shadow
        popupWindow.elevation = 20f

        //we must define the views outside of the listenet
        val seekBarTime = popupView.findViewById<SeekBar>(R.id.seekBar1)
        val seekBarNumber = popupView.findViewById<SeekBar>(R.id.seekBar2)
        //use the last values as initial values
        seekBarTime.progress = intervalTime
        seekBarNumber.progress = intervalCounter
        //also we must have reference to the textviews here and not inside the listeners
        val textviewTimeInterval = popupView.findViewById<TextView>(R.id.interval_textview)
        val textViewNumber = popupView.findViewById<TextView>(R.id.number_readings_textview)
        //and initialize the views
        textviewTimeInterval.text = "Time interval between readings:  $intervalTime seconds"
        textViewNumber.text = "Number of readings: $intervalCounter"

        seekBarTime.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
                // Display the current progress of SeekBar
                textviewTimeInterval.text = "Time interval between readings:  $i seconds"
                intervalTime = i
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                // Do something
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                // Do something

            }
        })

        seekBarNumber.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
                // Display the current progress of SeekBar
                textViewNumber.text = "Number of readings: $i"
                intervalCounter = i
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                // Do something
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                // Do something

            }
        })

        // show the popup window
        // which view you pass in doesn't matter, it is only used for the window tolken
        popupWindow.showAtLocation(explore_imageView, Gravity.CENTER, 0, 0)

    }

    private fun similarityButtonPressed() {
        //zerorise the values
        explore_button.isEnabled = false
        cellCounter = 0
        wifiCounter = 0
        receiverTriggeredCounter = 0
        progressDialog.setCancelable(false)
        progressDialog.setTitle("Similarity matching enabled")
        progressDialog.setMessage("Gathering data from signals...")
        progressDialog.show()
        gatherWifiCellData()
    }

    private fun locationButtonPressed() {
        progressDialog.setCancelable(false)
        progressDialog.setTitle("GPS matching enabled")
        progressDialog.setMessage("Gathering location data...")
        progressDialog.show()
        //gather GPS data and make a get request
        similarity_button.isEnabled = false
        if (!userLocation.latitude.equals(0f)) {
            volleyGPSRequest()
        } else {
            Toast.makeText(this, "GPS data not available, try again later", Toast.LENGTH_SHORT)
                .show()
            similarity_button.isEnabled = true
            progressDialog.dismiss()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun volleyGPSRequest() {
        //Toast.makeText(this, "Getting nearest router...", Toast.LENGTH_SHORT).show()
        progressDialog.setMessage("Uploading data...")
        val url =
            "http://" + mApp.IPaddress + "/JavaWebApp_war/gps?longitude=${userLocation.longitude}&latitude=${userLocation.latitude}"
        val queue = Volley.newRequestQueue(this)
        val stringReq =
            //we want an object only, not an array. The backend will respond with the best object
            JsonObjectRequest(
                Request.Method.GET, url, null, Response.Listener { response ->
                    val strResp = response.toString()
                    Log.d(tag, "got response: $strResp")

                    val jsonObjectResponse = JSONObject(strResp)
                    Log.d(
                        tag,
                        "we got the bssid: ${jsonObjectResponse.get("bssid")} and image url: ${jsonObjectResponse.get(
                            "image_url"
                        )}"
                    )
                    similarity_button.isEnabled = true
                    progressDialog.dismiss()
                    if(jsonObjectResponse.get("image_url").toString().equals("no image")){
                        bssid_textView.text = "No router available at close distance"
                    }else{
                        Picasso.get().clearCache()
                        Picasso.get().load(jsonObjectResponse.get("image_url").toString())
                            .into(explore_imageView)
                        bssid_textView.text = "Bssid: ${jsonObjectResponse.get("bssid")}"
                        //if the phone supports AR then create a live view
                        val arAvailability = ArCoreApk.getInstance().checkAvailability(this)
                        if (arAvailability == ArCoreApk.Availability.SUPPORTED_INSTALLED) {
                            val intent = Intent(this, GPSNavigateActivity::class.java)
                            intent.putExtra("bssid", jsonObjectResponse.get("bssid").toString())
                            intent.putExtra("imageUrl", jsonObjectResponse.get("image_url").toString())
                            //coordicates
                            intent.putExtra("latitude", jsonObjectResponse.getDouble("latitude"))
                            intent.putExtra("longitude", jsonObjectResponse.getDouble("longitude"))
                            startActivity(intent)
                            finish()
                        }
                    }
                },
                Response.ErrorListener {
                    similarity_button.isEnabled = true
                    progressDialog.dismiss()
                    Log.d(tag, "got error response: $it")
                    Toast.makeText(this, "Couldn't retrieve image", Toast.LENGTH_SHORT).show()
                })
        queue.add(stringReq)
    }

    private fun volleySimilarityRequest() {

        val url = "http://" + mApp.IPaddress + "/JavaWebApp_war/similarity"
        val queue = Volley.newRequestQueue(this)
        // Request a string response from the provided URL.

        //send JSON object
        val jsonRequest = JSONObject()

        val jsonArrayWifis = JSONArray()
        for (wifi in resultWifiSet) {
            val jsonWifi = JSONObject()
            jsonWifi.put("bssid", wifi.bssid)
            jsonWifi.put("power", wifi.power)
            jsonArrayWifis.put(jsonWifi)
        }

        val jsonArrayCells = JSONArray()
        for (cell in resultCellSet) {
            val jsonCell = JSONObject()
            jsonCell.put("id", cell.id)
            jsonCell.put("power", cell.signal_strength)
            jsonArrayCells.put(jsonCell)
        }
        jsonRequest.put("Wifis", jsonArrayWifis)
        jsonRequest.put("Cells", jsonArrayCells)
        Log.d(tag, jsonRequest.toString())

        val stringReq =
            //we want an object only, not an array. The backend will respond with the best object
            @SuppressLint("SetTextI18n")
            object : JsonObjectRequest(
                Method.POST, url, jsonRequest, Response.Listener { response ->
                    val strResp = response.toString()
                    Log.d(tag, "got response: $strResp")

                    val jsonObjectResponse = JSONObject(strResp)
                    Log.d(
                        tag,
                        "we got the bssid: ${jsonObjectResponse.get("bssid")} and image url: ${jsonObjectResponse.get(
                            "image_url"
                        )}"
                    )
                    explore_button.isEnabled = true
                    progressDialog.dismiss()
                    if(jsonObjectResponse.get("image_url")=="No URL available"){
                        bssid_textView.text = "There is no router to match your data."
                        explore_imageView.setImageResource(R.drawable.icon_my_launcher)
                        Toast.makeText(this,"No wifi or cell tower common in your area to run similarity matching",Toast.LENGTH_LONG).show()
                    }else{
                        Picasso.get().clearCache()
                        Picasso.get()
                            .load(jsonObjectResponse.get("image_url").toString())
                            .into(explore_imageView)
                        bssid_textView.text = "Bssid: ${jsonObjectResponse.get("bssid")}"
                    }

                },
                Response.ErrorListener { error ->
                    explore_button.isEnabled = true
                    progressDialog.dismiss()
                    if(error is ParseError){
                        bssid_textView.text = "There is no router to match your data."
                        explore_imageView.setImageResource(R.drawable.icon_my_launcher)
                        Toast.makeText(this,"No wifi or cell tower common in your area to run similarity matching",Toast.LENGTH_LONG).show()
                    }else{
                        Log.d(tag, "got error response: $error")
                        Toast.makeText(this, "$error", Toast.LENGTH_SHORT).show()
                    }
                }) {

                override fun getHeaders(): Map<String, String> {
                    val headers = HashMap<String, String>()
                    headers["Content-Type"] = "application/json"
                    headers["Cache-control"] = "no-cache"

                    return headers
                }
            }
        queue.add(stringReq)
        emptyLists()
    }

    private fun emptyLists(){
        //empty the lists for future searches
        aggregateCellList.clear()
        resultCellSet.clear()
        aggregateWifiList.clear()
        resultWifiSet.clear()
        wifiSet.clear()
        allWifiDataAcquired = false
    }

    private fun gatherWifiCellData() {
        //run once and gather data for our current position
        runFunctionOnce = true
        myHandler.post(checkDataRunnable)
        //or
        //checkDataRunnable.run()
        acquireCellDataRunnable.run()
        acquireWifiDataRunnable.run()
    }

    private val acquireCellDataRunnable = object : Runnable {
        override fun run() {
            scanCellular()
            Log.d(tag, "Run with cell counter: $cellCounter")
            cellCounter++
            if (cellCounter < intervalCounter) {
                //rerun every internal set
                myHandler.postDelayed(this, (intervalTime * 1000).toLong())
            }
        }
    }

    private val acquireWifiDataRunnable = object : Runnable {
        override fun run() {
            scanWifis()
            Log.d(tag, "Run with wifi counter: $wifiCounter")
            wifiCounter++
            if (wifiCounter < intervalCounter) {
                //rerun every internal set
                myHandler.postDelayed(this, (intervalTime * 1000).toLong())
            }
        }
    }

    private val checkDataRunnable = object : Runnable {
        override fun run() {
            checkWifiCellData()
            if (runFunctionOnce) {
                myHandler.postDelayed(this, 5000)
            }
        }
    }

    private fun checkWifiCellData() {
        if (wifiSet.isNotEmpty() && aggregateCellList.isNotEmpty()) {
            if (cellCounter >= intervalCounter &&
                    allWifiDataAcquired) {
                myHandler.removeCallbacksAndMessages(acquireCellDataRunnable)
                myHandler.removeCallbacksAndMessages(acquireWifiDataRunnable)
                if (runFunctionOnce) {//first time it will check
                    myHandler.removeCallbacksAndMessages(checkDataRunnable)
                    runFunctionOnce = false

                    //find the average values for the data in aggregateCellList and put them in resultCellSet
                    /*
                    * First we group by the id.
                    * Then we take each group and calculate the average of the signals to pass it to the resultCellSet
                    * */
                    aggregateCellList.groupBy { it.id }.forEach{
                        val cells = it.value
                        val averageSignal = cells.map { s->s.signal_strength }.average().toInt()
                        resultCellSet.add(Cell(it.key,averageSignal))
                    }
                    Log.d(tag,"Result Cell set is: $resultCellSet")

                    //similarly for wifis
                    //In wifis we take the unique pairs of ssid-bssid
                    aggregateWifiList.groupBy { Pair(it.bssid,it.ssid) }.forEach{
                        val wifis = it.value
                        val averageSignal = wifis.map { s->s.power }.average().toInt()
                        resultWifiSet.add(Wifi(it.key.second,it.key.first,averageSignal))
                    }
                    Log.d(tag,"Result Wifi set is: $resultWifiSet")

                    volleySimilarityRequest()
                }
            }

        }
    }

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
            cellSet.clear()
            newCells.clear()
            val cellInfo = telephonyManager.allCellInfo
            if (cellInfo != null && cellInfo.isNotEmpty()) {
                for (cell in cellInfo) {
                    when (cell) {
                        is CellInfoGsm -> {
                            if (!newCells.containsKey(cell.cellIdentity.cid)) {//use this map to prevent double values for dual sim phones
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
        aggregateCellList.addAll(cellSet)
        Log.d(tag, "aggregateCellList is: $aggregateCellList")
    }//SCAN CELLULAR END

    @Suppress("DEPRECATION")
    private fun scanWifis() {
//        if (!wifiManager.isWifiEnabled) {
//            AlertDialog.Builder(this).setTitle("Wifi required")
//                .setMessage("Please enable wifi first to have this app work")
//                .setPositiveButton(android.R.string.ok) { _, _ ->
//                    val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
//                    startActivity(intent)
//                }
//                .setIcon(resources.getDrawable(android.R.drawable.ic_dialog_alert, null))
//                .show()
//                .setCancelable(false)
//        }
        applicationContext.registerReceiver(
            wifiReceiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        )

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // Permission is not granted
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                FINE_LOCATION_PERMISSION_CODE
            )
        } else {
            wifiManager.startScan()
        }
    }

    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            wifiResultList = wifiManager.scanResults as ArrayList<ScanResult>
            applicationContext.unregisterReceiver(this)
            //Log.d(tag, "new wifi readings")
            for (result in wifiResultList) {
                wifiSet.add(
                    Wifi(
                        result.SSID,
                        result.BSSID,
                        result.level
                    )
                )
            }
            aggregateWifiList.addAll(wifiSet)
            receiverTriggeredCounter++
            if(receiverTriggeredCounter >= intervalCounter){
                allWifiDataAcquired = true
            }
            Log.d(tag, "aggregateWifiList is: $wifiSet")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        //called when user presses ALLOW or DENY from Permission Request Popup
        when (requestCode) {
            FINE_LOCATION_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    //permission from popup was granted
                    scanWifis()
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