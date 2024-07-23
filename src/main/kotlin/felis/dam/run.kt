package felis.dam

import org.gradle.api.Project

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
    fun gradleTask() {
        project.tasks.register("run${this.name}", ModdedRunTask::class.java) { task ->
            task.args(*this.args.value.toTypedArray())
            task.group = "minecraft"
            task.side.set(this.side)
            task.dependsOn(*this.taskDependencies.toTypedArray(), project.getTasksByName("build", false))
        }
    }
}