package migrations

import (
	"ucloud.dk/shared/pkg/audit"
	db "ucloud.dk/shared/pkg/database"
)

func Init() {
	for _, migration := range migrationScripts() {
		db.AddMigration(migration)
	}
}

func migrationScripts() []db.MigrationScript {
	return []db.MigrationScript{
		coreV1(),
		projectsV1(),
		projectsV2(),
		sharesV1(),
		usageV1(),
		usageV2(),
		accountingV1(),
		accountingV2(),
		authV1(),
		audit.MigrationV1(),
		apiTokensV1(),
		accountingV3(),
		authV2(),
		newsV1(),
		coreV2(),
		accountingV4(),
		projectsV3(),
		audit.MigrationV2(),
		authV3(),
		grantV1(),
		projectsV4(),
		jobSettingsV1(),
		grantV2(),
		apiTokensV2(),
		apiTokensV3(),
		privateNetworksV1(),
		jobsV1(),
		resourcesV1(),
		stacksV1(),
		accountingV5(),
		grantV3(),
		grantV4(),
		projectsV5(),
		sshV1(),
		containerRepositoriesV1(),
		applicationVariantsV1(),
		tasksV1(),
		projectsV6(),
		customApplicationsV1(),
		applicationEditorV1(),
		customApplicationsV2(),
		customApplicationsV3(),
		customApplicationsV4(),
		featuresV1(),
		grantV5(),
		privateNetworksV2(),
		privateNetworkIpsV1(),
		privateNetworkIpsV2(),
		privateNetworksV3(),
		servicesV1(),
		stacksDeletionV1(),
		servicesV2(),
		privateNetworkIpsV3(),
		privateNetworksV4(),
		privateNetworksV5(),
		privateNetworksV6(),
		accountingV6(),
		accountingAllocationPackagesV1(),
		accountingModelV1(),
		jobsV2(),
	}
}
