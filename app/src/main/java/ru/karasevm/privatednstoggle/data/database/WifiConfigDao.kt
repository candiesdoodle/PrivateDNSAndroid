package ru.karasevm.privatednstoggle.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.karasevm.privatednstoggle.model.WifiConfig

@Dao
interface WifiConfigDao {
    @Query("SELECT * FROM wifi_configs ORDER BY ssid ASC")
    fun getAll(): Flow<List<WifiConfig>>

    @Query("SELECT * FROM wifi_configs WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): WifiConfig?

    @Query("SELECT * FROM wifi_configs WHERE ssid = :ssid LIMIT 1")
    suspend fun getBySsid(ssid: String): WifiConfig?

    @Insert
    suspend fun insert(wifiConfig: WifiConfig)

    @Update
    suspend fun update(wifiConfig: WifiConfig)

    @Query("DELETE FROM wifi_configs WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM wifi_configs")
    suspend fun deleteAll()
}