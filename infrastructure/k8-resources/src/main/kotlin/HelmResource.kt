package dk.sdu.cloud.k8

import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.ObjectMeta
import java.io.File
import java.nio.file.Files

data class HelmRepo(val name: String, val url: String)

class HelmResource(
    var name: String,
    var namespace: String,
    var chart: String,
    var version: String
) : KubernetesResource {
    override val phase: DeploymentPhase = DeploymentPhase.DEPLOY
    var chartVersion: String? = null

    private val cm = ConfigMapResource("$name-helm-version", version)
    var onCreate: (HelmResource.() -> Unit)? = null
    val values = HashMap<String, Any?>()
    var valuesAsString: String? = null

    var repo = HelmRepo("stable", "https://kubernetes-charts.storage.googleapis.com")

    override fun DeploymentContext.isUpToDate(): Boolean {
        return with(cm) { isUpToDate() }
    }

    override fun DeploymentContext.create() {
        onCreate?.invoke(this@HelmResource)

        val repo = repo
        Helm.addRepo(repo.name, repo.url)
        Helm.updateRepo()

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

        client.namespaces().createOrReplace(Namespace().apply {
            metadata = ObjectMeta().apply {
                name = this@HelmResource.namespace
            }
        })

        Helm.installChart(
            environment.toLowerCase(),
            name,
            this@HelmResource.namespace,
            chart.removePrefix(repo.name + "/").let { "${repo.name}/${it}" },
            valueFiles,
            chartVersion
        )
        with(cm) { create() }
    }

    override fun DeploymentContext.delete() {
        Helm.delete(environment.toLowerCase(), name)
        with(cm) { delete() }
    }

    override fun toString() = "HelmResource($name, $version, $chart)"
}

fun MutableBundle.withHelmChart(
    chart: String,
    name: String = this.name,
    version: String = this.version,
    namespace: String = name,
    onCreate: HelmResource.() -> Unit
): HelmResource {
    return HelmResource(name, namespace, chart, version).apply {
        this.onCreate = onCreate
        resources.add(this)
    }
}

object Helm {
    fun addRepo(
        repoName: String,
        repoUrl: String
    ) {
        println("Adding Helm repository: $repoName $repoUrl")
        Process.runAndPrint(
            "helm",
            "repo",
            "add",
            repoName,
            repoUrl
        )
    }

    fun updateRepo() {
        println("Updating local Helm repository")
        Process.runAndPrint(
            "helm",
            "repo",
            "update"
        )
    }

    fun installChart(
        kubeContext: String,
        name: String,
        namespace: String,
        chart: String,
        valueFiles: List<File>,
        chartVersion: String?
    ) {
        val (stdout, stderr, status) = Process.runAndCollect(
            *ArrayList<String>().apply {
                addAll(
                    listOf(
                        "helm",
                        "--kube-context",
                        kubeContext,
                        "upgrade",
                        "--install"
                    )
                )

                add(name)
                add(chart)

                addAll(
                    valueFiles.flatMap { listOf("--values", it.absolutePath) }
                )

                if (chartVersion != null) {
                    addAll(
                        listOf(
                            "--version",
                            chartVersion
                        )
                    )
                }

                add("--namespace")
                add(namespace)
            }.toTypedArray()
        )

        System.err.println(stderr)
        println(stdout)

        if (status != 0) {
            println("HELM INSTALL FAILED FOR $name $namespace $chart")
            throw IllegalStateException("Helm install failed: $name")
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
