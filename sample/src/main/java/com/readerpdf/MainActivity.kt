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
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import com.viewer.pdf.PdfReader
import com.viewer.pdf.PdfReaderPage
import com.viewer.pdf.PdfReaderState
import kotlinx.coroutines.launch
import java.io.File
import java.lang.StrictMath.ceil
import java.lang.StrictMath.max
import kotlin.random.Random
import kotlin.random.nextInt

class MainActivity : ComponentActivity() {

    private val pwd = "4826e69ed1a35b923ce91edd06d2ec5527b9d949"

    private lateinit var inernalAssetsFolder: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        inernalAssetsFolder = applicationContext.cacheDir!!.absolutePath + "/assets/"

        copyAssets(this)

        setContent {
            val list = remember {
                mutableStateListOf<PdfReaderPage>().apply {
                    val file = File("${cacheDir.absolutePath}/assets")
                    repeat(254) {
                        val pageFile = it.toString().padStart(6, '0')
                        add(PdfReaderPage.PdfFile(File(file, "$pageFile.pdf"), pwd, File(file, "$pageFile.jpg")))
                    }
                }
            }
            val scope = rememberCoroutineScope()
            val orientation = LocalConfiguration.current.orientation
            var pdfPage by remember { mutableStateOf(0) }
            val readerState = remember(orientation) {
                PdfReaderState(
                    initialPage = pdfPage,
                    pages = list,
                    doublePage = orientation == ORIENTATION_LANDSCAPE,
                    reverseLayout = false
                )
            }

            LaunchedEffect(readerState) {
                snapshotFlow {
                    readerState.currentPage
                }.collect {
                    pdfPage = if (readerState.doublePage) {
                        max(it * 2 - 1, 0)
                    } else {
                        ceil(it / 2.0).toInt()
                    }
                }
            }

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
                            modifier = Modifier.padding(it),
                            onLinkClick = {
                                Log.d("link", "Clicked on link $it")
                            },
                        )
                    }
                }
            )
        }
    }
}

