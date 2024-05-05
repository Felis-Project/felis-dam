package felis.dam

import io.github.joemama.atr.JarRemapper
import io.github.joemama.atr.ProguardMappings
import org.gradle.api.Project
import java.io.File
import java.util.jar.JarFile
import javax.inject.Inject

open class GameJars @Inject constructor(@Inject private val project: Project) {
    data class Jars(val client: File, val server: File)

    private val versionId by lazy { project.extensions.getByType(FelisDamPlugin.Extension::class.java).version }
    private val version by lazy {
        FelisDamPlugin.piston.getVersion(this.versionId)
    }

    private val versionDir by lazy {
        val dir = project.extensions.getByType(FelisDamPlugin.Extension::class.java).userCache
            .resolve("jars")
            .resolve(this.versionId)
            .toFile()
        dir.mkdirs()
        dir
    }

    private val mappingsDir by lazy {
        val dir = project.extensions.getByType(FelisDamPlugin.Extension::class.java)
            .userCache
            .resolve("mappings")
            .resolve(this.versionId)
            .toFile()
        dir.mkdirs()
        dir
    }

    data class JarResult(val jars: Jars, val merged: File)

    fun prepare(): JarResult {
        val jars = fetchJars()
        val mapped = remapJars(jars)
        val merger = project.objects.newInstance(JarMerger::class.java)
        val merged = merger.merge(mapped.client, mapped.server)

        return JarResult(jars, merged)
    }

    private fun fetchJars(): Jars {
        val client = fetchFile(version.downloads.client.url, this.versionDir.resolve("$versionId-client.jar")).join()
        val server =
            fetchFile(version.downloads.server.url, this.versionDir.resolve("$versionId-server-bundle.jar")).join()
        return Jars(client, this.extractServerJar(server))
    }

    private fun extractServerJar(server: File): File {
        val serverJar = server.parentFile.resolve("$versionId-server.jar")
        if (serverJar.exists()) return serverJar

        println("Extracting server jar ${server.path} to ${serverJar.path}")
        val bundle = JarFile(server)
        val versionPath = bundle.getJarEntry("META-INF/versions.list").let { bundle.getInputStream(it) }.use {
            it.reader().readText()
        }.split(Regex("\\s+"))[2] // <hash> <versionId> <path>

        println("Found version $versionPath")
        serverJar.outputStream().use { out ->
            bundle.getJarEntry("META-INF/versions/$versionPath").let { bundle.getInputStream(it) }.use {
                out.write(it.readAllBytes())
            }
        }

        return serverJar
    }

    private fun remapJars(jars: Jars): Jars {
        val clientMaps = fetchFile(
            version.downloads.clientMappings.url,
            this.mappingsDir.resolve("$versionId-client.txt")
        ).join()
        val serverMaps = fetchFile(
            version.downloads.serverMappings.url,
            this.mappingsDir.resolve("$versionId-server.txt")
        ).join()
        val remappedClient = jars.client.resolveSibling(jars.client.nameWithoutExtension + "-mapped.jar")
        val remappedServer = jars.server.resolveSibling(jars.server.nameWithoutExtension + "-mapped.jar")
        val client = FelisDamPlugin.taskExecutor.submit {
            if (remappedClient.exists()) return@submit
            println("Remapping ${jars.client.path} to ${remappedClient.path} using ${clientMaps.path}")
            JarRemapper(jars.client.toPath()).remap(ProguardMappings(clientMaps.readText()), remappedClient.toPath())
        }
        val server = FelisDamPlugin.taskExecutor.submit {
            if (remappedServer.exists()) return@submit
            println("Remapping ${jars.server.path} to ${remappedServer.path} using ${serverMaps.path}")
            JarRemapper(jars.server.toPath()).remap(ProguardMappings(serverMaps.readText()), remappedServer.toPath())
        }
        client.get()
        server.get()
        return Jars(remappedClient, remappedServer)
    }
}