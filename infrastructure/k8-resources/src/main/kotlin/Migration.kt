package dk.sdu.cloud.k8

/**
 * A migration style job for service-common's database migration (`--run-script migrate-db`)
 */
class Migration(
    deployment: DeploymentResource,
    args: List<String>,
    migrationSuffix: String,
    name: String = deployment.name,
    version: String = deployment.version
) : AdHocJob(deployment, { args }, migrationSuffix, name, version) {
    override val phase: DeploymentPhase = DeploymentPhase.MIGRATE

    override fun toString(): String = "Migration($name, $version)"
}

fun MutableBundle.withMigration(
    deployment: DeploymentResource,
    args: List<String>,
    migrationSuffix: String,
    name: String = deployment.name,
    version: String = deployment.version,
    init: Migration.() -> Unit = {}
): Migration {
    val migration = Migration(deployment, args, migrationSuffix, name, version).apply(init)
    resources.add(migration)
    return migration
}
