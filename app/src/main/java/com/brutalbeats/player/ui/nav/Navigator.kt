package com.brutalbeats.player.ui.nav

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class TopTab(val label: String) {
    HOME("Home"),
    LIBRARY("Library"),
    EQUALIZER("EQ"),
    SETTINGS("Settings")
}

/** Pages opened on top of a tab. */
sealed interface Page {
    data class Songs(val detail: Detail) : Page
    data object FolderRules : Page
    data object About : Page
}

/** Tiny navigation state: the selected tab plus a back stack of pages on top of it. */
@Stable
class Navigator {
    var tab by mutableStateOf(TopTab.HOME)
        private set
    val stack = mutableStateListOf<Page>()

    fun select(target: TopTab) {
        stack.clear()
        tab = target
    }

    fun push(page: Page) {
        stack.add(page)
    }

    /** Returns true if a page was closed. */
    fun pop(): Boolean {
        if (stack.isEmpty()) return false
        stack.removeAt(stack.lastIndex)
        return true
    }
}
