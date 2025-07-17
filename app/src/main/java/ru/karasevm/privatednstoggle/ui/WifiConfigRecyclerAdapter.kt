package ru.karasevm.privatednstoggle.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ru.karasevm.privatednstoggle.R
import ru.karasevm.privatednstoggle.model.DnsServer
import ru.karasevm.privatednstoggle.model.WifiConfig
import ru.karasevm.privatednstoggle.model.WifiActionType

class WifiConfigRecyclerAdapter(
    private var dnsServers: Map<Int, DnsServer>,
    private val onItemClick: (WifiConfig) -> Unit,
    private val onEditClick: (WifiConfig) -> Unit,
    private val onDeleteClick: (WifiConfig) -> Unit
) :
    ListAdapter<WifiConfig, WifiConfigRecyclerAdapter.WifiConfigViewHolder>(WifiConfigDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WifiConfigViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.recyclerview_row_wifi_config, parent, false)
        return WifiConfigViewHolder(view)
    }

    override fun onBindViewHolder(holder: WifiConfigViewHolder, position: Int) {
        val current = getItem(position)
        holder.bind(current)
    }

    fun updateDnsServers(newDnsServers: Map<Int, DnsServer>) {
        dnsServers = newDnsServers
        notifyDataSetChanged()
    }

    inner class WifiConfigViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ssidTextView: TextView = itemView.findViewById(R.id.ssidTextView)
        private val onConnectActionTextView: TextView = itemView.findViewById(R.id.onConnectActionTextView)
        private val onDisconnectActionTextView: TextView = itemView.findViewById(R.id.onDisconnectActionTextView)
        private val editButton: ImageButton = itemView.findViewById(R.id.editButton)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)

        init {
            itemView.setOnClickListener {
                onItemClick(getItem(bindingAdapterPosition))
            }
            editButton.setOnClickListener {
                onEditClick(getItem(bindingAdapterPosition))
            }
            deleteButton.setOnClickListener {
                onDeleteClick(getItem(bindingAdapterPosition))
            }
        }

        fun bind(wifiConfig: WifiConfig) {
            ssidTextView.text = wifiConfig.ssid

            // Display onConnectAction
            onConnectActionTextView.text = getActionText(wifiConfig.onConnectAction)

            // Display onDisconnectAction
            onDisconnectActionTextView.text = getActionText(wifiConfig.onDisconnectAction)
        }

        private fun getActionText(action: ru.karasevm.privatednstoggle.model.WifiAction): String {
            return when (action.type) {
                WifiActionType.OFF -> itemView.context.getString(R.string.action_off)
                WifiActionType.AUTO -> itemView.context.getString(R.string.action_auto)
                WifiActionType.PRIVATE_DNS_SERVER -> {
                    val server = dnsServers[action.dnsServerId]
                    itemView.context.getString(R.string.action_private_dns_server) + ": " + (server?.label ?: server?.server ?: "Unknown")
                }
            }
        }
    }
}

class WifiConfigDiffCallback : DiffUtil.ItemCallback<WifiConfig>() {
    override fun areItemsTheSame(oldItem: WifiConfig, newItem: WifiConfig): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: WifiConfig, newItem: WifiConfig): Boolean {
        return oldItem == newItem
    }
}
