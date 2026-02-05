package com.example.blitzread.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import android.util.Log
object EpubThumbs {

    fun makeCoverThumb(context: Context, uri: Uri, docId: String): String? {
        // Need a real file path for ZipFile -> copy to cache
        val epubFile = copyUriToCache(context, uri, "$docId.epub") ?: return null
        Log.d("EPUB", "Opened EPUB zip: ${epubFile.absolutePath}")

        ZipFile(epubFile).use { zip ->

            val opfPath = findOpfPath(zip) ?: return null
            Log.d("EPUB", "OPF path = $opfPath")
            val opfBytes = zip.getEntry(opfPath)?.let { zip.getInputStream(it).readBytes() } ?: return null

            val coverHref = findCoverHrefInOpf(opfBytes)
                ?: return null
            Log.d("EPUB", "Cover href from OPF = $coverHref")
            val finalCoverHref =
                if (coverHref == "__FROM_SPINE__") {
                    findFirstImageHrefFromFirstSpine(zip, opfPath, opfBytes) ?: return null
                } else {
                    coverHref
                }

            val opfDir = opfPath.substringBeforeLast('/', missingDelimiterValue = "")
            val coverPath = if (opfDir.isBlank()) finalCoverHref else "$opfDir/$finalCoverHref"
            val normalizedCoverPath = coverPath.replace("\\", "/").replace("//", "/")
            Log.d("EPUB", "Trying zip entry cover path = $normalizedCoverPath")
            val imgEntry = zip.getEntry(normalizedCoverPath) ?: return null
            val imgBytes = zip.getInputStream(imgEntry).readBytes()

            val bmp = BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.size) ?: return null

            val dir = File(context.filesDir, "thumbs").apply { mkdirs() }
            val outFile = File(dir, "$docId.png")
            FileOutputStream(outFile).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            return outFile.absolutePath
        }
    }

    private fun copyUriToCache(context: Context, uri: Uri, fileName: String): File? {
        val outFile = File(context.cacheDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(outFile).use { output -> input.copyTo(output) }
        } ?: return null
        return outFile
    }

    private fun findOpfPath(zip: ZipFile): String? {
        val entry = zip.getEntry("META-INF/container.xml")
        if (entry == null) {
            Log.d("EPUB", "container.xml missing")
            return null
        }

        val bytes = runCatching { zip.getInputStream(entry).readBytes() }
            .getOrElse {
                Log.e("EPUB", "Failed reading container.xml", it)
                return null
            }

        // Fast fallback: regex extract full-path="..."
        val raw = runCatching { bytes.toString(Charsets.UTF_8) }.getOrDefault("")
        Regex("""full-path\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
            ?.let {
                Log.d("EPUB", "OPF path (regex) = $it")
                return it
            }

        // DOM parse (namespaced)
        val doc = parseXml(bytes)
        if (doc == null) {
            Log.d("EPUB", "parseXml(container.xml) returned null")
            return null
        }

        val nodes = doc.getElementsByTagNameNS("*", "rootfile")
        Log.d("EPUB", "rootfile nodes = ${nodes.length}")

        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            val fullPath = node.attributes?.getNamedItem("full-path")?.nodeValue
            if (!fullPath.isNullOrBlank()) {
                Log.d("EPUB", "OPF path (dom) = $fullPath")
                return fullPath
            }
        }

        Log.d("EPUB", "No full-path found in container.xml")
        return null
    }

    private fun findCoverHrefInOpf(opfBytes: ByteArray): String? {
        val doc = parseXml(opfBytes) ?: return null

        // 1) EPUB2: <meta name="cover" content="cover-id" />
        run {
            val metas = doc.getElementsByTagName("meta")
            var coverId: String? = null
            for (i in 0 until metas.length) {
                val node = metas.item(i)
                val attrs = node.attributes ?: continue
                val name = attrs.getNamedItem("name")?.nodeValue
                if (name == "cover") {
                    coverId = attrs.getNamedItem("content")?.nodeValue
                    break
                }
            }
            if (!coverId.isNullOrBlank()) {
                val href = findManifestHrefById(doc, coverId)
                if (!href.isNullOrBlank()) return href
            }
        }

        // 2) EPUB3: manifest item with properties="cover-image"
        run {
            val items = doc.getElementsByTagName("item")
            for (i in 0 until items.length) {
                val node = items.item(i)
                val attrs = node.attributes ?: continue
                val props = attrs.getNamedItem("properties")?.nodeValue.orEmpty()
                if (props.split(' ').any { it == "cover-image" }) {
                    val href = attrs.getNamedItem("href")?.nodeValue
                    if (!href.isNullOrBlank()) return href
                }
            }
        }

        // 3) Heuristic: first image with "cover" in href
        run {
            val items = doc.getElementsByTagName("item")
            var firstImageHref: String? = null
            for (i in 0 until items.length) {
                val node = items.item(i)
                val attrs = node.attributes ?: continue
                val mediaType = attrs.getNamedItem("media-type")?.nodeValue.orEmpty()
                val href = attrs.getNamedItem("href")?.nodeValue
                if (href.isNullOrBlank()) continue

                if (mediaType.startsWith("image/")) {
                    if (firstImageHref == null) firstImageHref = href
                    if (href.contains("cover", ignoreCase = true)) return href
                }
            }
            if (!firstImageHref.isNullOrBlank()) return firstImageHref
        }

        return "__FROM_SPINE__"
    }

    private fun findManifestHrefById(doc: org.w3c.dom.Document, id: String): String? {
        val items = doc.getElementsByTagName("item")
        for (i in 0 until items.length) {
            val node = items.item(i)
            val attrs = node.attributes ?: continue
            val itemId = attrs.getNamedItem("id")?.nodeValue
            if (itemId == id) return attrs.getNamedItem("href")?.nodeValue
        }
        return null
    }

    private fun parseXml(bytes: ByteArray): org.w3c.dom.Document? {
        return runCatching {
            val dbf = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                // Don't set apache features on Android; they can throw.
            }
            dbf.newDocumentBuilder().parse(bytes.inputStream())
        }.getOrElse {
            Log.e("EPUB", "XML parse failed", it)
            null
        }
    }

    private fun resolveZipPath(baseDir: String, hrefRaw: String): String? {
        // EPUB hrefs can be percent-encoded.
        val href = runCatching { java.net.URLDecoder.decode(hrefRaw, "UTF-8") }
            .getOrDefault(hrefRaw)

        // Absolute-ish hrefs (rare) or empty
        if (href.isBlank()) return null

        // Combine base + href
        val combined = (if (baseDir.isBlank()) href else "$baseDir/$href")
            .replace("\\", "/")

        // Normalize ./ and ../
        val parts = combined.split("/")
        val stack = ArrayDeque<String>()
        for (p in parts) {
            when {
                p.isEmpty() || p == "." -> Unit
                p == ".." -> if (stack.isNotEmpty()) stack.removeLast()
                else -> stack.addLast(p)
            }
        }
        return stack.joinToString("/")
    }

    private fun findFirstImageHrefFromFirstSpine(
        zip: ZipFile,
        opfPath: String,
        opfBytes: ByteArray
    ): String? {
        val doc = parseXml(opfBytes) ?: return null

        val opfDir = opfPath.substringBeforeLast('/', missingDelimiterValue = "")

        // 1) Get first <itemref idref="..."> from <spine>
        val itemrefs = doc.getElementsByTagName("itemref")
        if (itemrefs.length == 0) return null

        val idref = itemrefs.item(0).attributes?.getNamedItem("idref")?.nodeValue ?: return null

        // 2) Resolve that idref to an href in the manifest
        val htmlHref = findManifestHrefById(doc, idref) ?: return null

        // 3) Read the first spine HTML content
        val normalizedHtmlPath = resolveZipPath(opfDir, htmlHref) ?: return null
        val htmlEntry = zip.getEntry(normalizedHtmlPath) ?: return null
        val htmlText = zip.getInputStream(htmlEntry).readBytes().toString(Charsets.UTF_8)

        // 4) Find first <img ... src="...">
        val imgSrc = extractFirstImgSrc(htmlText) ?: return null

        // 5) Resolve relative path against the HTML file’s directory
        val htmlDir = normalizedHtmlPath.substringBeforeLast('/', missingDelimiterValue = "")
        val imgResolved = resolveZipPath(htmlDir, imgSrc) ?: return null

        // Convert to OPF-relative href (because makeCoverThumb resolves against opfDir)
        val opfPrefix = if (opfDir.isBlank()) "" else "$opfDir/"
        return imgResolved.removePrefix(opfPrefix)

    }

    private fun extractFirstImgSrc(html: String): String? {
        // Very simple, good enough for Gutenberg: src="..."
        val regex = Regex("""<img[^>]+src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val match = regex.find(html) ?: return null
        return match.groupValues.getOrNull(1)
            ?.substringBefore('#')
            ?.substringBefore('?')
            ?.trim()
            ?.trimStart('/')
            ?.takeIf { it.isNotBlank() }
    }

}
