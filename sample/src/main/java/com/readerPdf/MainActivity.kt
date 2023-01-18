package com.ReaderPdf

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.ReaderPdf.databinding.ActivityMainBinding
import com.artifex.mupdf.fitz.Buffer
import com.artifex.mupdf.fitz.PDFDocument
import com.artifex.mupdf.fitz.Rect
import java.io.File

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding

    private lateinit var inernalAssetsFolder: String
    private val totalPages = 10

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        inernalAssetsFolder = applicationContext.cacheDir!!.absolutePath+"/assets/"

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
        val documentFile = File(inernalAssetsFolder, "/Main.pdf")

        val document = PDFDocument()

        val buffer = Buffer()

        for (i in 0 until totalPages) {
            val page = document.addPage(
                Rect(0f, 0f, 6f, 8f),
                0,
                document.newDictionary(),
                buffer
            )
            document.insertPage(-1, page)
        }
        buffer.destroy()

        document.save(documentFile.absolutePath, "")
    }

    private fun loadPdf(){

    }

    private fun loadPdfPage(){

    }

    private fun reset(){

    }
}