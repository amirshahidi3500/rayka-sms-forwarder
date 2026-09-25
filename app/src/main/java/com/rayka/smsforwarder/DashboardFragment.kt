package com.rayka.smsforwarder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    private val uiHandler = Handler(Looper.getMainLooper())

    private lateinit var switchOnline: SwitchMaterial
    private lateinit var switchOffline: SwitchMaterial
    private lateinit var editMainUrl: TextInputEditText
    private lateinit var editLocalUrl: TextInputEditText
    private lateinit var editInterval: TextInputEditText
    private lateinit var txtServiceStatus: MaterialTextView
    private lateinit var txtCountTotal: MaterialTextView
    private lateinit var txtCountSynced: MaterialTextView
    private lateinit var txtCountQueued: MaterialTextView
    private lateinit var btnToggleService: MaterialButton

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = result.values.all { it }
        Toast.makeText(
            requireContext(),
            if (allGranted) "مجوزها با موفقیت گرفته شد" else "برخی مجوزها رد شدند — سرویس بدون آن‌ها کار نمی‌کند",
            Toast.LENGTH_LONG
        ).show()
    }

    private val counterPoller = object : Runnable {
        override fun run() {
            refreshCounts()
            uiHandler.postDelayed(this, 2500)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Prefs.init(requireContext())

        switchOnline = view.findViewById(R.id.switchOnline)
        switchOffline = view.findViewById(R.id.switchOffline)
        editMainUrl = view.findViewById(R.id.editMainUrl)
        editLocalUrl = view.findViewById(R.id.editLocalUrl)
        editInterval = view.findViewById(R.id.editInterval)
        txtServiceStatus = view.findViewById(R.id.txtServiceStatus)
        txtCountTotal = view.findViewById(R.id.txtCountTotal)
        txtCountSynced = view.findViewById(R.id.txtCountSynced)
        txtCountQueued = view.findViewById(R.id.txtCountQueued)
        btnToggleService = view.findViewById(R.id.btnToggleService)

        editMainUrl.setText(Prefs.mainUrl)
        editLocalUrl.setText(Prefs.localUrl)
        editInterval.setText(Prefs.intervalSeconds.toString())
        switchOnline.isChecked = Prefs.onlineEnabled
        switchOffline.isChecked = Prefs.offlineEnabled

        switchOnline.setOnCheckedChangeListener { _, checked -> Prefs.onlineEnabled = checked }
        switchOffline.setOnCheckedChangeListener { _, checked -> Prefs.offlineEnabled = checked }

        view.findViewById<MaterialButton>(R.id.btnSaveSettings).setOnClickListener {
            saveSettings()
        }

        view.findViewById<MaterialButton>(R.id.btnGrantPermissions).setOnClickListener {
            requestPermissions()
        }

        view.findViewById<MaterialButton>(R.id.btnBatteryOptimization).setOnClickListener {
            requestBatteryOptimizationExemption()
        }

        btnToggleService.setOnClickListener {
            if (Prefs.serviceEnabled) {
                ForwarderService.stop(requireContext())
            } else {
                saveSettings()
                ForwarderService.start(requireContext())
            }
            updateServiceButton()
        }

        updateServiceButton()
        refreshCounts()
    }

    override fun onResume() {
        super.onResume()
        uiHandler.post(counterPoller)
        updateServiceButton()
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(counterPoller)
    }

    private fun saveSettings() {
        Prefs.mainUrl = editMainUrl.text?.toString()?.trim() ?: ""
        Prefs.localUrl = editLocalUrl.text?.toString()?.trim() ?: ""
        val interval = editInterval.text?.toString()?.trim()?.toIntOrNull() ?: 15
        Prefs.intervalSeconds = interval
        editInterval.setText(Prefs.intervalSeconds.toString())
        Toast.makeText(requireContext(), "تنظیمات ذخیره شد", Toast.LENGTH_SHORT).show()
    }

    private fun requestPermissions() {
        val perms = mutableListOf(
            android.Manifest.permission.RECEIVE_SMS,
            android.Manifest.permission.READ_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    private fun requestBatteryOptimizationExemption() {
        val pm = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
        val pkg = requireContext().packageName
        if (!pm.isIgnoringBatteryOptimizations(pkg)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$pkg")
            }
            try {
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "این گوشی از این تنظیم پشتیبانی نمی‌کند", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(requireContext(), "قبلاً از بهینه‌سازی باتری معاف شده است", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateServiceButton() {
        if (Prefs.serviceEnabled) {
            btnToggleService.text = "خاموش کردن سرویس ارسال"
            btnToggleService.setBackgroundColor(resources.getColor(R.color.queued_red, requireContext().theme))
            txtServiceStatus.text = "سرویس روشن است — بازه: ${Prefs.intervalSeconds} ثانیه"
        } else {
            btnToggleService.text = "روشن کردن سرویس ارسال"
            btnToggleService.setBackgroundColor(resources.getColor(R.color.online_green, requireContext().theme))
            txtServiceStatus.text = "سرویس خاموش است"
        }
    }

    private fun refreshCounts() {
        val (total, synced, queued) = DbHelper.get(requireContext()).counts()
        txtCountTotal.text = total.toString()
        txtCountSynced.text = synced.toString()
        txtCountQueued.text = queued.toString()
    }
}
