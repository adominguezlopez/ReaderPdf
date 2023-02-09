package com.readerpdf

import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import com.viewer.pdf.PdfReader
import com.viewer.pdf.PdfReaderPage
import com.viewer.pdf.rememberPdfReaderState
import kotlinx.coroutines.launch
import java.io.File
import kotlin.random.Random
import kotlin.random.nextInt

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
                        val pageFile = it.toString().padStart(6, '0')
                        add(PdfReaderPage.PdfFile(File(file, "$pageFile.pdf"), pwd, File(file, "$pageFile.jpg")))
                    }
                }
            }
            val scope = rememberCoroutineScope()
            val readerState = rememberPdfReaderState(0, list)
            MaterialTheme(
                content = {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("Page ${readerState.currentPage + 1}/${readerState.pageCount}") },
                                actions = {
                                    IconButton(onClick = {
                                        val newPage = Random.nextInt(IntRange(0, readerState.pageCount - 1))
                                        scope.launch {
                                            readerState.setCurrentPage(newPage)
                                        }
                                    }) {
                                        Icon(imageVector = Icons.Default.Search, contentDescription = null)
                                    }
                                },
                                backgroundColor = Color.Black,
                                contentColor = Color.White
                            )
                        }
                    ) {
                        PdfReader(
                            readerState = readerState,
                            doublePage = LocalConfiguration.current.orientation == ORIENTATION_LANDSCAPE,
                            rtl = false,
                            onLinkClick = {
                                Log.d("link", "Clicked on link $it")
                            },
                            modifier = Modifier.padding(it)
                        )
                    }
                }
            )
        }
    }
}

