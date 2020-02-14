package dk.sdu.cloud.k8

import java.io.File
import java.nio.file.Files

class HelmResource(
    var name: String,
    var namespace: String,
    var chart: String,
    var version: String
) : KubernetesResource {
    override val phase: DeploymentPhase = DeploymentPhase.MIGRATE

    private val cm = ConfigMapResource("$name-helm-version", version)
    var configure: (HelmResource.(ctx: DeploymentContext) -> Unit)? = null
    val values = HashMap<String, Any?>()
    var valuesAsString: String? = null

    override fun DeploymentContext.isUpToDate(): Boolean {
        return Helm.hasDeployment(environment.name.toLowerCase(), name) && with(cm) { isUpToDate() }
    }

    override fun DeploymentContext.create() {
        configure?.invoke(this@HelmResource, this)
        val valueFiles = ArrayList<File>()

        val valuesFile = Files.createTempFile("chart", ".yml").toFile()
        valuesFile.writeText(yamlMapper.writeValueAsString(values))
        valueFiles.add(valuesFile)

        val valAsString = valuesAsString
        if (valAsString != null) {
            val file = Files.createTempFile("chart", ".yml").toFile()
            file.writeText(valAsString)
            valueFiles.add(file)
        }

        Helm.installChart(environment.name.toLowerCase(), name, namespace, chart, valueFiles)
        with(cm) { create() }
    }

    override fun DeploymentContext.delete() {
        Helm.delete(environment.name.toLowerCase(), name)
        with(cm) { delete() }
    }

    override fun toString() = "HelmResource($name, $version, $chart)"
}

fun MutableBundle.withHelmChart(
    chart: String,
    name: String = this.name,
    version: String = this.version,
    namespace: String = name,
    init: HelmResource.(ctx: DeploymentContext) -> Unit
): HelmResource {
    return HelmResource(name, namespace, chart, version).apply {
        configure = init
        resources.add(this)
    }
}

object Helm {
    fun hasDeployment(
        kubeContext: String,
        name: String
    ): Boolean {
        return Process.runAndDiscard("helm", "--kube-context", kubeContext, "get", name) != 0
    }

    fun installChart(
        kubeContext: String,
        name: String,
        namespace: String,
        chart: String,
        valueFiles: List<File>
    ) {
        val (stdout, stderr, status) = Process.runAndCollect(
            *ArrayList<String>().apply {
                addAll(
                    listOf(
                        "helm",
                        "--kube-context",
                        kubeContext,
                        "install",
                        "--name",
                        name,
                        "--namespace",
                        namespace
                    )
                )

                addAll(
                    valueFiles.flatMap { listOf("--values", it.absolutePath) }
                )

                add(chart)
            }.toTypedArray()
        )

        System.err.println(stderr)
        println(stdout)

        if (status != 0) {
            println("HELM INSTALL FAILED FOR $name $namespace $chart")
        }
    }

    fun delete(
        kubeContext: String,
        name: String
    ) {
        val (stdout, stderr, status) = Process.runAndCollect(
            "helm",
            "--kube-context",
            kubeContext,
            "delete",
            "--purge",
            name
        )

        System.err.println(stderr)
        println(stdout)

        if (status != 0) {
            println("HELM DELETE FAILED FOR $name")
        }
    }
}
