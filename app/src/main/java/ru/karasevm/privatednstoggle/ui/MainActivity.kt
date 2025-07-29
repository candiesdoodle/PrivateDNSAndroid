package ru.karasevm.privatednstoggle.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import ru.karasevm.privatednstoggle.R
import ru.karasevm.privatednstoggle.databinding.ActivityMainBinding
import ru.karasevm.privatednstoggle.service.WifiMonitorService
import ru.karasevm.privatednstoggle.util.ShizukuUtil.grantPermissionWithShizuku


import android.net.Uri
import android.provider.Settings
import androidx.appcompat.app.AlertDialog

class MainActivity : AppCompatActivity(), Shizuku.OnRequestPermissionResultListener,
    AddServerDialogFragment.NoticeDialogListener {

    private lateinit var binding: ActivityMainBinding

    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, now check for background location
            checkBackgroundLocationPermission()
        } else {
            // Permission denied, inform the user
            Toast.makeText(this, "Location permission denied. Wi-Fi features may not work.", Toast.LENGTH_LONG).show()
        }
    }

    private val requestBackgroundLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Background location permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Background location permission denied. Wi-Fi features may not work reliably in background.", Toast.LENGTH_LONG).show()
        }
    }

    // --- ADD THIS DECLARATION ---
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
        } else {
            // You might want to inform the user that notifications won't work
            Toast.makeText(this, "Notification permission denied. App notifications will not be shown.", Toast.LENGTH_LONG).show()
        }
    }
    // --- END OF ADDED DECLARATION ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Shizuku.addRequestPermissionResultListener(this::onRequestPermissionResult)

        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, DnsServerListFragment())
                .commit()
        }

        val serviceIntent = Intent(this, WifiMonitorService::class.java)
        startService(serviceIntent)

        binding.topAppBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.privacy_policy -> {
                    val browserIntent = Intent(
                        Intent.ACTION_VIEW,
                        "https://karasevm.github.io/PrivateDNSAndroid/privacy_policy".toUri()
                    )
                    startActivity(browserIntent)
                    true
                }

                R.id.options -> {
                    val newFragment = OptionsDialogFragment()
                    newFragment.show(supportFragmentManager, "options")
                    true
                }

                R.id.wifi_settings -> {
                    binding.floatingActionButton.visibility = View.GONE
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, WifiSettingsFragment())
                        .addToBackStack(null)
                        .commit()
                    true
                }

                else -> true
            }
        }

        binding.floatingActionButton.setOnClickListener {
            val newFragment = AddServerDialogFragment(null)
            newFragment.show(supportFragmentManager, "add_server")
        }

        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                binding.floatingActionButton.visibility = View.VISIBLE
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onResume() {
        super.onResume()

        // Request ACCESS_FINE_LOCATION permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            // If fine location is already granted, check for background location
            checkBackgroundLocationPermission()
        }

        // Request POST_NOTIFICATIONS permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }


        // Check if WRITE_SECURE_SETTINGS is granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
            // Check if Shizuku is available
            if (Shizuku.pingBinder()) {
                // check if permission is granted already
                val isGranted = if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
                    ContextCompat.checkSelfPermission(this, ShizukuProvider.PERMISSION) == PackageManager.PERMISSION_GRANTED
                } else {
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                }
                // request permission if not granted
                if (!isGranted && !Shizuku.shouldShowRequestPermissionRationale()) {
                    if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
                        requestPermissions(arrayOf(ShizukuProvider.PERMISSION), 1)
                    } else {
                        Shizuku.requestPermission(1)
                    }
                } else {
                    grantPermission()
                }
            } else {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
                    val browserIntent = Intent(
                        Intent.ACTION_VIEW,
                        "https://karasevm.github.io/PrivateDNSAndroid/".toUri()
                    )
                    Toast.makeText(
                        this, R.string.shizuku_failure_toast, Toast.LENGTH_SHORT
                    ).show()
                    startActivity(browserIntent)
                    finish()
                }
            }
        }
    }

    private fun checkBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // Explain to the user why we need background location
                AlertDialog.Builder(this)
                    .setTitle("Background Location Permission")
                    .setMessage("For continuous Wi-Fi monitoring and DNS changes, this app needs 'Allow all the time' location access. Please grant this in app settings.")
                    .setPositiveButton("Go to Settings") { dialog, which ->
                        openAppSettings()
                    }
                    .setNegativeButton("Cancel") { dialog, which ->
                        // User declined, Wi-Fi features might not work reliably
                        Toast.makeText(this, "Background location not granted. Wi-Fi features may not work reliably.", Toast.LENGTH_LONG).show()
                    }
                    .show()
            }
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", packageName, null)
        intent.data = uri
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(this::onRequestPermissionResult)
    }

    private fun grantPermission() {
        if (grantPermissionWithShizuku(this)) {
            Toast.makeText(
                this, R.string.shizuku_success_toast, Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(
                this, R.string.shizuku_failure_toast, Toast.LENGTH_SHORT
            ).show()
            val browserIntent = Intent(
                Intent.ACTION_VIEW,
                "https://karasevm.github.io/PrivateDNSAndroid/".toUri()
            )
            startActivity(browserIntent)
            finish()
        }
    }

    override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
        val isGranted = grantResult == PackageManager.PERMISSION_GRANTED

        if (!isGranted && checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
            val browserIntent = Intent(
                Intent.ACTION_VIEW,
                "https://karasevm.github.io/PrivateDNSAndroid/".toUri()
            )
            startActivity(browserIntent)
            finish()
        }
    }

    override fun onAddDialogPositiveClick(label: String?, server: String) {
        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (fragment is DnsServerListFragment) {
            fragment.onAddDialogPositiveClick(label, server)
        }
    }

    override fun onUpdateDialogPositiveClick(
        id: Int,
        server: String,
        label: String?,
        enabled: Boolean
    ) {
        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (fragment is DnsServerListFragment) {
            fragment.onUpdateDialogPositiveClick(id, server, label, enabled)
        }
    }

    override fun onDeleteItemClicked(id: Int) {
        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (fragment is DnsServerListFragment) {
            fragment.onDeleteItemClicked(id)
        }
    }
}