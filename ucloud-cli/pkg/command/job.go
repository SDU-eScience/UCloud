package command

import (
	"fmt"

	_ "ucloud.dk/shared/pkg/cli"
)

func listJobs(args []string) error {
	return fmt.Errorf("list Jobs is not implemented %s", args)
}

func getJob(args []string) error {
	return fmt.Errorf("get Job is not implemented %s", args)
}

func createJob(args []string) error {
	return fmt.Errorf("create Job is not implemented %s", args)
}

type JobCommand struct {
	Verb string
	Args []string
	Flag map[string]string
}

func (JobCommand) Parse() {}

func parseJobCommand(args []string) (Command, bool) {
	return nil, false
}
