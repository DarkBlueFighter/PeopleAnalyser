package com.uka.peopleanalyser

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val etAzureKey = findViewById<EditText>(R.id.etAzureKey)
        val etAzureEndpoint = findViewById<EditText>(R.id.etAzureEndpoint)
        val etServerUrl = findViewById<EditText>(R.id.etServerUrl)
        val etStoreId = findViewById<EditText>(R.id.etStoreId)
        val btnSave = findViewById<Button>(R.id.btnSaveSettings)

        val prefs = getSharedPreferences("PeopleAnalyserPrefs", MODE_PRIVATE)
        etAzureKey.setText(prefs.getString("azure_key", ""))
        etAzureEndpoint.setText(prefs.getString("azure_endpoint", ""))
        etServerUrl.setText(prefs.getString("server_url", ""))
        etStoreId.setText(prefs.getString("store_id", ""))

        btnSave.setOnClickListener {
            val editor = prefs.edit()
            editor.putString("azure_key", etAzureKey.text.toString().trim())
            editor.putString("azure_endpoint", etAzureEndpoint.text.toString().trim())
            editor.putString("server_url", etServerUrl.text.toString().trim())
            editor.putString("store_id", etStoreId.text.toString().trim())
            editor.apply()

            Toast.makeText(this, "設定已儲存", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}