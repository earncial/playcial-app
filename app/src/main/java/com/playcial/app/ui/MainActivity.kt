package com.playcial.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.playcial.app.R
import com.playcial.app.data.model.Video
import com.playcial.app.data.model.VideoFolder
import com.playcial.app.data.repository.FileOperationsRepository
import com.playcial.app.databinding.ActivityMainBinding
import com.playcial.app.ui.common.ActionBottomSheet
import com.playcial.app.ui.common.ActionItem
import com.playcial.app.ui.common.DialogType
import com.playcial.app.ui.common.PlaycialDialog
import com.playcial.app.ui.common.RenameDialog
import com.playcial.app.ui.home.FolderAdapter
import com.playcial.app.ui.home.HomeViewModel
import com.playcial.app.ui.home.LibraryTab
import com.playcial.app.ui.home.SortMode
import com.playcial.app.ui.home.VideoAdapter
import com.playcial.app.ui.home.ViewMode
import com.playcial.app.ui.player.PlayerActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: HomeViewModel by viewModels()
    private lateinit var videoAdapter: VideoAdapter
    private lateinit var folderAdapter: FolderAdapter

    @Inject lateinit var fileOps: FileOperationsRepository

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.loadLibrary()
        else showPermissionRequiredDialog()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        setupTabs()
        setupRecycler()
        setupSwipeRefresh()
        observeState()
        observeEvents()
        ensurePermissionAndLoad()

        onBackPressedDispatcher.addCallback(this) {
            if (viewModel.uiState.value.selectionMode) {
                viewModel.clearSelection()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        if (viewModel.uiState.value.selectionMode) {
            menuInflater.inflate(R.menu.menu_selection, menu)
        } else {
            menuInflater.inflate(R.menu.menu_main, menu)
        }
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> { toggleSearchBar(); true }
            R.id.action_sort -> { showSortSheet(); true }
            R.id.action_view_toggle -> {
                val next = if (viewModel.uiState.value.viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID
                viewModel.setViewMode(next)
                true
            }
            R.id.action_select_all -> { viewModel.selectAll(); true }
            R.id.action_invert_selection -> { viewModel.invertSelection(); true }
            R.id.action_selection_delete -> { showBulkDeleteConfirm(); true }
            R.id.action_selection_favorite -> { viewModel.favoriteSelected(); true }
            R.id.action_selection_hide -> { viewModel.hideSelected(); true }
            R.id.action_selection_lock -> { viewModel.lockSelected(); true }
            R.id.action_recycle_bin -> {
                startActivity(android.content.Intent(this, com.playcial.app.ui.recyclebin.RecycleBinActivity::class.java))
                true
            }
            R.id.action_show_hidden -> {
                item.isChecked = !item.isChecked
                viewModel.setShowHidden(item.isChecked)
                true
            }
            android.R.id.home -> { viewModel.clearSelection(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showBulkDeleteConfirm() {
        val count = viewModel.uiState.value.selectedIds.size
        PlaycialDialog.Builder()
            .type(DialogType.DELETE)
            .title("Delete $count video(s)?")
            .message(getString(R.string.delete_confirm_message))
            .positive("Delete") { viewModel.deleteSelected() }
            .negative("Cancel")
            .build()
            .show(supportFragmentManager, PlaycialDialog.TAG)
    }

    private fun setupTabs() {
        binding.segmentedTabs.addTab(binding.segmentedTabs.newTab().setText(R.string.tab_videos))
        binding.segmentedTabs.addTab(binding.segmentedTabs.newTab().setText(R.string.tab_folders))
        binding.segmentedTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.setTab(if (tab.position == 0) LibraryTab.VIDEOS else LibraryTab.FOLDERS)
                renderForCurrentTab()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun setupRecycler() {
        videoAdapter = VideoAdapter(
            onClick = { video -> openPlayer(video) },
            onLongClick = { video ->
                val state = viewModel.uiState.value
                if (state.selectionMode) viewModel.toggleSelected(video.id)
                else viewModel.enterSelectionMode(video.id)
            },
            onFavoriteClick = { video -> viewModel.toggleFavorite(video) },
            onMoreClick = { video -> showVideoActionsSheet(video) }
        )
        folderAdapter = FolderAdapter(onClick = { folder -> openFolder(folder) })
        binding.videoRecycler.layoutManager = GridLayoutManager(this, 2)
        binding.videoRecycler.adapter = videoAdapter
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(R.color.brand_primary)
        binding.swipeRefresh.setOnRefreshListener { viewModel.loadLibrary() }
    }

    private var lastSelectionMode = false

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                binding.swipeRefresh.isRefreshing = false
                binding.loadingIndicator.visibility =
                    if (state.isLoading) android.view.View.VISIBLE else android.view.View.GONE

                val isEmpty = when (state.tab) {
                    LibraryTab.VIDEOS -> state.videos.isEmpty()
                    LibraryTab.FOLDERS -> state.folders.isEmpty()
                }
                binding.emptyState.visibility =
                    if (!state.isLoading && isEmpty) android.view.View.VISIBLE else android.view.View.GONE

                binding.videoRecycler.layoutManager = if (state.viewMode == ViewMode.GRID) {
                    GridLayoutManager(this@MainActivity, 2).apply {
                        spanSizeLookup = videoAdapter.spanSizeLookupFor(2)
                    }
                } else {
                    LinearLayoutManager(this@MainActivity)
                }
                videoAdapter.setSelectionState(state.selectionMode, state.selectedIds)
                videoAdapter.submitList(state.videos)
                videoAdapter.submitSections(
                    com.playcial.app.ui.home.HomeSections(
                        continueWatching = state.continueWatching,
                        favorites = state.favoritesSection,
                        recentlyAdded = state.recentlyAdded
                    )
                )
                folderAdapter.submitList(state.folders)

                if (state.selectionMode != lastSelectionMode) {
                    lastSelectionMode = state.selectionMode
                    supportActionBar?.setDisplayHomeAsUpEnabled(state.selectionMode)
                    invalidateOptionsMenu()
                }
                supportActionBar?.title = if (state.selectionMode) {
                    "${state.selectedIds.size} selected"
                } else {
                    getString(R.string.app_name)
                }
            }
        }
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            viewModel.events.collectLatest { message ->
                Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderForCurrentTab() {
        val state = viewModel.uiState.value
        binding.videoRecycler.adapter = if (state.tab == LibraryTab.VIDEOS) videoAdapter else folderAdapter
    }

    private fun ensurePermissionAndLoad() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            viewModel.loadLibrary()
        } else {
            permissionLauncher.launch(permission)
        }
    }

    private fun showPermissionRequiredDialog() {
        PlaycialDialog.Builder()
            .type(DialogType.PERMISSION)
            .title("Permission needed")
            .message("Playcial needs access to your videos to build your library.")
            .positive("Grant access") { ensurePermissionAndLoad() }
            .negative("Not now")
            .build()
            .show(supportFragmentManager, PlaycialDialog.TAG)
    }

    private fun openPlayer(video: Video) {
        startActivity(PlayerActivity.newIntent(this, video.uri, video.displayName))
    }

    private fun openFolder(folder: VideoFolder) {
        viewModel.setSearchQuery(folder.name)
        binding.segmentedTabs.getTabAt(0)?.select()
    }

    private fun toggleSearchBar() {
        // Minimal instant search entry point; a persistent search bar view
        // (with live results as you type) is the next UI slice.
        RenameDialog("") { query -> viewModel.setSearchQuery(query) }
            .show(supportFragmentManager, "search")
    }

    private fun showVideoActionsSheet(video: Video) {
        val items = listOf(
            ActionItem("rename", "Rename", iconRes = android.R.drawable.ic_menu_edit),
            ActionItem("share", "Share", iconRes = android.R.drawable.ic_menu_share),
            ActionItem("favorite", if (video.isFavorite) "Remove favorite" else "Add favorite", iconRes = android.R.drawable.btn_star),
            ActionItem("hide", "Hide", iconRes = android.R.drawable.ic_secure),
            ActionItem("lock", "Lock", iconRes = android.R.drawable.ic_lock_lock),
            ActionItem("pin", "Pin to top", iconRes = android.R.drawable.ic_menu_myplaces),
            ActionItem("details", "View Details", iconRes = android.R.drawable.ic_menu_info_details),
            ActionItem("delete", "Delete", iconRes = android.R.drawable.ic_menu_delete, isDestructive = true)
        )
        ActionBottomSheet(video.displayName, items) { selected ->
            when (selected.id) {
                "rename" -> RenameDialog(video.displayName) { newName ->
                    viewModel.renameVideo(video, newName)
                }.show(supportFragmentManager, RenameDialog.TAG)
                "share" -> startActivity(fileOps.buildShareChooser(video))
                "favorite" -> viewModel.toggleFavorite(video)
                "hide" -> viewModel.hideVideo(video)
                "lock" -> viewModel.lockVideo(video)
                "pin" -> viewModel.pinVideo(video)
                "details" -> showDetailsDialog(video)
                "delete" -> showDeleteConfirm(video)
            }
        }.show(supportFragmentManager, ActionBottomSheet.TAG)
    }

    private fun showDeleteConfirm(video: Video) {
        PlaycialDialog.Builder()
            .type(DialogType.DELETE)
            .title(getString(R.string.delete_confirm_title))
            .message(getString(R.string.delete_confirm_message))
            .positive("Delete") { viewModel.deleteVideo(video) }
            .negative("Cancel")
            .build()
            .show(supportFragmentManager, PlaycialDialog.TAG)
    }

    private fun showDetailsDialog(video: Video) {
        val sizeMb = video.sizeBytes / (1024.0 * 1024.0)
        PlaycialDialog.Builder()
            .type(DialogType.INFO)
            .title(video.displayName)
            .message(
                "Resolution: ${video.width}x${video.height} (${video.resolutionLabel})\n" +
                    "Size: ${String.format("%.1f MB", sizeMb)}\n" +
                    "Folder: ${video.folderName}\n" +
                    "Type: ${video.mimeType}"
            )
            .positive("Close")
            .negative(null)
            .build()
            .show(supportFragmentManager, PlaycialDialog.TAG)
    }

    private fun showSortSheet() {
        val items = listOf(
            ActionItem("newest", "Newest first", iconRes = android.R.drawable.ic_menu_recent_history),
            ActionItem("oldest", "Oldest first", iconRes = android.R.drawable.ic_menu_recent_history),
            ActionItem("largest", "Largest size", iconRes = android.R.drawable.ic_menu_sort_by_size),
            ActionItem("smallest", "Smallest size", iconRes = android.R.drawable.ic_menu_sort_by_size),
            ActionItem("longest", "Longest duration", iconRes = android.R.drawable.ic_menu_recent_history),
            ActionItem("az", "A-Z", iconRes = android.R.drawable.ic_menu_sort_alphabetically),
            ActionItem("za", "Z-A", iconRes = android.R.drawable.ic_menu_sort_alphabetically)
        )
        ActionBottomSheet("Sort by", items) { selected ->
            val mode = when (selected.id) {
                "newest" -> SortMode.NEWEST
                "oldest" -> SortMode.OLDEST
                "largest" -> SortMode.LARGEST
                "smallest" -> SortMode.SMALLEST
                "longest" -> SortMode.LONGEST
                "az" -> SortMode.NAME_ASC
                "za" -> SortMode.NAME_DESC
                else -> SortMode.NEWEST
            }
            viewModel.setSort(mode)
        }.show(supportFragmentManager, ActionBottomSheet.TAG)
    }
}
