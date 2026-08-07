package com.playcial.app.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.playcial.app.data.model.Video
import com.playcial.app.databinding.ItemVideoGridBinding
import java.util.concurrent.TimeUnit

data class HomeSections(
    val continueWatching: List<Video>,
    val favorites: List<Video>,
    val recentlyAdded: List<Video>
)

private const val VIEW_TYPE_HEADER = 0
private const val VIEW_TYPE_VIDEO = 1

/**
 * Backs the home grid. Position 0 is an optional full-span header hosting the
 * Continue Watching / Favorites / Recently Added horizontal rows; every
 * position after that is a regular video card. This keeps the whole home
 * screen -- sections and library alike -- inside one recycled RecyclerView
 * instead of nesting scrollables, which is what actually lets it stay smooth
 * at 50,000+ videos.
 */
class VideoAdapter(
    private val onClick: (Video) -> Unit,
    private val onLongClick: (Video) -> Unit,
    private val onFavoriteClick: (Video) -> Unit,
    private val onMoreClick: (Video) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var videos: List<Video> = emptyList()
    private var sections: HomeSections? = null

    var selectionMode: Boolean = false
    var selectedIds: Set<Long> = emptySet()

    fun setSelectionState(mode: Boolean, ids: Set<Long>) {
        selectionMode = mode
        selectedIds = ids
        notifyItemRangeChanged(headerOffset(), videos.size)
    }

    fun submitList(newVideos: List<Video>) {
        videos = newVideos
        notifyDataSetChanged()
    }

    fun submitSections(newSections: HomeSections) {
        val hadHeader = hasHeader()
        sections = newSections
        if (hadHeader) notifyItemChanged(0) else notifyItemInserted(0)
    }

    private fun hasHeader(): Boolean {
        val s = sections ?: return false
        return s.continueWatching.isNotEmpty() || s.favorites.isNotEmpty() || s.recentlyAdded.isNotEmpty()
    }

    private fun headerOffset() = if (hasHeader()) 1 else 0

    override fun getItemCount(): Int = videos.size + headerOffset()

    override fun getItemViewType(position: Int): Int =
        if (hasHeader() && position == 0) VIEW_TYPE_HEADER else VIEW_TYPE_VIDEO

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_HEADER) {
            HeaderViewHolder(SectionsHeaderView(parent.context), onClick)
        } else {
            val binding = ItemVideoGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            VideoViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderViewHolder -> holder.bind(sections)
            is VideoViewHolder -> holder.bind(videos[position - headerOffset()])
        }
    }

    /** Full-span row for a GridLayoutManager; call from the Activity when wiring the layout manager. */
    fun spanSizeLookupFor(spanCount: Int) = object : androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup() {
        override fun getSpanSize(position: Int): Int =
            if (hasHeader() && position == 0) spanCount else 1
    }

    inner class HeaderViewHolder(
        private val view: SectionsHeaderView,
        private val onVideoClick: (Video) -> Unit
    ) : RecyclerView.ViewHolder(view) {
        fun bind(sections: HomeSections?) {
            sections ?: return
            view.bind(sections, onVideoClick)
        }
    }

    inner class VideoViewHolder(private val binding: ItemVideoGridBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(video: Video) {
            binding.thumbnail.load(video.uri) { crossfade(true) }
            binding.duration.text = formatDuration(video.durationMs)
            binding.resolutionBadge.text = video.resolutionLabel
            binding.videoName.text = video.displayName
            binding.folderName.text = video.folderName
            binding.videoSize.text = formatSize(video.sizeBytes)
            binding.progressBar.progress = video.progressPercent
            binding.progressBar.visibility =
                if (video.progressPercent > 0) android.view.View.VISIBLE else android.view.View.GONE
            binding.favoriteIcon.alpha = if (video.isFavorite) 1f else 0.5f

            val isSelected = video.id in selectedIds
            binding.root.alpha = if (selectionMode && !isSelected) 0.55f else 1f
            binding.favoriteIcon.setImageResource(
                if (isSelected) android.R.drawable.checkbox_on_background
                else android.R.drawable.btn_star_big_on
            )

            binding.root.setOnClickListener {
                if (selectionMode) onLongClick(video) else onClick(video)
            }
            binding.root.setOnLongClickListener { onLongClick(video); true }
            binding.favoriteIcon.setOnClickListener {
                if (selectionMode) onLongClick(video) else onFavoriteClick(video)
            }
            binding.moreButton.setOnClickListener { onMoreClick(video) }
            binding.moreButton.visibility =
                if (selectionMode) android.view.View.GONE else android.view.View.VISIBLE
        }

        private fun formatDuration(ms: Long): String {
            val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms)
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
            else String.format("%d:%02d", minutes, seconds)
        }

        private fun formatSize(bytes: Long): String {
            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1 -> String.format("%.2f GB", gb)
                mb >= 1 -> String.format("%.1f MB", mb)
                else -> String.format("%.0f KB", kb)
            }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Video>() {
            override fun areItemsTheSame(oldItem: Video, newItem: Video) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Video, newItem: Video) = oldItem == newItem
        }
    }
}

/** Vertical stack of the three horizontal home rows; only inflates rows that have content. */
private class SectionsHeaderView(context: android.content.Context) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        layoutParams = RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT
        )
    }

    fun bind(sections: HomeSections, onClick: (Video) -> Unit) {
        removeAllViews()
        addRowIfNotEmpty("Continue Watching", sections.continueWatching, onClick)
        addRowIfNotEmpty("Favorites", sections.favorites, onClick)
        addRowIfNotEmpty("Recently Added", sections.recentlyAdded, onClick)
    }

    private fun addRowIfNotEmpty(title: String, items: List<Video>, onClick: (Video) -> Unit) {
        if (items.isEmpty()) return

        val label = TextView(context).apply {
            text = title
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(24, 24, 24, 8)
        }
        addView(label)

        val recycler = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = HorizontalVideoAdapter(onClick).also { it.submitList(items) }
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 260)
            setPadding(16, 0, 16, 16)
            clipToPadding = false
        }
        addView(recycler)
    }
}
