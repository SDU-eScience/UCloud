package slurm

import (
	"fmt"
	"net/http"
	"os"

	db "ucloud.dk/pkg/database"
	fnd "ucloud.dk/pkg/foundation"
	"ucloud.dk/pkg/im/ipc"
	"ucloud.dk/pkg/log"
	"ucloud.dk/pkg/termio"
)

type scriptLogEntry struct {
	Timestamp  fnd.Timestamp
	Id         uint64
	Request    string
	ScriptPath string
	Stdout     string
	Stderr     string
	StatusCode string
	Success    bool
	Uid        int
}

func HandleScriptsCommand() {
	if os.Getuid() != 0 {
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "This command must be run as root!")
		os.Exit(1)
	}

	response, err := cliScriptsList.Invoke(cliScriptsListRequest{})

	if err != nil {
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Failed to get script log")
	}

	for _, entry := range response {
		termio.WriteStyledLine(termio.NoStyle, termio.DefaultColor, 0, "%d %s %s", entry.Uid, entry.ScriptPath, entry.Success)
	}
}

func HandleScriptsCommandServer() {
	cliScriptsList.Handler(func(r *ipc.Request[cliScriptsListRequest]) ipc.Response[[]scriptLogEntry] {
		if r.Uid != 0 {
			return ipc.Response[[]scriptLogEntry]{
				StatusCode: http.StatusForbidden,
			}
		}
		log.Info("Making transaction")

		result := db.NewTx(func(tx *db.Transaction) []scriptLogEntry {
			var result []scriptLogEntry
			rows := db.Select[struct {
				timestamp  fnd.Timestamp
				id         uint64
				request    string
				scriptPath string
				stdout     string
				stderr     string
				statusCode string
				success    bool
				uid        int
			}](
				tx,
				`
				select *
				from script_log
			`,
				db.Params{},
			)

			for _, row := range rows {
				fmt.Printf("%d %s\n", row.id, row.scriptPath)
				result = append(
					result,
					scriptLogEntry{
						Id:         row.id,
						ScriptPath: row.scriptPath,
					},
				)
			}

			return result
		})

		return ipc.Response[[]scriptLogEntry]{
			StatusCode: http.StatusOK,
			Payload:    result,
		}
	})
}

type cliScriptsListRequest struct{}

var (
	cliScriptsList = ipc.NewCall[cliScriptsListRequest, []scriptLogEntry]("cli.slurm.scripts.list")
)
