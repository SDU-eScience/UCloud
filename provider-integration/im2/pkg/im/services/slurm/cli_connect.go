package slurm

import (
	"fmt"
	"os"
	"os/user"
	"time"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/termio"
	"ucloud.dk/shared/pkg/util"
)

func HandleConnectCommand() {
	// Invoked via 'ucloud connect'
	connectionUrl, err := ctrl.InitiateReverseConnectionFromUser.Invoke(util.EmptyValue)
	if err != nil {
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "%s", err)
		os.Exit(1)
	}

	termio.WriteLine("You can finish the connection by going to: %s", connectionUrl)
	termio.WriteLine("")
	termio.WriteStyledLine(termio.Bold, 0, 0, "Waiting for connection to complete... (Please keep this window open)")
	for {
		ucloudUsername, err := ctrl.Whoami.Invoke(util.EmptyValue)
		if err == nil {
			localUserinfo, err := user.LookupId(fmt.Sprint(os.Getuid()))
			localUsername := ""
			if err != nil {
				localUsername = fmt.Sprintf("#%v", os.Getuid())
			} else {
				localUsername = localUserinfo.Username
			}
			termio.WriteStyledLine(termio.Bold, termio.Green, 0, "Connection complete! Welcome '%s'/'%s'", localUsername, ucloudUsername)
			break
		} else {
			time.Sleep(1 * time.Second)
		}
	}
}
