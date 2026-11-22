package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object LibraryRoute

@Serializable
data class EditorRoute(val scriptId: Long = 0L)

@Serializable
data class StudioRoute(val scriptId: Long)
