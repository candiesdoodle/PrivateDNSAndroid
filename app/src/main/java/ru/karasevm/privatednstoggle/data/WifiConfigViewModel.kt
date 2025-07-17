package ru.karasevm.privatednstoggle.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.karasevm.privatednstoggle.model.WifiConfig

class WifiConfigViewModel(private val wifiConfigRepository: WifiConfigRepository) : ViewModel() {

    val allWifiConfigs: LiveData<List<WifiConfig>> = wifiConfigRepository.allWifiConfigs.asLiveData()

    suspend fun getById(id: Int): WifiConfig? {
        return wifiConfigRepository.getById(id)
    }

    suspend fun getBySsid(ssid: String): WifiConfig? {
        return wifiConfigRepository.getBySsid(ssid)
    }

    fun insert(wifiConfig: WifiConfig) =
        viewModelScope.launch {
            wifiConfigRepository.insert(wifiConfig)
        }

    fun update(wifiConfig: WifiConfig) =
        viewModelScope.launch {
            wifiConfigRepository.update(wifiConfig)
        }

    fun delete(id: Int) = viewModelScope.launch { wifiConfigRepository.delete(id) }

    fun deleteAll() = viewModelScope.launch { wifiConfigRepository.deleteAll() }
}

class WifiConfigViewModelFactory(private val wifiConfigRepository: WifiConfigRepository) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WifiConfigViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WifiConfigViewModel(wifiConfigRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}