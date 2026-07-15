package com.naigen.app.ui.screen.favorites

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.naigen.app.NaiApplication
import com.naigen.app.data.db.entities.FavoriteEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FavoritesViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = getApplication<NaiApplication>().favoritesRepository

    val items: StateFlow<List<FavoriteEntity>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun add(title: String, content: String, tag: String, isNegative: Boolean) = viewModelScope.launch {
        repo.insert(FavoriteEntity(title = title, content = content, tag = tag, isNegative = isNegative))
    }

    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }
}
