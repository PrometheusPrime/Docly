package com.docly.app.core.common

import java.util.UUID
import javax.inject.Inject

interface IdProvider {
    fun generateId(): String
}

class UuidIdProvider @Inject constructor() : IdProvider {
    override fun generateId(): String = UUID.randomUUID().toString()
}
