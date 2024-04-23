package felis.dam

import org.gradle.api.Project
import java.io.File
import javax.inject.Inject

abstract class LibraryFetcher {
    @get:Inject
    abstract val project: Project
    private val librariesRoot by lazy {
        val libDir = project.gradle.gradleUserHomeDir
            .resolve("caches")
            .resolve("loader-make")
            .resolve("libs")
        libDir.mkdirs()
        val res = this.project.objects.directoryProperty()
        res.set(libDir)
        res
    }

    fun installLibs() {
        val version = project.extensions.getByType(FelisDamPlugin.Extension::class.java).version
        val root = this.project.objects.directoryProperty()
        root.set(this.librariesRoot)
        val versionMeta = FelisDamPlugin.piston.getVersion(version)
        val libs = versionMeta.libraries
        for (libId in libs.map(Library::name)) {
            this.project.dependencies.add("implementation", libId)
        }
    }
}