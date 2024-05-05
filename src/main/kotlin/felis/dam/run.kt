package felis.dam

import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.jvm.tasks.Jar
import java.io.File
import java.util.jar.JarFile

enum class Side {
    CLIENT, SERVER
}

data class ModRun(
    val project: Project,
    val name: String,
    val side: Side,
    val args: Lazy<List<String>> = lazy { emptyList() },
    val taskDependencies: List<String> = emptyList()
) {
    private val sourceJar by lazy { project.extensions.getByType(FelisDamPlugin.Extension::class.java).gameJars.merged }

    data class Classpaths(val loading: List<File>, val mods: List<File>, val thisProject: File) {
        val modPaths by lazy {
            (this.mods + this.thisProject).joinToString(File.pathSeparator) { it.path }
        }
    }

    companion object {
        fun createClasspaths(project: Project): Classpaths {
            val loading = mutableListOf<File>()
            val game = mutableListOf<File>()
            for (mod in project.extensions.getByType(FelisDamPlugin.Extension::class.java).modRuntime) {
                JarFile(mod).getJarEntry("mods.toml")?.let {
                    game.add(mod)
                } ?: loading.add(mod)
            }

            for (consideredMod in project.configurations.getByName("considerMod")) {
                game.add(consideredMod)
            }

            val jar = project.tasks.getByName("jar") as Jar
            return Classpaths(loading, game, jar.archiveFile.get().asFile)
        }
    }

    fun gradleTask() {
        project.tasks.register("run${this.name}", JavaExec::class.java) { it ->
            val loggerCfgFile = project.layout.buildDirectory.file("log4j2.xml")
            it.doFirst {
                loggerCfgFile.get().asFile.apply {
                    if (!exists()) {
                        parentFile.mkdirs()
                        FelisDamPlugin::class.java.classLoader.getResourceAsStream("log4j2.xml")?.readAllBytes()
                            ?.let {
                                writeBytes(it)
                            }
                    }
                }
            }

            it.dependsOn("jar")
            this.taskDependencies.forEach(it::dependsOn)

            it.group = "minecraft"
            val cps = createClasspaths(project)
            it.mainClass.set("felis.MainKt")
            it.classpath = project.objects.fileCollection().also {
                it.from(cps.loading)
            }

            if (Os.isFamily(Os.FAMILY_MAC)) {
                it.jvmArgs("-XStartOnFirstThread")
            }

            it.jvmArgs("-Dlog4j.configurationFile=${loggerCfgFile.get().asFile.path}")
            it.args(
                "--mods", cps.modPaths,
                "--source", this.sourceJar.path,
                "--side", this.side.name,
                "--", *this.args.value.toTypedArray(),
            )
        }
    }
}