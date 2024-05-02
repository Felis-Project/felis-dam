package felis.dam

import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.kotlin.konan.file.File

@DisableCachingByDefault(because = "we want users to be able to apply transformations at any point")
abstract class ApplyTransformationsTask : JavaExec() {
    private val cps = ModRun.createClasspaths(project)

    init {
        mainClass.set("felis.MainKt")
        classpath = project.objects.fileCollection().also { obs -> obs.from(cps.loading) }
    }

    @get:Internal
    abstract val auditJar: RegularFileProperty

    @TaskAction
    override fun exec() {
        val ext = project.extensions.getByType(FelisDamPlugin.Extension::class.java)

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
        jvmArgs("-Dlog4j.configurationFile=${loggerCfgFile.get().asFile.path}")

        args(
            "--mods", cps.mods.joinToString(File.pathSeparator) { it.path },
            "--source", ext.gameJars.merged.path,
            "--side", Side.CLIENT,
            "--audit", this.auditJar.get().asFile.path
        )
        super.exec()
    }
}