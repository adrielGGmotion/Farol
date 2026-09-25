/*
 * Copyright (C) 2026 Răzvan Albu
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>
 */

package com.farol.launcher.activities.launcher.widgets.glance.extensions.weather

import android.app.Activity
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import android.webkit.WebSettings
import com.farol.launcher.utils.Utils
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import androidx.core.view.isEmpty

class GeocoderFallback(private val activity: Activity) {
    private val geocoder: Geocoder? = if (Geocoder.isPresent()) Geocoder(activity) else null

    fun getFromLocationName(query: String?, maxResults: Int): List<Address>? {
        if (query.isNullOrEmpty()) {
            return ArrayList()
        }

        geocoder?.let {
            return getFromLocationName(it, query, maxResults)
        }

        val addresses = ArrayList<Address>()
        var connection: HttpURLConnection? = null

        try {
            val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            val url = URL(API + "api/?q=" + encodedQuery + "&limit=" + maxResults)
            connection = url.openConnection() as HttpURLConnection
            connection.readTimeout = REQUEST_TIMEOUT
            connection.connectTimeout = REQUEST_TIMEOUT
            connection.requestMethod = "GET"
            connection.addRequestProperty("User-Agent", WebSettings.getDefaultUserAgent(activity))
            connection.addRequestProperty("Content-type", "application/json")

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val jsonObject = JSONObject(Utils.readStream(connection.inputStream))
                val features = jsonObject.optJSONArray("features")

                if (features != null) {
                    for (index in 0 until features.length()) {
                        val address = parsePhotonFeature(features.getJSONObject(index))
                        if (address != null) {
                            addresses.add(address)
                        }
                    }
                }
            } else {
                Log.w(TAG, "getFromLocationName: Server returned non-OK status: $responseCode")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error in getFromLocationName", e)
        } catch (e: JSONException) {
            Log.e(TAG, "Error in getFromLocationName", e)
        } finally {
            connection?.disconnect()
        }

        return addresses
    }

    fun getFromLocation(lat: Double, lon: Double): Address? {
        geocoder?.let {
            return getFromLocation(it, lat, lon)
        }

        var connection: HttpURLConnection? = null

        try {
            val url = URL(API + "reverse?lon=" + lon + "&lat=" + lat)
            connection = url.openConnection() as HttpURLConnection
            connection.readTimeout = REQUEST_TIMEOUT
            connection.connectTimeout = REQUEST_TIMEOUT
            connection.requestMethod = "GET"
            connection.addRequestProperty("User-Agent", WebSettings.getDefaultUserAgent(activity))
            connection.addRequestProperty("Content-type", "application/json")

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val jsonObject = JSONObject(Utils.readStream(connection.inputStream))
                val features = jsonObject.optJSONArray("features")

                if (features != null && features.length() > 0) {
                    return parsePhotonFeature(features.getJSONObject(0))
                }
            } else {
                Log.w(TAG, "getFromLocation: Server returned non-OK status: $responseCode")
            }
        } catch (exception: IOException) {
            Log.e(TAG, "getFromLocation: ", exception)
        } catch (exception: JSONException) {
            Log.e(TAG, "getFromLocation: ", exception)
        } finally {
            connection?.disconnect()
        }

        return null
    }

    @Throws(JSONException::class)
    private fun parsePhotonFeature(feature: JSONObject?): Address? {
        if (feature == null) {
            return null
        }

        val address = Address(Locale.ENGLISH)
        val geometry = feature.optJSONObject("geometry")
        if (geometry != null) {
            val coordinates = geometry.optJSONArray("coordinates")
            if (coordinates != null && coordinates.length() >= 2) {
                address.longitude = coordinates.getDouble(0)
                address.latitude = coordinates.getDouble(1)
            }
        }

        val properties = feature.optJSONObject("properties")
        if (properties != null) {
            val name = properties.optString("name")
            val city = properties.optString("city")
            val type = properties.optString("type")

            address.featureName = name
            address.locality = if (city.isEmpty() && "city" == type) name else city

            val locality = properties.optString("locality")
            val district = properties.optString("district")
            address.subLocality = if (!locality.isEmpty()) locality else district

            address.countryName = properties.optString("country")
            address.countryCode = properties.optString("countrycode")
            address.adminArea = properties.optString("state")
            address.subAdminArea = properties.optString("county")
            address.thoroughfare = properties.optString("street")
        }

        return address
    }

    private fun getFromLocationName(geocoder: Geocoder, query: String, maxResults: Int): List<Address>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            var result: List<Address>? = null
            val latch = CountDownLatch(1)
            geocoder.getFromLocationName(query, maxResults) { addresses ->
                result = addresses
                latch.countDown()
            }
            latch.await(GEOCODER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            result
        } else {
            try {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(query, maxResults)
            } catch (e: IOException) {
                null
            }
        }
    }

    private fun getFromLocation(geocoder: Geocoder, lat: Double, lon: Double): Address? {
        val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            var result: List<Address>? = null
            val latch = CountDownLatch(1)
            geocoder.getFromLocation(lat, lon, 1) { values ->
                result = values
                latch.countDown()
            }
            latch.await(GEOCODER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            result
        } else {
            try {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(lat, lon, 1)
            } catch (e: IOException) {
                null
            }
        }
        return addresses?.firstOrNull()
    }

    companion object {
        private const val TAG = "Geocoder"
        private const val API = "https://photon.komoot.io/"
        private const val REQUEST_TIMEOUT = 5_000
        private const val GEOCODER_TIMEOUT_SECONDS = 5L
    }
}
