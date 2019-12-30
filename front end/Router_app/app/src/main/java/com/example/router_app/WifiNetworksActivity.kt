package com.example.router_app

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_wifi_networks.*
import java.util.ArrayList

class WifiNetworksActivity : AppCompatActivity() {

    private val tag = "myTest"
    private val FINE_LOCATION_PERMISSION_CODE = 1
    //wifi variables
    private lateinit var wifiManager: WifiManager
    var wifiResultList = ArrayList<ScanResult>()
    private lateinit var wifiListView: ListView
    var wifiArrayList = ArrayList<String>()
    var wifiBSSIDArrayList = ArrayList<String>()
    var wifiSSIDArrayList = ArrayList<String>()
    var wifiPowerArrayList = ArrayList<Int>()
    private lateinit var adapterForWifiList: ArrayAdapter<String> // adapter for wifi ListView
    //handler
    private var myHandler = Handler()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi_networks)
        //initialization of variables
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiListView = findViewById(R.id.wifiList)
        adapterForWifiList =
            ArrayAdapter(applicationContext, android.R.layout.simple_list_item_1, wifiArrayList)
        wifiListView.adapter = adapterForWifiList

        wifiListView.setOnItemClickListener { _, _, pos, _ ->
            //sending back the data
            val returnIntent = this.intent
            returnIntent.putExtra("bssid", wifiBSSIDArrayList[pos])
            returnIntent.putExtra("ssid",wifiSSIDArrayList[pos])
            returnIntent.putStringArrayListExtra("routers", wifiArrayList)
            setResult(Activity.RESULT_OK, returnIntent)
            finish()
        }
    }

    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            wifiResultList = wifiManager.scanResults as ArrayList<ScanResult>
            applicationContext.unregisterReceiver(this)
            wifiArrayList.clear()
            wifiSSIDArrayList.clear()
            wifiBSSIDArrayList.clear()
            wifiPowerArrayList.clear()
            //Log.d(tag, "new wifi readings")
            for (result in wifiResultList) {
                wifiArrayList.add(result.SSID + "/" + result.BSSID + "/" + result.level)
                wifiSSIDArrayList.add(result.SSID)
                wifiBSSIDArrayList.add(result.BSSID)
                wifiPowerArrayList.add(result.level)
            }
            adapterForWifiList.notifyDataSetChanged()
            wifiInstructions.visibility = View.VISIBLE
        }
    }

    @Suppress("DEPRECATION")
    private fun scanWifi() {
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
            Toast.makeText(applicationContext, "Scanning WiFi ...", Toast.LENGTH_SHORT).show()
        }
    }

    private val scanWifiPeriodically = object : Runnable {
        override fun run() {
            scanWifi()
            // Run code again after 10 seconds
            myHandler.postDelayed(this, 10000)
        }
    }

    override fun onResume() {
        super.onResume()
        //Get wi-fi readings periodically
        scanWifiPeriodically.run()
    }

    override fun onPause() {
        super.onPause()
        myHandler.removeCallbacks(scanWifiPeriodically)
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
                    scanWifi()
                } else {
                    //permission from popup was denied
                    Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
                }
            }

        }
    }


}