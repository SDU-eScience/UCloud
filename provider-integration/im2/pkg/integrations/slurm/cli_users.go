package slurm

import (
	"flag"
	"fmt"
	"net/http"
	"os"
	"regexp"
	"strconv"

	ctrl "ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/external/user"
	"ucloud.dk/pkg/ipc"
	"ucloud.dk/shared/pkg/cli"
	db "ucloud.dk/shared/pkg/database"
	termio2 "ucloud.dk/shared/pkg/termio"
	"ucloud.dk/shared/pkg/util"
)

func HandleUsersCommand() {
	// Invoked via 'ucloud users <subcommand> [options]'

	if os.Getuid() != 0 {
		termio2.WriteStyledLine(termio2.Bold, termio2.Red, 0, "This command must be run as root!")
		os.Exit(1)
	}

	var (
		ucloudName   string
		localName    string
		localUid     string
		actualRemove bool
	)

	fs := flag.NewFlagSet("", flag.ExitOnError)
	fs.StringVar(&ucloudName, "ucloud-name", "", "Query by UCloud name, supports regex")
	fs.StringVar(&localName, "local-name", "", "Query by a local username, supports regex")
	fs.StringVar(&localUid, "local-uid", "", "Query by a local UID, supports regex")
	fs.BoolVar(&actualRemove, "really-remove-mapping", false, "Causes the mapping to actually be removed instead of invalidating it")

	printUsers := func() []cliUserMapping {
		result, err := cliUsersList.Invoke(cliUsersListRequest{
			UCloudName: ucloudName,
			LocalName:  localName,
			LocalUid:   localUid,
		})

		cli.HandleError("fetching user mapping", err)

		t := termio2.Table{}
		t.AppendHeader("UCloud username")
		t.AppendHeader("Local username")
		t.AppendHeader("Local UID")
		for _, mapping := range result {
			t.Cell("%v", mapping.UCloudName)
			t.Cell("%v", mapping.LocalName)
			t.Cell("%v", mapping.Uid)
		}
		t.Print()
		termio2.WriteLine("")
		return result
	}

	command := ""
	if len(os.Args) >= 3 {
		command = os.Args[2]
		_ = fs.Parse(os.Args[3:])
	}

	switch {
	case cli.IsListCommand(command):
		_ = printUsers()

	case cli.IsAddCommand(command):
		if ucloudName == "" {
			termio2.WriteStyledLine(termio2.Bold, termio2.Red, 0, "ucloud-name must be supplied")
			os.Exit(1)
		}

		if localName == "" {
			termio2.WriteStyledLine(termio2.Bold, termio2.Red, 0, "local-name must be supplied")
			os.Exit(1)
		}

		if localUid != "" {
			termio2.WriteStyledLine(termio2.Bold, termio2.Red, 0, "local-uid is ignored for this command")
			os.Exit(1)
		}

		_, err := cliUsersAdd.Invoke(
			cliUsersAddRequest{
				UCloudName: ucloudName,
				LocalName:  localName,
			},
		)

		cli.HandleError("adding user", err)
		termio2.WriteStyledLine(termio2.Bold, termio2.Green, 0, "OK")

	case cli.IsDeleteCommand(command):
		if ucloudName == "" && localName == "" && localUid == "" {
			termio2.WriteStyledLine(termio2.Bold, termio2.Red, 0, "either ucloud-name, local-name or local-uid must be supplied")
			os.Exit(1)
		}

		termio2.WriteStyledLine(termio2.Bold, 0, 0, "This command will delete the following connections:")
		termio2.WriteLine("")

		rows := printUsers()
		termio2.WriteLine("")
		if len(rows) == 0 {
			termio2.WriteLine("No such account, try again with a different query.")
			os.Exit(0)
		}

		shouldDelete, _ := termio2.ConfirmPrompt(
			"Are you sure you want to delete these user mappings? No users will be deleted from the system.",
			termio2.ConfirmValueFalse,
			termio2.ConfirmPromptExitOnCancel,
		)

		if !shouldDelete {
			os.Exit(0)
		}

		var uids []uint32
		for _, row := range rows {
			uids = append(uids, row.Uid)
		}

		resp, err := cliUsersDelete.Invoke(cliUsersDeleteRequest{Uids: uids, ActualRemove: actualRemove})
		cli.HandleError("deleting users", err)

		for i, failure := range resp.Failures {
			if failure != "" {
				termio2.WriteStyledLine(termio2.Bold, termio2.Red, 0, "Failed to delete user %s (uid: %v): %s", rows[i].LocalName, rows[i].Uid, failure)
			}
		}

		termio2.WriteStyledLine(termio2.Bold, termio2.Green, 0, "OK")
	}
}

func HandleUsersCommandServer() {
	cliUsersList.Handler(func(r *ipc.Request[cliUsersListRequest]) ipc.Response[[]cliUserMapping] {
		if r.Uid != 0 {
			return ipc.Response[[]cliUserMapping]{
				StatusCode: http.StatusForbidden,
			}
		}

		_, err := regexp.CompilePOSIX(r.Payload.UCloudName)
		if err != nil {
			return ipc.Response[[]cliUserMapping]{
				StatusCode:   http.StatusBadRequest,
				ErrorMessage: fmt.Sprintf("ucloud-name: %s", err),
			}
		}

		localRegex, err := regexp.CompilePOSIX(r.Payload.LocalName)
		if err != nil {
			return ipc.Response[[]cliUserMapping]{
				StatusCode:   http.StatusBadRequest,
				ErrorMessage: fmt.Sprintf("ucloud-name: %s", err),
			}
		}

		_, err = regexp.CompilePOSIX(r.Payload.LocalUid)
		if err != nil {
			return ipc.Response[[]cliUserMapping]{
				StatusCode:   http.StatusBadRequest,
				ErrorMessage: fmt.Sprintf("local-uid: %s", err),
			}
		}

		result, err := db.NewTx2(func(tx *db.Transaction) ([]cliUserMapping, error) {
			rows := db.Select[struct {
				UCloudUsername string
				Uid            uint32
			}](
				tx,
				`
					select ucloud_username, uid
					from connections
					where
						(
							:ucloud_username = ''
							or ucloud_username ~ :ucloud_username
						)
						and (
						    :local_uid = ''
						    or cast(uid as text) ~ :local_uid
						)
					order by ucloud_username
					limit 250
			    `,
				db.Params{
					"ucloud_username": r.Payload.UCloudName,
					"local_uid":       r.Payload.LocalUid,
				},
			)

			if err := tx.ConsumeError(); err != nil {
				return nil, fmt.Errorf("invalid regex supplied (%s)", err)
			}

			var result []cliUserMapping
			for _, row := range rows {
				uidString := fmt.Sprint(row.Uid)
				localUsername := ""
				localUser, err := user.LookupId(uidString)
				if err == nil {
					localUsername = localUser.Username
				}

				if r.Payload.LocalName != "" {
					if !localRegex.MatchString(localUsername) {
						continue
					}
				}

				result = append(result, cliUserMapping{
					UCloudName: row.UCloudUsername,
					LocalName:  localUsername,
					Uid:        row.Uid,
				})
			}

			return result, nil
		})

		if err != nil {
			return ipc.Response[[]cliUserMapping]{
				StatusCode:   http.StatusBadRequest,
				ErrorMessage: fmt.Sprintf("%s", err),
			}
		}
		return ipc.Response[[]cliUserMapping]{
			StatusCode: http.StatusOK,
			Payload:    result,
		}
	})

	cliUsersAdd.Handler(func(r *ipc.Request[cliUsersAddRequest]) ipc.Response[util.Empty] {
		if r.Uid != 0 {
			return ipc.Response[util.Empty]{
				StatusCode: http.StatusForbidden,
			}
		}

		uinfo, err := user.Lookup(r.Payload.LocalName)
		if err != nil {
			return ipc.Response[util.Empty]{
				StatusCode:   http.StatusBadRequest,
				ErrorMessage: fmt.Sprintf("%s", err),
			}
		}

		nuid, _ := strconv.Atoi(uinfo.Uid)
		err = ctrl.IdmRegisterCompleted(r.Payload.UCloudName, uint32(nuid), true).AsError()
		if err != nil {
			return ipc.Response[util.Empty]{
				StatusCode:   http.StatusBadRequest,
				ErrorMessage: fmt.Sprintf("%s", err),
			}
		}

		/*
			if cfg.Services.Unmanaged {
				_, err = ctrl.CreatePersonalProviderProject(r.Payload.UCloudName)
				if err != nil {
					return ipc.Response[util.Empty]{
						StatusCode:   http.StatusBadRequest,
						ErrorMessage: fmt.Sprintf("%s", err),
					}
				}
			}
		*/

		return ipc.Response[util.Empty]{
			StatusCode: http.StatusOK,
		}
	})

	cliUsersDelete.Handler(func(r *ipc.Request[cliUsersDeleteRequest]) ipc.Response[cliUsersDeleteResponse] {
		if r.Uid != 0 {
			return ipc.Response[cliUsersDeleteResponse]{
				StatusCode: http.StatusForbidden,
			}
		}

		// TODO Make sure the Core actually deletes the personal provider projects in this case

		var errors []string
		for _, uid := range r.Payload.Uids {
			var flags ctrl.IdmConnectionRemoveFlag
			flags = ctrl.IdmConnectionRemoveNotify
			if r.Payload.ActualRemove {
				flags |= ctrl.IdmConnectionRemoveTrulyRemove
			}

			err := ctrl.IdmConnectionRemove(uid, flags)
			if err != nil {
				errors = append(errors, err.AsError().Error())
			} else {
				errors = append(errors, "")
			}
		}

		return ipc.Response[cliUsersDeleteResponse]{
			StatusCode: http.StatusOK,
			Payload:    cliUsersDeleteResponse{Failures: errors},
		}
	})
}

type cliUsersListRequest struct {
	UCloudName string
	LocalName  string
	LocalUid   string
}

type cliUserMapping struct {
	UCloudName string
	LocalName  string
	Uid        uint32
}

type cliUsersAddRequest struct {
	UCloudName string
	LocalName  string
}

type cliUsersDeleteRequest struct {
	Uids         []uint32
	ActualRemove bool
}

type cliUsersDeleteResponse struct {
	Failures []string // Empty string if none. Will have the same length as request.Uids
}

var (
	cliUsersList   = ipc.NewCall[cliUsersListRequest, []cliUserMapping]("slurm.cli.users.list")
	cliUsersAdd    = ipc.NewCall[cliUsersAddRequest, util.Empty]("slurm.cli.users.add")
	cliUsersDelete = ipc.NewCall[cliUsersDeleteRequest, cliUsersDeleteResponse]("slurm.cli.users.delete")
)
