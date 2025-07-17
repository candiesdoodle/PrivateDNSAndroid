import android.Manifest
import android.app.Dialog
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ru.karasevm.privatednstoggle.PrivateDNSApp
import ru.karasevm.privatednstoggle.R
import ru.karasevm.privatednstoggle.data.DnsServerViewModel
import ru.karasevm.privatednstoggle.data.DnsServerViewModelFactory
import ru.karasevm.privatednstoggle.data.WifiConfigViewModel
import ru.karasevm.privatednstoggle.data.WifiConfigViewModelFactory
import ru.karasevm.privatednstoggle.databinding.DialogAddEditWifiConfigBinding
import ru.karasevm.privatednstoggle.model.DnsServer
import ru.karasevm.privatednstoggle.model.WifiAction
import ru.karasevm.privatednstoggle.model.WifiActionType
import ru.karasevm.privatednstoggle.model.WifiConfig
import android.content.Context
import android.os.Bundle
import android.content.pm.PackageManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import ru.karasevm.privatednstoggle.ui.DNSServerDialogFragment.Companion.TAG

class AddEditWifiConfigDialogFragment(private val wifiConfig: WifiConfig? = null) : DialogFragment() {

    internal lateinit var listener: NoticeDialogListener

    private var _binding: DialogAddEditWifiConfigBinding? = null
    private val binding get() = _binding!!

    private val dnsServerViewModel: DnsServerViewModel by viewModels { DnsServerViewModelFactory((activity?.application as PrivateDNSApp).dnsServerRepository) }
    private val wifiConfigViewModel: WifiConfigViewModel by viewModels { WifiConfigViewModelFactory((activity?.application as PrivateDNSApp).wifiConfigRepository) }

    private var allDnsServers: List<DnsServer> = emptyList()
    private var dnsServerMap: Map<Int, DnsServer> = emptyMap()
    private var availableSsids: List<String> = emptyList()

    interface NoticeDialogListener {
        fun onAddEditWifiConfigPositiveClick(wifiConfig: WifiConfig)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            listener = parentFragment as NoticeDialogListener
        } catch (e: ClassCastException) {
            throw ClassCastException("$parentFragment must implement NoticeDialogListener")
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogAddEditWifiConfigBinding.inflate(LayoutInflater.from(context))

        val builder = AlertDialog.Builder(requireActivity())
        builder.setView(binding.root)
            .setTitle(if (wifiConfig == null) R.string.add_wifi_config else R.string.edit_wifi_config)
            .setPositiveButton(R.string.menu_save) { dialog, id ->
                val ssid = if (availableSsids.isNotEmpty()) {
                    availableSsids[binding.ssidSpinner.selectedItemPosition]
                } else {
                    "" // Should not happen if button is enabled
                }

                if (ssid.isEmpty()) {
                    Toast.makeText(context, R.string.wifi_config_ssid_empty_error, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val onConnectAction = getSelectedWifiAction(binding.onConnectActionRadioGroup, binding.onConnectDnsServerSpinner)
                val onDisconnectAction = getSelectedWifiAction(binding.onDisconnectActionRadioGroup, binding.onDisconnectDnsServerSpinner)

                val newWifiConfig = wifiConfig?.copy(
                    ssid = ssid,
                    onConnectAction = onConnectAction,
                    onDisconnectAction = onDisconnectAction
                ) ?: WifiConfig(
                    ssid = ssid,
                    onConnectAction = onConnectAction,
                    onDisconnectAction = onDisconnectAction
                )
                listener.onAddEditWifiConfigPositiveClick(newWifiConfig)
            }
            .setNegativeButton(R.string.cancel) { dialog, id ->
                dialog.cancel()
            }

        setupDnsServerSpinners()
        setupActionRadioGroups()
        setupSsidSpinner()

        return builder.create()
    }

    private fun setupSsidSpinner() {
        binding.ssidProgressBar.visibility = View.VISIBLE
        binding.ssidSpinner.visibility = View.GONE
        binding.ssidErrorTextView.visibility = View.GONE

        lifecycleScope.launch @androidx.annotation.RequiresPermission(allOf = [android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_WIFI_STATE]) {
            val wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

            val hasLocationPermission = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            val hasWifiStatePermission = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_WIFI_STATE
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasLocationPermission || !hasWifiStatePermission) {
                Log.e(TAG, "Missing Wi-Fi permissions. Cannot retrieve configured networks.")
                binding.ssidProgressBar.visibility = View.GONE
                binding.ssidErrorTextView.text = getString(R.string.permission_missing)
                binding.ssidErrorTextView.visibility = View.VISIBLE
                (dialog as? AlertDialog)?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = false
                return@launch
            }

            @Suppress("DEPRECATION")
            val configuredNetworks = wifiManager.configuredNetworks.map { it.SSID.replace("\"", "") }
            Log.d(TAG, "Configured networks: $configuredNetworks")

            val existingWifiConfigs = wifiConfigViewModel.allWifiConfigs.value?.map { it.ssid } ?: emptyList()
            Log.d(TAG, "Existing Wi-Fi configs in app: $existingWifiConfigs")

            availableSsids = configuredNetworks.filter { it !in existingWifiConfigs || it == wifiConfig?.ssid }.distinct()
            Log.d(TAG, "Available SSIDs after deduplication: $availableSsids")

            binding.ssidProgressBar.visibility = View.GONE

            if (availableSsids.isNotEmpty()) {
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, availableSsids)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.ssidSpinner.adapter = adapter
                binding.ssidSpinner.visibility = View.VISIBLE

                wifiConfig?.let { config ->
                    val index = availableSsids.indexOf(config.ssid)
                    if (index != -1) {
                        binding.ssidSpinner.setSelection(index)
                    }
                }
            } else {
                binding.ssidErrorTextView.visibility = View.VISIBLE
                // Disable positive button if no SSIDs are available for selection
                (dialog as? AlertDialog)?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = false
            }
        }
    }

    private fun setupDnsServerSpinners() {
        dnsServerViewModel.allServers.observe(this) { servers ->
            allDnsServers = servers
            dnsServerMap = servers.associateBy { it.id }
            val dnsServerNames = servers.map { it.label.ifEmpty { it.server } }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, dnsServerNames)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

            binding.onConnectDnsServerSpinner.adapter = adapter
            binding.onDisconnectDnsServerSpinner.adapter = adapter

            // Re-select previously selected DNS server if in edit mode
            wifiConfig?.let { config ->
                setSelectedWifiAction(binding.onConnectActionRadioGroup, binding.onConnectDnsServerSpinner, config.onConnectAction, true)
                setSelectedWifiAction(binding.onDisconnectActionRadioGroup, binding.onDisconnectDnsServerSpinner, config.onDisconnectAction, false)
            }
        }
    }

    private fun setupActionRadioGroups() {
        binding.onConnectActionRadioGroup.setOnCheckedChangeListener { group, checkedId ->
            binding.onConnectDnsServerSpinner.visibility = if (checkedId == R.id.onConnectActionPrivateDnsServer) View.VISIBLE else View.GONE
        }
        binding.onDisconnectActionRadioGroup.setOnCheckedChangeListener { group, checkedId ->
            binding.onDisconnectDnsServerSpinner.visibility = if (checkedId == R.id.onDisconnectActionPrivateDnsServer) View.VISIBLE else View.GONE
        }
    }

    private fun getSelectedWifiAction(radioGroup: RadioGroup, spinner: Spinner): WifiAction {
        val actionType = when (radioGroup.checkedRadioButtonId) {
            R.id.onConnectActionOff, R.id.onDisconnectActionOff -> WifiActionType.OFF
            R.id.onConnectActionAuto, R.id.onDisconnectActionAuto -> WifiActionType.AUTO
            R.id.onConnectActionPrivateDnsServer, R.id.onDisconnectActionPrivateDnsServer -> WifiActionType.PRIVATE_DNS_SERVER
            else -> WifiActionType.OFF // Default
        }
        val dnsServerId = if (actionType == WifiActionType.PRIVATE_DNS_SERVER) {
            allDnsServers.getOrNull(spinner.selectedItemPosition)?.id
        } else {
            null
        }
        return WifiAction(actionType, dnsServerId)
    }

    private fun setSelectedWifiAction(radioGroup: RadioGroup, spinner: Spinner, action: WifiAction, isConnect: Boolean) {
        val offButtonId = if (isConnect) R.id.onConnectActionOff else R.id.onDisconnectActionOff
        val autoButtonId = if (isConnect) R.id.onConnectActionAuto else R.id.onDisconnectActionAuto
        val privateDnsButtonId = if (isConnect) R.id.onConnectActionPrivateDnsServer else R.id.onDisconnectActionPrivateDnsServer

        when (action.type) {
            WifiActionType.OFF -> radioGroup.findViewById<RadioButton>(offButtonId).isChecked = true
            WifiActionType.AUTO -> radioGroup.findViewById<RadioButton>(autoButtonId).isChecked = true
            WifiActionType.PRIVATE_DNS_SERVER -> {
                radioGroup.findViewById<RadioButton>(privateDnsButtonId).isChecked = true
                action.dnsServerId?.let { id ->
                    val server = dnsServerMap[id]
                    val index = allDnsServers.indexOf(server)
                    if (index != -1) {
                        spinner.setSelection(index)
                    }
                }
                spinner.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
