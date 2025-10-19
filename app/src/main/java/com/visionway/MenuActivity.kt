package com.project.visionway

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MenuActivity : AppCompatActivity() {

    private val suporteUrl = "https://forms.gle/z89t6gAymjWViYR99"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)

        val btnIniciar = findViewById<Button>(R.id.btnIniciar)
        val btnConfiguracoes = findViewById<Button>(R.id.btnConfiguracoes)
        val btnSuporte = findViewById<Button>(R.id.btnSuporte)

        btnIniciar.setOnClickListener {
            startActivity(android.content.Intent(this, AppActivity::class.java))
        }
        btnConfiguracoes.setOnClickListener {
            startActivity(android.content.Intent(this, ConfigActivity::class.java))
        }
        btnSuporte.setOnClickListener {
            // evita “duplo clique” acidental
            btnSuporte.isEnabled = false
            CustomTabsHelper.open(this, suporteUrl)
            btnSuporte.postDelayed({ btnSuporte.isEnabled = true }, 600)
        }
    }

    override fun onStart() {
        super.onStart()
        // pré-aquecimento: deixa a abertura quase instantânea
        CustomTabsHelper.warmup(this)
    }

    override fun onStop() {
        super.onStop()
        CustomTabsHelper.unbind(this)
    }
}