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

package com.stario.launcher.sheet.drawer.category

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.stario.launcher.R
import com.stario.launcher.sheet.drawer.category.list.FolderList

open class Categories : Fragment() {
    companion object {
        const val FOLDER_STACK_ID = "com.stario.CATEGORIES_FRAGMENTS"
        private const val RESTORE_IDENTIFIER = "Categories.RESTORE"
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(RESTORE_IDENTIFIER, true)
        super.onSaveInstanceState(outState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.drawer_categories, container, false)
        var restored = false
        if (savedInstanceState?.getBoolean(RESTORE_IDENTIFIER, false) == true) {
            val manager = parentFragmentManager
            for (fragment in manager.fragments) {
                if (fragment.javaClass == FolderList::class.java) {
                    if (fragment.isHidden) manager.beginTransaction().show(fragment).commit()
                    restored = true
                    break
                }
            }
        }
        if (!restored) {
            view.post {
                try {
                    parentFragmentManager.beginTransaction().add(R.id.categories, FolderList()).commit()
                } catch (exception: Exception) {
                    Log.e("Categories", "FolderList failed to attach.")
                }
            }
        }
        return view
    }
}
