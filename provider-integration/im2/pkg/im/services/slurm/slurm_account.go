package slurm

import (
	"ucloud.dk/pkg/apm"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	slurmcli "ucloud.dk/pkg/im/external/slurm"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

type AccountingService interface {
	OnWalletUpdated(update *ctrl.NotificationWalletUpdated)
	FetchUsageInMinutes() map[SlurmAccountOwner]int64
}

type AccountMapperService interface {
	ServerEvaluateAccountMapper(category string, owner apm.WalletOwner) (util.Option[string], error)
	ServerSlurmJobToConfiguration(job *slurmcli.Job) util.Option[SlurmJobConfiguration]
	ServerListAccountsForWorkspace(workspace apm.WalletOwner) map[string]string
	ServerLookupOwnerOfAccount(account string) []SlurmAccountOwner

	UCloudConfigurationFindSlurmAccount(config SlurmJobConfiguration) []string
}

var Accounting AccountingService = nil
var AccountMapper AccountMapperService = nil

func InitAccountManagement() {
	if cfg.Mode == cfg.ServerModeServer {
		switch ServiceConfig.Compute.AccountManagement.Accounting.Type {
		case cfg.SlurmAccountingTypeAutomatic:
			Accounting = InitAutomaticAccountManagement()
		case cfg.SlurmAccountingTypeNone:
			Accounting = InitUnmanagedAccountManagement()
		case cfg.SlurmAccountingTypeScripted:
			Accounting = InitScriptedAccountManagement()
		}
	}
	switch ServiceConfig.Compute.AccountManagement.AccountMapper.Type {
	case cfg.SlurmAccountMapperTypeNone:
		// TODO This might need to have its own special account mapper to be useful. In particular being able to reverse
		//  map a Slurm account could be tricky without a custom implementation in place.
		fallthrough
	case cfg.SlurmAccountMapperTypePattern:
		fallthrough
	case cfg.SlurmAccountMapperTypeScripted:
		AccountMapper = InitDefaultAccountMapper()
	}
}

type SlurmJobConfiguration struct {
	UCloudUsername     string
	Owner              apm.WalletOwner
	EstimatedProduct   apm.ProductReference
	EstimatedNodeCount int
	Job                util.Option[*orc.Job]
}

type SlurmAccountOwner struct {
	AssociatedWithCategory string
	Owner                  apm.WalletOwner
}
