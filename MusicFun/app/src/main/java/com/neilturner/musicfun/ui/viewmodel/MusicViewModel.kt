package com.neilturner.musicfun.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.neilturner.musicfun.data.MusicMetadata
import com.neilturner.musicfun.data.MusicMetadataRepository
import kotlinx.coroutines.flow.StateFlow

class MusicViewModel : ViewModel() {

	val metadata: StateFlow<MusicMetadata?> = MusicMetadataRepository.metadata
}