package slurm

import (
	"encoding/json"
	"flag"
	"fmt"
	"net/http"
	"os"
	"regexp"
	"strings"
	"ucloud.dk/pkg/cli"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/external/user"
	"ucloud.dk/pkg/im/ipc"
	"ucloud.dk/pkg/termio"
	db "ucloud.dk/shared/pkg/database"
	orc "ucloud.dk/shared/pkg/orchestrators"
)

func HandleDrivesCommand() {
	// Invoked via 'ucloud drives <subcommand> [options]'
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
		req := cliDrivesListRequest{}
		req.Parse()
		drives, err := cliDrivesList.Invoke(req)
		cli.HandleError("listing drives", err)

		t := termio.Table{}
		t.AppendHeader("Created at")
		t.AppendHeader("ID")
		t.AppendHeader("Path")
		t.AppendHeader("Project (local)")

		for _, drive := range drives {
			t.Cell(cli.FormatTime(drive.Drive.CreatedAt))
			t.Cell(drive.Drive.Id)
			t.Cell(drive.LocalPath)
			t.Cell(drive.LocalGroupName)
		}

		t.Print()
		termio.WriteLine("")

	case cli.IsGetCommand(command):
		req := cliDrivesListRequest{}
		req.Parse()
		drives, err := cliDrivesList.Invoke(req)
		cli.HandleError("listing drives", err)

		if len(drives) > 1 {
			termio.WriteStyledString(termio.Bold, termio.Red, 0, "Too many results, try with a more precise query")
			os.Exit(1)
		} else if len(drives) == 0 {
			termio.WriteStyledString(termio.Bold, termio.Red, 0, "No results, try with a different query")
			os.Exit(1)
		}

		drive := drives[0]
		driveSpec := &drive.Drive.Specification

		f := termio.Frame{}

		{
			f.AppendTitle("UCloud metadata")

			f.AppendField("ID", drive.Drive.Id)
			f.AppendField("Created at", cli.FormatTime(drive.Drive.CreatedAt))
			f.AppendSeparator()

			createdBy := drive.Drive.Owner.CreatedBy
			if createdBy == "_ucloud" {
				createdBy = "Registered by the provider"
			}
			f.AppendField("Created by", createdBy)

			if drive.Drive.Owner.Project != "" {
				f.AppendField("Project", fmt.Sprintf("%v (ID: %v)", drive.WorkspaceTitle, drive.Drive.Owner.Project))
			}

			f.AppendField("Product", fmt.Sprintf("%s/%s", driveSpec.Product.Id, driveSpec.Product.Category))
		}

		{
			f.AppendTitle("Provider metadata")
			f.AppendField("Path", drive.LocalPath)
			f.AppendSeparator()

			{
				maxLength := max(len(drive.LocalUsername), len(drive.LocalGroupName))
				usernamePadding := strings.Repeat(" ", max(0, maxLength-len(drive.LocalUsername)))
				groupPadding := strings.Repeat(" ", max(0, maxLength-len(drive.LocalGroupName)))
				f.AppendField("User", fmt.Sprintf("%v%v (UID: %v)", drive.LocalUsername, usernamePadding, drive.LocalUid))
				f.AppendField("Group", fmt.Sprintf("%v%v (GID: %v)", drive.LocalGroupName, groupPadding, drive.LocalGid))
			}
			f.AppendSeparator()
		}

		f.Print()
		termio.WriteLine("")
	}
}

func singleDriveToCliDrive(drive *orc.Drive, ok bool) ipc.Response[[]cliDrive] {
	if drive == nil || !ok {
		return ipc.Response[[]cliDrive]{
			StatusCode:   http.StatusNotFound,
			ErrorMessage: "Unable to map this path to a drive",
		}
	}

	result := cliDrive{}
	result.Drive = drive

	if drive.Owner.Project != "" {
		gid, ok := ctrl.MapUCloudProjectToLocal(drive.Owner.Project)
		if !ok {
			return ipc.Response[[]cliDrive]{
				StatusCode:   http.StatusNotFound,
				ErrorMessage: "Unable to map the project owner of this drive",
			}
		}

		groupString := fmt.Sprint(gid)
		ginfo, err := user.LookupGroupId(groupString)
		if err == nil {
			groupString = ginfo.Name
		}

		result.LocalGid = gid
		result.LocalGroupName = groupString
	}

	if !strings.HasPrefix(drive.Owner.CreatedBy, "_") {
		uid, ok, _ := ctrl.MapUCloudToLocal(drive.Owner.CreatedBy)
		if !ok {
			return ipc.Response[[]cliDrive]{
				StatusCode:   http.StatusNotFound,
				ErrorMessage: "Unable to map the owner of this drive",
			}
		}
		result.LocalUid = uid
	} else {
		result.LocalUsername = "root"
		result.LocalUid = 0
	}

	result.LocalPath = DriveToLocalPath(*drive)

	return ipc.Response[[]cliDrive]{
		StatusCode: http.StatusOK,
		Payload:    []cliDrive{result},
	}
}

func HandleDrivesCommandServer() {
	cliDrivesList.Handler(func(r *ipc.Request[cliDrivesListRequest]) ipc.Response[[]cliDrive] {
		if r.Uid != 0 {
			return ipc.Response[[]cliDrive]{
				StatusCode: http.StatusForbidden,
			}
		}

		var (
			ucloudIdRegex,
			localUsernameRegex,
			localGroupNameRegex,
			localUidRegex,
			localGidRegex,
			ucloudProjectIdRegex *regexp.Regexp
		)

		err := cli.ValidateRegexes(
			&ucloudIdRegex, "ucloud-id", r.Payload.UCloudId,
			&localUsernameRegex, "local-username", r.Payload.LocalUsername,
			&localGroupNameRegex, "local-group-name", r.Payload.LocalGroupName,
			&localUidRegex, "local-uid", r.Payload.LocalUid,
			&localGidRegex, "local-gid", r.Payload.LocalGid,
			&ucloudProjectIdRegex, "ucloud-project-id", r.Payload.UCloudProjectId,
		)

		if err != nil {
			return ipc.Response[[]cliDrive]{
				StatusCode:   http.StatusBadRequest,
				ErrorMessage: err.Error(),
			}
		}

		if r.Payload.LocalPath != "" && r.Payload.UCloudPath != "" {
			return ipc.Response[[]cliDrive]{
				StatusCode:   http.StatusBadRequest,
				ErrorMessage: "Cannot specify both local-path and ucloud-path",
			}
		} else if r.Payload.LocalPath != "" {
			drive, ok := ResolveDriveByLocalPath(r.Payload.LocalPath)
			return singleDriveToCliDrive(&drive, ok)
		} else if r.Payload.UCloudPath != "" {
			drive, ok := ResolveDriveByUCloudPath(r.Payload.UCloudPath)
			return singleDriveToCliDrive(&drive, ok)
		}

		drives := db.NewTx(func(tx *db.Transaction) []cliDrive {
			rows := db.Select[struct {
				DriveId  string
				Uid      uint32
				Gid      uint32
				Title    string
				Resource string
			}](
				tx,
				`
					select
						d.drive_id,
						coalesce(c.uid, 0) as uid,
						coalesce(pc.gid, 0) as gid,
						p.ucloud_project#>>'{specification,title}' as title,
						d.resource
					from
						tracked_drives d
						left join connections c on d.created_by = c.ucloud_username
						left join project_connections pc on d.project_id = pc.ucloud_project_id
						left join tracked_projects p on pc.ucloud_project_id = p.project_id
					where
						(
							:ucloud_id = ''
							or d.drive_id ~ :ucloud_id
						)
						and (
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
					order by
						d.resource#>>'{createdAt}' desc
				`,
				db.Params{
					"ucloud_id":         r.Payload.UCloudId,
					"local_uid":         r.Payload.LocalUid,
					"local_gid":         r.Payload.LocalGid,
					"ucloud_project_id": r.Payload.UCloudProjectId,
				},
			)

			var drives []cliDrive

			for _, row := range rows {
				var drive orc.Drive
				err = json.Unmarshal([]byte(row.Resource), &drive)
				if err != nil {
					continue
				}

				if row.Title == "" {
					row.Title = drive.Owner.CreatedBy
				}

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

				elem := cliDrive{
					Drive:          &drive,
					WorkspaceTitle: row.Title,
					LocalUid:       row.Uid,
					LocalGid:       row.Gid,
					LocalUsername:  username,
					LocalGroupName: groupName,
					LocalPath:      DriveToLocalPath(drive),
				}

				drives = append(drives, elem)
			}

			return drives
		})

		return ipc.Response[[]cliDrive]{
			StatusCode: http.StatusOK,
			Payload:    drives,
		}
	})
}

type cliDrivesListRequest struct {
	UCloudId        string
	LocalUsername   string
	LocalGroupName  string
	LocalUid        string
	LocalGid        string
	UCloudProjectId string
	UCloudPath      string
	LocalPath       string
}

func (c *cliDrivesListRequest) Parse() {
	fs := flag.NewFlagSet("", flag.ExitOnError)
	fs.StringVar(&c.UCloudId, "ucloud-id", "", "The UCloud ID of the drive, supports regex")
	fs.StringVar(&c.UCloudProjectId, "ucloud-project-id", "", "The UCloud project ID owning the drive, supports regex")
	fs.StringVar(&c.LocalUsername, "local-user", "", "The local username of the user who started the job, supports regex")
	fs.StringVar(&c.LocalGroupName, "local-group", "", "The local group name of the group who submitted the job, supports regex")
	fs.StringVar(&c.LocalUid, "local-uid", "", "The local UID of the user who submitted the job, supports regex")
	fs.StringVar(&c.LocalGid, "local-gid", "", "The local gid of the group who submitted the job, supports regex")
	fs.StringVar(&c.UCloudPath, "ucloud-path", "", "The path to a UCloud file. Does not support regex.")
	fs.StringVar(&c.LocalPath, "local-path", "", "The path to a local file. Does not support regex.")
	_ = fs.Parse(os.Args[3:])
}

type cliDrive struct {
	Drive     *orc.Drive
	LocalPath string

	WorkspaceTitle string
	LocalUid       uint32
	LocalGid       uint32
	LocalUsername  string
	LocalGroupName string
}

var (
	cliDrivesList = ipc.NewCall[cliDrivesListRequest, []cliDrive]("cli.slurm.drives.list")
)
