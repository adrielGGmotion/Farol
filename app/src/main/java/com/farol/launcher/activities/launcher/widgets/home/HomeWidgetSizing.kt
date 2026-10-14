package com.farol.launcher.activities.launcher.widgets.home

import com.google.gson.annotations.SerializedName
import com.farol.launcher.utils.Utils
import kotlin.math.ceil
import kotlin.math.floor

class HomeWidgetRecord(
    @field:SerializedName("id") val id: Int,
    @field:SerializedName("provider") val provider: String,
    @field:SerializedName("userSerial") val userSerial: Long,
    @field:SerializedName("spanX") val spanX: Int,
    @field:SerializedName("spanY") val spanY: Int
) {
    val itemId: String
        get() = "HomeWidget:$id"

    fun serialize(): String = Utils.getGsonInstance().toJson(this)

    companion object {
        fun deserialize(data: String?): HomeWidgetRecord? = try {
            val record = Utils.getGsonInstance().fromJson(data, HomeWidgetRecord::class.java)
            if (record == null || record.id < 0 || record.provider.isBlank() ||
                record.userSerial < 0 || record.spanX < 1 || record.spanY < 1
            ) {
                null
            } else {
                record
            }
        } catch (exception: Exception) {
            null
        }
    }
}

class HomeWidgetPending(
    @field:SerializedName("id") val id: Int,
    @field:SerializedName("provider") val provider: String,
    @field:SerializedName("userSerial") val userSerial: Long,
    @field:SerializedName("spanX") val spanX: Int,
    @field:SerializedName("spanY") val spanY: Int,
    @field:SerializedName("phase") val phase: String
) {
    fun toRecord(): HomeWidgetRecord = HomeWidgetRecord(id, provider, userSerial, spanX, spanY)

    fun canRestore(restoredRequestId: Int?): Boolean = phase == PHASE_COMMIT || id == restoredRequestId

    fun serialize(): String = Utils.getGsonInstance().toJson(this)

    companion object {
        const val PHASE_BIND = "bind"
        const val PHASE_CONFIGURE = "configure"
        const val PHASE_COMMIT = "commit"

        fun deserialize(data: String?): HomeWidgetPending? = try {
            val pending = Utils.getGsonInstance().fromJson(data, HomeWidgetPending::class.java)
            if (pending == null || pending.id < 0 || pending.provider.isBlank() ||
                pending.userSerial < 0 || pending.spanX < 1 || pending.spanY < 1 ||
                pending.phase !in setOf(PHASE_BIND, PHASE_CONFIGURE, PHASE_COMMIT)
            ) {
                null
            } else {
                pending
            }
        } catch (exception: Exception) {
            null
        }
    }
}

data class HomeWidgetSpan(val columns: Int, val rows: Int)

data class HomeWidgetProviderLimits(
    val minWidthPx: Int,
    val minHeightPx: Int,
    val minResizeWidthPx: Int,
    val minResizeHeightPx: Int,
    val maxResizeWidthPx: Int,
    val maxResizeHeightPx: Int,
    val targetCellWidth: Int,
    val targetCellHeight: Int,
    val resizeHorizontal: Boolean,
    val resizeVertical: Boolean
)

data class HomeWidgetBounds(
    val minColSpan: Int,
    val minRowSpan: Int,
    val maxColSpan: Int,
    val maxRowSpan: Int,
    val minWidth: Int,
    val minHeight: Int,
    val maxWidth: Int,
    val maxHeight: Int
)

object HomeWidgetSizing {
    fun initialSpan(
        requested: HomeWidgetSpan,
        limits: HomeWidgetProviderLimits,
        cellWidth: Int,
        cellHeight: Int,
        columns: Int,
        rows: Int
    ): HomeWidgetSpan {
        val width = maxOf(requested.columns, limits.targetCellWidth, minCells(limits.minWidthPx, cellWidth))
        val height = maxOf(requested.rows, limits.targetCellHeight, minCells(limits.minHeightPx, cellHeight))
        return HomeWidgetSpan(width.coerceIn(1, maxOf(1, columns)), height.coerceIn(1, maxOf(1, rows)))
    }

    fun bounds(
        initial: HomeWidgetSpan,
        limits: HomeWidgetProviderLimits,
        cellWidth: Int,
        cellHeight: Int,
        columns: Int,
        rows: Int,
        forceResizable: Boolean = false
    ): HomeWidgetBounds {
        // Ignore what the provider declares: any size from one cell up to the whole grid.
        if (forceResizable) return HomeWidgetBounds(1, 1, -1, -1, -1, -1, -1, -1)
        val minWidth = resizeMinimum(limits.minResizeWidthPx, limits.minWidthPx)
        val minHeight = resizeMinimum(limits.minResizeHeightPx, limits.minHeightPx)
        val minCols = if (limits.resizeHorizontal) {
            minCells(minWidth, cellWidth)
        } else {
            initial.columns
        }.coerceIn(1, maxOf(1, columns))
        val minRows = if (limits.resizeVertical) {
            minCells(minHeight, cellHeight)
        } else {
            initial.rows
        }.coerceIn(1, maxOf(1, rows))
        val maxCols = maxSpan(limits.maxResizeWidthPx, cellWidth, columns, minCols, limits.resizeHorizontal, initial.columns)
        val maxRows = maxSpan(limits.maxResizeHeightPx, cellHeight, rows, minRows, limits.resizeVertical, initial.rows)
        return HomeWidgetBounds(
            minCols,
            minRows,
            maxCols,
            maxRows,
            positiveOrMinusOne(minWidth),
            positiveOrMinusOne(minHeight),
            positiveOrMinusOne(limits.maxResizeWidthPx),
            positiveOrMinusOne(limits.maxResizeHeightPx)
        )
    }

    private fun minCells(pixels: Int, cell: Int): Int = if (pixels > 0 && cell > 0) {
        maxOf(1, ceil(pixels.toDouble() / cell).toInt())
    } else {
        1
    }

    private fun maxSpan(
        pixels: Int,
        cell: Int,
        gridMax: Int,
        min: Int,
        resizable: Boolean,
        fixed: Int
    ): Int {
        if (!resizable) return fixed
        if (pixels <= 0 || cell <= 0) return -1
        return maxOf(min, minOf(gridMax, floor(pixels.toDouble() / cell).toInt()))
    }

    private fun resizeMinimum(resize: Int, base: Int): Int = if (resize > 0) resize else base

    private fun positiveOrMinusOne(value: Int): Int = if (value > 0) value else -1
}
