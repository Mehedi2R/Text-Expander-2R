package com.textexpander.app

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.documentfile.provider.DocumentFile
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.textexpander.app.data.AppDatabase
import com.textexpander.app.data.SettingsPreferences
import com.textexpander.app.data.ShortcutEntity
import com.textexpander.app.data.ShortcutSummary
import com.textexpander.app.databinding.ActivityMainBinding
import com.textexpander.app.service.TextExpanderAccessibilityService
import com.textexpander.app.service.TextExpanderForegroundService
import com.textexpander.app.ui.ClipboardAdapter
import com.textexpander.app.ui.FolderCategory
import com.textexpander.app.ui.FoldersAdapter
import com.textexpander.app.ui.MainViewModel
import com.textexpander.app.ui.ShortcutsAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by lazy {
        ViewModelProvider(this)[MainViewModel::class.java]
    }

    private lateinit var shortcutsAdapter: ShortcutsAdapter
    private lateinit var foldersAdapter: FoldersAdapter
    private lateinit var clipboardAdapter: ClipboardAdapter
    private lateinit var clipboardManager: ClipboardManager

    private var currentTab = 0 // 0: Text, 1: Clipboard, 2: Settings
    private var activeCategory: String = "/" // "/" is root
    private var allRawShortcuts: List<ShortcutSummary> = emptyList()
    private var clipboardCount = 0

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val settings = SettingsPreferences.get(this)
            if (settings.enableForegroundService) {
                TextExpanderForegroundService.startService(this)
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private val backupFolderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}
            val settings = SettingsPreferences.get(this)
            val displayPath = getDisplayPathFromUri(uri)
            settings.backupFolderUri = uri.toString()
            settings.backupFolderPath = displayPath
            updateBackupViews()
            Toast.makeText(this, "Backup folder set: $displayPath", Toast.LENGTH_SHORT).show()
        }
    }

    private val restoreJsonPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            restoreDataFromUri(uri)
        }
    }

    private fun getDisplayPathFromUri(uri: Uri): String {
        val path = uri.path ?: uri.toString()
        return if (path.contains(":")) {
            val segment = path.substringAfterLast(":")
            if (path.contains("primary", ignoreCase = true)) {
                "/storage/emulated/0/$segment"
            } else {
                segment
            }
        } else {
            path
        }
    }

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        captureClipboardText()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        setupRecyclerViews()
        setupBottomNav()
        setupFloatingDock()
        setupHeaderBack()
        setupSettingsTab()
        setupSearch()
        setupBackHandler()
        observeData()

        switchTab(0)
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentTab == 0 && activeCategory != "/") {
                    navigateToRoot()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private var lastCapturedText: String = ""

    override fun onResume() {
        super.onResume()
        try {
            clipboardManager.addPrimaryClipChangedListener(clipListener)
        } catch (_: Exception) {}

        updateAccessibilityStatus()

        val settings = SettingsPreferences.get(this)
        if (settings.enableForegroundService && OnboardingActivity.isAccessibilityAllowed(this)) {
            try {
                TextExpanderForegroundService.startService(this)
            } catch (_: Throwable) {}
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            clipboardManager.removePrimaryClipChangedListener(clipListener)
        } catch (_: Exception) {}
    }

    private fun captureClipboardText() {
        val settings = SettingsPreferences.get(this)
        if (!settings.enableClipboard) return

        try {
            if (clipboardManager.hasPrimaryClip()) {
                val clip = clipboardManager.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0)?.coerceToText(this)?.toString()
                    val clean = text?.trim()
                    if (!clean.isNullOrBlank() && clean != lastCapturedText) {
                        lastCapturedText = clean
                        viewModel.recordCopiedText(clean)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun setupRecyclerViews() {
        // Folders Category list (Text tab)
        foldersAdapter = FoldersAdapter(
            onFolderClick = { folder ->
                openCategory(folder.name)
            },
            onFolderLongClick = { folder ->
                showFolderOptions(folder)
            }
        )
        binding.rvFolders.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = foldersAdapter
        }

        // Shortcuts list (Text tab)
        shortcutsAdapter = ShortcutsAdapter(
            onItemClick = { item -> openAddEditPage(item.id) },
            onMenuClick = { item -> showShortcutOptions(item) },
            onItemLongClick = { item -> showShortcutOptions(item) },
            onCopyClick = { item ->
                val clip = ClipData.newPlainText("Copied", item.snippet)
                clipboardManager.setPrimaryClip(clip)
            }
        )
        binding.rvShortcuts.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = shortcutsAdapter
        }

        // Clipboard list (Clipboard tab)
        clipboardAdapter = ClipboardAdapter(
            onCopyClick = { item ->
                lastCapturedText = item.content
                val clip = ClipData.newPlainText("Copied", item.content)
                clipboardManager.setPrimaryClip(clip)
            },
            onConvertToShortcutClick = { item ->
                openAddEditPage(null, prefilledText = item.content)
            },
            onDeleteClick = { item ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("Clipboard Item")
                    .setItems(arrayOf("Copy Text", "Create Text Snippet", "Delete")) { _, which ->
                        when (which) {
                            0 -> {
                                val clip = ClipData.newPlainText("Copied", item.content)
                                clipboardManager.setPrimaryClip(clip)
                                viewModel.recordCopiedText(item.content)
                            }
                            1 -> openAddEditPage(null, prefilledText = item.content)
                            2 -> {
                                viewModel.deleteClipboardItem(item.id)
                            }
                        }
                    }
                    .show()
            }
        )
        binding.rvClipboard.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = clipboardAdapter
        }
    }

    private fun setupBottomNav() {
        binding.tabText.setOnClickListener { switchTab(0) }
        binding.tabClipboard.setOnClickListener { switchTab(1) }
        binding.tabSettings.setOnClickListener { switchTab(2) }
    }

    private fun switchTab(tabIndex: Int) {
        currentTab = tabIndex

        // When switching to Text or Clipboard tab, ensure any opened Settings option is closed
        if (tabIndex == 0 || tabIndex == 1) {
            closeAllSettingsAccordions()
        }

        // Content Visibility
        binding.viewTabText.visibility = if (tabIndex == 0) View.VISIBLE else View.GONE
        binding.viewTabClipboard.visibility = if (tabIndex == 1) View.VISIBLE else View.GONE
        binding.viewTabSettings.visibility = if (tabIndex == 2) View.VISIBLE else View.GONE

        // Floating Dock Visibility & Elements
        val density = resources.displayMetrics.density
        when (tabIndex) {
            0 -> {
                binding.floatingDock.visibility = View.VISIBLE
                binding.btnDockFolder.visibility = View.VISIBLE
                binding.btnDockAdd.visibility = View.VISIBLE
                binding.floatingDock.setPaddingRelative((16 * density).toInt(), 0, (8 * density).toInt(), 0)
                updateCategoryViews()
            }
            1 -> {
                binding.floatingDock.visibility = View.GONE
                binding.layoutSearchBar.visibility = View.GONE
                binding.etSearch.setText("")
                updateHeaderBanner()
            }
            else -> {
                binding.floatingDock.visibility = View.GONE
                binding.layoutSearchBar.visibility = View.GONE
                binding.etSearch.setText("")
                updateHeaderBanner()
            }
        }

        // Update Bottom Nav Indicators & Colors
        val selectedColor = ContextCompat.getColor(this, R.color.bottom_nav_selected)
        val unselectedColor = ContextCompat.getColor(this, R.color.bottom_nav_unselected)

        binding.indicatorText.setBackgroundResource(if (tabIndex == 0) R.drawable.bg_bottom_nav_indicator else 0)
        binding.iconText.setColorFilter(if (tabIndex == 0) selectedColor else unselectedColor)
        binding.labelText.setTextColor(if (tabIndex == 0) selectedColor else unselectedColor)

        binding.indicatorClipboard.setBackgroundResource(if (tabIndex == 1) R.drawable.bg_bottom_nav_indicator else 0)
        binding.iconClipboard.setColorFilter(if (tabIndex == 1) selectedColor else unselectedColor)
        binding.labelClipboard.setTextColor(if (tabIndex == 1) selectedColor else unselectedColor)

        binding.indicatorSettings.setBackgroundResource(if (tabIndex == 2) R.drawable.bg_bottom_nav_indicator else 0)
        binding.iconSettings.setColorFilter(if (tabIndex == 2) selectedColor else unselectedColor)
        binding.labelSettings.setTextColor(if (tabIndex == 2) selectedColor else unselectedColor)
    }

    private fun setupHeaderBack() {
        binding.btnHeaderBack.setOnClickListener {
            navigateToRoot()
        }
    }

    private fun openCategory(name: String) {
        activeCategory = name
        updateCategoryViews()
    }

    private fun navigateToRoot() {
        activeCategory = "/"
        updateCategoryViews()
    }

    private fun updateCategoryViews() {
        if (currentTab != 0) {
            updateHeaderBanner()
            return
        }

        val settings = SettingsPreferences.get(this)
        val customFolders = settings.getCustomFolders()

        val allFolderNames = linkedSetOf<String>()
        for (f in customFolders) {
            if (f.name.isNotBlank()) allFolderNames.add(f.name)
        }
        for (s in allRawShortcuts) {
            val f = s.folderName?.trim().orEmpty()
            if (f.isNotEmpty() && f != "/") {
                allFolderNames.add(f)
            }
        }

        val searchQuery = viewModel.searchQuery.value.trim()

        if (searchQuery.isNotEmpty()) {
            binding.cardHeaderBanner.visibility = View.VISIBLE
            binding.btnHeaderBack.visibility = View.GONE
            binding.imgHeaderIcon.visibility = View.VISIBLE
            binding.imgHeaderIcon.setImageResource(R.drawable.ic_tab_text)
            binding.tvHeaderTitle.text = "Search Results"
            binding.tvHeaderSubtitle.text = "${allRawShortcuts.size} results"
            binding.btnHeaderInfo.visibility = View.GONE

            binding.rvFolders.visibility = View.GONE
            shortcutsAdapter.submitList(ArrayList(allRawShortcuts))
            binding.layoutEmptyShortcuts.visibility = if (allRawShortcuts.isEmpty()) View.VISIBLE else View.GONE
            binding.rvShortcuts.visibility = if (allRawShortcuts.isEmpty()) View.GONE else View.VISIBLE
            return
        }

        if (activeCategory == "/") {
            // Root View matching media_1790671412173.jpg
            binding.cardHeaderBanner.visibility = View.VISIBLE
            binding.btnHeaderBack.visibility = View.GONE
            binding.imgHeaderIcon.visibility = View.VISIBLE
            binding.imgHeaderIcon.setImageResource(R.drawable.ic_root)
            binding.imgHeaderIcon.imageTintList = null
            binding.tvHeaderTitle.text = "/"
            binding.btnHeaderInfo.visibility = View.GONE

            val folderItems = allFolderNames.map { name ->
                val count = allRawShortcuts.count { it.folderName.equals(name, ignoreCase = true) }
                FolderCategory(name, count)
            }
            foldersAdapter.submitList(folderItems)
            binding.rvFolders.visibility = if (folderItems.isNotEmpty()) View.VISIBLE else View.GONE

            val rootShortcuts = allRawShortcuts.filter { it.folderName.isNullOrEmpty() || it.folderName == "/" }
            shortcutsAdapter.submitList(ArrayList(rootShortcuts))

            val folderCount = folderItems.size
            val snippetCount = rootShortcuts.size
            val folderStr = if (folderCount == 1) "1 folder" else "$folderCount folders"
            val snippetStr = if (snippetCount == 1) "1 text snippet" else "$snippetCount text snippets"
            binding.tvHeaderSubtitle.text = "$folderStr · $snippetStr"

            if (folderItems.isEmpty() && rootShortcuts.isEmpty()) {
                binding.layoutEmptyShortcuts.visibility = View.VISIBLE
                binding.rvShortcuts.visibility = View.GONE
            } else {
                binding.layoutEmptyShortcuts.visibility = View.GONE
                binding.rvShortcuts.visibility = View.VISIBLE
            }
        } else {
            // Inside Category View matching media_1790671412176.jpg
            binding.cardHeaderBanner.visibility = View.VISIBLE
            binding.btnHeaderBack.visibility = View.VISIBLE
            binding.imgHeaderIcon.visibility = View.GONE
            binding.tvHeaderTitle.text = activeCategory
            binding.btnHeaderInfo.visibility = View.GONE

            binding.rvFolders.visibility = View.GONE

            val categoryShortcuts = allRawShortcuts.filter { it.folderName.equals(activeCategory, ignoreCase = true) }
            shortcutsAdapter.submitList(ArrayList(categoryShortcuts))

            val count = categoryShortcuts.size
            binding.tvHeaderSubtitle.text = if (count == 1) "1 text snippet" else "$count text snippets"

            if (categoryShortcuts.isEmpty()) {
                binding.layoutEmptyShortcuts.visibility = View.VISIBLE
                binding.rvShortcuts.visibility = View.GONE
            } else {
                binding.layoutEmptyShortcuts.visibility = View.GONE
                binding.rvShortcuts.visibility = View.VISIBLE
            }
        }
    }

    private fun updateHeaderBanner() {
        val prefs = SettingsPreferences.get(this)
        val savedKeystrokes = prefs.savedKeystrokes
        val hardwareKeystrokes = prefs.hardwareKeystrokes
        val totalKeystrokes = savedKeystrokes + hardwareKeystrokes

        val formattedStats = when {
            totalKeystrokes >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM keystrokes", totalKeystrokes / 1_000_000.0)
            totalKeystrokes >= 1_000 -> String.format(java.util.Locale.US, "%.1fk keystrokes", totalKeystrokes / 1000.0)
            else -> "$totalKeystrokes keystrokes"
        }
        binding.tvHeaderStatsCount.text = formattedStats

        val subtitleText = when {
            hardwareKeystrokes > 0 -> "Reset saved ($savedKeystrokes) & hardware ($hardwareKeystrokes) keystrokes back to 0"
            else -> "Reset saved keystrokes count back to 0"
        }
        binding.tvStatsSavedSubtitle.text = subtitleText

        when (currentTab) {
            0 -> updateCategoryViews()
            1 -> {
                binding.cardHeaderBanner.visibility = View.VISIBLE
                binding.btnHeaderBack.visibility = View.GONE
                binding.imgHeaderIcon.visibility = View.VISIBLE
                binding.imgHeaderIcon.setImageResource(R.drawable.ic_tab_clipboard)
                binding.tvHeaderTitle.text = "Clipboard"
                binding.tvHeaderSubtitle.text = "$clipboardCount copied text" + (if (clipboardCount == 1) "" else "s")
                binding.btnHeaderInfo.visibility = View.VISIBLE
            }
            2 -> {
                binding.cardHeaderBanner.visibility = View.GONE
            }
        }
    }

    private fun setupFloatingDock() {
        binding.pillHeaderStats.setOnClickListener {
            val count = SettingsPreferences.get(this).savedKeystrokes
            MaterialAlertDialogBuilder(this)
                .setTitle("⚡ Keystrokes Counter")
                .setMessage("Total $count keystrokes saved by Text Expander 2R shortcuts.\n\nWould you like to reset this counter back to 0?")
                .setPositiveButton("Reset to 0") { _, _ ->
                    SettingsPreferences.get(this).resetSavedKeystrokes()
                    updateHeaderBanner()
                    Snackbar.make(binding.root, "Keystrokes counter reset to 0.", Snackbar.LENGTH_SHORT).show()
                }
                .setNegativeButton("Close", null)
                .show()
        }

        binding.btnDockAdd.setOnClickListener {
            openAddEditPage(null)
        }

        binding.btnDockSearch.setOnClickListener {
            val isVisible = binding.layoutSearchBar.visibility == View.VISIBLE
            binding.layoutSearchBar.visibility = if (isVisible) View.GONE else View.VISIBLE
            if (!isVisible) {
                binding.etSearch.requestFocus()
            } else {
                binding.etSearch.setText("")
            }
        }

        binding.btnDockFolder.setOnClickListener {
            showCreateFolderDialog {
                updateCategoryViews()
            }
        }

        binding.cardHeaderBanner.setOnClickListener {
            if (currentTab == 0 && activeCategory != "/") {
                navigateToRoot()
            }
        }

        binding.btnHeaderInfo.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Clipboard History")
                .setMessage("Every text you copy on your phone is safely saved in local storage. Tap any item to re-copy or convert it into a text expander shortcut.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun showFolderOptions(folder: FolderCategory) {
        val options = arrayOf("Open Category", "Delete Category")
        MaterialAlertDialogBuilder(this)
            .setTitle(folder.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openCategory(folder.name)
                    1 -> {
                        MaterialAlertDialogBuilder(this)
                            .setTitle("Delete Category")
                            .setMessage("Delete '${folder.name}' and all text snippets inside it? This cannot be undone.")
                            .setPositiveButton("Delete") { _, _ ->
                                SettingsPreferences.get(this).deleteCustomFolder(folder.name)
                                lifecycleScope.launch(Dispatchers.IO) {
                                    val db = AppDatabase.getInstance(this@MainActivity)
                                    val all = db.shortcutDao().allShortcutsSync
                                    var deletedCount = 0
                                    for (s in all) {
                                        if (s.folderName.equals(folder.name, ignoreCase = true)) {
                                            db.shortcutDao().deleteById(s.id)
                                            deletedCount++
                                        }
                                    }
                                    TextExpanderAccessibilityService.reloadShortcuts()
                                    withContext(Dispatchers.Main) {
                                        if (activeCategory.equals(folder.name, ignoreCase = true)) {
                                            activeCategory = "/"
                                        }
                                        updateCategoryViews()
                                    }
                                }
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
            }
            .show()
    }

    private fun showCreateFolderDialog(onFolderCreated: ((String) -> Unit)? = null) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_folder, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val etFolderName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etFolderName)
        val btnCancel = dialogView.findViewById<android.widget.Button>(R.id.btnCancelFolder)
        val btnCreate = dialogView.findViewById<android.widget.Button>(R.id.btnCreateFolder)

        btnCancel?.setOnClickListener {
            dialog.dismiss()
        }

        btnCreate?.setOnClickListener {
            val name = etFolderName?.text?.toString()?.trim() ?: ""
            if (name.isNotEmpty()) {
                val settings = SettingsPreferences.get(this)
                val folderItem = SettingsPreferences.CustomFolderItem(
                    name = name,
                    path = "",
                    uriString = "",
                    createdAt = System.currentTimeMillis()
                )
                settings.saveCustomFolder(folderItem)

                openCategory(name)
                onFolderCreated?.invoke(name)
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Please enter a folder name", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun setupSearch() {
        binding.etSearch.doAfterTextChanged { text ->
            viewModel.setSearchQuery(text?.toString() ?: "")
        }
    }

    private fun updateBackupViews() {
        val settings = SettingsPreferences.get(this)
        binding.tvBackupFolderPath.text = "(${settings.backupFolderPath})"

        val lastBackupStr = if (settings.lastBackupTimestamp > 0) {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date(settings.lastBackupTimestamp))
        } else {
            "-"
        }
        binding.tvBackupInfoDetails.text = "Last backup: $lastBackupStr"
    }

    private fun setupSettingsTab() {
        val settings = SettingsPreferences.get(this)

        binding.btnSettingsToggleService.setOnClickListener {
            openAccessibilitySettings()
        }

        binding.btnSettingsFixRestricted.setOnClickListener {
            showRestrictedSettingsGuide()
        }

        binding.btnSettingsAllowBackground.setOnClickListener {
            openBackgroundSettings()
        }

        // Setup exclusive single-open accordions for all 7 cards (only one card can be open at a time)
        accordionControllers.clear()
        registerAccordion(binding.headerDetection, binding.bodyDetection, binding.arrowDetection)
        registerAccordion(binding.headerSuggestion, binding.bodySuggestion, binding.arrowSuggestion)
        registerAccordion(binding.headerClipboard, binding.bodyClipboard, binding.arrowClipboard)
        registerAccordion(binding.headerData, binding.bodyData, binding.arrowData)
        registerAccordion(binding.headerStatistics, binding.bodyStatistics, binding.arrowStatistics)
        registerAccordion(binding.headerInteraction, binding.bodyInteraction, binding.arrowInteraction)
        registerAccordion(binding.headerAbout, binding.bodyAbout, binding.arrowAbout)

        // 1. Detection Switches
        binding.switchEnableDetection.isChecked = settings.enableDetection
        binding.switchEnableDetection.setOnCheckedChangeListener { _, isChecked ->
            settings.enableDetection = isChecked
        }

        binding.switchOptimizedDetection.isChecked = settings.useOptimizedDetection
        binding.switchOptimizedDetection.setOnCheckedChangeListener { _, isChecked ->
            settings.useOptimizedDetection = isChecked
        }

        binding.switchDetectInAllApps.isChecked = settings.detectInAllApps
        binding.switchDetectInAllApps.setOnCheckedChangeListener { _, isChecked ->
            settings.detectInAllApps = isChecked
        }

        binding.switchDetectAllLanguages.isChecked = settings.detectInAllLanguages
        binding.switchDetectAllLanguages.setOnCheckedChangeListener { _, isChecked ->
            settings.detectInAllLanguages = isChecked
        }

        binding.switchMatchFromBeginning.isChecked = settings.matchFromBeginning
        binding.switchMatchFromBeginning.setOnCheckedChangeListener { _, isChecked ->
            settings.matchFromBeginning = isChecked
        }

        binding.switchKeepAliveService.isChecked = settings.enableForegroundService
        binding.switchKeepAliveService.setOnCheckedChangeListener { _, isChecked ->
            settings.enableForegroundService = isChecked
            if (isChecked) {
                requestNotificationPermissionIfNeeded()
                TextExpanderForegroundService.startService(this)
            } else {
                TextExpanderForegroundService.stopService(this)
            }
        }

        // 2. Suggestion Switch
        binding.switchEnableSuggestionOverlay.isChecked = settings.enableSuggestionOverlay
        binding.switchEnableSuggestionOverlay.setOnCheckedChangeListener { _, isChecked ->
            settings.enableSuggestionOverlay = isChecked
        }

        // 3. Clipboard Switch
        binding.switchEnableClipboard.isChecked = settings.enableClipboard
        binding.switchEnableClipboard.setOnCheckedChangeListener { _, isChecked ->
            settings.enableClipboard = isChecked
        }

        // 4. Data Action Rows (matching media_1790671840106.jpg)
        updateBackupViews()

        binding.rowBackupFolder.setOnClickListener {
            backupFolderPickerLauncher.launch(null)
        }

        binding.rowCreateBackup.setOnClickListener {
            createBackupJson()
        }

        binding.rowRestoreBackup.setOnClickListener {
            restoreJsonPickerLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
        }

        // 5. Statistics (Enable Statistics controls Share Statistics; View Statistics removed)
        binding.switchEnableStatistics.isChecked = settings.enableStatistics
        binding.switchShareStatistics.isChecked = settings.shareStatistics && settings.enableStatistics
        binding.switchShareStatistics.isEnabled = settings.enableStatistics

        binding.switchEnableStatistics.setOnCheckedChangeListener { _, isChecked ->
            settings.enableStatistics = isChecked
            if (!isChecked) {
                binding.switchShareStatistics.isChecked = false
                settings.shareStatistics = false
                binding.switchShareStatistics.isEnabled = false
            } else {
                binding.switchShareStatistics.isEnabled = true
            }
        }

        binding.switchShareStatistics.setOnCheckedChangeListener { _, isChecked ->
            if (binding.switchEnableStatistics.isChecked) {
                settings.shareStatistics = isChecked
            } else {
                binding.switchShareStatistics.isChecked = false
                settings.shareStatistics = false
            }
        }

        binding.btnResetStatistics.setOnClickListener {
            val saved = settings.savedKeystrokes
            val hw = settings.hardwareKeystrokes
            val total = saved + hw
            val msg = if (hw > 0) {
                "Are you sure you want to reset the keystrokes counter back to 0? (Saved: $saved, Hardware: $hw)"
            } else {
                "Are you sure you want to reset the saved keystrokes count (currently $saved) back to 0?"
            }
            MaterialAlertDialogBuilder(this)
                .setTitle("Reset Keystrokes Counter")
                .setMessage(msg)
                .setPositiveButton("Reset") { _, _ ->
                    settings.resetSavedKeystrokes()
                    settings.resetHardwareKeystrokes()
                    updateHeaderBanner()
                    Snackbar.make(binding.root, "Keystrokes counter has been reset to 0.", Snackbar.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // 6. Interaction Switches (matching media_1790661230654.jpg)
        binding.switchShowClipboardAccess.isChecked = settings.showClipboardAccess
        binding.switchShowClipboardAccess.setOnCheckedChangeListener { _, isChecked ->
            settings.showClipboardAccess = isChecked
        }

        binding.switchEnableHapticFeedback.isChecked = settings.enableHapticFeedback
        binding.switchEnableHapticFeedback.setOnCheckedChangeListener { _, isChecked ->
            settings.enableHapticFeedback = isChecked
        }

        // 7. About card details (Prototype 2 Bento Accordion)
        binding.tvAboutVersion.text = "v${BuildConfig.VERSION_NAME} • Stable"

        binding.btnAboutDeveloper.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/Mehedi2R")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (_: Exception) {}
        }

        binding.btnAboutPrivacyPolicy.setOnClickListener {
            showPrivacyPolicyDialog()
        }

        binding.btnAboutRateApp.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (_: Exception) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (_: Exception) {}
            }
        }
    }

    private fun showPrivacyPolicyDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Privacy Policy")
            .setMessage("Text Expander 2R is built with a 100% offline, privacy-first architecture.\n\n• Zero Internet Access: No internet permission is declared in this application. Your data never leaves your device.\n• Local Storage: All text snippets and clipboard items are stored exclusively in your device's local database.\n• Accessibility API: Used solely to detect typed shortcut keywords and perform in-place expansion, and monitor on-device clipboard if enabled.\n• Zero Analytics & Tracking: No personal information, telemetry, or analytics are ever collected, recorded, or transmitted.")
            .setPositiveButton("Close", null)
            .show()
    }

    private val accordionControllers = mutableListOf<() -> Unit>()

    private fun closeAllSettingsAccordions() {
        for (close in accordionControllers) {
            close.invoke()
        }
    }

    private fun registerAccordion(header: View, body: View, arrow: ImageView, isInitiallyExpanded: Boolean = false) {
        var isExpanded = isInitiallyExpanded

        fun applyState(expanded: Boolean) {
            isExpanded = expanded
            body.visibility = if (expanded) View.VISIBLE else View.GONE
            header.setBackgroundResource(
                if (expanded) R.drawable.bg_settings_card_header_expanded
                else R.drawable.bg_settings_card_collapsed
            )
            // Open and Close arrow:
            // When closed: points down (ic_chevron_down) indicating tap to open
            // When open: points up (ic_chevron_up) indicating tap to close, with cyan accent
            arrow.setImageResource(if (expanded) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down)
            arrow.imageTintList = android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(
                    this,
                    if (expanded) R.color.primary else R.color.text_primary
                )
            )
        }

        val closeAction = {
            if (isExpanded) {
                applyState(false)
            }
        }
        accordionControllers.add(closeAction)

        applyState(isInitiallyExpanded)

        header.setOnClickListener {
            val targetState = !isExpanded
            // Exclusive single open: When opening this card, close any currently opened card
            if (targetState) {
                for (closeOther in accordionControllers) {
                    closeOther.invoke()
                }
            }
            applyState(targetState)
        }
    }

    private fun createBackupJson() {
        val settings = SettingsPreferences.get(this)

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(this@MainActivity)
            val all = db.shortcutDao().allShortcutsSync
            val customFolders = settings.getCustomFolders()

            val rootObj = org.json.JSONObject()
            rootObj.put("app", "TextExpander2R")
            rootObj.put("version", 3)
            rootObj.put("timestamp", System.currentTimeMillis())

            val foldersArray = org.json.JSONArray()
            for (f in customFolders) {
                val fObj = org.json.JSONObject()
                fObj.put("name", f.name)
                fObj.put("path", f.path)
                fObj.put("uriString", f.uriString)
                foldersArray.put(fObj)
            }
            rootObj.put("folders", foldersArray)

            val shortcutsArray = org.json.JSONArray()
            for (s in all) {
                val sObj = org.json.JSONObject()
                sObj.put("shortcutKey", s.shortcutKey)
                sObj.put("expandedText", s.expandedText)
                sObj.put("folderName", s.folderName)
                sObj.put("updatedAt", s.updatedAt)
                shortcutsArray.put(sObj)
            }
            rootObj.put("shortcuts", shortcutsArray)

            val jsonContent = rootObj.toString(2)
            val timestampStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "TextExpander_Backup_$timestampStr.json"

            var savedPath: String? = null
            var writeError: String? = null

            // 1. Primary Method: SAF DocumentFile write to user's selected folder (if selected)
            if (settings.backupFolderUri.isNotBlank()) {
                try {
                    val treeUri = Uri.parse(settings.backupFolderUri)
                    val targetDir = DocumentFile.fromTreeUri(this@MainActivity, treeUri)
                    if (targetDir != null && targetDir.canWrite()) {
                        val backupDoc = targetDir.createFile("application/json", fileName)
                            ?: targetDir.createFile("text/plain", fileName)
                            ?: targetDir.createFile("application/octet-stream", fileName)

                        if (backupDoc != null) {
                            contentResolver.openOutputStream(backupDoc.uri, "wt")?.use { os ->
                                os.write(jsonContent.toByteArray(Charsets.UTF_8))
                                os.flush()
                            }
                            savedPath = "${settings.backupFolderPath}/$fileName"
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "SAF DocumentFile backup write failed", e)
                    writeError = e.message
                }
            }

            // 2. Secondary Fallback: MediaStore for Android 10+ (API 29+) -> Documents/TextExpander
            if (savedPath == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/TextExpander")
                    }
                    val targetUri = contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
                        ?: contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)

                    if (targetUri != null) {
                        contentResolver.openOutputStream(targetUri, "wt")?.use { os ->
                            os.write(jsonContent.toByteArray(Charsets.UTF_8))
                            os.flush()
                        }
                        savedPath = "/Documents/TextExpander/$fileName"
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "MediaStore backup write failed", e)
                    writeError = e.message
                }
            }

            // 3. Fallback: Direct File API
            if (savedPath == null) {
                try {
                    val targetDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "TextExpander")
                    if (!targetDir.exists()) targetDir.mkdirs()
                    val targetFile = File(targetDir, fileName)
                    targetFile.writeText(jsonContent)
                    savedPath = targetFile.absolutePath
                } catch (e: Exception) {
                    Log.e("MainActivity", "Direct File backup write failed", e)
                    writeError = e.message
                }
            }

            withContext(Dispatchers.Main) {
                if (savedPath != null) {
                    settings.lastBackupTimestamp = System.currentTimeMillis()
                    updateBackupViews()

                    MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle("Backup Successful")
                        .setMessage("Backup saved successfully with ${all.size} snippets and ${customFolders.size} folders.\n\nFile:\n$savedPath")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle("Backup Failed")
                        .setMessage("Failed to save backup file to default folder (/Documents/TextExpander).\n\nError: ${writeError ?: "Permission or storage error"}\n\nPlease try selecting a backup folder.")
                        .setPositiveButton("Select Folder") { _, _ ->
                            backupFolderPickerLauncher.launch(null)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        }
    }

    private fun restoreDataFromUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val jsonString = contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader().use { it.readText() }
                }
                if (jsonString.isNullOrBlank()) {
                    return@launch
                }

                val settings = SettingsPreferences.get(this@MainActivity)
                val shortcutsToRestore = mutableListOf<ShortcutEntity>()

                if (jsonString.trim().startsWith("[")) {
                    // Legacy array format
                    val array = org.json.JSONArray(jsonString)
                    for (i in 0 until array.length()) {
                        val sObj = array.getJSONObject(i)
                        val key = sObj.optString("shortcutKey", sObj.optString("key", ""))
                        val text = sObj.optString("expandedText", sObj.optString("text", ""))
                        val folderName = sObj.optString("folderName", "")
                        val updatedAt = sObj.optLong("updatedAt", System.currentTimeMillis())
                        if (key.isNotBlank()) {
                            val byteSize = text.toByteArray(Charsets.UTF_8).size.toLong()
                            shortcutsToRestore.add(ShortcutEntity(0, key, text, byteSize, updatedAt, folderName))
                        }
                    }
                } else {
                    val rootObj = org.json.JSONObject(jsonString)
                    if (rootObj.has("folders")) {
                        val foldersArray = rootObj.getJSONArray("folders")
                        for (i in 0 until foldersArray.length()) {
                            val fObj = foldersArray.getJSONObject(i)
                            val folderItem = SettingsPreferences.CustomFolderItem(
                                name = fObj.getString("name"),
                                path = fObj.optString("path", ""),
                                uriString = fObj.optString("uriString", ""),
                                createdAt = fObj.optLong("createdAt", System.currentTimeMillis())
                            )
                            settings.saveCustomFolder(folderItem)
                        }
                    }

                    if (rootObj.has("shortcuts")) {
                        val shortcutsArray = rootObj.getJSONArray("shortcuts")
                        for (i in 0 until shortcutsArray.length()) {
                            val sObj = shortcutsArray.getJSONObject(i)
                            val key = sObj.optString("shortcutKey", sObj.optString("key", ""))
                            val text = sObj.optString("expandedText", sObj.optString("text", ""))
                            val folderName = sObj.optString("folderName", "")
                            val updatedAt = sObj.optLong("updatedAt", System.currentTimeMillis())
                            if (key.isNotBlank()) {
                                val byteSize = text.toByteArray(Charsets.UTF_8).size.toLong()
                                shortcutsToRestore.add(ShortcutEntity(0, key, text, byteSize, updatedAt, folderName))
                                if (folderName.isNotBlank() && folderName != "/") {
                                    settings.saveCustomFolder(
                                        SettingsPreferences.CustomFolderItem(
                                            name = folderName,
                                            path = "",
                                            uriString = "",
                                            createdAt = System.currentTimeMillis()
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    viewModel.restoreShortcutsBatch(shortcutsToRestore) { _ ->
                        TextExpanderAccessibilityService.reloadShortcuts()
                        updateCategoryViews()
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to restore backup", e)
            }
        }
    }

    private fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.shortcuts.collect { list ->
                        allRawShortcuts = list
                        if (currentTab == 0) {
                            updateCategoryViews()
                        }
                    }
                }

                launch {
                    viewModel.clipboardHistory.collect { list ->
                        clipboardAdapter.submitList(ArrayList(list))
                        clipboardCount = list.size

                        if (currentTab == 1) updateHeaderBanner()
                    }
                }
            }
        }
    }

    private fun updateAccessibilityStatus() {
        val isAccAllowed = OnboardingActivity.isAccessibilityAllowed(this)
        val isBgAllowed = OnboardingActivity.isBackgroundAllowed(this)

        binding.cardSettingsAccessibility.visibility = if (!isAccAllowed) View.VISIBLE else View.GONE
        binding.cardSettingsBackground.visibility = if (!isBgAllowed) View.VISIBLE else View.GONE
    }

    private data class DialogActionItem(
        val title: String,
        val iconRes: Int,
        val isDelete: Boolean = false
    )

    private fun showShortcutOptions(item: ShortcutSummary) {
        val isInRoot = activeCategory == "/" || item.folderName.isNullOrBlank() || item.folderName == "/"
        val moveTitle = if (isInRoot) "Move to Folder" else "Move to Folder / Root"

        val actionItems = listOf(
            DialogActionItem("Edit", R.drawable.ic_action_edit),
            DialogActionItem(moveTitle, R.drawable.ic_folder),
            DialogActionItem("Copy Text", R.drawable.ic_action_copy),
            DialogActionItem("Delete", R.drawable.ic_action_delete, isDelete = true)
        )

        val adapter = object : android.widget.ArrayAdapter<DialogActionItem>(
            this,
            R.layout.item_dialog_action,
            actionItems
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context)
                    .inflate(R.layout.item_dialog_action, parent, false)
                val action = getItem(position)!!
                val iconView = view.findViewById<ImageView>(R.id.imgActionIcon)
                val titleView = view.findViewById<TextView>(R.id.tvActionTitle)

                iconView.setImageResource(action.iconRes)
                if (action.iconRes == R.drawable.ic_folder || action.iconRes == R.drawable.ic_root) {
                    iconView.imageTintList = null
                } else if (action.isDelete) {
                    iconView.imageTintList = android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.error)
                    )
                } else {
                    iconView.imageTintList = android.content.res.ColorStateList.valueOf(
                        android.graphics.Color.parseColor("#111827")
                    )
                }

                titleView.text = action.title
                if (action.isDelete) {
                    titleView.setTextColor(ContextCompat.getColor(context, R.color.error))
                } else {
                    titleView.setTextColor(android.graphics.Color.parseColor("#111827"))
                }
                return view
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(item.shortcutKey)
            .setAdapter(adapter) { _, which ->
                when (which) {
                    0 -> openAddEditPage(item.id)
                    1 -> showMoveShortcutDialog(item)
                    2 -> copyExpansionToClipboard(item)
                    3 -> confirmDeleteShortcut(item)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showMoveShortcutDialog(item: ShortcutSummary) {
        val settings = SettingsPreferences.get(this)
        val customFolders = settings.getCustomFolders()
        val allFolders = linkedSetOf<String>()
        for (f in customFolders) {
            if (f.name.isNotBlank() && f.name != "/") allFolders.add(f.name)
        }
        for (s in allRawShortcuts) {
            val f = s.folderName?.trim().orEmpty()
            if (f.isNotEmpty() && f != "/") allFolders.add(f)
        }

        val currentFolder = item.folderName?.trim().orEmpty()
        val isCurrentlyInRoot = currentFolder.isEmpty() || currentFolder == "/"

        val moveItems = mutableListOf<DialogActionItem>()
        val actions = mutableListOf<() -> Unit>()

        // 1. Root option
        val rootLabel = if (isCurrentlyInRoot) "Root (/)  ✓ (Current)" else "Root (/)"
        moveItems.add(DialogActionItem(rootLabel, R.drawable.ic_root))
        actions.add {
            if (!isCurrentlyInRoot) {
                moveShortcutToCategory(item.id, "")
            }
        }

        // 2. Folder options
        for (folder in allFolders) {
            val isCurrent = !isCurrentlyInRoot && currentFolder.equals(folder, ignoreCase = true)
            val label = if (isCurrent) "Folder: $folder  ✓ (Current)" else "Folder: $folder"
            moveItems.add(DialogActionItem(label, R.drawable.ic_folder))
            actions.add {
                if (!isCurrent) {
                    moveShortcutToCategory(item.id, folder)
                }
            }
        }

        // 3. Create new folder option
        moveItems.add(DialogActionItem("Create New Folder...", R.drawable.ic_folder))
        actions.add {
            showCreateFolderDialog { newFolderName ->
                moveShortcutToCategory(item.id, newFolderName)
            }
        }

        val adapter = object : android.widget.ArrayAdapter<DialogActionItem>(
            this,
            R.layout.item_dialog_action,
            moveItems
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context)
                    .inflate(R.layout.item_dialog_action, parent, false)
                val action = getItem(position)!!
                val iconView = view.findViewById<ImageView>(R.id.imgActionIcon)
                val titleView = view.findViewById<TextView>(R.id.tvActionTitle)

                iconView.setImageResource(action.iconRes)
                if (action.iconRes == R.drawable.ic_folder || action.iconRes == R.drawable.ic_root) {
                    iconView.imageTintList = null
                } else {
                    iconView.imageTintList = android.content.res.ColorStateList.valueOf(
                        android.graphics.Color.parseColor("#111827")
                    )
                }
                titleView.text = action.title
                titleView.setTextColor(android.graphics.Color.parseColor("#111827"))
                return view
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Move '${item.shortcutKey}' to...")
            .setAdapter(adapter) { _, which ->
                if (which in actions.indices) {
                    actions[which].invoke()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun moveShortcutToCategory(shortcutId: Long, targetCategory: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(this@MainActivity)
            val entity = db.shortcutDao().getShortcutById(shortcutId)
            if (entity != null) {
                val cleanTarget = if (targetCategory == "/") "" else targetCategory.trim()
                entity.folderName = cleanTarget
                entity.updatedAt = System.currentTimeMillis()
                db.shortcutDao().updateShortcut(entity)
                TextExpanderAccessibilityService.reloadShortcuts()

                if (cleanTarget.isNotEmpty()) {
                    SettingsPreferences.get(this@MainActivity).saveCustomFolder(
                        SettingsPreferences.CustomFolderItem(
                            name = cleanTarget,
                            path = "",
                            uriString = "",
                            createdAt = System.currentTimeMillis()
                        )
                    )
                }

                withContext(Dispatchers.Main) {
                    updateCategoryViews()
                }
            }
        }
    }

    private fun openAddEditPage(shortcutId: Long? = null, prefilledText: String? = null) {
        val intent = Intent(this, AddEditShortcutActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            if (shortcutId != null) {
                putExtra(AddEditShortcutActivity.EXTRA_SHORTCUT_ID, shortcutId)
            }
            if (prefilledText != null) {
                putExtra(AddEditShortcutActivity.EXTRA_PREFILLED_TEXT, prefilledText)
            }
            putExtra(AddEditShortcutActivity.EXTRA_FOLDER_NAME, if (activeCategory != "/") activeCategory else "")
        }
        startActivity(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    private fun copyExpansionToClipboard(item: ShortcutSummary) {
        lifecycleScope.launch {
            val fullEntity = viewModel.getFullShortcut(item.id)
            if (fullEntity != null) {
                val clip = ClipData.newPlainText("TextExpander", fullEntity.expandedText)
                clipboardManager.setPrimaryClip(clip)
            }
        }
    }

    private fun confirmDeleteShortcut(item: ShortcutSummary) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete))
            .setMessage(getString(R.string.delete_shortcut_confirm, item.shortcutKey))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                viewModel.deleteShortcut(item.id)
                TextExpanderAccessibilityService.reloadShortcuts()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (_: Exception) {}
    }

    private fun showRestrictedSettingsGuide() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Fix 'Restricted setting'")
            .setMessage("Android blocks accessibility for apps installed outside Google Play.\n\n" +
                    "To enable:\n" +
                    "1. Tap 'Open App Info' below.\n" +
                    "2. Tap the 3 dots (⋮) in the top-right corner.\n" +
                    "3. Select 'Allow restricted settings' and confirm with your phone lock/fingerprint.\n" +
                    "4. Return here and tap 'Enable in Settings'.")
            .setPositiveButton("Open App Info") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (_: Exception) {}
            }
            .setNegativeButton("Close", null)
            .show()
    }

    @SuppressLint("BatteryLife")
    private fun openBackgroundSettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (_: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (_: Exception) {}
            }
        }
    }
}
