package ru.karasevm.privatednstoggle.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import ru.karasevm.privatednstoggle.PrivateDNSApp
import ru.karasevm.privatednstoggle.R
import ru.karasevm.privatednstoggle.data.DnsServerViewModel
import ru.karasevm.privatednstoggle.data.DnsServerViewModelFactory
import ru.karasevm.privatednstoggle.data.WifiConfigViewModel
import ru.karasevm.privatednstoggle.data.WifiConfigViewModelFactory
import ru.karasevm.privatednstoggle.databinding.FragmentWifiSettingsBinding
import ru.karasevm.privatednstoggle.model.WifiConfig

import ru.karasevm.privatednstoggle.util.PreferenceHelper

class WifiSettingsFragment : Fragment(), AddEditWifiConfigDialogFragment.NoticeDialogListener {

    private var _binding: FragmentWifiSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: WifiConfigRecyclerAdapter
    private val wifiConfigViewModel: WifiConfigViewModel by viewModels { WifiConfigViewModelFactory((activity?.application as PrivateDNSApp).wifiConfigRepository) }
    private val dnsServerViewModel: DnsServerViewModel by viewModels { DnsServerViewModelFactory((activity?.application as PrivateDNSApp).dnsServerRepository) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWifiSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.topAppBar.setNavigationOnClickListener { // Handle back button
            activity?.onBackPressedDispatcher?.onBackPressed()
        }

        binding.topAppBar.title = getString(R.string.wifi_settings_title)

        val sharedPreferences = PreferenceHelper.defaultPreference(requireContext())
        binding.globalWifiToggle.isChecked = sharedPreferences.getBoolean("wifi_logic_enabled", true)
        binding.globalWifiToggle.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("wifi_logic_enabled", isChecked).apply()
        }

        val linearLayoutManager = LinearLayoutManager(context)
        binding.recyclerView.layoutManager = linearLayoutManager

        adapter = WifiConfigRecyclerAdapter(emptyMap(), { wifiConfig ->
            // Nothing to do on item click
        }, { wifiConfig ->
            val newFragment = AddEditWifiConfigDialogFragment(wifiConfig)
            newFragment.show(childFragmentManager, "edit_wifi_config")
        }, { wifiConfig ->
            showDeleteConfirmationDialog(wifiConfig)
        }, { wifiConfig, isEnabled ->
            wifiConfigViewModel.update(wifiConfig.copy(enabled = isEnabled))
        })
        binding.recyclerView.adapter = adapter

        wifiConfigViewModel.allWifiConfigs.observe(viewLifecycleOwner) { wifiConfigs ->
            adapter.submitList(wifiConfigs)
            if (wifiConfigs.isEmpty()) {
                binding.emptyView.visibility = View.VISIBLE
            } else {
                binding.emptyView.visibility = View.GONE
            }
        }

        dnsServerViewModel.allServers.observe(viewLifecycleOwner) { dnsServers ->
            adapter.updateDnsServers(dnsServers.associateBy { it.id })
        }

        binding.floatingActionButton.setOnClickListener {
            val newFragment = AddEditWifiConfigDialogFragment()
            newFragment.show(childFragmentManager, "add_wifi_config")
        }
    }

    private fun showDeleteConfirmationDialog(wifiConfig: WifiConfig) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_wifi_config_title)
            .setMessage(R.string.delete_wifi_config_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                wifiConfigViewModel.delete(wifiConfig.id)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onAddEditWifiConfigPositiveClick(wifiConfig: WifiConfig) {
        if (wifiConfig.id == 0) {
            wifiConfigViewModel.insert(wifiConfig)
        } else {
            wifiConfigViewModel.update(wifiConfig)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
