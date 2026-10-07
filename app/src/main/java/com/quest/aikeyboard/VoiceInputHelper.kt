package com.quest.aikeyboard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class VoiceInputHelper(
    private val context: Context,
    private val onTextRecognized: (String) -> Unit,
    private val onStateChanged: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    var isListening = false
        private set

    init {
        initRecognizer()
    }

    private fun initRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        onStateChanged("Слушаю...")
                    }

                    override fun onBeginningOfSpeech() {
                        onStateChanged("Запись голоса...")
                    }

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        onStateChanged("Обработка речи...")
                        isListening = false
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        val message = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Ошибка аудио"
                            SpeechRecognizer.ERROR_CLIENT -> "Ошибка клиента"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Нужен доступ к микрофону"
                            SpeechRecognizer.ERROR_NETWORK -> "Ошибка сети"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Таймаут сети"
                            SpeechRecognizer.ERROR_NO_MATCH -> "Речь не распознана"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Сервис занят"
                            SpeechRecognizer.ERROR_SERVER -> "Ошибка сервера"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Тишина"
                            else -> "Ошибка распознавания ($error)"
                        }
                        onError(message)
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val recognized = matches[0]
                            onTextRecognized(recognized)
                        } else {
                            onError("Нет текста")
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            onStateChanged(matches[0])
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
    }

    fun startListening(languageCode: String) {
        if (speechRecognizer == null) {
            initRecognizer()
        }

        if (speechRecognizer == null) {
            onError("Распознавание речи недоступно на устройстве")
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageCode)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, languageCode)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        try {
            isListening = true
            speechRecognizer?.startListening(intent)
            onStateChanged("Подключение к микрофону...")
        } catch (e: Exception) {
            isListening = false
            Log.e("VoiceInputHelper", "Error starting listening", e)
            onError("Не удалось запустить микрофон")
        }
    }

    fun stopListening() {
        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
            onStateChanged("Остановлено")
        }
    }

    fun cancel() {
        speechRecognizer?.cancel()
        isListening = false
        onStateChanged("Отменено")
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}
