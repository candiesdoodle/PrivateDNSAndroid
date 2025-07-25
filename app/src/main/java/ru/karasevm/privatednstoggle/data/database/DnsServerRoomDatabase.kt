package ru.karasevm.privatednstoggle.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import ru.karasevm.privatednstoggle.data.DnsServerDao
import ru.karasevm.privatednstoggle.model.DnsServer
import ru.karasevm.privatednstoggle.model.WifiConfig

@Database(entities = [DnsServer::class, WifiConfig::class], version = 2, exportSchema = false)
abstract class DnsServerRoomDatabase : RoomDatabase() {

    abstract fun dnsServerDao(): DnsServerDao
    abstract fun wifiConfigDao(): WifiConfigDao

    companion object {
        @Volatile
        private var INSTANCE: DnsServerRoomDatabase? = null
        fun getDatabase(context: Context): DnsServerRoomDatabase {
            val tempInstance = INSTANCE
            if (tempInstance != null) {
                return tempInstance
            }
            synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DnsServerRoomDatabase::class.java,
                    "dns_server_database"
                ).build()
                INSTANCE = instance
                return instance
            }
        }
    }
}