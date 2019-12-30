package com.example.router_app

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import kotlinx.android.synthetic.main.activity_main.*

/**
 * This class is used only for the purpose of selecting between the explorer and the photographer activity
 */

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        //get values from intent in order to pass the to the other classes
        val userId = intent.getStringExtra("userId")
        val serverUrl = intent.getStringExtra("server url")
        Log.d("myTest","server url is $serverUrl")

        photographer_button.setOnClickListener{
            val intent = Intent(this, PhotographerActivity::class.java)
            intent.putExtra("userId", userId)
            intent.putExtra("server url",serverUrl)
            startActivity(intent)
        }

        explorer_button.setOnClickListener{
            val intent = Intent(this, ExplorerActivity::class.java)
            intent.putExtra("userId", userId)
            startActivity(intent)
        }
    }
}
