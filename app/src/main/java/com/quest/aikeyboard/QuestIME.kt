package com.quest.aikeyboard

import android.content.Context
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

enum class KeyboardMode {
    TEXT,
    SYMBOLS
}

enum class KeyboardLanguage(val code: String, val displayName: String, val speechCode: String) {
    RU("RU", "Русский", "ru-RU"),
    EN("EN", "English", "en-US")
}

class QuestIME : InputMethodService() {

    private lateinit var rootView: View
    private lateinit var keysContainer: LinearLayout
    private lateinit var tvVoiceStatus: TextView
    private lateinit var tvCurrentLangBadge: TextView

    private var currentMode = KeyboardMode.TEXT
    private var currentLang = KeyboardLanguage.RU
    private var isShifted = false
    private var isCapsLocked = false

    private var voiceInputHelper: VoiceInputHelper? = null

    // Handler for repeat backspace
    private val repeatHandler = Handler(Looper.getMainLooper())
    private var isRepeatingBackspace = false
    private val backspaceRunnable = object : Runnable {
        override fun run() {
            if (isRepeatingBackspace) {
                handleDelete()
                repeatHandler.postDelayed(this, 50)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        voiceInputHelper = VoiceInputHelper(
            context = this,
            onTextRecognized = { text ->
                currentInputConnection?.commitText("$text ", 1)
                tvVoiceStatus.text = "Распознано: $text"
                Handler(Looper.getMainLooper()).postDelayed({
                    tvVoiceStatus.text = "Quest AI Keyboard"
                }, 3000)
            },
            onStateChanged = { status ->
                tvVoiceStatus.text = status
            },
            onError = { err ->
                tvVoiceStatus.text = err
                Toast.makeText(this, err, Toast.LENGTH_SHORT).show()
                Handler(Looper.getMainLooper()).postDelayed({
                    tvVoiceStatus.text = "Quest AI Keyboard"
                }, 3000)
            }
        )
    }

    override fun onCreateInputView(): View {
        rootView = layoutInflater.inflate(R.layout.keyboard_view, null)
        keysContainer = rootView.findViewById(R.id.keysContainer)
        tvVoiceStatus = rootView.findViewById(R.id.tvVoiceStatus)
        tvCurrentLangBadge = rootView.findViewById(R.id.tvCurrentLangBadge)

        renderKeyboard()
        return rootView
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        renderKeyboard()
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceInputHelper?.destroy()
    }

    private fun renderKeyboard() {
        keysContainer.removeAllViews()
        tvCurrentLangBadge.text = currentLang.code

        val rows: List<List<String>> = when (currentMode) {
            KeyboardMode.TEXT -> if (currentLang == KeyboardLanguage.RU) getRussianLayout() else getEnglishLayout()
            KeyboardMode.SYMBOLS -> getSymbolsLayout()
        }

        for (row in rows) {
            val rowLayout = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

            for (key in row) {
                val keyButton = createKeyButton(key)
                rowLayout.addView(keyButton)
            }

            keysContainer.addView(rowLayout)
        }
    }

    private fun createKeyButton(key: String): View {
        val button = Button(this).apply {
            text = formatKeyText(key)
            isAllCaps = false
            textSize = 18f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
        }

        val lp = LinearLayout.LayoutParams(0, dpToPx(56)).apply {
            setMargins(dpToPx(2), dpToPx(3), dpToPx(2), dpToPx(3))
        }

        when (key) {
            "SHIFT" -> {
                lp.weight = 1.4f
                button.setBackgroundResource(R.drawable.key_special_background)
                button.text = if (isCapsLocked) "⇪" else if (isShifted) "⬆" else "⇧"
                button.setOnClickListener {
                    if (isShifted) {
                        isCapsLocked = !isCapsLocked
                    } else {
                        isShifted = true
                    }
                    renderKeyboard()
                }
            }
            "DEL" -> {
                lp.weight = 1.4f
                button.setBackgroundResource(R.drawable.key_special_background)
                button.text = "⌫"
                setupBackspaceRepeat(button)
            }
            "123", "ABC" -> {
                lp.weight = 1.3f
                button.setBackgroundResource(R.drawable.key_special_background)
                button.setOnClickListener {
                    currentMode = if (currentMode == KeyboardMode.TEXT) KeyboardMode.SYMBOLS else KeyboardMode.TEXT
                    renderKeyboard()
                }
            }
            "LANG" -> {
                lp.weight = 1.2f
                button.setBackgroundResource(R.drawable.key_special_background)
                button.text = "🌐 " + currentLang.code
                button.setOnClickListener {
                    currentLang = if (currentLang == KeyboardLanguage.RU) KeyboardLanguage.EN else KeyboardLanguage.RU
                    renderKeyboard()
                }
            }
            "MIC" -> {
                lp.weight = 1.2f
                button.setBackgroundResource(R.drawable.key_voice_background)
                button.text = "🎙️"
                button.setOnClickListener {
                    toggleVoiceInput()
                }
            }
            "SPACE" -> {
                lp.weight = 4.5f
                button.setBackgroundResource(R.drawable.key_background)
                button.text = currentLang.displayName
                button.setOnClickListener {
                    handleKeyInput(" ")
                }
            }
            "ENTER" -> {
                lp.weight = 1.5f
                button.setBackgroundResource(R.drawable.key_special_background)
                button.text = "↵"
                button.setOnClickListener {
                    handleEnter()
                }
            }
            "HIDE" -> {
                lp.weight = 1.0f
                button.setBackgroundResource(R.drawable.key_special_background)
                button.text = "⌨️⬇"
                button.setOnClickListener {
                    requestHideSelf(0)
                }
            }
            else -> {
                lp.weight = 1.0f
                button.setBackgroundResource(R.drawable.key_background)
                button.setOnClickListener {
                    handleKeyInput(formatKeyText(key))
                    if (isShifted && !isCapsLocked) {
                        isShifted = false
                        renderKeyboard()
                    }
                }
            }
        }

        button.layoutParams = lp
        return button
    }

    private fun setupBackspaceRepeat(button: Button) {
        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    handleDelete()
                    isRepeatingBackspace = true
                    repeatHandler.postDelayed(backspaceRunnable, 400)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isRepeatingBackspace = false
                    repeatHandler.removeCallbacks(backspaceRunnable)
                    true
                }
                else -> false
            }
        }
    }

    private fun toggleVoiceInput() {
        val helper = voiceInputHelper ?: return
        if (helper.isListening) {
            helper.stopListening()
        } else {
            tvVoiceStatus.text = "Запуск микрофона (${currentLang.speechCode})..."
            helper.startListening(currentLang.speechCode)
        }
    }

    private fun formatKeyText(key: String): String {
        return if (key.length == 1) {
            if (isShifted || isCapsLocked) key.uppercase() else key.lowercase()
        } else {
            key
        }
    }

    private fun handleKeyInput(char: String) {
        currentInputConnection?.commitText(char, 1)
    }

    private fun handleDelete() {
        val selectedText = currentInputConnection?.getSelectedText(0)
        if (selectedText.isNullOrEmpty()) {
            currentInputConnection?.deleteSurroundingText(1, 0)
        } else {
            currentInputConnection?.commitText("", 1)
        }
    }

    private fun handleEnter() {
        currentInputConnection?.sendKeyEvent(
            android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER)
        )
        currentInputConnection?.sendKeyEvent(
            android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER)
        )
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private fun getRussianLayout(): List<List<String>> {
        return listOf(
            listOf("й", "ц", "у", "к", "е", "н", "г", "ш", "щ", "з", "х", "ъ"),
            listOf("ф", "ы", "в", "а", "п", "р", "о", "л", "д", "ж", "э"),
            listOf("SHIFT", "я", "ч", "с", "м", "и", "т", "ь", "б", "ю", "DEL"),
            listOf("123", "LANG", "MIC", "SPACE", "ENTER", "HIDE")
        )
    }

    private fun getEnglishLayout(): List<List<String>> {
        return listOf(
            listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
            listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
            listOf("SHIFT", "z", "x", "c", "v", "b", "n", "m", "DEL"),
            listOf("123", "LANG", "MIC", "SPACE", "ENTER", "HIDE")
        )
    }

    private fun getSymbolsLayout(): List<List<String>> {
        return listOf(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            listOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "/"),
            listOf("=", "*", "\"", "'", ":", ";", "!", "?", ",", ".", "DEL"),
            listOf("ABC", "LANG", "MIC", "SPACE", "ENTER", "HIDE")
        )
    }
}
