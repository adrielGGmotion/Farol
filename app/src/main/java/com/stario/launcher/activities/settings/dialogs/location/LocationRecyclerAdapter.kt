/*
 * Copyright (C) 2025 Răzvan Albu
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

package com.stario.launcher.activities.settings.dialogs.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Address
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.stario.launcher.R
import com.stario.launcher.activities.launcher.widgets.glance.extensions.weather.GeocoderFallback
import com.stario.launcher.activities.launcher.widgets.glance.extensions.weather.Weather
import com.stario.launcher.preferences.Entry
import com.stario.launcher.themes.ThemedActivity
import com.stario.launcher.ui.utils.UiUtils
import com.stario.launcher.utils.Utils
import java.util.ArrayList
import java.util.Locale
import androidx.core.view.size
import androidx.core.content.edit

open class LocationRecyclerAdapter(
    private val activity: ThemedActivity,
    private val clickListener: View.OnClickListener?
) : RecyclerView.Adapter<LocationRecyclerAdapter.ViewHolder>() {
    private val geocoder = GeocoderFallback(activity)
    private val preferences: SharedPreferences = activity.getApplicationContext().getSharedPreferences(Entry.WEATHER)
    private val defaultAddresses: List<Address> = loadDefaultAddresses()
    private val addresses: ArrayList<Address> = ArrayList(defaultAddresses)
    private var query: String? = null

    private fun loadDefaultAddresses(): ArrayList<Address> {
        val list = ArrayList<Address>()

        val lindholmen = Address(Locale.ENGLISH)
        lindholmen.setAddressLine(0, "Lindholmen, Gothenburg, Sweden")
        lindholmen.setFeatureName("Lindholmen")
        lindholmen.setAdminArea("Västra Götaland County")
        lindholmen.setLocality("Gothenburg")
        lindholmen.setCountryCode("SE")
        lindholmen.setCountryName("Sweden")
        lindholmen.setLatitude(57.7077599)
        lindholmen.setLongitude(11.9382865)

        list.add(lindholmen)

        val boulderCity = Address(Locale.ENGLISH)
        boulderCity.setAddressLine(0, "Boulder City, NV, USA")
        boulderCity.setFeatureName("Boulder City")
        boulderCity.setAdminArea("Nevada")
        boulderCity.setSubAdminArea("Clark County")
        boulderCity.setLocality("Boulder City")
        boulderCity.setCountryCode("US")
        boulderCity.setCountryName("United States")
        boulderCity.setLatitude(35.9782216)
        boulderCity.setLongitude(-114.8345117)

        list.add(boulderCity)

        val storo = Address(Locale.ENGLISH)
        storo.setAddressLine(0, "Storo, Oslo, Norway")
        storo.setFeatureName("Storo")
        storo.setAdminArea("Oslo")
        storo.setLocality("Oslo")
        storo.setCountryCode("NO")
        storo.setCountryName("Norway")
        storo.setLatitude(59.946576199999996)
        storo.setLongitude(10.779069800000002)

        list.add(storo)

        val sibiu = Address(Locale.ENGLISH)
        sibiu.setAddressLine(0, "Sibiu, Romania")
        sibiu.setFeatureName("Sibiu")
        sibiu.setAdminArea("Sibiu")
        sibiu.setSubAdminArea("Sibiu")
        sibiu.setLocality("Sibiu")
        sibiu.setCountryCode("Ro")
        sibiu.setCountryName("Romania")
        sibiu.setLatitude(45.803478899999995)
        sibiu.setLongitude(24.1449997)

        list.add(sibiu)

        return list
    }

    open fun update(query: String?) {
        this.query = query

        if (query == null || query.isBlank()) {
            addresses.clear()
            addresses.addAll(defaultAddresses)

            // noinspection notifyDataSetChanged
            notifyDataSetChanged()
        } else {
            Utils.submitTask {
                val addressList: List<Address>? = geocoder.getFromLocationName(query, MAX_LOCALITIES)

                if (query == this.query) {
                    UiUtils.post {
                        addresses.clear()

                        if (addressList != null) {
                            for (address in addressList) {
                                if (address.hasLatitude() && address.hasLongitude()) {
                                    addresses.add(address)
                                }
                            }
                        }

                        // noinspection notifyDataSetChanged
                        notifyDataSetChanged()
                    }
                }
            }
        }
    }

    open class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        internal val locality: TextView = itemView.findViewById(R.id.locality)
        internal val location: TextView = itemView.findViewById(R.id.location)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        if (position == 0) {
            viewHolder.locality.setText(R.string.precise_location)
            viewHolder.location.setVisibility(View.GONE)

            viewHolder.itemView.setOnClickListener { view ->
                activity.requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)) { granted ->
                    if (granted[0] == PackageManager.PERMISSION_GRANTED) {
                        preferences.edit {
                                remove(Weather.LATITUDE_KEY)
                                .remove(Weather.LONGITUDE_KEY)
                                .remove(Weather.LOCATION_NAME)
                                .putBoolean(Weather.PRECISE_LOCATION, true)
                            }

                        clickListener?.onClick(view)
                    } else {
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            val uri = Uri.fromParts("package", activity.getPackageName(), null)
                            intent.setData(uri)
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                            activity.startActivity(intent)
                        } catch (exception: ActivityNotFoundException) {
                            Log.e(TAG, "onBindViewHolder: Settings activity not found.")
                        }

                        Toast.makeText(activity, R.string.location_reasoning, Toast.LENGTH_LONG).show()
                    }
                }
            }
        } else if (position == 1) {
            viewHolder.locality.setText(R.string.location_ip_based)
            viewHolder.location.setVisibility(View.GONE)

            viewHolder.itemView.setOnClickListener { view ->
                preferences.edit {
                        remove(Weather.LATITUDE_KEY)
                        .remove(Weather.LONGITUDE_KEY)
                        .remove(Weather.LOCATION_NAME)
                        .putBoolean(Weather.PRECISE_LOCATION, false)
                    }

                clickListener?.onClick(view)
            }
        } else {
            val address = addresses[position - 2]
            var locality = address.getSubLocality()

            if (locality == null || locality.isBlank()) {
                locality = address.getLocality()

                if (locality == null || locality.isBlank()) {
                    locality = address.getFeatureName()
                }

                if (locality == null || locality.isBlank()) {
                    locality = address.getAdminArea()
                }

                viewHolder.locality.setText(locality)
                viewHolder.location.setText(address.getCountryName())
            } else {
                viewHolder.locality.setText(locality)

                val mainLocality = address.getLocality()
                if (mainLocality != null) {
                    viewHolder.location.setText(mainLocality + ", " + address.getCountryName())
                } else {
                    viewHolder.location.setText(address.getCountryName())
                }
            }

            viewHolder.location.setVisibility(View.VISIBLE)

            viewHolder.itemView.setOnClickListener { view ->
                preferences.edit {
                    putLong(Weather.LATITUDE_KEY, java.lang.Double.doubleToLongBits(address.getLatitude()))
                    putLong(Weather.LONGITUDE_KEY, java.lang.Double.doubleToLongBits(address.getLongitude()))

                    var city = address.getSubLocality()
                    if (city != null && !city.isBlank()) {
                        putString(Weather.LOCATION_NAME, city)
                    } else {
                        city = address.getLocality()

                        if (city != null && !city.isBlank()) {
                            putString(Weather.LOCATION_NAME, city)
                        } else {
                            putString(Weather.LOCATION_NAME, null)
                        }
                    }

                    putBoolean(Weather.PRECISE_LOCATION, false)
                }

                clickListener?.onClick(view)
            }
        }
    }

    override fun getItemCount(): Int = addresses.size + 2

    override fun onCreateViewHolder(container: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(activity).inflate(R.layout.location_item, container, false))
    }

    private companion object {
        const val TAG = "LocationRecyclerAdapter"
        const val MAX_LOCALITIES = 4
    }
}
