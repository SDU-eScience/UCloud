package slurm

import (
	"slices"

	"ucloud.dk/pkg/apm"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/log"
	"ucloud.dk/pkg/util"
)

func InitScriptedAccountManagement() AccountingService {
	config := ServiceConfig.Compute.AccountManagement.Accounting.Scripted()

	service := &scriptedAccountManagementService{}
	service.onQuotaUpdated.Script = config.OnQuotaUpdated
	service.onUsageReporting.Script = config.OnUsageReporting
	service.onProjectUpdated.Script = config.OnProjectUpdated

	ctrl.OnConnectionComplete(func(username string, uid uint32) {
		// Do nothing
	})

	ctrl.OnProjectNotification(func(update *ctrl.NotificationProjectUpdated) {
		service.synchronizeProjectToSlurmAccount(&update.Project, &update.ProjectComparison)
	})

	return service
}

type scriptedSlurmAccount struct {
	AccountName  string              `json:"accountName"`
	Partition    string              `json:"partition"`
	QoS          util.Option[string] `json:"qos"`
	CategoryName string              `json:"categoryName"`
}

type scriptedSlurmAccountProjectMember struct {
	Uid            uint32                       `json:"uid"`
	UCloudUsername string                       `json:"ucloudUsername"`
	Role           util.Option[apm.ProjectRole] `json:"role"`
}

type scriptedSlurmAccProjectUpdated struct {
	Account                   scriptedSlurmAccount
	AllMembers                []scriptedSlurmAccountProjectMember
	MembersAddedToProject     []scriptedSlurmAccountProjectMember
	MembersRemovedFromProject []scriptedSlurmAccountProjectMember
}

type scriptedSlurmAccQuotaUpdated struct {
	Account     scriptedSlurmAccount `json:"account"`
	QuotaUpdate struct {
		CombinedQuotaInResourceMinutes uint64 `json:"combinedQuotaInResourceMinutes"`
		Locked                         bool   `json:"locked"`
	} `json:"quotaUpdate"`
}

type scriptedSlurmAccUsage struct {
	Account                string `json:"account"`
	UsageInResourceMinutes uint64 `json:"usageInResourceMinutes"`
}

type scriptedAccountManagementService struct {
	onQuotaUpdated   ctrl.Script[scriptedSlurmAccQuotaUpdated, util.Empty]
	onUsageReporting ctrl.Script[util.Empty, struct {
		Usage []scriptedSlurmAccUsage `json:"usage"`
	}]
	onProjectUpdated ctrl.Script[scriptedSlurmAccProjectUpdated, util.Empty]
}

func (u *scriptedAccountManagementService) OnWalletUpdated(update *ctrl.NotificationWalletUpdated) {
	log.Info("slurm account wallet update %v %v", update.Category.Name, update.CombinedQuota)
	machineConfig, ok := ServiceConfig.Compute.Machines[update.Category.Name]
	if !ok {
		return
	}

	accountName, err := AccountMapper.ServerEvaluateAccountMapper(update.Category.Name, update.Owner)
	if err != nil || !accountName.Present {
		return
	}

	// The ordering on these are a bit weird. Normally, we want to handle the identity notification before the
	// accounting notification. But in this case we have a weird circular dependency in the identity notifications
	// requiring that parts of the accounting handler has already run (namely the account mapper). So we trigger the
	// account mapper first and then re-trigger the identity notifications.
	if update.Owner.Type == apm.WalletOwnerTypeProject {
		project := update.Project.Get()
		u.synchronizeProjectToSlurmAccount(&project, nil)
	}

	// TODO Make sure Slurm service only accepts resource as a unit
	multiplier := uint64(1)
	switch machineConfig.Payment.Interval {
	case cfg.PaymentIntervalMinutely:
		multiplier = 1
	case cfg.PaymentIntervalHourly:
		multiplier = 60
	case cfg.PaymentIntervalDaily:
		multiplier = 60 * 24
	}
	quotaInMinutes := update.CombinedQuota * multiplier

	req := scriptedSlurmAccQuotaUpdated{
		Account: scriptedSlurmAccount{
			AccountName:  accountName.Get(),
			Partition:    machineConfig.Partition,
			QoS:          machineConfig.Qos,
			CategoryName: update.Category.Name,
		},
	}
	req.QuotaUpdate.Locked = update.Locked
	req.QuotaUpdate.CombinedQuotaInResourceMinutes = quotaInMinutes

	u.onQuotaUpdated.Invoke(req)
}

func (u *scriptedAccountManagementService) FetchUsageInMinutes() map[SlurmAccountOwner]int64 {
	result := map[SlurmAccountOwner]int64{}
	resp, _ := u.onUsageReporting.Invoke(util.EmptyValue)

	for _, row := range resp.Usage {
		owners := AccountMapper.ServerLookupOwnerOfAccount(row.Account)
		for _, owner := range owners {
			result[owner] = int64(row.UsageInResourceMinutes)
		}
	}

	return result
}

func (u *scriptedAccountManagementService) synchronizeProjectToSlurmAccount(project *apm.Project, comparison *ctrl.ProjectComparison) {
	categoriesToAccountName := AccountMapper.ServerListAccountsForWorkspace(apm.WalletOwnerProject(project.Id))

	for categoryName, accountName := range categoriesToAccountName {
		machineConfig, ok := ServiceConfig.Compute.Machines[categoryName]
		if !ok {
			continue
		}

		req := scriptedSlurmAccProjectUpdated{}
		req.Account = scriptedSlurmAccount{
			AccountName:  accountName,
			Partition:    machineConfig.Partition,
			QoS:          machineConfig.Qos,
			CategoryName: categoryName,
		}

		var membersAddedToProject []string
		var membersRemovedFromProject []string
		if comparison != nil {
			membersAddedToProject = comparison.MembersAddedToProject
			membersRemovedFromProject = comparison.MembersRemovedFromProject
		} else {
			for _, member := range project.Status.Members {
				membersAddedToProject = append(membersAddedToProject, member.Username)
			}
		}

		for _, member := range project.Status.Members {
			wasAdded := slices.Contains(membersAddedToProject, member.Username)
			uid, ok, _ := ctrl.MapUCloudToLocal(member.Username)
			if !ok {
				continue
			}

			projectMember := scriptedSlurmAccountProjectMember{
				Uid:            uid,
				UCloudUsername: member.Username,
				Role:           util.OptValue(member.Role),
			}

			req.AllMembers = append(req.AllMembers, projectMember)
			if wasAdded {
				req.MembersAddedToProject = append(req.MembersAddedToProject, projectMember)
			}
		}

		for _, member := range membersRemovedFromProject {
			uid, ok, _ := ctrl.MapUCloudToLocal(member)
			if !ok {
				continue
			}

			req.MembersRemovedFromProject = append(req.MembersRemovedFromProject, scriptedSlurmAccountProjectMember{
				Uid:            uid,
				UCloudUsername: member,
			})
		}

		// TODO no mechanism to try again later if needed
		u.onProjectUpdated.Invoke(req)
	}
}
