package dk.sdu.cloud.k8

/**
 * A migration style job for service-common's database migration (`--run-script migrate-db`)
 */
class PsqlMigration(
    deployment: DeploymentResource,
    name: String = deployment.name,
    version: String = deployment.version
) : AdHocJob(deployment, { listOf("--run-script", "migrate-db") }, "migration", name, version) {
    override val phase: DeploymentPhase = DeploymentPhase.MIGRATE

    override fun toString(): String = "PsqlMigration($name, $version)"
}

fun MutableBundle.withPostgresMigration(
    deployment: DeploymentResource,
    name: String = deployment.name,
    version: String = deployment.version,
    init: PsqlMigration.() -> Unit = {}
): PsqlMigration {
    val psqlMigration = PsqlMigration(deployment, name, version).apply(init)
    resources.add(psqlMigration)
    return psqlMigration
}
