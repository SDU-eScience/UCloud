package slurm

import (
	"fmt"
	"os/user"
	"slices"
	"ucloud.dk/pkg/apm"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	slurmcli "ucloud.dk/pkg/im/external/slurm"
	"ucloud.dk/pkg/log"
)

func InitAutomaticAccountManagement() AccountingService {
	service := &automaticAccountManagementService{}

	ctrl.OnConnectionComplete(func(username string, uid uint32) {
		service.createPersonalSlurmAccount(username, uid)
	})

	ctrl.OnProjectNotification(func(update *ctrl.NotificationProjectUpdated) {
		service.synchronizeProjectToSlurmAccount(&update.Project, &update.ProjectComparison)
	})

	return service
}

type automaticAccountManagementService struct{}

func (a *automaticAccountManagementService) createSlurmAccount(category string, accountName string) bool {
	machineConfig, ok := ServiceConfig.Compute.Machines[category]
	if !ok {
		return false
	}

	if !SlurmClient.AccountExists(accountName) {
		account := slurmcli.NewAccount()
		account.Name = accountName
		if machineConfig.Qos.IsSet() {
			qos := machineConfig.Qos.Get()
			account.QoS = []string{qos}
			account.DefaultQoS = qos
		}

		return SlurmClient.AccountCreate(account)
	}
	return true
}

func (a *automaticAccountManagementService) createPersonalSlurmAccount(username string, uid uint32) {
	log.Info("creating personal slurm account: %v %v", username, uid)
	localUsername, err := user.LookupId(fmt.Sprint(uid))
	if err != nil {
		return
	}

	categoriesToAccountName := AccountMapper.ServerListAccountsForWorkspace(apm.WalletOwnerUser(username))
	for categoryName, accountName := range categoriesToAccountName {
		if !a.createSlurmAccount(categoryName, accountName) {
			continue
		}

		slurmUser := SlurmClient.UserQuery(localUsername.Username)
		if slurmUser != nil && !slices.Contains(slurmUser.Accounts, accountName) {
			SlurmClient.AccountAddUser(localUsername.Username, accountName)
		}
	}
}

func (a *automaticAccountManagementService) synchronizeProjectToSlurmAccount(project *apm.Project, comparison *ctrl.ProjectComparison) {
	log.Info("synchronizing project to slurm account %v", project.Id)
	categoriesToAccountName := AccountMapper.ServerListAccountsForWorkspace(apm.WalletOwnerProject(project.Id))
	for categoryName, accountName := range categoriesToAccountName {
		if !a.createSlurmAccount(categoryName, accountName) {
			continue
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

		// TODO I am worried about the performance of this loop
		for _, member := range membersAddedToProject {
			uid, ok := ctrl.MapUCloudToLocal(member)
			if !ok {
				continue
			}

			localUsername, err := user.LookupId(fmt.Sprint(uid))
			if err != nil {
				continue
			}

			slurmUser := SlurmClient.UserQuery(localUsername.Username)
			if slurmUser == nil || !slices.Contains(slurmUser.Accounts, accountName) {
				SlurmClient.AccountAddUser(localUsername.Username, accountName)
			}
		}

		for _, member := range membersRemovedFromProject {
			uid, ok := ctrl.MapUCloudToLocal(member)
			if !ok {
				continue
			}

			localUsername, err := user.LookupId(fmt.Sprint(uid))
			if err != nil {
				continue
			}

			slurmUser := SlurmClient.UserQuery(localUsername.Username)
			if slurmUser == nil || slices.Contains(slurmUser.Accounts, accountName) {
				SlurmClient.AccountRemoveUser(localUsername.Username, accountName)
			}
		}
	}
}

func (a *automaticAccountManagementService) OnWalletUpdated(update *ctrl.NotificationWalletUpdated) {
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
	if update.Owner.Type == apm.WalletOwnerTypeUser {
		localUid, ok := ctrl.MapUCloudToLocal(update.Owner.Username)
		if !ok {
			return
		}

		a.createPersonalSlurmAccount(update.Owner.Username, localUid)
	} else if update.Owner.Type == apm.WalletOwnerTypeProject {
		project := update.Project.Get()
		a.synchronizeProjectToSlurmAccount(&project, nil)
	}

	// The slurm account should exist now. If not, we will assume that Slurm is unavailable, and we just bail out.
	account := SlurmClient.AccountQuery(accountName.Get())
	if account == nil {
		return
	}

	account.MaxJobs = -1
	if update.Locked {
		account.MaxJobs = 0
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
	retiredInMinutes := update.LocalRetiredUsage * multiplier

	if account.QuotaTRES == nil {
		account.QuotaTRES = make(map[string]int)
	}
	account.QuotaTRES["billing"] = int(quotaInMinutes) + int(retiredInMinutes)
	account.RawShares = int((quotaInMinutes + retiredInMinutes) / 60 / 24)

	SlurmClient.AccountModify(account)
}

func (a *automaticAccountManagementService) FetchUsageInMinutes() map[SlurmAccountOwner]int64 {
	result := map[SlurmAccountOwner]int64{}
	billing := SlurmClient.AccountBillingList()

	allocations := map[string][]ctrl.TrackedAllocation{}

	for account, usage := range billing {
		actualUsage := usage
		owners := AccountMapper.ServerLookupOwnerOfAccount(account)
		for _, owner := range owners {
			_, ok := allocations[owner.AssociatedWithCategory]
			if !ok {
				allocations[owner.AssociatedWithCategory] = ctrl.FindAllAllocations(owner.AssociatedWithCategory)
			}

			allocList := allocations[owner.AssociatedWithCategory]
			for _, a := range allocList {
				if a.Owner == owner.Owner {
					if actualUsage >= int64(a.LocalRetiredUsage) {
						actualUsage -= int64(a.LocalRetiredUsage)
					}
				}
			}

			result[owner] = actualUsage
		}
	}
	return result
}
