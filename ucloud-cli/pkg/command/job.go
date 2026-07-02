package command

import (
	"fmt"
)

type JobGetCommand struct {
	JobID string `flag:"job-id" usage:"Job ID"`
}

type JobCreateCommand struct {
	Application string            `flag:"app" usage:"Application name"`
	Product     string            `flag:"prod" usage:"Product name"`
	Name        string            `flag:"name" usage:"Job name"`
	Time        int               `flag:"time" usage:"Time in minutes"`
	SSH         bool              `flag:"ssh" usage:"Use SSH"`
	Folder      string            `flag:"folder" usage:"Folder name"`
	PublicLink  string            `flag:"public-link" usage:"Public link"`
	Parameters  map[string]string `flag:"param" usage:"eg. image=ubuntu"`
}

type JobDeleteCommand struct {
	JobID string `flag:"job-id" usage:"Job ID"`
}

type JobRenameCommand struct {
	JobID   string `flag:"job-id" usage:"Job ID"`
	NewName string `flag:"new-name" usage:"New job name"`
}

type JobSearchCommand struct {
	JobName string `flag:"job-name" usage:"Job name"`
}

type JobExtendCommand struct {
	JobID string `flag:"job-id" usage:"Job ID"`
	Time  int    `flag:"time" usage:"Time in minutes"`
}

type JobResumeCommand struct {
	JobID string `flag:"job-id" usage:"Job ID"`
}

type JobTerminateCommand struct {
	JobID string `flag:"job-id" usage:"Job ID"`
}

type JobSuspendCommand struct {
	JobID string `flag:"job-id" usage:"Job ID"`
}

var JobCommands = map[string]CommandFunc{
	"rename":    func() Command { return &JobRenameCommand{} },
	"search":    func() Command { return &JobSearchCommand{} },
	"suspend":   func() Command { return &JobSuspendCommand{} },
	"extend":    func() Command { return &JobExtendCommand{} },
	"get":       func() Command { return &JobGetCommand{} },
	"create":    func() Command { return &JobCreateCommand{} },
	"delete":    func() Command { return &JobDeleteCommand{} },
	"terminate": func() Command { return &JobTerminateCommand{} },
	"resume":    func() Command { return &JobResumeCommand{} },
}

func (c JobRenameCommand) Execute() error {
	return fmt.Errorf("job rename not implemented")
}

func (c JobSearchCommand) Execute() error {
	return fmt.Errorf("job search not implemented")
}

func (c JobSuspendCommand) Execute() error {
	return fmt.Errorf("job suspend not implemented")
}

func (c JobExtendCommand) Execute() error {
	return fmt.Errorf("job extend not implemented")
}

func (c JobGetCommand) Execute() error {
	return fmt.Errorf("job get not implemented")
}

func (c JobCreateCommand) Execute() error {
	return fmt.Errorf("job create not implemented")
}

func (c JobDeleteCommand) Execute() error {
	return fmt.Errorf("job delete not implemented")
}

func (c JobTerminateCommand) Execute() error {
	return fmt.Errorf("job terminate not implemented")
}

func (c JobResumeCommand) Execute() error {
	return fmt.Errorf("job resume not implemented")
}
