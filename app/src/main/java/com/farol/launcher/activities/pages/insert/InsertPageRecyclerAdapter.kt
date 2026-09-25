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

package com.farol.launcher.activities.pages.insert

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.util.Pair
import androidx.recyclerview.widget.RecyclerView
import com.farol.launcher.R
import com.farol.launcher.sheet.SheetDialogFragment
import com.farol.launcher.sheet.SheetType
import com.farol.launcher.themes.ThemedActivity
import java.lang.reflect.InvocationTargetException
import androidx.core.view.size

class InsertPageRecyclerAdapter(
    private val activity: ThemedActivity,
    private val listener: InsertPageDialog.OnItemSelected
) : RecyclerView.Adapter<InsertPageRecyclerAdapter.ViewHolder>() {
    private val items = ArrayList<Pair<SheetType, Class<out SheetDialogFragment>>>()

    fun setItems(items: List<Pair<SheetType, Class<out SheetDialogFragment>>>) {
        val oldSize = this.items.size
        this.items.clear()
        if (oldSize > 0) notifyItemRangeRemoved(0, oldSize)
        this.items.addAll(items)
        if (items.isNotEmpty()) notifyItemRangeInserted(0, items.size)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val position: TextView = itemView.findViewById(R.id.position)
        val label: TextView = itemView.findViewById(R.id.label)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val item = items[position]
        val clazz = item.second
        var name: String? = null
        try {
            name = clazz.getMethod("getName").invoke(null) as String
        } catch (exception: NoSuchMethodException) {
            Log.e(TAG, "inflatePage: ${clazz.name} does not implement getName(). Defaulting to class name...")
        } catch (exception: InvocationTargetException) {
            Log.e(TAG, "inflatePage: ${clazz.name} does not implement getName(). Defaulting to class name...")
        } catch (exception: IllegalAccessException) {
            Log.e(TAG, "inflatePage: ${clazz.name} getName() method is not publicly visible. Defaulting to class name...")
        } catch (exception: ClassCastException) {
            Log.e(TAG, "inflatePage: ${clazz.name} getName() return type is not ${String::class.java.name}. Defaulting to class name...")
        } finally {
            viewHolder.label.text = name ?: clazz.simpleName
        }

        val resources = activity.resources
        viewHolder.position.text = resources.getText(R.string.default_position).toString() + ": "
        val positionLabel = when (item.first) {
            SheetType.LEFT_SHEET -> R.string.left
            SheetType.TOP_SHEET -> R.string.top
            SheetType.RIGHT_SHEET -> R.string.right
            SheetType.BOTTOM_SHEET -> R.string.bottom
            else -> null
        }
        if (positionLabel != null) {
            viewHolder.position.append(resources.getText(positionLabel))
            viewHolder.position.visibility = View.VISIBLE
        } else {
            viewHolder.position.visibility = View.GONE
        }

        viewHolder.itemView.setOnClickListener { listener.onSelect(item) }
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(container: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(activity).inflate(R.layout.insert_item, container, false))

    companion object {
        private const val TAG = "InsertPageRecyclerAdapter"
    }
}
