package com.textexpander.app

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.lifecycle.ViewModelProvider
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.textexpander.app.data.SettingsPreferences
import com.textexpander.app.data.ShortcutEntity
import com.textexpander.app.databinding.ActivityAddEditShortcutBinding
import com.textexpander.app.service.TextExpanderAccessibilityService
import com.textexpander.app.ui.MainViewModel
import kotlinx.coroutines.launch

class AddEditShortcutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddEditShortcutBinding
    private val viewModel: MainViewModel by lazy {
        ViewModelProvider(this)[MainViewModel::class.java]
    }

    private var shortcutId: Long = -1L
    private var prefilledText: String? = null
    private var folderName: String = ""
    private var currentEntity: ShortcutEntity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditShortcutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        shortcutId = intent.getLongExtra(EXTRA_SHORTCUT_ID, -1L)
        prefilledText = intent.getStringExtra(EXTRA_PREFILLED_TEXT)
        folderName = intent.getStringExtra(EXTRA_FOLDER_NAME).orEmpty()

        setupViews()
        setupListeners()
        loadData()
    }

    private fun setupViews() {
        if (shortcutId != -1L) {
            binding.tvHeaderTitle.text = getString(R.string.edit_shortcut)
        } else {
            binding.tvHeaderTitle.text = getString(R.string.add_shortcut)
        }
        val displayFolder = when {
            folderName.isNotBlank() && folderName != "/" -> folderName
            else -> "Root"
        }
        binding.tvFolderBadge.text = "📁 $displayFolder"
        setupVariableChips()
        updateCounter()
    }

    private fun setupVariableChips() {
        val container = binding.layoutVariableChipsContainer
        container.removeAllViews()
        val density = resources.displayMetrics.density

        for (v in com.textexpander.app.util.VariableHelper.SUPPORTED_VARIABLES) {
            val chip = android.widget.TextView(this).apply {
                text = v
                textSize = 11.5f
                setTextColor(ContextCompat.getColor(this@AddEditShortcutActivity, R.color.primary))
                setBackgroundResource(R.drawable.bg_bento_chip_clickable)
                val hPad = (10 * density).toInt()
                val vPad = (4 * density).toInt()
                setPadding(hPad, vPad, hPad, vPad)
                val params = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, (6 * density).toInt(), 0)
                }
                layoutParams = params
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    insertVariableAtCursor(v)
                }
            }
            container.addView(chip)
        }
    }

    private fun insertVariableAtCursor(variable: String) {
        val et = binding.etExpandedText
        val start = et.selectionStart.coerceAtLeast(0)
        val end = et.selectionEnd.coerceAtLeast(0)
        et.text?.replace(Math.min(start, end), Math.max(start, end), variable)
        et.setSelection(Math.min(start, end) + variable.length)
        et.requestFocus()
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun setupListeners() {
        binding.btnHeaderBack.setOnClickListener {
            finish()
        }

        binding.btnCancel.setOnClickListener {
            finish()
        }

        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        binding.btnPasteClipboard.setOnClickListener {
            val clip = clipboardManager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val pasteText = clip.getItemAt(0).coerceToText(this).toString()
                binding.etExpandedText.setText(pasteText)
                binding.etExpandedText.setSelection(pasteText.length)
            }
        }

        binding.etExpandedText.movementMethod = android.text.method.ScrollingMovementMethod.getInstance()
        binding.etExpandedText.setOnTouchListener { _, event ->
            val canScroll = binding.etExpandedText.canScrollVertically(1) || binding.etExpandedText.canScrollVertically(-1)
            if (canScroll) {
                binding.scrollViewContent.requestDisallowInterceptTouchEvent(true)
                if (event.action == android.view.MotionEvent.ACTION_UP || event.action == android.view.MotionEvent.ACTION_CANCEL) {
                    binding.scrollViewContent.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }

        binding.etExpandedText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateCounter()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.etExpandedText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.scrollViewContent.postDelayed({
                    if (!isFinishing && !isDestroyed) {
                        binding.scrollViewContent.smoothScrollTo(0, binding.layoutActionButtons.bottom)
                    }
                }, 200)
            }
        }

        binding.root.viewTreeObserver.addOnGlobalLayoutListener {
            val r = android.graphics.Rect()
            binding.root.getWindowVisibleDisplayFrame(r)
            val screenHeight = binding.root.rootView.height
            val keypadHeight = screenHeight - r.bottom
            if (keypadHeight > screenHeight * 0.15) {
                if (binding.etExpandedText.hasFocus()) {
                    binding.scrollViewContent.postDelayed({
                        if (!isFinishing && !isDestroyed) {
                            binding.scrollViewContent.smoothScrollTo(0, binding.layoutActionButtons.bottom)
                        }
                    }, 100)
                }
            }
        }

        binding.btnSave.setOnClickListener { saveShortcut() }
    }

    private fun loadData() {
        if (shortcutId != -1L) {
            lifecycleScope.launch {
                val entity = viewModel.getFullShortcut(shortcutId)
                if (entity != null) {
                    currentEntity = entity
                    binding.etShortcutKey.setText(entity.shortcutKey)
                    binding.etExpandedText.setText(entity.expandedText)
                    binding.etExpandedText.setSelection(entity.expandedText.length)
                    if (!entity.folderName.isNullOrBlank() && entity.folderName != "/") {
                        binding.tvFolderBadge.text = "📁 ${entity.folderName}"
                    }
                    updateCounter()
                }
            }
        } else {
            if (!prefilledText.isNullOrEmpty()) {
                binding.etExpandedText.setText(prefilledText)
                binding.etExpandedText.setSelection(prefilledText?.length ?: 0)
                updateCounter()
            }
        }
    }

    private fun updateCounter() {
        val text = binding.etExpandedText.text?.toString() ?: ""
        val charCount = text.length
        val byteCount = text.toByteArray(Charsets.UTF_8).size
        val formatted = formatByteSize(byteCount.toLong())

        binding.tvSizeCounter.text = if (charCount == 1) "1 character • Limitless" else "$charCount characters • Limitless"
        binding.tvSizeCounter.setTextColor(ContextCompat.getColor(this, R.color.text_tertiary))
        binding.tilExpandedText.error = null
    }

    private fun formatByteSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
            else -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }

    private fun saveShortcut() {
        val key = binding.etShortcutKey.text?.toString() ?: ""
        val expandedText = binding.etExpandedText.text?.toString() ?: ""
        val category = currentEntity?.folderName ?: folderName

        binding.tilShortcutKey.error = null
        binding.tilExpandedText.error = null

        viewModel.saveShortcut(
            id = currentEntity?.id ?: 0,
            key = key,
            expandedText = expandedText,
            folderName = category
        ) { success, errorMsg ->
            if (success) {
                if (category.isNotBlank() && category != "/") {
                    SettingsPreferences.get(this).saveCustomFolder(
                        SettingsPreferences.CustomFolderItem(
                            name = category,
                            path = "",
                            uriString = "",
                            createdAt = System.currentTimeMillis()
                        )
                    )
                }
                TextExpanderAccessibilityService.reloadShortcuts()
                finish()
            } else {
                if (errorMsg?.contains("key", ignoreCase = true) == true) {
                    binding.tilShortcutKey.error = errorMsg
                    binding.etShortcutKey.requestFocus()
                } else {
                    binding.tilExpandedText.error = errorMsg
                    binding.etExpandedText.requestFocus()
                }
            }
        }
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    companion object {
        const val EXTRA_SHORTCUT_ID = "extra_shortcut_id"
        const val EXTRA_PREFILLED_TEXT = "extra_prefilled_text"
        const val EXTRA_FOLDER_NAME = "extra_folder_name"
    }
}
