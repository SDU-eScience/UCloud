package slurm

import (
	"fmt"
	"net/http"
	"os/user"
	"regexp"
	"strconv"
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
		ServerCache: util.NewCache[string, util.Option[string]](15 * time.Minute),
	}

	if cfg.Mode == cfg.ServerModeServer {
		findSlurmAccount.Handler(func(r *ipc.Request[SlurmJobConfiguration]) ipc.Response[[]string] {
			if !ctrl.BelongsToWorkspace(r.Payload.Owner, r.Uid) {
				return ipc.Response[[]string]{
					StatusCode: http.StatusForbidden,
				}
			}

			result := service.UCloudConfigurationFindSlurmAccount(r.Payload)
			return ipc.Response[[]string]{
				StatusCode: http.StatusOK,
				Payload:    result,
			}
		})

		cliSlurmAccountList.Handler(func(r *ipc.Request[accountMapperCliQuery]) ipc.Response[[]accountMapperRow] {
			if r.Uid != 0 {
				return ipc.Response[[]accountMapperRow]{
					StatusCode: http.StatusForbidden,
				}
			}

			if r.Payload.Account != "" {
				_, err := regexp.CompilePOSIX(r.Payload.Account)
				if err != nil {
					return ipc.Response[[]accountMapperRow]{
						StatusCode:   http.StatusBadRequest,
						ErrorMessage: fmt.Sprintf("account-name: %s", err),
					}
				}
			}

			if r.Payload.Account != "" {
				_, err := regexp.CompilePOSIX(r.Payload.UCloudName)
				if err != nil {
					return ipc.Response[[]accountMapperRow]{
						StatusCode:   http.StatusBadRequest,
						ErrorMessage: fmt.Sprintf("ucloud-name: %s", err),
					}
				}
			}

			if r.Payload.Category != "" {
				_, err := regexp.CompilePOSIX(r.Payload.Category)
				if err != nil {
					return ipc.Response[[]accountMapperRow]{
						StatusCode:   http.StatusBadRequest,
						ErrorMessage: fmt.Sprintf("category: %s", err),
					}
				}
			}

			result, err := db.NewTx2(func(tx *db.Transaction) ([]accountMapperRow, error) {
				rows := db.Select[struct {
					Title       string
					Username    string
					Project     string
					AccountName string
					Category    string
				}](
					tx,
					`
						select
							coalesce(p.ucloud_project#>>'{specification,title}', a.owner_username) as title,
							a.owner_username as username,
							a.owner_project as project,
							a.account_name,
							a.category
						from
							slurm.accounts a
							left join tracked_projects p on p.project_id = a.owner_project
						where
							(
								:ucloud_name = ''
								or coalesce(p.ucloud_project#>>'{specification,title}', a.owner_username) ~ :ucloud_name
							)
							and (
								:slurm_account = ''
								or a.account_name ~ :slurm_account
							)
							and (
								:category = ''
								or a.category ~ :category
							)
						order by title
						limit 250
					`,
					db.Params{
						"ucloud_name":   r.Payload.UCloudName,
						"slurm_account": r.Payload.Account,
						"category":      r.Payload.Category,
					},
				)

				if tx.ConsumeError() != nil {
					// NOTE(Dan): This can happen since the two POSIX regexes don't fully agree on each other here.
					// The easiest way to reproduce it through something like '.*substring**' (note double *)
					return nil, fmt.Errorf("invalid regex supplied")
				}

				var localNameRegex *regexp.Regexp
				if r.Payload.LocalName != "" {
					reg, err := regexp.CompilePOSIX(r.Payload.LocalName)
					if err != nil {
						return nil, fmt.Errorf("invalid local-name regex: %s", err)
					}

					localNameRegex = reg
				}

				var result []accountMapperRow
				for _, row := range rows {
					localName := ""

					if row.Username != "" {
						local, ok := ctrl.MapUCloudToLocal(row.Username)
						if !ok {
							continue
						}

						u, err := user.LookupId(fmt.Sprint(local))
						if err != nil {
							continue
						}

						localName = u.Username
					} else if row.Project != "" {
						local, ok := ctrl.MapUCloudProjectToLocal(row.Project)
						if !ok {
							continue
						}

						g, err := user.LookupGroupId(fmt.Sprint(local))
						if err != nil {
							continue
						}

						localName = g.Name
					}

					if localNameRegex != nil {
						if !localNameRegex.MatchString(localName) {
							continue
						}
					}

					result = append(result, accountMapperRow{
						UCloudName:   row.Title,
						LocalName:    localName,
						Category:     row.Category,
						SlurmAccount: row.AccountName,
					})
				}

				return result, nil
			})

			if err != nil {
				return ipc.Response[[]accountMapperRow]{
					StatusCode:   http.StatusBadRequest,
					ErrorMessage: err.Error(),
				}
			}

			return ipc.Response[[]accountMapperRow]{
				StatusCode: http.StatusOK,
				Payload:    result,
			}
		})

		cliSlurmAccountAdd.Handler(func(r *ipc.Request[accountMappingRequest]) ipc.Response[util.Empty] {
			if r.Uid != 0 {
				return ipc.Response[util.Empty]{
					StatusCode: http.StatusForbidden,
				}
			}

			_, ok := ServiceConfig.Compute.Machines[r.Payload.Category]
			if !ok {
				return ipc.Response[util.Empty]{
					StatusCode: http.StatusForbidden,
					ErrorMessage: fmt.Sprintf(
						"unknown category specified, make sure it is in the machine type configuration: %s",
						r.Payload.Category,
					),
				}
			}

			isProject := false
			workspaceRef := ""

			{
				userName, asUserErr := user.Lookup(r.Payload.LocalName)
				_, asUserUidErr := user.LookupId(r.Payload.LocalName)
				groupName, asGroupErr := user.LookupGroup(r.Payload.LocalName)
				_, asGroupGidErr := user.LookupGroupId(r.Payload.LocalName)

				isGroup := false
				numericId := uint32(0)

				switch {
				case asUserUidErr == nil:
					numeric, _ := strconv.Atoi(r.Payload.LocalName)
					numericId = uint32(numeric)
					isGroup = false

				case asUserErr == nil:
					numeric, _ := strconv.Atoi(userName.Uid)
					numericId = uint32(numeric)
					isGroup = false

				case asGroupGidErr == nil:
					numeric, _ := strconv.Atoi(r.Payload.LocalName)
					numericId = uint32(numeric)
					isGroup = true

				case asGroupErr == nil:
					numeric, _ := strconv.Atoi(groupName.Gid)
					numericId = uint32(numeric)
					isGroup = true

				default:
					return ipc.Response[util.Empty]{
						StatusCode:   http.StatusBadRequest,
						ErrorMessage: fmt.Sprintf("could not resolve local user/group with name: %s", r.Payload.LocalName),
					}
				}

				if isGroup {
					projectId, found := ctrl.MapLocalProjectToUCloud(numericId)
					ok = found
					isProject = true
					workspaceRef = projectId
				} else {
					ucloudUsername, found := ctrl.MapLocalToUCloud(numericId)
					ok = found
					isProject = false
					workspaceRef = ucloudUsername
				}

				if !ok {
					return ipc.Response[util.Empty]{
						StatusCode:   http.StatusBadRequest,
						ErrorMessage: fmt.Sprintf("local user/group is not connected with UCloud: %s (%v)", r.Payload.LocalName, numericId),
					}
				}
			}

			ownerUsername := ""
			ownerProject := ""

			if isProject {
				ownerProject = workspaceRef
			} else {
				ownerUsername = workspaceRef
			}

			db.NewTx0(func(tx *db.Transaction) {
				db.Exec(
					tx,
					`
						insert into slurm.accounts(owner_username, owner_project, category, account_name) 
						values (:owner_username, :owner_project, :category, :account_name)
				    `,
					db.Params{
						"owner_username": ownerUsername,
						"owner_project":  ownerProject,
						"category":       r.Payload.Category,
						"account_name":   r.Payload.SlurmAccount,
					},
				)
			})

			return ipc.Response[util.Empty]{
				StatusCode: http.StatusOK,
			}
		})

		cliSlurmAccountDelete.Handler(func(r *ipc.Request[accountMapperCliDeleteRequest]) ipc.Response[util.Empty] {
			if r.Uid != 0 {
				return ipc.Response[util.Empty]{
					StatusCode: http.StatusForbidden,
				}
			}

			db.NewTx0(func(tx *db.Transaction) {
				db.Exec(
					tx,
					`
						delete from slurm.accounts a
						where
							a.account_name = some(:names)
				    `,
					db.Params{
						"names": r.Payload.AccountNames,
					},
				)
			})

			return ipc.Response[util.Empty]{
				StatusCode: http.StatusOK,
			}
		})
	}

	return service
}

type defaultAccountMapper struct {
	ServerCache *util.AsyncCache[string, util.Option[string]]
}

func (a *defaultAccountMapper) ServerEvaluateAccountMapper(category string, owner apm.WalletOwner) (util.Option[string], error) {
	val, ok := a.ServerCache.Get(category+"\n"+owner.Username+"\n"+owner.ProjectId, func() (util.Option[string], error) {
		_, ok := ServiceConfig.Compute.Machines[category]
		if !ok {
			return util.OptNone[string](), fmt.Errorf("unable to evaluate slurm account due to unknown machine configuration %v", category)
		}

		existing := db.NewTx(func(tx *db.Transaction) util.Option[string] {
			row, ok := db.Get[struct{ AccountName string }](
				tx,
				`
					select a.account_name
					from slurm.accounts a
					where
						a.category = :category
						and a.owner_username = :username
						and a.owner_project = :project
			    `,
				db.Params{
					"category": category,
					"username": owner.Username,
					"project":  owner.ProjectId,
				},
			)

			if ok {
				return util.OptValue(row.AccountName)
			} else {
				return util.OptNone[string]()
			}
		})

		if existing.Present {
			return existing, nil
		}

		params := make(map[string]string)
		params["productCategory"] = category

		switch owner.Type {
		case apm.WalletOwnerTypeUser:
			localUid, ok := ctrl.MapUCloudToLocal(owner.Username)
			if !ok {
				return util.OptNone[string](), fmt.Errorf("could not map user")
			}

			userInfo, err := user.LookupId(fmt.Sprint(localUid))
			if err != nil {
				return util.OptNone[string](), fmt.Errorf("local username is unknown: ucloud=%v uid=%v err=%v", owner.Username, localUid, err)
			}

			params["ucloudUsername"] = userInfo.Username
			params["localUsername"] = userInfo.Username
			params["uid"] = fmt.Sprint(localUid)

		case apm.WalletOwnerTypeProject:
			localGid, ok := ctrl.MapUCloudProjectToLocal(owner.ProjectId)
			if !ok {
				lastKnown, ok := ctrl.GetLastKnownProject(owner.ProjectId)
				if !ok {
					return util.OptNone[string](), fmt.Errorf("could not map project")
				}
				if !lastKnown.Status.PersonalProviderProjectFor.Present {
					return util.OptNone[string](), fmt.Errorf("could not map project")
				}

				// OK, but there is no known slurm account from the account mapper. Needs to fetch this from the job.
				return util.OptNone[string](), nil
			}

			groupInfo, err := user.LookupGroupId(fmt.Sprint(localGid))
			if err != nil {
				return util.OptNone[string](), fmt.Errorf("local group is unknown: ucloud=%v uid=%v err=%v", owner.ProjectId, localGid, err)
			}

			params["localGroupName"] = groupInfo.Name
			params["ucloudProjectId"] = owner.ProjectId
			params["gid"] = fmt.Sprint(localGid)

		default:
			return util.OptNone[string](), fmt.Errorf("unhandled owner type: %v", owner.Type)
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
				return util.OptNone[string](), fmt.Errorf("failed to invoke account mapper script")
			}

			accountName = resp.AccountName
		}

		db.NewTx0(func(tx *db.Transaction) {
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
		return util.OptValue(accountName), nil
	})

	if !ok {
		return util.OptNone[string](), fmt.Errorf("failed to retrieve slurm account")
	}

	return val, nil
}

func (a *defaultAccountMapper) UCloudConfigurationFindSlurmAccount(config SlurmJobConfiguration) []string {
	if cfg.Mode == cfg.ServerModeUser {
		result, err := findSlurmAccount.Invoke(config)

		if err != nil {
			log.Warn("Could not lookup slurm account: %v", err)
			return nil
		}

		return result
	} else {
		result, err := a.ServerEvaluateAccountMapper(config.EstimatedProduct.Category, config.Owner)
		if err != nil {
			log.Warn("Could not lookup slurm account: %v", err)
			return nil
		}

		if !result.Present {
			if config.Job.Present {
				params := config.Job.Get().Specification.Parameters
				acc, ok := params[SlurmAccountParameter]
				if ok {
					return []string{acc.Value.(string)}
				}
			}
			return nil
		}

		return []string{result.Get()}
	}
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

var findSlurmAccount = ipc.NewCall[SlurmJobConfiguration, []string]("slurm.account_mapper.evaluate")

type accountMapperCliQuery struct {
	UCloudName string
	Account    string
	LocalName  string
	Category   string
}

type accountMappingRequest struct {
	LocalName    string
	Category     string
	SlurmAccount string
}

type accountMapperRow struct {
	UCloudName   string
	LocalName    string
	Category     string
	SlurmAccount string
}

type accountMapperCliDeleteRequest struct {
	AccountNames []string
}

var cliSlurmAccountList = ipc.NewCall[accountMapperCliQuery, []accountMapperRow]("slurm.account_mapper.list")
var cliSlurmAccountDelete = ipc.NewCall[accountMapperCliDeleteRequest, util.Empty]("slurm.account_mapper.delete")
var cliSlurmAccountAdd = ipc.NewCall[accountMappingRequest, util.Empty]("slurm.account_mapper.add")
