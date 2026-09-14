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

package com.stario.launcher.ui.common.grid

import android.content.Context
import android.util.Log
import androidx.annotation.RawRes
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.stario.launcher.Stario
import com.stario.launcher.preferences.Entry
import com.stario.launcher.utils.Utils
import java.io.InputStreamReader
import java.lang.reflect.Type

class GridTemplateManager(
    context: Stario,
    identifier: String,
    @RawRes templateId: Int,
) {
    private val templateCache = HashMap<String, GridTemplate>()
    private val prefs = context.getSharedPreferences(
        Entry.GRID_TEMPLATE_MANAGER.toSubPreference(identifier),
        Context.MODE_PRIVATE,
    )

    init {
        if (templateId == 0) {
            Log.w(TAG, "GridTemplateManager: a template file has not been provided.")
        } else {
            try {
                val inputStream = context.resources.openRawResource(templateId)
                val list: List<GridTemplate>? = Utils.getGsonInstance().fromJson(
                    InputStreamReader(inputStream),
                    object : TypeToken<List<GridTemplate>>() {}.type,
                )
                list?.forEach { template ->
                    template.processItems()
                    templateCache[template.getDimensionsKey()] = template
                }
            } catch (exception: Exception) {
                Log.e(TAG, "loadTemplates: $exception")
            }
        }
    }

    fun getLayoutForSize(cols: Int, rows: Int): Map<String, DynamicGridLayout.ItemLayoutData> {
        val layout = HashMap<String, DynamicGridLayout.ItemLayoutData>()
        val key = "${cols}x$rows"
        templateCache[key]?.let { layout.putAll(it.getItemMap()) }

        prefs.getString("state_$key", null)?.let { savedJson ->
            val type: Type = object : TypeToken<Map<String, DynamicGridLayout.ItemLayoutData>>() {}.type
            val savedMap: Map<String, DynamicGridLayout.ItemLayoutData> =
                Utils.getGsonInstance().fromJson(savedJson, type)
            layout.putAll(savedMap)
        }
        return layout
    }

    fun saveUserLayout(cols: Int, rows: Int, map: Map<String, DynamicGridLayout.ItemLayoutData>) {
        prefs.edit()
            .putString("state_${cols}x$rows", Utils.getGsonInstance().toJson(map))
            .apply()
    }

    class GridTemplate {
        @Transient
        private var itemMap: MutableMap<String, DynamicGridLayout.ItemLayoutData> = HashMap()

        @field:SerializedName("items")
        @JvmField var itemList: MutableList<DynamicGridLayout.ItemLayoutData>? = ArrayList()

        @field:SerializedName("cols")
        @JvmField var cols: Int = 0

        @field:SerializedName("rows")
        @JvmField var rows: Int = 0

        fun processItems() {
            itemMap = HashMap()
            itemList?.forEach { item -> itemMap[item.id] = item }
        }

        fun getItemMap(): Map<String, DynamicGridLayout.ItemLayoutData> {
            if (itemMap.isEmpty() && itemList?.isNotEmpty() == true) {
                processItems()
            }
            return itemMap
        }

        fun getDimensionsKey(): String = "${cols}x$rows"
    }

    companion object {
        private const val TAG = "GridTemplateManager"
    }
}
