package command

import "fmt"

type JobLogsCommand struct {
	JobId  string `required:true`
	Follow bool   `optional`
	Rank   int    `optional number"`
}

func (c JobLogsCommand) Execute() error {
	return fmt.Errorf("job logs not implemented")
}

type JobShellCommand struct {
	JobId string
}
