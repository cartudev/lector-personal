package dev.cartu.lector

import android.content.Context
import java.io.File
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

internal data class OpenedEpub(
    val publication: Publication,
    val navigatorFactory: EpubNavigatorFactory,
)

internal class ReadiumPublicationOpener(private val context: Context) {
    suspend fun open(file: File): Result<OpenedEpub> = runCatching {
        val httpClient = DefaultHttpClient()
        val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
        val opener = PublicationOpener(
            publicationParser = DefaultPublicationParser(
                context = context,
                httpClient = httpClient,
                assetRetriever = assetRetriever,
                pdfFactory = null
            )
        )
        val asset = assetRetriever.retrieve(file.toUrl(isDirectory = false)).getOrElse {
            error("No se pudo leer el archivo: $it")
        }
        val publication = opener.open(asset, allowUserInteraction = false).getOrElse {
            error("El archivo no es un EPUB compatible: $it")
        }
        OpenedEpub(publication, EpubNavigatorFactory(publication))
    }
}
