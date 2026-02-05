package com.example.blitzread.engine.readium

import android.app.Application
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File


class ReadiumReaderOpener(
    private val application: Application
) {
    private val httpClient = DefaultHttpClient()

    private val assetRetriever =
        AssetRetriever(application.contentResolver, httpClient)

    private val publicationParser =
        DefaultPublicationParser(application, httpClient, assetRetriever, null)

    private val publicationOpener =
        PublicationOpener(publicationParser)

    suspend fun openLocalFile(file: File): Try<Publication, Any> {

        val assetTry = assetRetriever.retrieve(file)

        val asset = when (assetTry) {
            is Try.Success -> assetTry.value
            is Try.Failure -> return Try.failure(assetTry.value)
        }

        val pubTry = publicationOpener.open(asset, allowUserInteraction = false)

        val publication = when (pubTry) {
            is Try.Success -> pubTry.value
            is Try.Failure -> {
                // Close asset if opener failed
                try { asset.close() } catch (_: Exception) {}
                return Try.failure(pubTry.value)
            }
        }

        return Try.success(publication)
    }

}

