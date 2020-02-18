package dk.sdu.cloud.k8

class YamlResource(private val document: String) : KubernetesResource {
    override val phase = DeploymentPhase.MIGRATE
    override fun DeploymentContext.isUpToDate(): Boolean = false

    override fun DeploymentContext.create() {
        client.load(document.byteInputStream()).createOrReplace()
    }

    override fun DeploymentContext.delete() {
        client.load(document.byteInputStream()).delete()
    }

    override fun toString() = "YamlResource()"
}
