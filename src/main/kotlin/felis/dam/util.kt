package felis.dam

import java.io.File
import java.io.InputStream
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture

fun fetchFile(url: String, file: File, checkExistance: Boolean = true): CompletableFuture<File> =
    if (checkExistance && file.exists()) {
        CompletableFuture.completedFuture(file)
    } else {
        file.parentFile.mkdirs()
        file.createNewFile()
        println("Downloading $url")
        FelisDamPlugin.httpClient.sendAsync(
            HttpRequest.newBuilder(URI.create(url)).GET().build(),
            HttpResponse.BodyHandlers.ofInputStream()
        ).thenApply(HttpResponse<InputStream>::body).thenApply {
            file.outputStream().use { out -> out.write(it.readAllBytes()) }
            file
        }
    }