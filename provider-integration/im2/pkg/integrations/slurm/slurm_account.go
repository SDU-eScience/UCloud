package slurm

import (
	"ucloud.dk/pkg/config"
	ctrl "ucloud.dk/pkg/controller"
	slurmcli "ucloud.dk/pkg/external/slurm"
	apm "ucloud.dk/shared/pkg/accounting"
	orc "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/util"
)

type AccountingService interface {
	OnWalletUpdated(update *ctrl.EventWalletUpdated)
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
	if config.Mode == config.ServerModeServer {
		switch ServiceConfig.Compute.AccountManagement.Accounting.Type {
		case config.SlurmAccountingTypeAutomatic:
			Accounting = InitAutomaticAccountManagement()
		case config.SlurmAccountingTypeNone:
			Accounting = InitUnmanagedAccountManagement()
		case config.SlurmAccountingTypeScripted:
			Accounting = InitScriptedAccountManagement()
		}
	}
	switch ServiceConfig.Compute.AccountManagement.AccountMapper.Type {
	case config.SlurmAccountMapperTypeNone:
		// TODO This might need to have its own special account mapper to be useful. In particular being able to reverse
		//  map a Slurm account could be tricky without a custom implementation in place.
		fallthrough
	case config.SlurmAccountMapperTypePattern:
		fallthrough
	case config.SlurmAccountMapperTypeScripted:
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
