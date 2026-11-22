package com.brutalbeats.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.brutalbeats.player.playback.PlayerController
import com.brutalbeats.player.ui.BrutalBeatsApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** The single screen of the app. Everything else is Compose. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var playerController: PlayerController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        playerController.connect() // starts / attaches to the music service
        setContent { BrutalBeatsApp() }
    }
}
