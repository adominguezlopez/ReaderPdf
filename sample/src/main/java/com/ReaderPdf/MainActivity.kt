package com.ReaderPdf

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.ReaderPdf.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.downloadAssetsButton.setOnClickListener {
            copyAssets(this)
        }

        binding.clearPdf.setOnClickListener {
            clearPdf()
        }

        binding.loadPdfButton.setOnClickListener {
            loadPdf()
        }

        binding.loadNewPage.setOnClickListener {
            loadPdfPage()
        }

        binding.test.setOnClickListener {
            reset()
        }
    }

    private fun clearPdf(){

    }

    private fun loadPdf(){

    }

    private fun loadPdfPage(){

    }

    private fun reset(){

    }
}