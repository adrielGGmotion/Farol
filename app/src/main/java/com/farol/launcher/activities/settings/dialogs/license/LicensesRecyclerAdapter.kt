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

package com.farol.launcher.activities.settings.dialogs.license

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.farol.launcher.R
import androidx.core.view.size

open class LicensesRecyclerAdapter(context: Context) : RecyclerView.Adapter<LicensesRecyclerAdapter.ViewHolder>() {
    private val inflater: LayoutInflater = LayoutInflater.from(context)

    open class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.name)
        private val user: TextView = itemView.findViewById(R.id.user)
        private val license: TextView = itemView.findViewById(R.id.license)

        internal fun setLicense(licenseData: Array<String>) {
            name.setText(licenseData[0])
            user.setText(licenseData[1])
            license.text = itemView.resources.getString(R.string.license_name, licenseData[2])
        }
    }

        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        viewHolder.setLicense(licenses[position])
    }

    override fun getItemCount(): Int = licenses.size

    override fun onCreateViewHolder(container: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(inflater.inflate(R.layout.licenses_item, container, false))
    }

    private companion object {
        val licenses = arrayOf(
            arrayOf("Android", "The Android Open Source Project", "Apache 2.0"),
            arrayOf("Android Fading Edge Layout", "Yang Bo", "Apache 2.0"),
            arrayOf("Android Jetpack", "The Android Open Source Project", "Apache 2.0"),
            arrayOf("Android Support Library", "The Android Open Source Project", "Apache 2.0"),
            arrayOf("Carbon", "Zileoni", "Apache 2.0"),
            arrayOf("Date Parser", "sisyphsu", "MIT"),
            arrayOf("Glide", "Meta", "MIT"),
            arrayOf("Glide Transformations", "Daichi Furiya", "Apache 2.0"),
            arrayOf("Hidden Api Refine Plugin", "RikkaW", "MIT"),
            arrayOf("Jsoup", "Jonathan Hedley", "MIT"),
            arrayOf("Material Components for Android", "The Android Open Source Project", "Apache 2.0"),
            arrayOf("Material Design", "The Android Open Source Project", "Apache 2.0"),
            arrayOf("OkHttp", "Square", "Apache 2.0"),
            arrayOf("RecyclerView Fast Scroller", "Quiph", "Apache 2.0"),
            arrayOf("RSS Parser", "Marco Gomiero", "Apache 2.0"),
            arrayOf("Smart Tab Layout", "ogaclejapan", "Apache 2.0"),
            arrayOf("Squiggly Slider", "Saket Narayan", "Apache 2.0"),
            arrayOf("Sunrise Sunset Calculator", "Mike Reedell", "Apache 2.0"),
            arrayOf("Weather Data", "MET Norway", "NLOD 2.0 and CC 4.0")
        )
    }
}
