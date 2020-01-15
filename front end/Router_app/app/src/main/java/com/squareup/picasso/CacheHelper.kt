package com.squareup.picasso

/**
 * this file is needed to clear the cache in the Picasso library in case
 * an image is partially loaded due to bad internet connection
 * **/


fun Picasso.clearCache() {
    cache.clear()
}

fun Picasso.clearCache(url: String) {
    cache.clearKeyUri(url)
}