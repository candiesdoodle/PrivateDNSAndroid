package ru.karasevm.privatednstoggle.ui

import android.content.ClipData
import android.content.ClipDescription.MIMETYPE_TEXT_PLAIN
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ShareCompat
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.DOWN
import androidx.recyclerview.widget.ItemTouchHelper.UP
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import ru.karasevm.privatednstoggle.PrivateDNSApp
import ru.karasevm.privatednstoggle.R
import ru.karasevm.privatednstoggle.data.DnsServerViewModel
import ru.karasevm.privatednstoggle.data.DnsServerViewModelFactory

import ru.karasevm.privatednstoggle.databinding.FragmentDnsServerListBinding
import ru.karasevm.privatednstoggle.model.DnsServer
import ru.karasevm.privatednstoggle.util.BackupUtils
import ru.karasevm.privatednstoggle.util.PreferenceHelper
import ru.karasevm.privatednstoggle.util.PreferenceHelper.dns_servers

class DnsServerListFragment : Fragment(), AddServerDialogFragment.NoticeDialogListener,
    DeleteServerDialogFragment.NoticeDialogListener {

    private var _binding: FragmentDnsServerListBinding? = null
    private val binding get() = _binding!!

    private lateinit var linearLayoutManager: LinearLayoutManager
    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var adapter: ServerListRecyclerAdapter
    private lateinit var clipboard: ClipboardManager
    private val dnsServerViewModel: DnsServerViewModel by viewModels { DnsServerViewModelFactory((activity?.application as PrivateDNSApp).dnsServerRepository) }

    private val itemTouchHelper by lazy {
        val simpleItemTouchCallback =
            object : ItemTouchHelper.SimpleCallback(UP or DOWN, 0) {
                var dragFrom = -1
                var dragTo = -1

                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    if (dragFrom == viewHolder.bindingAdapterPosition && dragTo == target.bindingAdapterPosition) {
                        return true
                    }
                    // store the drag position
                    if (dragFrom == -1) dragFrom = viewHolder.bindingAdapterPosition
                    dragTo = target.bindingAdapterPosition
                    adapter.onItemMove(
                        viewHolder.bindingAdapterPosition,
                        target.bindingAdapterPosition
                    )
                    return true
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

                override fun onSelectedChanged(
                    viewHolder: RecyclerView.ViewHolder?, actionState: Int
                ) {
                    super.onSelectedChanged(viewHolder, actionState)
                    if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                        viewHolder?.itemView?.apply {
                            // Example: Elevate the view
                            elevation = 8f
                            alpha = 0.5f
                            setBackgroundColor(Color.GRAY)
                        }
                    }
                }

                override fun clearView(
                    recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder
                ) {
                    super.clearView(recyclerView, viewHolder)
                    viewHolder.itemView.apply {
                        // Reset the appearance
                        elevation = 0f
                        alpha = 1.0f
                        setBackgroundColor(Color.TRANSPARENT)
                    }
                    // commit the change to the db
                    dnsServerViewModel.move(
                        dragFrom,
                        dragTo,
                        (viewHolder as ServerListRecyclerAdapter.DnsServerViewHolder).id
                    )
                    dragTo = -1
                    dragFrom = -1
                }
            }
        ItemTouchHelper(simpleItemTouchCallback)
    }

    private fun importSettings(json: String) {
        runCatching {
            val data: BackupUtils.Backup = Json.decodeFromString<BackupUtils.Backup>(json)
            BackupUtils.import(data, dnsServerViewModel, sharedPrefs)
        }.onSuccess {
            Toast.makeText(
                context, getString(R.string.import_success), Toast.LENGTH_SHORT
            ).show()
        }.onFailure { exception ->
            runCatching {
                Log.e("IMPORT", "Malformed json, falling back to legacy", exception)
                val data = Json.decodeFromString<BackupUtils.LegacyBackup>(json)
                BackupUtils.importLegacy(data, dnsServerViewModel, sharedPrefs)
            }.onSuccess {
                Toast.makeText(
                    context, getString(R.string.import_success), Toast.LENGTH_SHORT
                ).show()
            }.onFailure { exception ->
                Log.e("IMPORT", "Import failed", exception)
                Toast.makeText(
                    context, getString(R.string.import_failure), Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     *  Migrate the SharedPreferences server list to Room
     */
    private fun migrateServerList() {
        dnsServerViewModel.viewModelScope.launch {
            if (sharedPrefs.dns_servers.isNotEmpty() && sharedPrefs.dns_servers[0] != "") {
                Log.i(
                    "migrate",
                    "existing sharedPrefs list: ${sharedPrefs.dns_servers} ${sharedPrefs.dns_servers.size}"
                )
                sharedPrefs.dns_servers.forEach { server ->
                    val parts = server.split(" : ").toMutableList()
                    if (parts.size != 2) parts.add(0, "")
                    Log.i("migrate", "migrating: $server -> $parts")
                    dnsServerViewModel.insert(DnsServer(0, parts[1], parts[0]))
                }
                sharedPrefs.dns_servers = emptyList<String>().toMutableList()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDnsServerListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        linearLayoutManager = LinearLayoutManager(context)
        binding.recyclerView.layoutManager = linearLayoutManager

        sharedPrefs = PreferenceHelper.defaultPreference(requireContext())
        clipboard = requireActivity().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        migrateServerList()

        adapter = ServerListRecyclerAdapter(true)
        binding.recyclerView.adapter = adapter

        dnsServerViewModel.allServers.observe(viewLifecycleOwner) { servers ->
            adapter.submitList(servers)
            if (servers.isEmpty()) {
                binding.emptyView.visibility = View.VISIBLE
                binding.emptyViewHint.visibility = View.VISIBLE
            } else {
                binding.emptyView.visibility = View.GONE
                binding.emptyViewHint.visibility = View.GONE
            }
        }
        adapter.onItemClick = { id ->
            dnsServerViewModel.viewModelScope.launch {
                val server = dnsServerViewModel.getById(id)
                if (server != null) {
                    val newFragment =
                        AddServerDialogFragment(server)
                    newFragment.show(childFragmentManager, "edit_server")
                }
            }
        }
        adapter.onDragStart = { viewHolder ->
            itemTouchHelper.startDrag(viewHolder)
        }
        binding.recyclerView.adapter = adapter
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     *  Show the dialog for deleting the server
     *  @param id The server id
     */
    override fun onDeleteItemClicked(id: Int) {
        val newFragment = DeleteServerDialogFragment(id)
        newFragment.show(childFragmentManager, "delete_server")
    }

    /**
     *  Callback for adding the server
     *  @param label The label
     *  @param server The server
     */
    override fun onAddDialogPositiveClick(label: String?, server: String) {
        if (server.isEmpty()) {
            Toast.makeText(context, R.string.server_length_error, Toast.LENGTH_SHORT).show()
            return
        }

        if (label.isNullOrEmpty()) {
            dnsServerViewModel.insert(DnsServer(0, server))
        } else {
            dnsServerViewModel.insert(DnsServer(0, server, label))
        }
    }

    /**
     *  Callback for deleting the server
     *  @param id The server id
     */
    override fun onDeleteDialogPositiveClick(id: Int) {
        dnsServerViewModel.delete(id)
    }

    /**
     *  Callback for updating the server
     *  @param label New label
     *  @param server New server address
     *  @param id The server id
     */
    override fun onUpdateDialogPositiveClick(
        id: Int,
        server: String,
        label: String?,
        enabled: Boolean
    ) {
        if (server.isEmpty()) {
            Toast.makeText(context, R.string.server_length_error, Toast.LENGTH_SHORT).show()
            return
        }
        dnsServerViewModel.update(id, server, label, null, enabled)
    }
}