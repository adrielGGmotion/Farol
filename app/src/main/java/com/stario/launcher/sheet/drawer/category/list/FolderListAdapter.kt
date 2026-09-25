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

package com.stario.launcher.sheet.drawer.category.list

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.stario.launcher.R
import com.stario.launcher.apps.Category
import com.stario.launcher.apps.CategoryManager
import com.stario.launcher.preferences.Vibrations
import com.stario.launcher.sheet.drawer.category.Categories
import com.stario.launcher.sheet.drawer.category.folder.Folder
import com.stario.launcher.themes.ThemedActivity
import com.stario.launcher.ui.icons.AdaptiveIconView
import com.stario.launcher.ui.recyclers.async.AsyncRecyclerAdapter
import com.stario.launcher.ui.utils.UiUtils
import com.stario.launcher.ui.utils.animation.Animation
import com.stario.launcher.ui.utils.animation.FragmentTransition
import com.stario.launcher.ui.utils.animation.SharedElementTransition
import java.util.function.Supplier
import androidx.core.view.size

open class FolderListAdapter(private val activity: ThemedActivity, private val folderList: FolderList) :
    AsyncRecyclerAdapter<FolderListAdapter.ViewHolder>(activity) {
    companion object {
        private const val TARGET_ELEVATION = 10f
        private const val TARGET_SCALE = 0.9f
    }

    private val categoryManager = CategoryManager.getInstance()
    private val folder = Folder()
    private val listener = object : CategoryManager.CategoryListener {
        private var preparedRemovalIndex = -1
        override fun onCreated(category: Category) {
            val index = categoryManager.indexOf(category)
            if (index >= 0) UiUtils.post { notifyItemInserted(index) }
        }

        override fun onChanged(category: Category?) {
            val index = category?.let { categoryManager.indexOf(it) } ?: -1
            if (index >= 0) UiUtils.post { notifyItemChanged(index) }
        }

        override fun onPrepareRemoval(category: Category) {
            if (preparedRemovalIndex < 0) preparedRemovalIndex = categoryManager.indexOf(category)
        }

        @android.annotation.SuppressLint("NotifyDataSetChanged")
        override fun onRemoved(category: Category) {
            if (preparedRemovalIndex >= 0) {
                UiUtils.post { notifyItemRemoved(preparedRemovalIndex) }
                preparedRemovalIndex = -1
            } else {
                UiUtils.post { notifyDataSetChanged() }
            }
        }
    }

    init { setHasStableIds(true) }

    open fun move(viewHolder: RecyclerView.ViewHolder, targetHolder: RecyclerView.ViewHolder): Boolean {
        var position = viewHolder.bindingAdapterPosition
        val target = targetHolder.bindingAdapterPosition
        if (position == target || position == RecyclerView.NO_POSITION || target == RecyclerView.NO_POSITION) return false
        while (position - target != 0) {
            val newTarget = position - if (position - target > 0) 1 else -1
            categoryManager.swap(position, newTarget)
            notifyItemMoved(position, newTarget)
            position = newTarget
        }
        return true
    }

    open fun focus(holder: RecyclerView.ViewHolder) {
        if (holder is ViewHolder) {
            holder.itemView.bringToFront()
            holder.itemView.animate().scaleY(TARGET_SCALE).scaleX(TARGET_SCALE)
                .translationZ(TARGET_ELEVATION).setDuration(Animation.MEDIUM.getDuration().toLong())
            holder.category.animate().alpha(0f).setDuration(Animation.MEDIUM.getDuration().toLong())
        }
    }

    open fun reset(holder: RecyclerView.ViewHolder) {
        if (holder is ViewHolder) {
            holder.itemView.animate().scaleY(1f).scaleX(1f).translationZ(0f)
                .setDuration(Animation.MEDIUM.getDuration().toLong())
            holder.category.animate().alpha(1f).setDuration(Animation.MEDIUM.getDuration().toLong())
        }
    }

    inner class ViewHolder : AsyncViewHolder() {
        lateinit var category: TextView
        lateinit var recycler: RecyclerView
        lateinit var adapter: FolderListItemAdapter

        override fun onInflated() {
            category = itemView.findViewById(R.id.category)
            recycler = itemView.findViewById(R.id.items)
            recycler.layoutManager = createManager()
            recycler.itemAnimator = null
            adapter = FolderListItemAdapter(activity)
            recycler.adapter = adapter
            itemView.isHapticFeedbackEnabled = false
            recycler.isHapticFeedbackEnabled = false
        }

        private fun createManager(): GridLayoutManager {
            val manager = object : GridLayoutManager(activity, 4) {
                override fun supportsPredictiveItemAnimations(): Boolean = false
            }
            manager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int =
                    if (position < FolderListItemAdapter.SOFT_LIMIT ||
                        (::adapter.isInitialized && adapter.itemCount < FolderListItemAdapter.HARD_LIMIT)) 2 else 1
            }
            return manager
        }

        fun updateCategory(category: Category) { adapter.setCategory(category) }
    }

    override fun onBind(holder: ViewHolder, position: Int) {
        val category = categoryManager.get(position)
        holder.category.text = categoryManager.getCategoryName(category.identifier)
        val clickListener = object : View.OnClickListener {
            private fun getIcon(view: View): AdaptiveIconView? {
                if (view is AdaptiveIconView) return view
                if (view is ViewGroup) {
                    for (index in 0 until view.childCount) {
                        val icon = getIcon(view.getChildAt(index))
                        if (icon != null) return icon
                    }
                }
                return null
            }

            override fun onClick(view: View) {
                if (!folder.isAdded && !folder.isRemoving) {
                    Vibrations.getInstance().vibrate()
                    val excluded = ArrayList<View>()
                    val fragmentManager: FragmentManager = folderList.parentFragmentManager
                    val layoutManager = holder.recycler.layoutManager
                    if (layoutManager != null) {
                        val transaction = fragmentManager.beginTransaction()
                        for (index in 0 until minOf(holder.adapter.itemCount, FolderListItemAdapter.HARD_LIMIT)) {
                            val group = requireNotNull(layoutManager.findViewByPosition(index))
                            excluded.add(group)
                            val icon = getIcon(group)
                            if (icon != null) {
                                transaction.addSharedElement(icon, icon.transitionName)
                                excluded.add(icon)
                            }
                        }
                        if (UiUtils.areAnimationsOn() && UiUtils.areTransitionsOn()) {
                            folder.sharedElementEnterTransition = SharedElementTransition(excluded)
                            folder.enterTransition = FragmentTransition(true, excluded)
                            folderList.exitTransition = FragmentTransition(false, excluded)
                            folderList.reenterTransition = FragmentTransition(true)
                        } else {
                            folder.sharedElementEnterTransition = null
                            folder.enterTransition = null
                            folderList.exitTransition = null
                            folderList.reenterTransition = null
                        }
                        folder.sharedElementReturnTransition = null
                        transaction.setReorderingAllowed(true)
                        transaction.addToBackStack(Categories.FOLDER_STACK_ID)
                        transaction.hide(folderList).add(R.id.categories, folder)
                        transaction.commit()
                        folder.updateCategory(category.identifier)
                    }
                }
            }
        }
        holder.itemView.setOnClickListener(clickListener)
        holder.recycler.setOnClickListener(clickListener)
        holder.updateCategory(category)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        categoryManager.addOnCategoryUpdateListener(listener)
        super.onAttachedToRecyclerView(recyclerView)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        categoryManager.removeOnCategoryUpdateListener(listener)
        super.onDetachedFromRecyclerView(recyclerView)
    }

    override fun getLayout(viewType: Int): Int = R.layout.folder
    override fun getHolderSupplier(viewType: Int): Supplier<ViewHolder> = Supplier { ViewHolder() }
    override fun getItemId(position: Int): Long = categoryManager.get(position).identifier.hashCode().toLong()
    override fun getTotalItemCount(): Int = categoryManager.size()
}
