package slurm

import (
	"fmt"
	"net/http"
	"os/user"
	"time"
	"ucloud.dk/pkg/apm"
	db "ucloud.dk/pkg/database"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/ipc"
	slurmcli "ucloud.dk/pkg/im/slurm"
	"ucloud.dk/pkg/log"
	"ucloud.dk/pkg/util"
)

func InitDefaultAccountMapper() AccountMapperService {
	service := &defaultAccountMapper{
		ServerCache: util.NewCache[string, string](15 * time.Minute),
	}

	if cfg.Mode == cfg.ServerModeServer {
		evaluateAccountMapper.Handler(func(r *ipc.Request[evaluateMapperRequest]) ipc.Response[string] {
			if !ctrl.BelongsToWorkspace(r.Payload.Owner, r.Uid) {
				return ipc.Response[string]{
					StatusCode: http.StatusForbidden,
					Payload:    "",
				}
			}

			result, ok := service.ServerEvaluateAccountMapper(r.Payload.Category, r.Payload.Owner)
			if !ok {
				return ipc.Response[string]{
					StatusCode: http.StatusInternalServerError,
					Payload:    "",
				}
			}

			return ipc.Response[string]{
				StatusCode: http.StatusOK,
				Payload:    result,
			}
		})
	}

	return service
}

type defaultAccountMapper struct {
	ServerCache *util.AsyncCache[string, string]
}

func (a *defaultAccountMapper) ServerEvaluateAccountMapper(category string, owner apm.WalletOwner) (string, bool) {
	// TODO(Dan): This should only run once per (workspace, category). A cache is not good enough for this.
	//   Technically the account mapper should be allowed to literally generate a random value on every invocation and
	//   everything else should still work as expected.
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

		db.NewTxV(func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					insert into slurm.accounts(owner_username, owner_project, category, account_name) 
					values (:owner_username, :owner_project, :category, :account_name)
					on conflict (owner_username, owner_project, category) do update set
						account_name = excluded.account_name
			    `,
				db.Params{
					"owner_username": owner.Username,
					"owner_project":  owner.ProjectId,
					"category":       category,
					"account_name":   accountName,
				},
			)
		})
		return accountName, nil
	})
}

func (a *defaultAccountMapper) UCloudConfigurationFindSlurmAccount(config SlurmJobConfiguration) []string {
	account := ""
	if cfg.Mode == cfg.ServerModeUser {
		result, err := evaluateAccountMapper.Invoke(evaluateMapperRequest{
			Category: config.EstimatedProduct.Category,
			Owner:    config.Owner,
		})

		if err != nil {
			log.Warn("Could not lookup slurm account: %v", err)
			return nil
		}

		account = result
	} else {
		result, ok := a.ServerEvaluateAccountMapper(config.EstimatedProduct.Category, config.Owner)
		if !ok {
			return nil
		}

		account = result
	}

	return []string{account}
}

func (a *defaultAccountMapper) ServerSlurmJobToConfiguration(job *slurmcli.Job) (result util.Option[SlurmJobConfiguration]) {
	machines := ServiceConfig.Compute.Machines
	candidateCategory := ""
	for k, v := range machines {
		if v.Qos.IsSet() && v.Qos.Get() != job.QoS {
			continue
		}
		if v.Partition != job.Partition {
			continue
		}

		candidateCategory = k
		break
	}

	_, ok := machines[candidateCategory]
	if !ok {
		return
	}

	cpuCount := getAllocTres(job, "cpu", 1)
	memory := getAllocTres(job, "mem", 1) / (1000 * 1000 * 1000)
	trueNodeCount := getAllocTres(job, "node", 1)

	nodeMultiplier := 1

	config := SlurmJobConfiguration{}

	// Attempt exact match
	for _, machine := range Machines {
		if cpuCount == machine.Cpu && memory == machine.MemoryInGigs {
			config.EstimatedProduct = machine.ToReference()
			break
		}
	}

	// Attempt any machine which is evenly divisible
	if config.EstimatedProduct.Id == "" {
		for _, machine := range Machines {
			if cpuCount%machine.Cpu == 0 {
				config.EstimatedProduct = machine.ToReference()
				nodeMultiplier = cpuCount / machine.Cpu
			}
		}
	}

	if config.EstimatedProduct.Id == "" {
		return
	}

	config.EstimatedNodeCount = trueNodeCount * nodeMultiplier

	ok = false
	knownAccounts := a.ServerLookupOwnerOfAccount(job.Account)
	for _, acc := range knownAccounts {
		if acc.AssociatedWithCategory == candidateCategory {
			config.Owner = acc.Owner
			ok = true
		}
	}

	if !ok {
		return
	}

	result.Set(config)
	return
}

func getAllocTres(job *slurmcli.Job, key string, defaultValue int) int {
	result, ok := job.AllocTRES[key]
	if !ok {
		return defaultValue
	}
	return result
}

func (a *defaultAccountMapper) ServerListAccountsForWorkspace(workspace apm.WalletOwner) map[string]string {
	return db.NewTx(func(tx *db.Transaction) map[string]string {
		rows := db.Select[struct {
			Category    string
			AccountName string
		}](
			tx,
			`
				select category, account_name
				from slurm.accounts
				where
				    owner_username = :owner_username
					and owner_project = :owner_project
		    `,
			db.Params{
				"owner_username": workspace.Username,
				"owner_project":  workspace.ProjectId,
			},
		)

		result := make(map[string]string)
		for _, row := range rows {
			result[row.Category] = row.AccountName
		}
		return result
	})
}

func (a *defaultAccountMapper) ServerLookupOwnerOfAccount(account string) []SlurmAccountOwner {
	return db.NewTx(func(tx *db.Transaction) []SlurmAccountOwner {
		rows := db.Select[struct {
			OwnerUsername string
			OwnerProject  string
			Category      string
		}](
			tx,
			`
				select owner_username, owner_project, category
				from slurm.accounts
				where account_name = :account
		    `,
			db.Params{
				"account": account,
			},
		)

		var result []SlurmAccountOwner
		for _, row := range rows {
			result = append(result, SlurmAccountOwner{
				AssociatedWithCategory: row.Category,
				Owner:                  apm.WalletOwnerFromIds(row.OwnerUsername, row.OwnerProject),
			})
		}
		return result
	})
}

type evaluateMapperRequest struct {
	Category string
	Owner    apm.WalletOwner
}

var evaluateAccountMapper = ipc.NewCall[evaluateMapperRequest, string]("slurm.account_mapper.evaluate")
