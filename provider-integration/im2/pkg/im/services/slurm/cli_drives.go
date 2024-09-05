package slurm

import (
	"flag"
	"os"
	"ucloud.dk/pkg/im/ipc"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/termio"
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
	case isListCommand(command):
	}
}

func HandleDrivesCommandServer() {

}

type cliDrivesListRequest struct {
	UCloudId        string
	LocalUsername   string
	LocalGroupName  string
	LocalUid        string
	LocalGid        string
	UCloudProjectId string
}

func (c *cliDrivesListRequest) Parse() {
	fs := flag.NewFlagSet("", flag.ExitOnError)
	fs.StringVar(&c.UCloudId, "ucloud-id", "", "The UCloud ID of the drive, supports regex")
	fs.StringVar(&c.UCloudProjectId, "ucloud-project-id", "", "The UCloud project ID owning the drive, supports regex")
	fs.StringVar(&c.LocalUsername, "local-user", "", "The local username of the user who started the job, supports regex")
	fs.StringVar(&c.LocalGroupName, "local-group", "", "The local group name of the group who submitted the job, supports regex")
	fs.StringVar(&c.LocalUid, "local-uid", "", "The local UID of the user who submitted the job, supports regex")
	fs.StringVar(&c.LocalGid, "local-gid", "", "The local gid of the group who submitted the job, supports regex")
	_ = fs.Parse(os.Args[3:])
}

type cliDrive struct {
	Drive          *orc.Drive
	WorkspaceTitle string
	Partition      string
	Account        string

	LocalUid       uint32
	LocalGid       uint32
	LocalUsername  string
	LocalGroupName string
}

var (
	cliDrivesList = ipc.NewCall[cliDrivesListRequest, []cliDrive]("cli.slurm.drives.list")
)
