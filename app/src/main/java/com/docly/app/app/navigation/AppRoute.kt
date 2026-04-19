package com.docly.app.app.navigation

import kotlinx.serialization.Serializable

const val PLACEHOLDER_SESSION_ID = "placeholder-session"

@Serializable
data object ScannerRoute

@Serializable
data object LibraryRoute

@Serializable
data class ReviewRoute(val sessionId: String)

@Serializable
data class EditorRoute(val sessionId: String)

@Serializable
data class MetadataRoute(val sessionId: String)

@Serializable
data class ExportRoute(val sessionId: String)
