package felis.dam

import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.Project
import javax.inject.Inject

abstract class LibraryFetcher {
    @get:Inject
    abstract val project: Project
    fun installLibs() {
        val version = project.extensions.getByType(FelisDamPlugin.Extension::class.java).version
        val versionMeta = FelisDamPlugin.piston.getVersion(version)
        val libs = versionMeta.libraries
        println("Downloading libraries for ${System.getProperty("os.name")}")
        for (libId in libs
            .filter { lib ->
                lib.rules.all {
                    it.action == "allow" && it.os.name.let { osTarget ->
                        (Os.isFamily(Os.FAMILY_WINDOWS) && osTarget.contains("windows")) ||
                                (Os.isFamily(Os.FAMILY_MAC) && osTarget.contains("osx")) ||
                                osTarget.contains("linux")
                    }
                }
            }
            .map(Library::name)
        ) {
            this.project.dependencies.add("implementation", libId)
        }
    }
}