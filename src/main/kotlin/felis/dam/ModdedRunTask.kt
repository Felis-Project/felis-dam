package felis.dam

import org.apache.tools.ant.taskdefs.condition.Os
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

    init {
        mainClass.set("felis.MainKt")
        classpath = project.objects.fileCollection().also { obs ->
            val modRuntime = project.extensions.getByType(FelisDamPlugin.Extension::class.java).modRuntime
            obs.from(modRuntime)
        }
        mods.convention(project.provider {
            project.configurations.getByName("considerMod").resolve().map { it.toPath() }
        })
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
        super.exec()
    }
}
