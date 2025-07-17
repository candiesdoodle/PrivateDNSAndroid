package ru.karasevm.privatednstoggle.data

import androidx.annotation.WorkerThread
import kotlinx.coroutines.flow.Flow
import ru.karasevm.privatednstoggle.data.database.WifiConfigDao
import ru.karasevm.privatednstoggle.model.WifiConfig

class WifiConfigRepository(private val wifiConfigDao: WifiConfigDao) {

    val allWifiConfigs: Flow<List<WifiConfig>> = wifiConfigDao.getAll()

    @WorkerThread
    suspend fun getById(id: Int): WifiConfig? {
        return wifiConfigDao.getById(id)
    }

    @WorkerThread
    suspend fun getBySsid(ssid: String): WifiConfig? {
        return wifiConfigDao.getBySsid(ssid)
    }

    @WorkerThread
    suspend fun insert(wifiConfig: WifiConfig) {
        wifiConfigDao.insert(wifiConfig)
    }

    @WorkerThread
    suspend fun update(wifiConfig: WifiConfig) {
        wifiConfigDao.update(wifiConfig)
    }

    @WorkerThread
    suspend fun delete(id: Int) {
        wifiConfigDao.deleteById(id)
    }

    @WorkerThread
    suspend fun deleteAll() {
        wifiConfigDao.deleteAll()
    }
}