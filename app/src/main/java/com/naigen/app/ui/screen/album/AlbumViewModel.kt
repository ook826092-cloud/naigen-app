package com.naigen.app.ui.screen.album

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.naigen.app.NaiApplication
import com.naigen.app.data.db.entities.HistoryEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlbumViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = getApplication<NaiApplication>().historyRepository

    /** 仅展示成功的生成记录（相册里不该有失败条目） */
    val items: StateFlow<List<HistoryEntity>> = repo.observeAll()
        .let { flow ->
            kotlinx.coroutines.flow.map(flow) { list -> list.filter { it.success } }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }
    fun clearAll() = viewModelScope.launch { repo.clearAll() }
}
