package com.google.ai.edge.gallery.ui.recovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.data.recovery.Entry
import com.google.ai.edge.gallery.data.recovery.EntryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecoveryViewModel @Inject constructor(
  private val repository: EntryRepository
) : ViewModel() {

  val allEntries: StateFlow<List<Entry>> = repository.allEntries
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList()
    )

  fun saveEntry(
    mood: Int,
    sleepQuality: Int,
    stressLevel: Int,
    socialConnection: Int,
    cravingIntensity: Int,
    selfEfficacy: Int,
    trigger: String?,
    triggerNote: String?,
    note: String
  ) {
    viewModelScope.launch {
      val entry = Entry(
        mood = mood,
        sleepQuality = sleepQuality,
        stressLevel = stressLevel,
        socialConnection = socialConnection,
        cravingIntensity = cravingIntensity,
        selfEfficacy = selfEfficacy,
        trigger = trigger,
        triggerNote = triggerNote,
        note = note
      )
      repository.save(entry)
    }
  }
}
