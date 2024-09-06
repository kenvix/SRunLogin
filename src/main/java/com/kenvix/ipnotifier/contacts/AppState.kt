package com.kenvix.ipnotifier.contacts

import kotlinx.serialization.Serializable

class AppState {
}

@Serializable
data class IPState(
    val oldAddr: String,
    val newAddr: String,
)
