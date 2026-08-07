package com.playcial.app.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.playcial.app.data.model.VideoFolder

class FolderAdapter(
    private val onClick: (VideoFolder) -> Unit
) : ListAdapter<VideoFolder, FolderAdapter.FolderViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val context = parent.context
        val card = CardView(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).let { RecyclerView.LayoutParams(it).apply { setMargins(16, 8, 16, 8) } }
            radius = 32f
            cardElevation = 4f
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(24, 24, 24, 24)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val thumb = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(140, 140)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 0, 0, 0)
        }
        val name = TextView(context).apply { textSize = 15f; setTypeface(typeface, android.graphics.Typeface.BOLD) }
        val meta = TextView(context).apply { textSize = 12f; alpha = 0.6f }
        textContainer.addView(name)
        textContainer.addView(meta)
        row.addView(thumb)
        row.addView(textContainer)
        card.addView(row)
        return FolderViewHolder(card, thumb, name, meta)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val folder = getItem(position)
        holder.thumb.load(folder.coverVideoUri)
        holder.name.text = folder.name
        val sizeMb = folder.totalSizeBytes / (1024.0 * 1024.0)
        holder.meta.text = "${folder.videoCount} videos • ${String.format("%.0f MB", sizeMb)}"
        holder.itemView.setOnClickListener { onClick(folder) }
    }

    class FolderViewHolder(
        itemView: android.view.View,
        val thumb: ImageView,
        val name: TextView,
        val meta: TextView
    ) : RecyclerView.ViewHolder(itemView)

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<VideoFolder>() {
            override fun areItemsTheSame(oldItem: VideoFolder, newItem: VideoFolder) = oldItem.path == newItem.path
            override fun areContentsTheSame(oldItem: VideoFolder, newItem: VideoFolder) = oldItem == newItem
        }
    }
}
