package slurm

import (
	"net/http"
	"os"
	"ucloud.dk/pkg/cli"
	db "ucloud.dk/pkg/database"
	"ucloud.dk/pkg/im/ipc"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/termio"
	"ucloud.dk/pkg/util"
)

func HandleTasksCommand() {
	// Invoked via 'ucloud jobs <subcommand> [options]'
	if os.Getuid() != 0 {
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "This command must be run as root!")
		os.Exit(1)
	}

	command := ""
	if len(os.Args) >= 3 {
		command = os.Args[2]
	}

	switch {
	case command == "kill-all":
		_, err := cliTasksKillAll.Invoke(util.EmptyValue)
		cli.HandleError("killing tasks", err)
		termio.WriteStyledLine(termio.Bold, termio.Green, 0, "OK")
	}
}

func HandleTasksCommandServer() {
	cliTasksKillAll.Handler(func(r *ipc.Request[util.Empty]) ipc.Response[util.Empty] {
		if r.Uid != 0 {
			return ipc.Response[util.Empty]{
				StatusCode: http.StatusForbidden,
			}
		}

		tasks := db.NewTx(func(tx *db.Transaction) []uint64 {
			rows := db.Select[struct{ Id uint64 }](
				tx,
				`
					select id
					from slurm.tasks
			    `,
				db.Params{},
			)

			var res []uint64
			for _, row := range rows {
				res = append(res, row.Id)
			}
			return res
		})

		var err error
		for _, taskId := range tasks {
			err = util.MergeError(err, PostTaskStatusServer(0, TaskStatusUpdate{
				Id:            taskId,
				NewBody:       util.OptValue(""),
				NewProgress:   util.OptValue("Task has been cancelled"),
				NewPercentage: util.OptValue(100.0),
				NewState:      util.OptValue(orc.TaskStateCancelled),
			}))
		}

		if err != nil {
			return ipc.Response[util.Empty]{
				StatusCode:   http.StatusInternalServerError,
				ErrorMessage: err.Error(),
			}
		}

		return ipc.Response[util.Empty]{
			StatusCode: http.StatusOK,
		}
	})
}

var (
	cliTasksKillAll = ipc.NewCall[util.Empty, util.Empty]("cli.slurm.tasks.killAll")
)
