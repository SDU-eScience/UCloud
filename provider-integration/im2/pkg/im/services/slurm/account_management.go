package slurm

import (
	"ucloud.dk/pkg/apm"
	ctrl "ucloud.dk/pkg/im/controller"
	slurmcli "ucloud.dk/pkg/im/slurm"
)

type AccountManagementService interface {
	UCloudConfigurationFindSlurmAccount(config SlurmJobConfiguration) []string
	SlurmJobToConfiguration(job slurmcli.Job)
	OnWalletUpdated(update *ctrl.NotificationWalletUpdated)
}

var AccountManagement AccountManagementService = nil

func InitAccountManagement() {
	// TODO set account management
	AccountManagement = InitAutomaticAccountManagement()
}

type SlurmJobConfiguration struct {
	Owner              apm.WalletOwner
	EstimatedProduct   apm.ProductReference
	EstimatedNodeCount int
}
