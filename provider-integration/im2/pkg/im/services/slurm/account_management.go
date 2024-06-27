package slurm

import (
	"ucloud.dk/pkg/apm"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	slurmcli "ucloud.dk/pkg/im/slurm"
	"ucloud.dk/pkg/util"
)

type AccountingService interface {
	OnWalletUpdated(update *ctrl.NotificationWalletUpdated)
}

type AccountMapperService interface {
	ServerEvaluateAccountMapper(category string, owner apm.WalletOwner) (string, bool)
	ServerSlurmJobToConfiguration(job *slurmcli.Job) util.Option[SlurmJobConfiguration]
	ServerListAccountsForWorkspace(workspace apm.WalletOwner) map[string]string
	ServerLookupOwnerOfAccount(account string) []SlurmAccountOwner

	UCloudConfigurationFindSlurmAccount(config SlurmJobConfiguration) []string
}

var Accounting AccountingService = nil
var AccountMapper AccountMapperService = nil

func InitAccountManagement() {
	if cfg.Mode == cfg.ServerModeServer {
		Accounting = InitAutomaticAccountManagement()
	}
	AccountMapper = InitDefaultAccountMapper()
}

type SlurmJobConfiguration struct {
	Owner              apm.WalletOwner
	EstimatedProduct   apm.ProductReference
	EstimatedNodeCount int
}

type SlurmAccountOwner struct {
	AssociatedWithCategory string
	Owner                  apm.WalletOwner
}
