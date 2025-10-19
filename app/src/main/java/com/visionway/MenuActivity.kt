package com.project.visionway

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MenuActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)

        val btnIniciar = findViewById<Button>(R.id.btnIniciar)
        val btnConfiguracoes = findViewById<Button>(R.id.btnConfiguracoes)
        val btnSobre = findViewById<Button>(R.id.btnSobre)

        btnIniciar.setOnClickListener {
            startActivity(Intent(this, AppActivity::class.java))
        }

        btnConfiguracoes.setOnClickListener {
            startActivity(Intent(this, ConfigActivity::class.java))
        }

        btnSobre.setOnClickListener {
            // TODO: implementar tela "Sobre" (Activity ou Dialog)
        }
    }
}