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

package com.stario.launcher.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.UserHandle
import android.os.UserManager
import android.text.TextUtils
import android.util.Log
import androidx.annotation.FloatRange
import com.github.sisyphsu.dateparser.DateParser
import com.google.gson.Gson
import com.stario.launcher.BuildConfig
import com.stario.launcher.apps.ProfileManager
import com.stario.launcher.services.AccessibilityService
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Arrays
import java.util.Date
import java.util.HashSet
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import androidx.core.view.isEmpty

open class Utils {
    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 6.0.1; Nexus 5X Build/MMB29P) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/W.X.Y.Z Mobile Safari/537.36 (compatible; Googlebot/2.1; +https://www.google.com/bot.html)"

        private const val TAG = "com.stario.Utils"
        private val executorPool: ExecutorService = Executors.newCachedThreadPool()
        private val IPV4_APIS = arrayOf(
            "https://checkip.amazonaws.com/",
            "https://ipv4.icanhazip.com/",
            "https://ipv4.seeip.org",
            "https://api.ipify.org/"
        )
        private val IMPERIAL_COUNTRIES: Set<String> = HashSet(Arrays.asList(
            "US", // United States
            "PW", // Palau
            "MH", // Marshall Islands
            "MP", // Northern Mariana Islands
            "AS", // American Samoa
            "KY", // Cayman Islands
            "VI", // U.S. Virgin Islands
            "FM", // Micronesia
            "GU", // Guam
            "LR", // Liberia
            "PR" // Puerto Rico
        ))
        private var dateParser: DateParser? = null
        private var gson: Gson? = null

        @JvmStatic
        fun submitTask(runnable: Runnable): Future<*> {
            return executorPool.submit(runnable)
        }

        @JvmStatic
        fun <O> submitTask(callable: Callable<O>): CompletableFuture<O?> {
            return CompletableFuture.supplyAsync({
                try {
                    callable.call()
                } catch (exception: Exception) {
                    Log.e(TAG, "submitTask: ", exception)

                    null
                }
            }, executorPool)
        }

        @JvmStatic
        fun parseDate(date: String): Date? {
            if (dateParser == null) {
                dateParser = DateParser.newBuilder().build()
            }

            return dateParser!!.parseDate(date)
        }

        @JvmStatic
        fun getGsonInstance(): Gson {
            if (gson == null) {
                gson = Gson()
            }

            return gson!!
        }

        @JvmStatic
        fun isSystemUsingImperial(context: Context?): Boolean {
            if (context == null) {
                return false
            }

            val locale: Locale = context.getResources()
                .getConfiguration().getLocales().get(0)
            val countryCode = locale.country

            return IMPERIAL_COUNTRIES.contains(countryCode)
        }

        @JvmStatic
        fun isMinimumSDK(SDK: Int): Boolean {
            return Build.VERSION.SDK_INT >= SDK
        }

        @JvmStatic
        fun toFahrenheit(celsius: Double): Double {
            return celsius * 1.8 + 32
        }

        @JvmStatic
        fun msToMph(speed: Double): Double {
            return speed * 2.237
        }

        @JvmStatic
        fun intToUUID(value: Int): UUID {
            return UUID.nameUUIDFromBytes(
                ByteBuffer.allocate(java.lang.Integer.SIZE / java.lang.Byte.SIZE)
                    .putInt(value).array()
            )
        }

        @JvmStatic
        fun getGenericInterpolatedValue(@FloatRange(from = 0.0, to = 1.0) value: Double): Double {
            return if (value < 0.5) {
                4 * value * value * value
            } else {
                1 - Math.pow(-2 * value + 2, 3.0) / 2
            }
        }

        @JvmStatic
        fun getMainActivity(
            launcherApps: LauncherApps?,
            packageName: String,
            handle: UserHandle
        ): LauncherActivityInfo? {
            val activityInfoList: List<LauncherActivityInfo> =
                launcherApps!!.getActivityList(packageName, handle)

            if (!activityInfoList.isEmpty()) {
                return activityInfoList[0]
            }

            return null
        }

        @JvmStatic
        fun getMainActivity(
            context: Context,
            packageName: String,
            handle: UserHandle
        ): LauncherActivityInfo? {
            return getMainActivity(
                context.getSystemService(LauncherApps::class.java),
                packageName,
                handle
            )
        }

        @JvmStatic
        fun isMainProfile(handle: UserHandle?): Boolean {
            return handle != null && handle == ProfileManager.getOwner()
        }

        @JvmStatic
        fun isProfileAvailable(context: Context, handle: UserHandle): Boolean {
            return context.getSystemService(UserManager::class.java)!!.isUserUnlocked(handle)
        }

        @JvmStatic
        fun isNetworkAvailable(context: Context): Boolean {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
            val network = connectivityManager?.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

        @JvmStatic
        fun getPublicIPAddress(): String? {
            for (api in IPV4_APIS) {
                try {
                    val reader = BufferedReader(
                        InputStreamReader(URL(api).openStream(), StandardCharsets.UTF_8)
                    )

                    return reader.readLine()
                } catch (exception: Exception) {
                    Log.e(TAG, "getPublicIPAddress: ", exception)
                }
            }

            return null
        }

        @JvmStatic
        @Throws(IOException::class)
        fun readStream(inputStream: InputStream): String {
            val builder = StringBuilder()

            BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    builder.append(line)
                }
            }

            return builder.toString()
        }

        @JvmStatic
        fun isNotificationServiceEnabled(context: Context): Boolean {
            val flat = android.provider.Settings.Secure.getString(
                context.getContentResolver(),
                "enabled_notification_listeners"
            )

            if (!TextUtils.isEmpty(flat)) {
                val names = flat!!.split(":")

                for (name in names) {
                    val component = ComponentName.unflattenFromString(name)

                    if (component != null) {
                        if (TextUtils.equals(BuildConfig.APPLICATION_ID, component.getPackageName())) {
                            return true
                        }
                    }
                }
            }
            return false
        }

        @JvmStatic
        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            var accessibilityEnabled = 0
            val service = BuildConfig.APPLICATION_ID + "/" + AccessibilityService::class.java.canonicalName

            try {
                accessibilityEnabled = android.provider.Settings.Secure.getInt(
                    context.getApplicationContext().getContentResolver(),
                    android.provider.Settings.Secure.ACCESSIBILITY_ENABLED
                )
            } catch (exception: android.provider.Settings.SettingNotFoundException) {
                Log.e(
                    TAG,
                    "Error finding setting, default accessibility not found: " + exception.message
                )
            }

            val stringColonSplitter = TextUtils.SimpleStringSplitter(':')

            if (accessibilityEnabled == 1) {
                val settingValue = android.provider.Settings.Secure.getString(
                    context.getApplicationContext().getContentResolver(),
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )

                if (settingValue != null) {
                    stringColonSplitter.setString(settingValue)

                    while (stringColonSplitter.hasNext()) {
                        val accessibilityService = stringColonSplitter.next()

                        if (accessibilityService.equals(service, ignoreCase = true)) {
                            return true
                        }
                    }
                }
            }

            return false
        }
    }
}
