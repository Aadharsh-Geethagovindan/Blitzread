package com.example.blitzread.engine.rsvp
import com.example.blitzread.domain.model.ReaderLocation

data class RsvpSessionState(
    val location: ReaderLocation,
    val effectiveWpm: Int,
    val isPlaying: Boolean
)
