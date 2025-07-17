package ru.karasevm.privatednstoggle.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.karasevm.privatednstoggle.PrivateDNSApp
import ru.karasevm.privatednstoggle.R
import ru.karasevm.privatednstoggle.data.DnsServerRepository
import ru.karasevm.privatednstoggle.data.WifiConfigRepository
import ru.karasevm.privatednstoggle.model.WifiAction
import ru.karasevm.privatednstoggle.util.PreferenceHelper
import ru.karasevm.privatednstoggle.util.PrivateDNSUtils

class WifiMonitorService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private lateinit var wifiConfigRepository: WifiConfigRepository
    private lateinit var dnsServerRepository: DnsServerRepository
    private lateinit var sharedPreferences: android.content.SharedPreferences

    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == WifiManager.NETWORK_STATE_CHANGED_ACTION) {
                val networkInfo = intent.getParcelableExtra<android.net.NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)
                if (networkInfo?.isConnected == true) {
                    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    val ssid = wifiManager.connectionInfo.ssid.replace("\"", "")
                    handleWifiConnection(ssid)
                } else {
                    handleWifiDisconnection()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val app = applicationContext as PrivateDNSApp
        wifiConfigRepository = app.wifiConfigRepository
        dnsServerRepository = app.dnsServerRepository
        sharedPreferences = PreferenceHelper.defaultPreference(this)
        registerReceiver(wifiReceiver, IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION))
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Private DNS Toggle")
            .setContentText("Monitoring Wi-Fi changes")
            .setSmallIcon(R.drawable.ic_private_black_24dp)
            .build()
        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(wifiReceiver)
        job.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun handleWifiConnection(ssid: String) {
        Log.d(TAG, "Connected to Wi-Fi: $ssid")
        sharedPreferences.edit().putString("last_connected_wifi_ssid", ssid).apply()
        scope.launch {
            val wifiConfig = wifiConfigRepository.getBySsid(ssid)
            wifiConfig?.let { config ->
                Log.d(TAG, "Applying onConnectAction for SSID: ${config.ssid}")
                applyDnsAction(applicationContext, dnsServerRepository, config.onConnectAction)
            }
        }
    }

    private fun handleWifiDisconnection() {
        Log.d(TAG, "Disconnected from the tracked Wi-Fi network")
        scope.launch {
            val lastSsid = sharedPreferences.getString("last_connected_wifi_ssid", null)
            if (!lastSsid.isNullOrEmpty()) {
                val wifiConfig = wifiConfigRepository.getBySsid(lastSsid)
                wifiConfig?.let { config ->
                    Log.d(TAG, "Applying onDisconnectAction for SSID: ${config.ssid}")
                    applyDnsAction(applicationContext, dnsServerRepository, config.onDisconnectAction)
                }
                sharedPreferences.edit().remove("last_connected_wifi_ssid").apply()
            }
        }
    }

    private suspend fun applyDnsAction(context: Context, dnsServerRepository: DnsServerRepository, action: WifiAction) {
        when (action.type) {
            ru.karasevm.privatednstoggle.model.WifiActionType.OFF -> {
                PrivateDNSUtils.setPrivateMode(context.contentResolver, PrivateDNSUtils.DNS_MODE_OFF)
                PrivateDNSUtils.setPrivateProvider(context.contentResolver, null)
                Log.d(TAG, "DNS set to Off")
            }
            ru.karasevm.privatednstoggle.model.WifiActionType.AUTO -> {
                PrivateDNSUtils.setPrivateMode(context.contentResolver, PrivateDNSUtils.DNS_MODE_AUTO)
                PrivateDNSUtils.setPrivateProvider(context.contentResolver, null)
                Log.d(TAG, "DNS set to Auto")
            }
            ru.karasevm.privatednstoggle.model.WifiActionType.PRIVATE_DNS_SERVER -> {
                action.dnsServerId?.let { id ->
                    val dnsServer = dnsServerRepository.getById(id)
                    dnsServer?.server?.let { server ->
                        PrivateDNSUtils.setPrivateMode(context.contentResolver, PrivateDNSUtils.DNS_MODE_PRIVATE)
                        PrivateDNSUtils.setPrivateProvider(context.contentResolver, server)
                        Log.d(TAG, "DNS set to Private Provider: $server")
                    } ?: Log.e(TAG, "DNS server not found for ID: $id")
                } ?: Log.e(TAG, "DNS server ID is null for PRIVATE_DNS_SERVER action")
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Wi-Fi Monitor Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    companion object {
        private const val TAG = "WifiMonitorService"
        private const val CHANNEL_ID = "WifiMonitorServiceChannel"
    }
}