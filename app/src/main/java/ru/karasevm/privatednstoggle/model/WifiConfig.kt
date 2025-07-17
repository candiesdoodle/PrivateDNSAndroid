package ru.karasevm.privatednstoggle.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Embedded
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "wifi_configs")
data class WifiConfig(
    @SerialName("id")
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @SerialName("ssid")
    val ssid: String = "",
    @SerialName("enabled")
    val enabled: Boolean = true,
    @SerialName("onConnectAction")
    @Embedded(prefix = "connect_")
    val onConnectAction: WifiAction,
    @SerialName("onDisconnectAction")
    @Embedded(prefix = "disconnect_")
    val onDisconnectAction: WifiAction
)
