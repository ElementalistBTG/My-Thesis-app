package com.example.router_app.Helper

import java.io.Serializable

data class Cell(
    val id: String,
    val signal_strength: Int
) : Serializable