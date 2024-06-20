package slurm

import (
	"fmt"
	"os/user"
	"slices"
	"strings"
	"time"
	"ucloud.dk/pkg/apm"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	slurmcli "ucloud.dk/pkg/im/slurm"
	"ucloud.dk/pkg/kvdb"
	"ucloud.dk/pkg/log"
	"ucloud.dk/pkg/util"
)

func InitAutomaticAccountManagement() AccountManagementService {
	client := slurmcli.NewClient()
	if client == nil {
		panic("InitAutomaticAccountManagement: failed to initialize slurm client")
	}

	service := &automaticAccountManagementService{
		Client:      client,
		ServerCache: util.NewCache[string, string](15 * time.Minute),
	}

	ctrl.OnConnectionComplete(func(username string, uid uint32) {
		service.createPersonalSlurmAccount(username, uid)
	})

	ctrl.OnProjectNotification(func(update *ctrl.NotificationProjectUpdated) {
		service.synchronizeProjectToSlurmAccount(&update.Project, &update.ProjectComparison)
	})

	return service
}

type automaticAccountManagementService struct {
	Client      *slurmcli.Client
	ServerCache *util.AsyncCache[string, string]
}

func (a *automaticAccountManagementService) UCloudConfigurationFindSlurmAccount(config SlurmJobConfiguration) []string {
	//TODO implement me
	panic("implement me")
}

func (a *automaticAccountManagementService) SlurmJobToConfiguration(job slurmcli.Job) {
	//TODO implement me
	panic("implement me")
}

const kvPrefix = "slurm-account-mapper-"

func (a *automaticAccountManagementService) serverEvaluateAccountMapper(category string, owner apm.WalletOwner) (string, bool) {
	return a.ServerCache.Get(category+"\n"+owner.Username+"\n"+owner.ProjectId, func() (string, error) {
		_, ok := ServiceConfig.Compute.Machines[category]
		if !ok {
			return "", fmt.Errorf("unable to evaluate slurm account due to unknown machine configuration %v", category)
		}

		params := make(map[string]string)
		params["productCategory"] = category

		switch owner.Type {
		case apm.WalletOwnerTypeUser:
			localUid, ok := ctrl.MapUCloudToLocal(owner.Username)
			if !ok {
				return "", fmt.Errorf("could not map user")
			}

			userInfo, err := user.LookupId(fmt.Sprint(localUid))
			if err != nil {
				return "", fmt.Errorf("local username is unknown: ucloud=%v uid=%v err=%v", owner.Username, localUid, err)
			}

			params["ucloudUsername"] = userInfo.Username
			params["localUsername"] = userInfo.Username
			params["uid"] = fmt.Sprint(localUid)

		case apm.WalletOwnerTypeProject:
			localGid, ok := ctrl.MapUCloudProjectToLocal(owner.ProjectId)
			if !ok {
				return "", fmt.Errorf("could not map project")
			}

			groupInfo, err := user.LookupGroupId(fmt.Sprint(localGid))
			if err != nil {
				return "", fmt.Errorf("local group is unknown: ucloud=%v uid=%v err=%v", owner.ProjectId, localGid, err)
			}

			params["localGroupName"] = groupInfo.Name
			params["ucloudProjectId"] = owner.ProjectId
			params["gid"] = fmt.Sprint(localGid)

		default:
			return "", fmt.Errorf("unhandled owner type: %v", owner.Type)
		}

		accountName := ""
		accountMapper := &ServiceConfig.Compute.AccountManagement.AccountMapper
		switch accountMapper.Type {
		case cfg.SlurmAccountMapperTypePattern:
			mapper := accountMapper.Pattern()
			pattern := ""
			switch owner.Type {
			case apm.WalletOwnerTypeUser:
				pattern = mapper.Users
			case apm.WalletOwnerTypeProject:
				pattern = mapper.Projects
			}

			accountName = util.InjectParametersIntoString(pattern, params)

		case cfg.SlurmAccountMapperTypeScripted:
			mapper := accountMapper.Scripted()

			type Resp struct {
				AccountName string `json:"accountName"`
			}

			ext := ctrl.NewExtension[map[string]string, Resp]()
			ext.Script = mapper.Script

			resp, ok := ext.Invoke(params)
			if !ok {
				return "", fmt.Errorf("failed to invoke account mapper script")
			}

			accountName = resp.AccountName
		}

		kvdb.Set(accMapKey(owner, category), accountName)
		return accountName, nil
	})
}

func (a *automaticAccountManagementService) listAccountsForWorkspace(workspace apm.WalletOwner) map[string]string {
	prefix := accMapKey(workspace, "")
	keys := kvdb.ListKeysWithPrefix(prefix)
	result := make(map[string]string)
	for _, key := range keys {
		category := strings.TrimPrefix(key, prefix)
		accountName, ok := kvdb.Get[string](key)
		if !ok {
			continue
		}

		result[category] = accountName
	}
	return result
}

func accMapKey(owner apm.WalletOwner, category string) string {
	return kvPrefix + owner.Username + "-" + owner.ProjectId + "-" + category
}

func (a *automaticAccountManagementService) createSlurmAccount(category string, accountName string) bool {
	machineConfig, ok := ServiceConfig.Compute.Machines[category]
	if !ok {
		return false
	}

	if !a.Client.AccountExists(accountName) {
		account := slurmcli.NewAccount()
		account.Name = accountName
		if machineConfig.Qos.IsSet() {
			qos := machineConfig.Qos.Get()
			account.QoS = []string{qos}
			account.DefaultQoS = qos
		}

		return a.Client.AccountCreate(account)
	}
	return true
}

func (a *automaticAccountManagementService) createPersonalSlurmAccount(username string, uid uint32) {
	log.Info("creating personal slurm account: %v %v", username, uid)
	localUsername, err := user.LookupId(fmt.Sprint(uid))
	if err != nil {
		return
	}

	categoriesToAccountName := a.listAccountsForWorkspace(apm.WalletOwnerUser(username))
	for categoryName, accountName := range categoriesToAccountName {
		if !a.createSlurmAccount(categoryName, accountName) {
			continue
		}

		slurmUser := a.Client.UserQuery(localUsername.Username)
		if slurmUser != nil && !slices.Contains(slurmUser.Accounts, accountName) {
			a.Client.AccountAddUser(localUsername.Username, accountName)
		}
	}
}

func (a *automaticAccountManagementService) synchronizeProjectToSlurmAccount(project *apm.Project, comparison *ctrl.ProjectComparison) {
	log.Info("synchronizing project to slurm account %v", project.Id)
	categoriesToAccountName := a.listAccountsForWorkspace(apm.WalletOwnerProject(project.Id))
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

			slurmUser := a.Client.UserQuery(localUsername.Username)
			if slurmUser == nil || !slices.Contains(slurmUser.Accounts, accountName) {
				a.Client.AccountAddUser(localUsername.Username, accountName)
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

			slurmUser := a.Client.UserQuery(localUsername.Username)
			if slurmUser == nil || slices.Contains(slurmUser.Accounts, accountName) {
				a.Client.AccountRemoveUser(localUsername.Username, accountName)
			}
		}
	}
}

func (a *automaticAccountManagementService) OnWalletUpdated(update *ctrl.NotificationWalletUpdated) {
	log.Info("slurm account wallet update %v %v", update.Category.Name, update.CombinedQuota)
	_, ok := ServiceConfig.Compute.Machines[update.Category.Name]
	if !ok {
		return
	}

	accountName, ok := a.serverEvaluateAccountMapper(update.Category.Name, update.Owner)
	if !ok {
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
	account := a.Client.AccountQuery(accountName)
	if account == nil {
		return
	}

	account.MaxJobs = 0
	if update.Locked {
		account.MaxJobs = -1
	}

	// TODO This should be in minutes but we don't know the frequency and such yet
	if account.QuotaTRES == nil {
		account.QuotaTRES = make(map[string]int)
	}
	account.QuotaTRES["billing"] = int(update.CombinedQuota)
	account.RawShares = int(update.CombinedQuota / 60 / 24)

	a.Client.AccountModify(account)
}
