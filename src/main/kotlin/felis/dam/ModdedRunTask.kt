package felis.dam

import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.nio.file.Path
import kotlin.io.path.pathString

@DisableCachingByDefault(because = "what are we even caching?")
abstract class ModdedRunTask : JavaExec() {
    @get:Input
    abstract val side: Property<Side>

    @get:Input
    abstract val mods: ListProperty<Path>

    @get:Input
    abstract val shouldIncludeSelf: Property<Boolean>

    init {
        shouldIncludeSelf.convention(true)
        mainClass.set("felis.MainKt")
    }

    @TaskAction
    override fun exec() {
        val loggerCfgFile = project.layout.buildDirectory.file("log4j2.xml")
        loggerCfgFile.get().asFile.apply {
            if (!exists()) {
                parentFile.mkdirs()
                FelisDamPlugin::class.java.classLoader
                    .getResourceAsStream("log4j2.xml")
                    ?.readAllBytes()
                    ?.let { writeBytes(it) }
            }
        }
        if (Os.isFamily(Os.FAMILY_MAC)) {
            jvmArgs("-XStartOnFirstThread")
        }
        jvmArgs(
            "-Dlog4j.configurationFile=${loggerCfgFile.get().asFile.path}",
            "-Dfelis.side=${this.side.get().name}",
            "-Dfelis.mods=${this.mods.get().joinToString(File.pathSeparator) { it.pathString }}"
        )
        if (this.shouldIncludeSelf.get()) {
            classpath =
                project.extensions.getByType(JavaPluginExtension::class.java).sourceSets.getByName("main").runtimeClasspath
        } else {
            classpath = project.extensions.getByType(FelisDamPlugin.Extension::class.java).modRuntime
        }
        super.exec()
    }
}
