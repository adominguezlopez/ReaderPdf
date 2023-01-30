package com.readerpdf

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.SnackbarDefaults.backgroundColor
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.artifex.mupdf.fitz.Buffer
import com.artifex.mupdf.fitz.PDFDocument
import com.artifex.mupdf.fitz.Rect
import com.viewer.PDFCore
import com.viewer.PageAdapter
import com.viewer.PageView
import com.viewer.ReaderView
import com.viewer.presenter.components.PdfReader
import com.viewer.presenter.pager.PdfReaderPage
import com.viewer.presenter.pager.rememberPdfReaderState
import java.io.File

class MainActivity : ComponentActivity() {

    private val totalPages = 10
    private val pwd = "q82n3ks92j9sd72bnsldf7823hbzx7"

    private lateinit var inernalAssetsFolder: String

    private var adapter: PageAdapter? = null
    private var core: PDFCore? = null
    lateinit var document: PDFDocument
    private lateinit var readerView: ReaderView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        inernalAssetsFolder = applicationContext.cacheDir!!.absolutePath + "/assets/"

        setContent {
            val list = remember {
                mutableStateListOf<PdfReaderPage>().apply {
                    addAll(listOf(
                        PdfReaderPage.Url("https://cdnb.artstation.com/p/assets/images/images/014/837/255/large/jaya-basak-img-20170810-185149.jpg?1545822478"),
                        PdfReaderPage.Url("https://d1csarkz8obe9u.cloudfront.net/posterpreviews/travel-magazine-in-page-design-template-5313fa56e5a4b94c79d3d5bd8de42adf_screen.jpg?ts=1637035546"),
                        PdfReaderPage.Url("https://as1.ftcdn.net/v2/jpg/01/98/03/08/1000_F_198030876_RTsn9BQeFz7UMuKYa36ChnB7W6A9mqR3.jpg"),
                        PdfReaderPage.Url("https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQ98bZuiEHjQcgNE_2_QaRKwO0cvccP-eiyZQ&usqp=CAU"),
                        PdfReaderPage.Url("https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQICaiZ8uM-64Z0OlrLJRGByIu55VhCqKrxCg&usqp=CAU"),
                        PdfReaderPage.Url("https://cdnb.artstation.com/p/assets/images/images/014/837/255/large/jaya-basak-img-20170810-185149.jpg?1545822478"),
                        PdfReaderPage.Url("https://d1csarkz8obe9u.cloudfront.net/posterpreviews/travel-magazine-in-page-design-template-5313fa56e5a4b94c79d3d5bd8de42adf_screen.jpg?ts=1637035546"),
                        PdfReaderPage.Url("https://as1.ftcdn.net/v2/jpg/01/98/03/08/1000_F_198030876_RTsn9BQeFz7UMuKYa36ChnB7W6A9mqR3.jpg"),
                        PdfReaderPage.Url("https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQ98bZuiEHjQcgNE_2_QaRKwO0cvccP-eiyZQ&usqp=CAU"),
                        PdfReaderPage.Url("https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQICaiZ8uM-64Z0OlrLJRGByIu55VhCqKrxCg&usqp=CAU")
                    ))
                }
            }
            val readerState = rememberPdfReaderState(0, list)
            MaterialTheme(
                content = {
                    // A surface container using the 'background' color from the theme
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        /*
                        AndroidView(factory = { ctx ->
                            ReaderView(ctx).apply {
                                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                            }
                        }, update = {
                            readerView = it
                        })
                         */

                        PdfReader(readerState, doublePage = false, rtl = false, pageContent = {
                            Log.d("pageLIstener", "changeing page to: ${it}")
                        })

                        Buttons()
                    }
                })
        }
    }

    @Composable
    fun BoxScope.Buttons(
    ) {
        val context = LocalContext.current
        val actualPage: MutableState<Int> = remember { mutableStateOf(0) }

        Column(modifier = Modifier.align(Alignment.BottomCenter)) {
            Row {
                SettingsButton(
                    title = "Dw ASSETS",
                    onClick = {
                        copyAssets(context)
                    },
                )
                SettingsButton(
                    title = "Clr Pdf",
                    onClick = {
                        clearPdf()
                    },
                )
            }
            Row {
                SettingsButton(
                    title = "Ld Pdf",
                    onClick = {
                        loadPdf()
                    },
                )
                SettingsButton(
                    title = "Ld page ${actualPage.value}",
                    onClick = {
                        loadPdfPage(actualPage.value)
                        actualPage.value++
                    },
                )
                SettingsButton(
                    title = "reset",
                    onClick = {
                        reset()
                    },
                )
            }
        }
    }

    @Composable
    fun RowScope.SettingsButton(
        title: String,
        onClick: () -> Unit,
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = backgroundColor)
        ) {
            Text(text = title, color = Color.White)
        }

    }

    private fun clearPdf() {
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

    private fun loadPdf() {
        if (core != null)
            reset()

        val documentFile = File(inernalAssetsFolder, "/Main.pdf")

        document = PDFDocument.openDocument(documentFile.absolutePath) as PDFDocument

        core = PDFCore(document)
        readerView.destroy()
        adapter?.reset()
        adapter = PageAdapter(this, core!!)
        readerView.adapter = adapter
        readerView.setLinksEnabled(true)
    }

    private fun loadPdfPage(actualPage: Int) {
        addNewPage(actualPage)

        readerView.applyToChildArround(object : ReaderView.ViewMapper() {
            override fun applyToView(view: View) {
                (view as PageView).apply {
                    update()
                }
            }
        }, actualPage)
    }

    private fun addNewPage(actualPage: Int) {
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

            readerView.applyToChild(object : ReaderView.ViewMapper() {
                override fun applyToView(view: View) {
                    (view as PageView).apply {
                        updateLinks(links)
                    }
                }
            }, actualPage)
        }

        document.save(documentFile.absolutePath, "")
    }

    private fun reset() {
        readerView.applyToChildren(object : ReaderView.ViewMapper() {
            override fun applyToView(view: View) {
                (view as PageView).releaseBitmaps()
            }
        })

        adapter?.reset()

        readerView.destroy()
        core?.onDestroy()
    }
}

