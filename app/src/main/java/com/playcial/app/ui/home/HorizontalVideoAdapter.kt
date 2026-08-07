package com.playcial.app.ui.home

import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.playcial.app.data.model.Video

/** Compact card used inside the horizontal Continue Watching / Recently Added / Favorites rows. */
class HorizontalVideoAdapter(
    private val onClick: (Video) -> Unit
) : ListAdapter<Video, HorizontalVideoAdapter.Holder>(VideoAdapter.DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val context = parent.context
        val card = CardView(context).apply {
            layoutParams = RecyclerView.LayoutParams(320, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(8, 8, 8, 8)
            }
            radius = 24f
            cardElevation = 3f
        }
        val frame = FrameLayout(context)
        val thumb = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(320, 180)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        val label = TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = android.view.Gravity.BOTTOM }
            setBackgroundColor(android.graphics.Color.parseColor("#99000000"))
            setTextColor(android.graphics.Color.WHITE)
            setPadding(12, 8, 12, 8)
            textSize = 11f
            maxLines = 1
        }
        frame.addView(thumb)
        frame.addView(label)
        card.addView(frame)
        return Holder(card, thumb, label)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val video = getItem(position)
        holder.thumb.load(video.uri)
        holder.label.text = video.displayName
        holder.itemView.setOnClickListener { onClick(video) }
    }

    class Holder(itemView: android.view.View, val thumb: ImageView, val label: TextView) :
        RecyclerView.ViewHolder(itemView)
}
