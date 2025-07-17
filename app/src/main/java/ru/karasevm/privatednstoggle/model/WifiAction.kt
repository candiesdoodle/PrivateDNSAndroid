package ru.karasevm.privatednstoggle.model

import kotlinx.serialization.Serializable

@Serializable
enum class WifiActionType {
    OFF,
    AUTO,
    PRIVATE_DNS_SERVER
}

@Serializable
data class WifiAction(
    val type: WifiActionType,
    val dnsServerId: Int? = null // Only relevant if type is PRIVATE_DNS_SERVER
)
