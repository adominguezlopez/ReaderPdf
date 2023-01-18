package com.readerpdf

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import com.readerpdf.databinding.ActivityMainBinding
import com.artifex.mupdf.fitz.Buffer
import com.artifex.mupdf.fitz.PDFDocument
import com.artifex.mupdf.fitz.Rect
import com.viewer.PDFCore
import com.viewer.PageAdapter
import com.viewer.PageView
import com.viewer.ReaderView
import java.io.File

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding

    private lateinit var inernalAssetsFolder: String
    private val totalPages = 10

    private var adapter: PageAdapter? = null
    private var core: PDFCore? = null
    lateinit var document: PDFDocument
    private var actualPage = 0
    private val pwd = "q82n3ks92j9sd72bnsldf7823hbzx7"

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
        if (core!=null)
            reset()

        val documentFile = File(inernalAssetsFolder, "/Main.pdf")

        document = PDFDocument.openDocument(documentFile.absolutePath) as PDFDocument

        core = PDFCore(document)
        binding.readerView.destroy()
        adapter?.reset()
        adapter = PageAdapter(this, core!!)
        binding.readerView.adapter = adapter
        binding.readerView.setLinksEnabled(true)
    }

    private fun loadPdfPage(){
        addNewPage()

        binding.readerView.applyToChildArround(object : ReaderView.ViewMapper() {
            override fun applyToView(view: View) {
                (view as PageView).apply {
                    update()
                }
            }
        }, actualPage)

        actualPage++
        binding.loadNewPage.text = "Ld page $actualPage"
    }

    private fun addNewPage() {
        val documentFile = File(inernalAssetsFolder, "/Main.pdf")
        val insert = "${actualPage.toString().padStart(6, '0')}.pdf"
        val pageFile = File("${cacheDir.absolutePath}/assets", insert)
        val pageToInsert = PDFDocument.openDocument(pageFile.absolutePath) as PDFDocument
        pageToInsert.authenticatePassword(pwd)
        document.graftPage(actualPage, pageToInsert, 0)
        document.deletePage(actualPage + 1)
        adapter?.removePageSize(actualPage)
        val links = pageToInsert.loadPage(0)?.links

        if (links != null) {
            core?.addPageLinks(actualPage, links)
            adapter?.setLinks(links, actualPage)

            binding.readerView.applyToChild(object : ReaderView.ViewMapper() {
                override fun applyToView(view: View) {
                    (view as PageView).apply {
                        updateLinks(links)
                    }
                }
            }, actualPage)
        }

        document.save(documentFile.absolutePath, "")
    }

    private fun reset(){
        binding.readerView.applyToChildren(object : ReaderView.ViewMapper() {
            override fun applyToView(view: View) {
                (view as PageView).releaseBitmaps()
            }
        })

        adapter?.reset()

        binding.readerView.destroy()
        core?.onDestroy()
    }
}