package felis.dam

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault
abstract class DownloadAssetsTask : DefaultTask() {
    @get:InputDirectory
    abstract val assetDir: DirectoryProperty

    @get:Input
    abstract val minecraftVersion: Property<String>

    @TaskAction
    fun downloadAssets() {
        val assetDir = this.assetDir.get().asFile
        assetDir.mkdirs()
        val indices = assetDir.resolve("indexes")
        indices.mkdirs()
        val assetIndexMeta = FelisDamPlugin.piston.getVersion(this.minecraftVersion.get()).assetIndex
        val assetIndexFile =
            fetchFile(assetIndexMeta.url, indices.resolve(assetIndexMeta.id + ".json")).join()
        val assetIndex = FelisDamPlugin.json.decodeFromString<AssetIndex>(assetIndexFile.readText())
        val objects = assetDir.resolve("objects")
        assetIndex.objects.values.sortedBy { it.size }.chunked(20).flatMap { it ->
            it.map {
                fetchFile(it.url, objects.resolve(it.path))
            }.map { it.join() }
        }
    }
}