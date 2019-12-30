package com.example.router_app.Helper

import java.io.Serializable

data class Wifi (
    val ssid: String,
    val bssid: String,
    val power: Int
): Serializable