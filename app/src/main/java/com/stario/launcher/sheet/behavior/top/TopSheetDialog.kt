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

package com.stario.launcher.sheet.behavior.top

import android.view.View
import com.stario.launcher.R
import com.stario.launcher.sheet.SheetCoordinator
import com.stario.launcher.sheet.SheetDialog
import com.stario.launcher.sheet.SheetType
import com.stario.launcher.sheet.behavior.SheetBehavior
import com.stario.launcher.themes.ThemedActivity

class TopSheetDialog(
    activity: ThemedActivity,
    themeResId: Int
) : SheetDialog(activity, themeResId) {
    private var container: SheetCoordinator? = null

    override fun getContainer(): SheetCoordinator {
        val existing = container
        if (existing != null) {
            return existing
        }

        val created = View.inflate(context, R.layout.top_sheet_dialog, null) as SheetCoordinator
        container = created

        sheet = created.findViewById(R.id.design_top_sheet)
        behavior = SheetBehavior.from(sheet)

        return created
    }

    override fun getType(): SheetType = SheetType.TOP_SHEET
}
