package com.playcial.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.playcial.app.data.model.Video
import com.playcial.app.data.model.VideoFolder
import com.playcial.app.data.repository.FileOpResult
import com.playcial.app.data.repository.FileOperationsRepository
import com.playcial.app.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortMode { NEWEST, OLDEST, LARGEST, SMALLEST, LONGEST, SHORTEST, NAME_ASC, NAME_DESC }
enum class ViewMode { GRID, LIST }
enum class LibraryTab { VIDEOS, FOLDERS }

data class HomeUiState(
    val isLoading: Boolean = true,
    val videos: List<Video> = emptyList(),
    val folders: List<VideoFolder> = emptyList(),
    val tab: LibraryTab = LibraryTab.VIDEOS,
    val viewMode: ViewMode = ViewMode.GRID,
    val sortMode: SortMode = SortMode.NEWEST,
    val searchQuery: String = "",
    val showHidden: Boolean = false,
    val selectionMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    val continueWatching: List<Video> = emptyList(),
    val favoritesSection: List<Video> = emptyList(),
    val recentlyAdded: List<Video> = emptyList()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val fileOps: FileOperationsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events

    private var rawVideos: List<Video> = emptyList()

    fun loadLibrary() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val videos = repository.queryAllVideos()
            val folders = repository.queryFolders()
            rawVideos = videos
            refreshVisibleList(folders)
        }
    }

    private fun refreshVisibleList(folders: List<VideoFolder> = _uiState.value.folders) {
        val state = _uiState.value
        val visible = rawVideos.filter { state.showHidden || !it.isHidden }
        val filtered = filteredByQuery(visible, state.searchQuery)
        _uiState.value = state.copy(
            isLoading = false,
            videos = applySort(filtered, state.sortMode),
            folders = folders,
            continueWatching = visible.filter { it.watchProgressMs > 0 && it.progressPercent < 95 }
                .sortedByDescending { it.watchProgressMs }
                .take(10),
            favoritesSection = visible.filter { it.isFavorite }
                .sortedByDescending { it.dateModified }
                .take(10),
            recentlyAdded = visible.sortedByDescending { it.dateAdded }.take(10)
        )
    }

    fun setTab(tab: LibraryTab) {
        _uiState.value = _uiState.value.copy(tab = tab)
    }

    fun setViewMode(mode: ViewMode) {
        _uiState.value = _uiState.value.copy(viewMode = mode)
    }

    fun setSort(mode: SortMode) {
        _uiState.value = _uiState.value.copy(sortMode = mode)
        refreshVisibleList()
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        refreshVisibleList()
    }

    fun setShowHidden(show: Boolean) {
        _uiState.value = _uiState.value.copy(showHidden = show)
        refreshVisibleList()
    }

    fun toggleFavorite(video: Video) {
        viewModelScope.launch {
            repository.toggleFavorite(video)
            rawVideos = rawVideos.map { if (it.id == video.id) it.copy(isFavorite = !it.isFavorite) else it }
            refreshVisibleList()
        }
    }

    fun hideVideo(video: Video) {
        viewModelScope.launch {
            repository.setHidden(video, true)
            rawVideos = rawVideos.map { if (it.id == video.id) it.copy(isHidden = true) else it }
            refreshVisibleList()
            _events.emit("Hidden — enable \"Show hidden\" to view it again")
        }
    }

    fun lockVideo(video: Video) {
        viewModelScope.launch {
            repository.setLocked(video, true)
            rawVideos = rawVideos.map { if (it.id == video.id) it.copy(isLocked = true) else it }
            refreshVisibleList()
            _events.emit("Locked")
        }
    }

    fun pinVideo(video: Video) {
        viewModelScope.launch {
            repository.setPinned(video, true)
            rawVideos = rawVideos.map { if (it.id == video.id) it.copy(isPinned = true) else it }
            refreshVisibleList()
            _events.emit("Pinned to top")
        }
    }

    fun renameVideo(video: Video, newName: String) {
        viewModelScope.launch {
            val result = fileOps.rename(video, newName)
            _events.emit(resultMessage(result))
            if (result is FileOpResult.Success) loadLibrary()
        }
    }

    fun deleteVideo(video: Video) {
        viewModelScope.launch {
            val result = fileOps.moveToRecycleBin(video)
            _events.emit(resultMessage(result))
            if (result is FileOpResult.Success) loadLibrary()
        }
    }

    private fun resultMessage(result: FileOpResult) = when (result) {
        is FileOpResult.Success -> result.message
        is FileOpResult.Failure -> result.message
    }

    // ---- Multi-selection ----

    fun enterSelectionMode(startId: Long) {
        _uiState.value = _uiState.value.copy(selectionMode = true, selectedIds = setOf(startId))
    }

    fun toggleSelected(id: Long) {
        val current = _uiState.value.selectedIds
        val updated = if (id in current) current - id else current + id
        _uiState.value = _uiState.value.copy(
            selectedIds = updated,
            selectionMode = updated.isNotEmpty()
        )
    }

    fun selectAll() {
        val allIds = _uiState.value.videos.map { it.id }.toSet()
        _uiState.value = _uiState.value.copy(selectedIds = allIds, selectionMode = allIds.isNotEmpty())
    }

    fun invertSelection() {
        val allIds = _uiState.value.videos.map { it.id }.toSet()
        val inverted = allIds - _uiState.value.selectedIds
        _uiState.value = _uiState.value.copy(selectedIds = inverted, selectionMode = inverted.isNotEmpty())
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectionMode = false, selectedIds = emptySet())
    }

    private fun selectedVideos(): List<Video> =
        rawVideos.filter { it.id in _uiState.value.selectedIds }

    fun deleteSelected() {
        viewModelScope.launch {
            var success = 0
            selectedVideos().forEach { video ->
                if (fileOps.moveToRecycleBin(video) is FileOpResult.Success) success++
            }
            _events.emit("Deleted $success item(s)")
            clearSelection()
            loadLibrary()
        }
    }

    fun favoriteSelected() {
        viewModelScope.launch {
            selectedVideos().forEach { repository.toggleFavorite(it) }
            clearSelection()
            loadLibrary()
        }
    }

    fun hideSelected() {
        viewModelScope.launch {
            selectedVideos().forEach { repository.setHidden(it, true) }
            _events.emit("Hidden ${_uiState.value.selectedIds.size} item(s)")
            clearSelection()
            loadLibrary()
        }
    }

    fun lockSelected() {
        viewModelScope.launch {
            selectedVideos().forEach { repository.setLocked(it, true) }
            clearSelection()
            loadLibrary()
        }
    }

    fun copySelectedTo(targetDir: java.io.File) {
        viewModelScope.launch {
            var success = 0
            selectedVideos().forEach { video ->
                if (fileOps.copyTo(video, targetDir) is FileOpResult.Success) success++
            }
            _events.emit("Copied $success item(s)")
            clearSelection()
        }
    }

    fun moveSelectedTo(targetDir: java.io.File) {
        viewModelScope.launch {
            var success = 0
            selectedVideos().forEach { video ->
                if (fileOps.moveTo(video, targetDir) is FileOpResult.Success) success++
            }
            _events.emit("Moved $success item(s)")
            clearSelection()
            loadLibrary()
        }
    }

    private fun filteredByQuery(videos: List<Video>, query: String): List<Video> {
        if (query.isBlank()) return videos
        return videos.filter {
            it.displayName.contains(query, ignoreCase = true) ||
                it.folderName.contains(query, ignoreCase = true)
        }
    }

    private fun applySort(videos: List<Video>, mode: SortMode): List<Video> {
        val sorted = when (mode) {
            SortMode.NEWEST -> videos.sortedByDescending { it.dateModified }
            SortMode.OLDEST -> videos.sortedBy { it.dateModified }
            SortMode.LARGEST -> videos.sortedByDescending { it.sizeBytes }
            SortMode.SMALLEST -> videos.sortedBy { it.sizeBytes }
            SortMode.LONGEST -> videos.sortedByDescending { it.durationMs }
            SortMode.SHORTEST -> videos.sortedBy { it.durationMs }
            SortMode.NAME_ASC -> videos.sortedBy { it.displayName.lowercase() }
            SortMode.NAME_DESC -> videos.sortedByDescending { it.displayName.lowercase() }
        }
        // Pinned videos always float to the top regardless of sort mode.
        return sorted.sortedByDescending { it.isPinned }
    }
}
