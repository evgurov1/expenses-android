package com.evgurov.expenses.importing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import com.googlecode.tesseract.android.TessBaseAPI
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Достаёт текст из PDF и изображений. Всё работает на телефоне, без сети:
 * PDF читается через PdfBox, сканы и фото распознаются Tesseract с русской моделью из assets.
 */
@Singleton
class LoanTextExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val ocrLock = Mutex()

    /** Любой файл: PDF или изображение. Тип берём из системы, а если его нет — по первым байтам. */
    suspend fun fromAny(uri: Uri): String = if (isPdf(uri)) fromPdf(uri) else fromImage(uri)

    fun isPdf(uri: Uri): Boolean {
        val type = context.contentResolver.getType(uri)
        if (type != null && type != "application/octet-stream") return type == "application/pdf"
        return context.contentResolver.openInputStream(uri)?.use { input ->
            val head = ByteArray(4)
            input.read(head) == 4 && String(head, Charsets.US_ASCII) == "%PDF"
        } ?: false
    }

    suspend fun fromPdf(uri: Uri): String = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context)
        val text = context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Не удалось открыть файл" }
            PDDocument.load(input).use { doc ->
                PDFTextStripper().apply { sortByPosition = true }.getText(doc)
            }
        }
        // В PDF-скане текста нет — распознаём страницы как картинки.
        if (text.count { !it.isWhitespace() } >= MIN_TEXT_CHARS) text else ocrPdfPages(uri)
    }

    suspend fun fromImage(uri: Uri): String = withContext(Dispatchers.Default) {
        val bitmap = decodeBitmap(uri)
        try {
            recognize(listOf(bitmap))
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Скриншот списка операций банка: читаем построчно (название и сумма на одной строке)
     * и с английской моделью — в названиях магазинов много латиницы.
     */
    suspend fun fromScreenshot(uri: Uri): String = withContext(Dispatchers.Default) {
        val bitmap = decodeBitmap(uri)
        try {
            recognize(listOf(bitmap), languages = "rus+eng", pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK)
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun ocrPdfPages(uri: Uri): String {
        val pages = mutableListOf<Bitmap>()
        context.contentResolver.openFileDescriptor(uri, "r").use { fd ->
            requireNotNull(fd) { "Не удалось открыть файл" }
            PdfRenderer(fd).use { renderer ->
                for (i in 0 until minOf(renderer.pageCount, MAX_PDF_PAGES)) {
                    renderer.openPage(i).use { page ->
                        // ~200 dpi: страница A4 шириной 595 pt
                        val scale = 200f / 72f
                        val bitmap = Bitmap.createBitmap(
                            (page.width * scale).toInt(),
                            (page.height * scale).toInt(),
                            Bitmap.Config.ARGB_8888,
                        )
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        pages += bitmap
                    }
                }
            }
        }
        return try {
            withContext(Dispatchers.Default) { recognize(pages) }
        } finally {
            pages.forEach { it.recycle() }
        }
    }

    private suspend fun recognize(
        bitmaps: List<Bitmap>,
        languages: String = "rus",
        pageSegMode: Int = TessBaseAPI.PageSegMode.PSM_AUTO,
    ): String = ocrLock.withLock {
        val dataDir = prepareTessData()
        val tess = TessBaseAPI()
        try {
            check(tess.init(dataDir.absolutePath, languages)) { "Не удалось запустить распознавание" }
            tess.pageSegMode = pageSegMode
            bitmaps.joinToString("\n") { bitmap ->
                tess.setImage(bitmap)
                tess.getUTF8Text().orEmpty()
            }
        } finally {
            tess.recycle()
        }
    }

    /** Tesseract читает модели из файлов, поэтому один раз копируем их из assets. */
    private fun prepareTessData(): File {
        val root = File(context.filesDir, "ocr")
        for (name in listOf("rus.traineddata", "eng.traineddata")) {
            val target = File(root, "tessdata/$name")
            if (target.exists()) continue
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, "$name.tmp")
            context.assets.open("tessdata/$name").use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
            tmp.renameTo(target)
        }
        return root
    }

    private fun decodeBitmap(uri: Uri): Bitmap {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > MAX_IMAGE_SIDE) {
                    val k = MAX_IMAGE_SIDE.toFloat() / longest
                    decoder.setTargetSize((info.size.width * k).toInt(), (info.size.height * k).toInt())
                }
            }
        } else {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
            var sample = 1
            while (maxOf(options.outWidth, options.outHeight) / sample > MAX_IMAGE_SIDE) sample *= 2
            context.contentResolver.openInputStream(uri).use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: error("Не удалось открыть изображение")
        }
        return if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
    }

    private companion object {
        const val MIN_TEXT_CHARS = 50
        const val MAX_PDF_PAGES = 6
        const val MAX_IMAGE_SIDE = 3000
    }
}
