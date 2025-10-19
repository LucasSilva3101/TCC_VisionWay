package com.project.visionway

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class ConfigActivity : AppCompatActivity() {

    private var ttsPreview: TextToSpeech? = null
    private var ready = false
    private var voicesPtBr: List<Voice> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)

        findViewById<Button>(R.id.btnEscolherVoz).setOnClickListener {
            iniciarOuInstalarGoogleTts()
        }
        findViewById<Button>(R.id.btnEntradaVideo).setOnClickListener {
            Toast.makeText(this, "Entrada de vídeo: em breve", Toast.LENGTH_SHORT).show()
        }
    }

    private fun iniciarOuInstalarGoogleTts() {
        if (!TtsUtils.isGoogleTtsInstalled(this)) {
            AlertDialog.Builder(this)
                .setTitle("Google TTS não instalado")
                .setMessage("Instale o 'Speech Services by Google' para usar vozes pt-BR.")
                .setPositiveButton("Abrir Play Store") { _, _ -> TtsUtils.openPlayStoreGoogleTts(this) }
                .setNegativeButton("Cancelar", null)
                .show()
            return
        }
        // Inicia TTS já fixando o engine do Google
        iniciarPreviewGoogleTts()
    }

    private fun iniciarPreviewGoogleTts() {
        ready = false
        ttsPreview?.shutdown()
        ttsPreview = TextToSpeech(this, { status ->
            if (status == TextToSpeech.SUCCESS) {
                try { ttsPreview?.language = Locale("pt", "BR") } catch (_: Exception) {}
                carregarVozesGooglePtBr()
                if (voicesPtBr.isEmpty()) {
                    AlertDialog.Builder(this)
                        .setTitle("Sem vozes pt-BR")
                        .setMessage("Baixe as vozes pt-BR do Google para continuar.")
                        .setPositiveButton("Baixar vozes") { _, _ -> TtsUtils.requestInstallGoogleTtsData(this) }
                        .setNegativeButton("Configurações de TTS") { _, _ -> TtsUtils.openTtsSettings(this) }
                        .setNeutralButton("Fechar", null)
                        .show()
                } else {
                    mostrarDialogoVozesGoogle()
                }
                ready = true
            } else {
                Toast.makeText(this, "Falha ao iniciar Google TTS", Toast.LENGTH_SHORT).show()
            }
        }, TtsUtils.GOOGLE_TTS_PKG)
    }

    private fun carregarVozesGooglePtBr() {
        voicesPtBr = try {
            ttsPreview?.voices?.filter { v ->
                v.locale?.language?.equals("pt", true) == true &&
                        (v.locale?.country.isNullOrBlank() || v.locale.country.equals("BR", true))
            }?.sortedBy { it.name.lowercase(Locale.ROOT) } ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    private fun mostrarDialogoVozesGoogle() {
        val labels = voicesPtBr.map { v ->
            val quality = when {
                v.quality >= Voice.QUALITY_HIGH -> "alta"
                v.quality >= Voice.QUALITY_NORMAL -> "média"
                else -> "baixa"
            }
            "${v.name}  •  qualidade $quality"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Escolha a voz (Google TTS, pt-BR)")
            .setItems(labels) { dialog, which ->
                val voice = voicesPtBr[which]
                VoicePrefs.setVoiceName(this, voice.name)
                // gênero heurístico só para fallback de pitch se necessário
                val gen = if (voice.name.lowercase().contains("female") || voice.name.lowercase().contains("fem"))
                    VoicePrefs.GENDER_FEMALE else VoicePrefs.GENDER_MALE
                VoicePrefs.setGender(this, gen)

                // Preview imediato
                try {
                    ttsPreview?.voice = voice
                    ttsPreview?.setPitch(1.0f)
                    ttsPreview?.setSpeechRate(1.0f)
                    ttsPreview?.speak("Esta é uma amostra da voz selecionada.", TextToSpeech.QUEUE_FLUSH, null, "preview")
                } catch (_: Exception) {}

                Toast.makeText(this, "Selecionado: ${voice.name}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .setNeutralButton("Baixar vozes") { _, _ -> TtsUtils.requestInstallGoogleTtsData(this) }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { ttsPreview?.stop(); ttsPreview?.shutdown() } catch (_: Exception) {}
    }
}