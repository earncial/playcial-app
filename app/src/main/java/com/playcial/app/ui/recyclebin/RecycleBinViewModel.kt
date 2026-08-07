package com.playcial.app.ui.recyclebin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.playcial.app.data.local.PlaycialDatabase
import com.playcial.app.data.local.RecycleBinEntity
import com.playcial.app.data.repository.FileOperationsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecycleBinViewModel @Inject constructor(
    private val database: PlaycialDatabase,
    private val fileOps: FileOperationsRepository
) : ViewModel() {

    val items: StateFlow<List<RecycleBinEntity>> = database.recycleBinDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events

    fun restore(entry: RecycleBinEntity) {
        viewModelScope.launch {
            val result = fileOps.restoreFromRecycleBin(entry)
            _events.emit(
                when (result) {
                    is com.playcial.app.data.repository.FileOpResult.Success -> result.message
                    is com.playcial.app.data.repository.FileOpResult.Failure -> result.message
                }
            )
        }
    }

    fun deleteForever(entry: RecycleBinEntity) {
        viewModelScope.launch {
            fileOps.permanentlyDelete(entry)
            _events.emit("Permanently deleted")
        }
    }
}
