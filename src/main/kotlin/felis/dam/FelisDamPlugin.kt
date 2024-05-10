package felis.dam

import kotlinx.serialization.json.Json
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactRepositoryContainer
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.Directory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.provider.Provider
import org.jetbrains.gradle.ext.IdeaExtPlugin
import org.jetbrains.kotlin.gradle.plugin.*
import java.io.File
import java.net.http.HttpClient
import java.nio.file.Path
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlin.io.path.pathString

class FelisDamPlugin : Plugin<Project> {
    companion object {
        val taskExecutor: ExecutorService =
            Executors.newWorkStealingPool(Runtime.getRuntime().availableProcessors())
        val httpClient: HttpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .executor(taskExecutor)
            .build()
        val json: Json = Json {
            ignoreUnknownKeys = true
        }
        lateinit var piston: Piston
            private set
    }

    abstract class Extension(@Inject private val project: Project, @Inject private val objects: ObjectFactory) {
        val accessWideners = hashSetOf<Path>()
        var version = "1.20.4"
        val gameJars: GameJars.JarResult by lazy { objects.newInstance(GameJars::class.java).prepare() }

        val libs: LibraryFetcher by lazy { objects.newInstance(LibraryFetcher::class.java) }

        val userCache: Path by lazy {
            project.gradle.gradleUserHomeDir
                .resolve("caches")
                .resolve("loader-make")
                .toPath()
        }

        val modRuntime: Configuration by lazy {
            project.configurations.create("modRuntime") {
                val runtimeOnly = project.configurations.getByName("runtimeClasspath")
                it.extendsFrom(runtimeOnly)
                it.isCanBeResolved = true
                it.isCanBeConsumed = false
            }
        }

        val transformedJars: Provider<Directory> by lazy {
            project.layout.buildDirectory.dir("transformed")
        }

        fun accessWidener(file: File) = this.accessWideners.add(file.toPath())
    }

    override fun apply(project: Project) {
        piston = project.objects.newInstance(Piston::class.java)
        val ext = project.extensions.create("loaderMake", Extension::class.java)
        project.repositories.apply {
            maven {
                it.url = project.uri("https://libraries.minecraft.net/")
                it.name = "Mojang"
                it.metadataSources { meta ->
                    meta.mavenPom()
                    meta.artifact()
                    meta.ignoreGradleMetadataRedirection()
                }
                // allow downloading sources/javadocs from maven central
                it.artifactUrls(ArtifactRepositoryContainer.MAVEN_CENTRAL_URL)
            }
            maven {
                it.url = project.uri("https://repo.repsy.io/mvn/0xjoemama/public")
                it.name = "Loader Repo"
            }
            maven {
                it.url = project.uri("https://stianloader.org/maven/")
                it.name = "Stianloader"
            }
            mavenCentral()
        }

        project.buildscript.apply {
            repositories.apply {
                gradlePluginPortal()
                mavenCentral()
            }
        }

        project.plugins.apply {
            apply(JavaLibraryPlugin::class.java)
            apply(IdeaExtPlugin::class.java)
            apply(KotlinPluginWrapper::class.java)
        }

        val downloadAssetsTask = project.tasks.register("downloadAssets", DownloadAssetsTask::class.java) {
            it.group = "minecraft"
            it.minecraftVersion.set(ext.version)
            it.assetDir.set(ext.userCache
                .resolve("assets")
                .let(Path::toFile)
                .apply { mkdirs() }
            )
        }

        // TODO: Allow defining custom run configurations
        val clientRun = ModRun(
            name = "Client",
            project = project,
            side = Side.CLIENT,
            args = lazy {
                listOf(
                    "--accessToken", "0",
                    "--version", "${ext.version}-Felis",
                    "--gameDir", "run",
                    "--assetsDir", downloadAssetsTask.get().assetDir.get().asFile.path,
                    "--assetIndex", piston.getVersion(ext.version).assetIndex.id
                )
            },
            taskDependencies = listOf("downloadAssets")
        )

        val serverRun = ModRun(
            name = "Server",
            project = project,
            side = Side.SERVER,
            args = lazy { listOf("nogui") }
        )

        clientRun.gradleTask()
        serverRun.gradleTask()

        project.tasks.register("genSources", GenSourcesTask::class.java) {
            it.group = "minecraft"
            it.inputJar.set(ext.gameJars.merged)
            it.outputJar.set(ext.gameJars.merged.parentFile.resolve(ext.gameJars.merged.nameWithoutExtension + "-sources.jar"))
        }

        project.tasks.register("applyTransformations", ModdedRunTask::class.java) {
            it.group = "minecraft"
            it.jvmArgs("-Dfelis.audit=${ext.transformedJars.get().file("${ext.version}-transformed.jar")}")
            it.jvmArgs(
                "-Dfelis.access.wideners=${
                    ext.accessWideners.joinToString(
                        separator = File.pathSeparator,
                        transform = Path::pathString
                    )
                }"
            )
            it.side.set(Side.SERVER)
            it.mods.set(ModRun.createClasspaths(project).mods.map(File::toPath))
        }

        project.configurations.maybeCreate("considerMod").apply {
            project.configurations.getByName("compileOnly").extendsFrom(this)
            isTransitive = false
            isCanBeResolved = true
            isCanBeConsumed = false
        }

        project.afterEvaluate {
            ext.libs.installLibs()
            project.dependencies.add("runtimeOnly", project.files(ext.gameJars.merged))
            if (ext.transformedJars.get().asFileTree.isEmpty) {
                project.dependencies.add("compileOnly", project.files(ext.gameJars.merged))
            } else {
                project.dependencies.add("compileOnly", ext.transformedJars.get().asFileTree)
            }
        }
    }
}