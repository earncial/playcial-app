package com.playcial.app.ui.recyclebin

import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.playcial.app.data.local.RecycleBinEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecycleBinAdapter(
    private val onRestore: (RecycleBinEntity) -> Unit,
    private val onDeleteForever: (RecycleBinEntity) -> Unit
) : ListAdapter<RecycleBinEntity, RecycleBinAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val card = CardView(context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(24, 12, 24, 12) }
            radius = 28f
            cardElevation = 3f
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 24, 28, 24)
        }
        val name = TextView(context).apply { textSize = 14f; setTypeface(typeface, android.graphics.Typeface.BOLD) }
        val meta = TextView(context).apply { textSize = 11f; alpha = 0.6f }
        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 0)
        }
        val restoreBtn = Button(context).apply { text = "Restore" }
        val deleteBtn = Button(context).apply { text = "Delete forever"; setTextColor(android.graphics.Color.parseColor("#E53935")) }
        buttonRow.addView(restoreBtn)
        buttonRow.addView(deleteBtn)
        column.addView(name)
        column.addView(meta)
        column.addView(buttonRow)
        card.addView(column)
        return ViewHolder(card, name, meta, restoreBtn, deleteBtn)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = getItem(position)
        holder.name.text = entry.displayName
        val date = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(entry.deletedAt))
        val sizeMb = entry.sizeBytes / (1024.0 * 1024.0)
        holder.meta.text = "Deleted $date • ${String.format("%.1f MB", sizeMb)}"
        holder.restoreBtn.setOnClickListener { onRestore(entry) }
        holder.deleteBtn.setOnClickListener { onDeleteForever(entry) }
    }

    class ViewHolder(
        itemView: android.view.View,
        val name: TextView,
        val meta: TextView,
        val restoreBtn: Button,
        val deleteBtn: Button
    ) : RecyclerView.ViewHolder(itemView)

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<RecycleBinEntity>() {
            override fun areItemsTheSame(oldItem: RecycleBinEntity, newItem: RecycleBinEntity) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: RecycleBinEntity, newItem: RecycleBinEntity) = oldItem == newItem
        }
    }
}
