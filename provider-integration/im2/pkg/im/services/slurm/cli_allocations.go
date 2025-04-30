package slurm

import (
	"flag"
	"fmt"
	"net/http"
	"os"
	"regexp"
	"strings"
	"time"
	"ucloud.dk/pkg/cli"
	cfg "ucloud.dk/pkg/im/config"
	"ucloud.dk/pkg/im/external/user"
	"ucloud.dk/pkg/im/ipc"
	"ucloud.dk/pkg/termio"
	"ucloud.dk/shared/pkg/apm"
	db "ucloud.dk/shared/pkg/database"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/util"
)

func HandleAllocationsCommand() {
	// Invoked via 'ucloud allocations <subcommand> [options]'
	if os.Getuid() != 0 {
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "This command must be run as root!")
		os.Exit(1)
	}

	command := ""
	if len(os.Args) >= 3 {
		command = os.Args[2]
	}

	switch {
	case cli.IsListCommand(command):
		req := cliAllocationsListRequest{}
		req.Parse()

		allocations, err := cliAllocationsList.Invoke(req)
		cli.HandleError("listing allocations", err)

		t := termio.Table{}
		t.AppendHeader("Local owner")
		t.AppendHeader("Category")
		t.AppendHeaderEx("Quota", termio.TableHeaderAlignRight)
		t.AppendHeader("Unit")
		t.AppendHeader("Locked")

		for _, alloc := range allocations {
			if alloc.LocalUid != 0 {
				t.Cell(fmt.Sprintf("%v (UID: %v)", alloc.LocalUsername, alloc.LocalUid))
			} else {
				t.Cell(fmt.Sprintf("%v (GID: %v)", alloc.LocalGroupName, alloc.LocalGid))
			}

			t.Cell(alloc.Category)
			t.Cell(fmt.Sprint(alloc.Quota))
			t.Cell(alloc.Unit)
			if alloc.Locked {
				t.Cell("Yes")
			} else {
				t.Cell("No")
			}
		}

		t.Print()

	case cli.IsGetCommand(command):
		req := cliAllocationsListRequest{}
		req.Parse()
		req.ExpectFewElements = true
		req.IncludeAllocationInformation = true

		wallets, err := cliAllocationsList.Invoke(req)
		cli.HandleError("listing allocations", err)

		if len(wallets) == 0 {
			termio.WriteStyledString(termio.Bold, termio.Red, 0, "No results found, try a different query")
			os.Exit(1)
		}

		for i := 0; i < len(wallets); i++ {
			wallet := &wallets[i]

			f := termio.Frame{}

			{
				f.AppendTitle("Wallet metadata")
				f.AppendField("Product", wallet.Category)
				f.AppendField("Quota", fmt.Sprintf("%v %v", wallet.Quota, wallet.Unit))
				f.AppendField("Locked", fmt.Sprint(wallet.Locked))
				f.AppendField("Last update", cli.FormatTime(wallet.LastUpdate))
				f.AppendSeparator()

				if wallet.ProjectId != "" {
					maxLength := max(len(wallet.WorkspaceTitle), len(wallet.LocalGroupName))
					localPadding := strings.Repeat(" ", max(0, maxLength-len(wallet.LocalGroupName)))
					ucloudPadding := strings.Repeat(" ", max(0, maxLength-len(wallet.WorkspaceTitle)))

					f.AppendField("UCloud project", fmt.Sprintf("%v%v (ID: %v)", wallet.WorkspaceTitle, ucloudPadding, wallet.ProjectId))
					f.AppendField("Local project", fmt.Sprintf("%v%v (GID: %v)", wallet.LocalGroupName, localPadding, wallet.LocalGid))
				} else {
					f.AppendField("UCloud user", fmt.Sprintf("%v", wallet.Username))
					f.AppendField("Local user", fmt.Sprintf("%v (UID: %v)", wallet.LocalUsername, wallet.LocalUid))
				}
				f.AppendSeparator()
			}

			if wallet.Allocations.IsSet() {
				allAllocations := wallet.Allocations.Value
				for i, a := range allAllocations {
					f.AppendTitle(fmt.Sprintf("Allocation #%v", i+1))
					grant := &a.Grant.Value

					if a.Grant.IsSet() {
						f.AppendField("Grant ID", fmt.Sprint(grant.GrantId))
					}

					f.AppendField("Quota", fmt.Sprintf("%v %v", a.Quota, wallet.Unit))

					if a.Grant.IsSet() {
						if len(grant.ApproverTitles) > 1 {
							f.AppendSeparator()
						}
						for _, title := range grant.ApproverTitles {
							f.AppendField("Approved by", title)
						}
					}

					f.AppendSeparator()

					f.AppendField("Start date", cli.FormatTime(a.Start))
					f.AppendField("End date", cli.FormatTime(a.End))
					f.AppendSeparator()

					if a.Grant.IsSet() {
						for j, refId := range grant.ReferenceIds {
							f.AppendField(fmt.Sprintf("External ID #%v", j+1), refId)
						}
					}
				}
			}

			f.Print()
			termio.WriteLine("")
			termio.WriteLine("")
			termio.WriteLine("")
		}
	}
}

func HandleAllocationsCommandServer() {
	cliAllocationsList.Handler(func(r *ipc.Request[cliAllocationsListRequest]) ipc.Response[[]cliAllocation] {
		if r.Uid != 0 {
			return ipc.Response[[]cliAllocation]{
				StatusCode: http.StatusForbidden,
			}
		}

		var (
			localUsernameRegex,
			localGroupNameRegex,
			localUidRegex,
			localGidRegex,
			ucloudProjectIdRegex,
			categoryRegex *regexp.Regexp
		)

		err := cli.ValidateRegexes(
			&localUsernameRegex, "local-user", r.Payload.LocalUsername,
			&localGroupNameRegex, "local-group", r.Payload.LocalGroupName,
			&localUidRegex, "local-uid", r.Payload.LocalUid,
			&localGidRegex, "local-gid", r.Payload.LocalGid,
			&ucloudProjectIdRegex, "ucloud-project-id", r.Payload.UCloudProjectId,
			&categoryRegex, "category", r.Payload.Category,
		)

		if err != nil {
			return ipc.Response[[]cliAllocation]{
				StatusCode:   http.StatusBadRequest,
				ErrorMessage: err.Error(),
			}
		}

		type UnitNormalizer struct {
			Divisor float64
			Name    string
		}
		categories := map[string]UnitNormalizer{}

		categoryList := []string{}
		for category, machine := range ServiceConfig.Compute.Machines {
			if categoryRegex != nil && categoryRegex.MatchString(category) {
				categoryList = append(categoryList, category)
			}

			switch machine.Payment.Type {
			case cfg.PaymentTypeMoney:
				categories[category] = UnitNormalizer{
					Divisor: 1000000,
					Name:    machine.Payment.Currency,
				}
			case cfg.PaymentTypeResource:
				unit := machine.Payment.Unit
				divisor := float64(1)
				name := ""
				switch unit {
				case cfg.MachineResourceTypeCpu:
					name = "Core-hours"
				case cfg.MachineResourceTypeGpu:
					name = "GPU-hours"
				case cfg.MachineResourceTypeMemory:
					name = "GB-hours"
				}

				switch machine.Payment.Interval {
				case cfg.PaymentIntervalMinutely:
					divisor = 60
				case cfg.PaymentIntervalHourly:
					divisor = 1
				case cfg.PaymentIntervalDaily:
					divisor = 1 / 24
				}

				categories[category] = UnitNormalizer{
					Divisor: divisor,
					Name:    name,
				}
			}
		}

		for category, fs := range ServiceConfig.FileSystems {
			if categoryRegex != nil && categoryRegex.MatchString(category) {
				categoryList = append(categoryList, category)
			}

			switch fs.Payment.Type {
			case cfg.PaymentTypeMoney:
				categories[category] = UnitNormalizer{
					Divisor: 1000000,
					Name:    fs.Payment.Currency,
				}
			case cfg.PaymentTypeResource:
				unit := fs.Payment.Unit
				divisor := float64(1)
				name := "GB"
				suffix := ""

				switch unit {
				case "GB":
					divisor = 1
				case "TB":
					divisor = 1 / 1000
				case "PB":
					divisor = 1 / (1000 * 1000)
				case "EB":
					divisor = 1 / (1000 * 1000 * 1000)
				case "GiB":
					name = "GiB"
				case "TiB":
					name = "GiB"
					divisor = 1 / 1024
				case "PiB":
					name = "GiB"
					divisor = 1 / (1024 * 1024)
				case "EiB":
					name = "GiB"
					divisor = 1 / (1024 * 1024 * 1024)
				}

				switch fs.Payment.Interval {
				case cfg.PaymentIntervalMinutely:
					suffix = "-hours"
					divisor *= 60
				case cfg.PaymentIntervalHourly:
					suffix = "-hours"
				case cfg.PaymentIntervalDaily:
					suffix = "-hours"
					divisor *= 1 / 24
				}

				categories[category] = UnitNormalizer{
					Divisor: divisor,
					Name:    name + suffix,
				}
			}
		}

		allocations := db.NewTx(func(tx *db.Transaction) []cliAllocation {
			rows := db.Select[struct {
				Category      string
				CombinedQuota uint64
				Locked        bool
				Uid           uint32
				Gid           uint32
				Title         string
				LastUpdate    time.Time
				ProjectId     string
				Username      string
			}](
				tx,
				`
					select 
						alloc.category,
						alloc.combined_quota,
						alloc.locked,
						alloc.last_update,
						coalesce(c.uid, 0) as uid,
						coalesce(pc.gid, 0) as gid,
						coalesce(p.ucloud_project#>>'{specification,title}', '') as title,
						coalesce(p.project_id, '') as project_id,
						coalesce(alloc.owner_username, '') as username
					from
						tracked_allocations alloc
						left join connections c on alloc.owner_username = c.ucloud_username
						left join project_connections pc on alloc.owner_project = pc.ucloud_project_id
						left join tracked_projects p on pc.ucloud_project_id = p.project_id
					where
						(
							:local_uid = ''
							or cast(c.uid as text) ~ :local_uid
						)
						and (
							:local_gid = ''
							or cast(pc.gid as text) ~ :local_gid
						)
						and (
							:ucloud_project_id = ''
							or p.ucloud_project#>>'{id}' ~ :ucloud_project_id
						)
						and (
							cardinality(cast(:categories as text[])) = 0
							or alloc.category = some(:categories)
						)
					order by
						alloc.last_update desc,
						uid,
						gid,
						category
			    `,
				db.Params{
					"local_uid":         r.Payload.LocalUid,
					"local_gid":         r.Payload.LocalGid,
					"ucloud_project_id": r.Payload.UCloudProjectId,
					"categories":        categoryList,
				},
			)

			var allocations []cliAllocation
			for _, row := range rows {
				uidString := fmt.Sprint(row.Uid)
				gidString := fmt.Sprint(row.Gid)

				username := uidString
				groupName := gidString
				uinfo, err := user.LookupId(uidString)
				if err == nil {
					username = uinfo.Username
				}

				ginfo, err := user.LookupGroupId(gidString)
				if err == nil {
					groupName = ginfo.Name
				}

				if localUsernameRegex != nil && !localUsernameRegex.MatchString(username) {
					continue
				}

				if localGroupNameRegex != nil && !localGroupNameRegex.MatchString(groupName) {
					continue
				}

				normalizer := categories[row.Category]

				elem := cliAllocation{
					Quota:          uint64(float64(row.CombinedQuota) / normalizer.Divisor),
					Locked:         row.Locked,
					LastUpdate:     fnd.Timestamp(row.LastUpdate),
					Category:       row.Category,
					WorkspaceTitle: row.Title,
					LocalUid:       row.Uid,
					LocalGid:       row.Gid,
					LocalUsername:  username,
					LocalGroupName: groupName,
					Unit:           normalizer.Name,
					ProjectId:      row.ProjectId,
					Username:       row.Username,
				}

				allocations = append(allocations, elem)
			}

			return allocations
		})

		if r.Payload.ExpectFewElements && len(allocations) > 15 {
			return ipc.Response[[]cliAllocation]{
				StatusCode:   http.StatusBadRequest,
				ErrorMessage: "Too many results - try a more precise query",
			}
		}

		if r.Payload.IncludeAllocationInformation {
			for i := 0; i < len(allocations); i++ {
				alloc := &allocations[i]

				req := apm.BrowseProviderAllocationsReq{
					FilterCategory: alloc.Category,
				}

				if alloc.ProjectId != "" {
					req.FilterOwnerId = alloc.ProjectId
					req.FilterOwnerIsProject = true
				} else {
					req.FilterOwnerId = alloc.Username
					req.FilterOwnerIsProject = false
				}

				page, err := apm.BrowseProviderAllocations("", req)
				for j := 0; j < len(page.Items); j++ {
					item := &page.Items[j]
					normalizer := categories[alloc.Category]
					item.Quota = int64(float64(item.Quota) / normalizer.Divisor)
				}
				if err != nil {
					return ipc.Response[[]cliAllocation]{
						StatusCode:   http.StatusBadGateway,
						ErrorMessage: "Failed to retrieve information about UCloud grant",
					}
				}

				alloc.Allocations.Set(page.Items)
			}
		}

		return ipc.Response[[]cliAllocation]{
			StatusCode: http.StatusOK,
			Payload:    allocations,
		}
	})
}

type cliAllocationsListRequest struct {
	LocalUsername                string
	LocalGroupName               string
	LocalUid                     string
	LocalGid                     string
	UCloudProjectId              string
	Category                     string
	IncludeAllocationInformation bool
	ExpectFewElements            bool
}

func (c *cliAllocationsListRequest) Parse() {
	fs := flag.NewFlagSet("", flag.ExitOnError)
	fs.StringVar(&c.UCloudProjectId, "ucloud-project-id", "", "The UCloud project ID owning the drive, supports regex")
	fs.StringVar(&c.LocalUsername, "local-user", "", "The local username of the user who started the job, supports regex")
	fs.StringVar(&c.LocalGroupName, "local-group", "", "The local group name of the group who submitted the job, supports regex")
	fs.StringVar(&c.LocalUid, "local-uid", "", "The local UID of the user who submitted the job, supports regex")
	fs.StringVar(&c.LocalGid, "local-gid", "", "The local gid of the group who submitted the job, supports regex")
	fs.StringVar(&c.Category, "category", "", "The product category of the allocation, supports regex")
	_ = fs.Parse(os.Args[3:])
}

type cliAllocation struct {
	Quota       uint64
	Locked      bool
	LastUpdate  fnd.Timestamp
	Category    string
	Unit        string
	ProjectId   string
	Username    string
	Allocations util.Option[[]apm.BrowseProviderAllocationsResp]

	WorkspaceTitle string
	LocalUid       uint32
	LocalGid       uint32
	LocalUsername  string
	LocalGroupName string
}

var (
	cliAllocationsList = ipc.NewCall[cliAllocationsListRequest, []cliAllocation]("cli.slurm.allocations.list")
)
