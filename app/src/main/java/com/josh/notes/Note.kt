package com.josh.notes

data class Note(
    val id: Long,
    val title: String,
    val body: String,
    val updatedAt: Long,
) {
    val displayTitle: String
        get() = title.ifBlank { body.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: "" }
}
