package com.readerpdf

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.viewer.pdf.PdfReader
import com.viewer.pdf.PdfReaderPage
import com.viewer.pdf.rememberPdfReaderState
import java.io.File

class MainActivity : ComponentActivity() {

    private val pwd = "q82n3ks92j9sd72bnsldf7823hbzx7"

    private lateinit var inernalAssetsFolder: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        inernalAssetsFolder = applicationContext.cacheDir!!.absolutePath + "/assets/"

        copyAssets(this)

        setContent {
            val list = remember {
                mutableStateListOf<PdfReaderPage>().apply {
                    val file = File("${cacheDir.absolutePath}/assets")
                    repeat(10) {
                        val pageFile = "${it.toString().padStart(6, '0')}.pdf"
                        add(PdfReaderPage.PdfFile(File(file, pageFile), pwd))
                    }
                }
            }
            val readerState = rememberPdfReaderState(0, list)
            MaterialTheme(
                content = {
                    Surface(modifier = Modifier.fillMaxWidth()) {
                        PdfReader(
                            readerState = readerState,
                            doublePage = false,
                            rtl = false,
                            onLinkClick = {
                                Log.d("link", "Clicked on link $it")
                            }
                        )
                    }
                })
        }
    }
}

