package com.docly.app.app.navigation

import kotlinx.serialization.Serializable

const val PLACEHOLDER_SESSION_ID = "placeholder-session"

@Serializable
data object HomeRoute

@Serializable
data class ScannerRoute(val sessionId: String? = null)

@Serializable
data object LibraryRoute

@Serializable
data object SearchRoute

@Serializable
data object CreateRoute

@Serializable
data object ToolsRoute

@Serializable
data object SettingsRoute

@Serializable
data class ReviewRoute(val sessionId: String)

@Serializable
data class ReaderRoute(val documentId: String)

@Serializable
data class DocumentEditorRoute(val documentId: String)

@Serializable
data class PdfPageEditorRoute(val documentId: String)

@Serializable
data class EditorRoute(val sessionId: String)

@Serializable
data class MetadataRoute(val sessionId: String)

@Serializable
data class ExportRoute(val sessionId: String)
