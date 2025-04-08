package slurm

import (
	"flag"
	"fmt"
	"net/http"
	"os"
	"os/user"
	"regexp"
	"strconv"
	"ucloud.dk/pkg/cli"
	db "ucloud.dk/shared/pkg/database"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/ipc"
	"ucloud.dk/pkg/termio"
	"ucloud.dk/shared/pkg/util"
)

func HandleUsersCommand() {
	// Invoked via 'ucloud users <subcommand> [options]'

	if os.Getuid() != 0 {
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "This command must be run as root!")
		os.Exit(1)
	}

	var (
		ucloudName string
		localName  string
		localUid   string
	)

	fs := flag.NewFlagSet("", flag.ExitOnError)
	fs.StringVar(&ucloudName, "ucloud-name", "", "Query by UCloud name, supports regex")
	fs.StringVar(&localName, "local-name", "", "Query by a local username, supports regex")
	fs.StringVar(&localUid, "local-uid", "", "Query by a local UID, supports regex")

	printUsers := func() []cliUserMapping {
		result, err := cliUsersList.Invoke(cliUsersListRequest{
			UCloudName: ucloudName,
			LocalName:  localName,
			LocalUid:   localUid,
		})

		cli.HandleError("fetching user mapping", err)

		t := termio.Table{}
		t.AppendHeader("UCloud username")
		t.AppendHeader("Local username")
		t.AppendHeader("Local UID")
		for _, mapping := range result {
			t.Cell("%v", mapping.UCloudName)
			t.Cell("%v", mapping.LocalName)
			t.Cell("%v", mapping.Uid)
		}
		t.Print()
		termio.WriteLine("")
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
			termio.WriteStyledLine(termio.Bold, termio.Red, 0, "ucloud-name must be supplied")
			os.Exit(1)
		}

		if localName == "" {
			termio.WriteStyledLine(termio.Bold, termio.Red, 0, "local-name must be supplied")
			os.Exit(1)
		}

		if localUid != "" {
			termio.WriteStyledLine(termio.Bold, termio.Red, 0, "local-uid is ignored for this command")
			os.Exit(1)
		}

		_, err := cliUsersAdd.Invoke(
			cliUsersAddRequest{
				UCloudName: ucloudName,
				LocalName:  localName,
			},
		)

		cli.HandleError("adding user", err)
		termio.WriteStyledLine(termio.Bold, termio.Green, 0, "OK")

	case cli.IsDeleteCommand(command):
		if ucloudName == "" && localName == "" && localUid == "" {
			termio.WriteStyledLine(termio.Bold, termio.Red, 0, "either ucloud-name, local-name or local-uid must be supplied")
			os.Exit(1)
		}

		termio.WriteStyledLine(termio.Bold, 0, 0, "This command will delete the following connections:")
		termio.WriteLine("")

		rows := printUsers()
		termio.WriteLine("")
		if len(rows) == 0 {
			termio.WriteLine("No such account, try again with a different query.")
			os.Exit(0)
		}

		shouldDelete, _ := termio.ConfirmPrompt(
			"Are you sure you want to delete these user mappings? No users will be deleted from the system.",
			termio.ConfirmValueFalse,
			termio.ConfirmPromptExitOnCancel,
		)

		if !shouldDelete {
			os.Exit(0)
		}

		var uids []uint32
		for _, row := range rows {
			uids = append(uids, row.Uid)
		}

		resp, err := cliUsersDelete.Invoke(cliUsersDeleteRequest{Uids: uids})
		cli.HandleError("deleting users", err)

		for i, failure := range resp.Failures {
			if failure != "" {
				termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Failed to delete user %s (uid: %v): %s", rows[i].LocalName, rows[i].Uid, failure)
			}
		}

		termio.WriteStyledLine(termio.Bold, termio.Green, 0, "OK")
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
				localUser, _ := user.LookupId(uidString)
				if localUser != nil {
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
		err = ctrl.RegisterConnectionComplete(r.Payload.UCloudName, uint32(nuid), true)
		if err != nil {
			return ipc.Response[util.Empty]{
				StatusCode:   http.StatusBadRequest,
				ErrorMessage: fmt.Sprintf("%s", err),
			}
		}

		if cfg.Services.Unmanaged {
			_, err = ctrl.CreatePersonalProviderProject(r.Payload.UCloudName)
			if err != nil {
				return ipc.Response[util.Empty]{
					StatusCode:   http.StatusBadRequest,
					ErrorMessage: fmt.Sprintf("%s", err),
				}
			}
		}

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
			err := ctrl.RemoveConnection(uid, true)
			if err != nil {
				errors = append(errors, err.Error())
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
	Uids []uint32
}

type cliUsersDeleteResponse struct {
	Failures []string // Empty string if none. Will have the same length as request.Uids
}

var (
	cliUsersList   = ipc.NewCall[cliUsersListRequest, []cliUserMapping]("slurm.cli.users.list")
	cliUsersAdd    = ipc.NewCall[cliUsersAddRequest, util.Empty]("slurm.cli.users.add")
	cliUsersDelete = ipc.NewCall[cliUsersDeleteRequest, cliUsersDeleteResponse]("slurm.cli.users.delete")
)
