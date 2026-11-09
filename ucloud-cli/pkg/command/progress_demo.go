package command

import (
	"fmt"
	"os"
	"time"

	"ucloud.dk/shared/pkg/termio"
)

type ProgressTestCommand struct{}

var ProgressTestCommands = map[string]CommandFunc{
	"progress-test": func() Command { return &ProgressTestCommand{} },
}

func (c *ProgressTestCommand) Execute() error {
	progress := termio.NewProgressList(os.Stderr, "Progress bar test")
	progress.Start()
	defer progress.Stop()

	percent := 0.05
	for {
		progress.SetItems([]termio.ProgressItem{
			{
				Key:    "indeterminate-1",
				Label:  "indeterminate-1",
				Status: "Unknown progress",
				Known:  false,
				State:  termio.ProgressRunning,
			},
			{
				Key:    "indeterminate-2",
				Label:  "indeterminate-2",
				Status: "Unknown progress",
				Known:  false,
				State:  termio.ProgressRunning,
			},
			{
				Key:      "animated",
				Label:    "animated",
				Status:   fmt.Sprintf("%.0f%%", percent*100),
				Progress: percent,
				Known:    true,
				State:    termio.ProgressRunning,
			},
			{
				Key:      "success",
				Label:    "success",
				Status:   "ready",
				Progress: 1,
				Known:    true,
				State:    termio.ProgressSuccess,
			},
			{
				Key:      "warning",
				Label:    "warning",
				Status:   "waiting",
				Progress: 0.5,
				Known:    true,
				State:    termio.ProgressWarning,
			},
			{
				Key:      "failure",
				Label:    "failure",
				Status:   "failed",
				Progress: 1,
				Known:    true,
				State:    termio.ProgressFailure,
			},
		})

		if percent >= 1 {
			break
		}
		time.Sleep(1200 * time.Millisecond)
		percent += 0.15
		if percent > 1 {
			percent = 1
		}
	}

	time.Sleep(3 * time.Second)
	return nil
}
