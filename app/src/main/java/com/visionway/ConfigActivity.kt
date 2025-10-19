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
    private var currentEngine: String? = null
    private var ready = false
    private var voicesPtBr: List<Voice> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)

        findViewById<Button>(R.id.btnEscolherVoz).setOnClickListener {
            mostrarDialogoEscolherEngine()
        }

    }

    // 1) Usuário escolhe o ENGINE instalado
    private fun mostrarDialogoEscolherEngine() {
        val probe = TextToSpeech(this) { } // apenas para listar engines
        val engines = probe.engines
        probe.shutdown()

        if (engines.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Sem engines TTS")
                .setMessage("Nenhum engine TTS instalado. Instale o 'Speech Services by Google'.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val pm = packageManager
        val items = engines.map { ei ->
            val label = try { pm.getApplicationLabel(pm.getApplicationInfo(ei.name, 0)).toString() }
            catch (_: Exception) { ei.label }
            "${label}  (${ei.name})"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Escolha o mecanismo TTS")
            .setItems(items) { _, which ->
                val enginePkg = engines[which].name
                carregarVozesDoEngine(enginePkg)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // 2) Carrega vozes pt-BR do engine escolhido e abre o seletor de VOZ
    private fun carregarVozesDoEngine(enginePkg: String) {
        ready = false
        ttsPreview?.shutdown()
        currentEngine = enginePkg
        ttsPreview = TextToSpeech(this, { status ->
            if (status == TextToSpeech.SUCCESS) {
                try { ttsPreview?.language = Locale("pt", "BR") } catch (_: Exception) {}
                voicesPtBr = try {
                    ttsPreview?.voices?.filter { v ->
                        v.locale?.language?.equals("pt", true) == true &&
                                (v.locale?.country.isNullOrBlank() || v.locale.country.equals("BR", true))
                    }?.sortedBy { it.name.lowercase(Locale.ROOT) } ?: emptyList()
                } catch (_: Exception) { emptyList() }

                ready = true
                if (voicesPtBr.isEmpty()) {
                    AlertDialog.Builder(this)
                        .setTitle("Sem vozes pt-BR nesse engine")
                        .setMessage("Instale/baixe vozes pt-BR para o mecanismo selecionado.")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    mostrarDialogoEscolherVoz()
                }
            } else {
                Toast.makeText(this, "Falha ao iniciar $enginePkg", Toast.LENGTH_SHORT).show()
            }
        }, enginePkg)
    }

    // 3) Usuário escolhe a VOZ do engine; salvamos engine+voice e fazemos preview
    private fun mostrarDialogoEscolherVoz() {
        if (!ready || voicesPtBr.isEmpty()) return

        val labels = voicesPtBr.map { voice ->
            val quality = when {
                voice.quality >= Voice.QUALITY_HIGH -> "alta"
                voice.quality >= Voice.QUALITY_NORMAL -> "média"
                else -> "baixa"
            }
            "${voice.name}  •  qualidade $quality"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Escolha a voz (pt-BR)")
            .setItems(labels) { dialog, which ->
                val voice = voicesPtBr[which]
                VoicePrefs.setEngine(this, currentEngine)
                VoicePrefs.setVoiceName(this, voice.name)
                // opcional: marca um gênero para fallback
                VoicePrefs.setGender(this, if (voice.name.lowercase().contains("female") || voice.name.lowercase().contains("fem")) VoicePrefs.GENDER_FEMALE else VoicePrefs.GENDER_MALE)

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
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { ttsPreview?.stop(); ttsPreview?.shutdown() } catch (_: Exception) {}
    }
}