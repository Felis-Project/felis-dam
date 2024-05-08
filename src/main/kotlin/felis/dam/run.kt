package felis.dam

import org.gradle.api.Project
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
    data class Classpaths(val loading: List<File>, val mods: List<File>, val thisProject: File)

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
        project.tasks.register("run${this.name}", ModdedRunTask::class.java) { task ->
            task.args(*this.args.value.toTypedArray())
            task.group = "minecraft"
            val cps = createClasspaths(project)
            task.mods.set((cps.mods + cps.thisProject).map { it.toPath() })
            task.side.set(this.side)
            task.dependsOn(*this.taskDependencies.toTypedArray())
        }
    }
}