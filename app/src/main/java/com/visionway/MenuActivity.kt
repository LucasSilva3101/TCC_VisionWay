package com.project.visionway

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MenuActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Vincula o layout activity_main.xml
        setContentView(R.layout.activity_menu)

        // Referências dos botões
        val btnIniciar = findViewById<Button>(R.id.btnIniciar)
        val btnConfiguracoes = findViewById<Button>(R.id.btnConfiguracoes)
        val btnSobre = findViewById<Button>(R.id.btnSobre)

        // Botão Iniciar: abre AppActivity direto
        btnIniciar.setOnClickListener {
            val intent = Intent(this, AppActivity::class.java)
            startActivity(intent)
        }

        // Botão Configurações: apenas Toast por enquanto
        btnConfiguracoes.setOnClickListener {
            Toast.makeText(this, "Configurações clicado", Toast.LENGTH_SHORT).show()
        }

        // Botão Sobre: apenas Toast
        btnSobre.setOnClickListener {
            Toast.makeText(this, "Sobre clicado", Toast.LENGTH_SHORT).show()
        }
    }
}
