package com.example.router_app

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.router_app.Helper.MyApplication
import com.example.router_app.Helper.SnackbarHelper
import com.google.firebase.auth.FirebaseAuth
import kotlinx.android.synthetic.main.activity_login_screen.*
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.*

/**
 * Class for login screen
 * Handles the register/login of the user to the Firebase database
 * and the forwards the user to the main activity
 */

@Suppress("DEPRECATION")
class LoginScreenActivity : AppCompatActivity() {

    //Helper variables
    private val snackbarHelper = SnackbarHelper()
    private val tag = "myTest" //tag used throughout the app for logging
    var mApp = MyApplication()
    //Shared Preferences and Login values
    private val preferencesUrl = "url"
    private val preferencesid1 = "id1"
    private val preferencesid2 = "id2"
    private var url: String? = ""
    private var defaultURL = "https://homedatabase2-4b1c.restdb.io/media?&apikey=5dd0fa8964e7774913b6ed97"
    private var uniqueId1: String? = ""
    private var uniqueId2: String? = ""
    private lateinit var fbAuth: FirebaseAuth
    //coroutines
    private val parentJob = Job()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + parentJob)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_screen)
        //initialisation phase
        //login is made using Firebase
        fbAuth = FirebaseAuth.getInstance()

        //We pass the url of the images in restdb.io of the specific user
        //Retrieve url of home data database from internal storage (if there is any)
        val sharedPref = getPreferences(Context.MODE_PRIVATE)
        if (sharedPref.getString(preferencesUrl, "") != "") {
            url = sharedPref.getString(preferencesUrl, "")
            url_editText.setText(url)
            instructions_textview.visibility = View.GONE
        }

        if (sharedPref.getString(preferencesid1,"") ==""){
            uniqueId1 = createUniqueId()
            val editor = sharedPref.edit()
            editor.putString(preferencesid1, uniqueId1)
            editor.apply()
            uniqueId2 = createUniqueId()
            editor.putString(preferencesid2, uniqueId2)
            editor.apply()
            Log.d(tag,"Created the id1:$uniqueId1 and id2:$uniqueId2")
        }else {
            uniqueId1 = sharedPref.getString(preferencesid1,"")
            uniqueId2 = sharedPref.getString(preferencesid2,"")
            Log.d(tag,"Found the id1:$uniqueId1 and id2:$uniqueId2")
        }

        login_button.setOnClickListener {
            url = url_editText.text.toString()
            val editor = sharedPref.edit()
            editor.putString(preferencesUrl, url)
            editor.apply()
            //attempt tryLogin or register
            if (!isNetworkConnected()) {
                Toast.makeText(
                    this,
                    "You need to have an active internet connection to continue",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                tryLogin()
            }
        }
    }

    /*
    * The user uses his unique ids to login to Firebase.
    * If the user exists the app continues to Mainactivity
    * Else, the user is registered to Firebase and also to the Main Database
    * where the url of the media is stored for use.
    * */
    private fun tryLogin() {
        snackbarHelper.showMessage(this, "Authenticating...")
        fbAuth.signInWithEmailAndPassword("$uniqueId1@myapp.com", uniqueId2.toString())
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    startMainActivity()
                } else {
                    if (task.exception?.message == "There is no user record corresponding to this identifier. The user may have been deleted.") {
                        registerUser()
                    } else {
                        snackbarHelper.showMessage(this, "Error: ${task.exception?.message}")
                    }
                }
            }
    }

    private fun registerUser() {
        snackbarHelper.showMessage(this, "Registering new user...")
        fbAuth.createUserWithEmailAndPassword("$uniqueId1@myapp.com", uniqueId2.toString())
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    coroutineScope.launch(Dispatchers.IO) {
                        registerUserToDb()
                    }
                    startMainActivity()
                    Toast.makeText(this, "Successfully registered :)", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, task.exception.toString(), Toast.LENGTH_LONG)
                        .show()
                }
            }
    }

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("userId", uniqueId1)
        if(url_editText.text.toString()!=""){
            intent.putExtra("server url", url_editText.text.toString())
        }else{
            intent.putExtra("server url",defaultURL)
        }
        startActivity(intent)
        finish()
    }

    private suspend fun registerUserToDb() {

        var reqParam = URLEncoder.encode("userId", "UTF-8") + "=" + URLEncoder.encode(uniqueId1, "UTF-8")
        reqParam += "&" + URLEncoder.encode(
            "Url",
            "UTF-8"
        ) + "=" + URLEncoder.encode(url_editText.text.toString(), "UTF-8")
        val mURL = URL("http://"+mApp.IPaddress+"/JavaWebApp_war/add_user")

        withContext(coroutineScope.coroutineContext + Dispatchers.IO) {
            with(mURL.openConnection() as HttpURLConnection) {
                // optional default is GET
                requestMethod = "POST"

                val wr = OutputStreamWriter(outputStream)
                wr.write(reqParam)
                wr.flush()

                println("URL : $mURL")
                println("Response Code : $responseCode")

                BufferedReader(InputStreamReader(inputStream)).use {
                    val response = StringBuffer()

                    var inputLine = it.readLine()
                    while (inputLine != null) {
                        response.append(inputLine)
                        inputLine = it.readLine()
                    }
                    it.close()
                    println("Response : $response")
                }
            }
        }
    }

    private fun isNetworkConnected(): Boolean {
        val connectivityManager =
            applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager //Retrieves an instance of the ConnectivityManager class from the current application context.
        val networkInfo =
            connectivityManager.activeNetwork //Retrieves an instance of the NetworkInfo class that represents the current network connection. This will be null if no network is available.
        return networkInfo != null  //Check if there is an available network connection and the device is connected.
    }


    //create a unique string with max 20chars
    private fun createUniqueId(): String{
        var id = UUID.randomUUID().toString()
        id = id.replace("-","").substring(0,20)
        //Log.d(tag,"Created the id:$id")
        return id
    }
}