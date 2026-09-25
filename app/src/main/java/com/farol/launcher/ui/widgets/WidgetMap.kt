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

package com.farol.launcher.ui.widgets

import com.farol.launcher.sheet.widgets.WidgetSize
import java.util.HashSet

open class WidgetMap internal constructor(columns: Int) {
    private val set: MutableSet<Cell> = HashSet()
    private var columns = columns

    open fun setColumnCount(columns: Int) {
        this.columns = columns
    }

    internal open fun add(origin: Cell, size: WidgetSize) {
        for (row in origin.row until origin.row + size.height) {
            for (column in origin.column until origin.column + size.width) {
                set.add(Cell(row, column))
            }
        }
    }

    internal open fun getAvailableOrigin(size: WidgetSize): Cell {
        var column = 0
        var row = 0
        var testedCell: Cell

        do {
            testedCell = Cell(row, column)
            column += 1

            if (column >= columns) {
                column = 0
                row++
            }
        } while (!checkFreeSpace(testedCell, size))

        return testedCell
    }

    internal open fun checkFreeSpace(origin: Cell, size: WidgetSize): Boolean {
        if (origin.column + size.width > columns) {
            return false
        }

        for (row in origin.row until origin.row + size.height) {
            for (column in origin.column until origin.column + size.width) {
                if (set.contains(Cell(row, column))) {
                    return false
                }
            }
        }

        return true
    }

    internal open fun clear() {
        set.clear()
    }

    internal open class Cell(
        @JvmField val row: Int,
        @JvmField val column: Int
    ) {
        override fun hashCode(): Int {
            var result = row
            result = 31 * result + column
            return result
        }

        override fun equals(other: Any?): Boolean {
            return other is Cell && other.row == row && other.column == column
        }
    }
}
