package command

import (
	"fmt"
)

type JobListCommand struct {
	State       string
	Application string
	Provider    string
}

func (c JobListCommand) Execute() error {
	return fmt.Errorf("job list not implemented")
}

type JobGetCommand struct {
	JobID string
}

func (c JobGetCommand) Execute() error {
	return fmt.Errorf("job get not implemented")
}

type JobCreateCommand struct {
	JobName     string
	Application string
	Product     string
	Name        string
	Time        int
	SSH         bool
	Folder      string
	PublicLink  string
	Parameters  map[string]string
}

func (c JobCreateCommand) Execute() error {
	return fmt.Errorf("job create not implemented")
}

type JobDeleteCommand struct {
	JobID string
}

func (c JobDeleteCommand) Execute() error {
	return fmt.Errorf("job delete not implemented")
}

type JobRenameCommand struct {
	JobID   string
	NewName string
}

func (c JobRenameCommand) Execute() error {
	return fmt.Errorf("job rename not implemented")
}

type JobSearchCommand struct {
	JobName string
}

func (c JobSearchCommand) Execute() error {
	return fmt.Errorf("job search not implemented")
}

type JobExtendCommand struct {
	JobID string
	Time  int
}

func (c JobExtendCommand) Execute() error {
	return fmt.Errorf("job extend not implemented")
}

type JobResumeCommand struct {
	JobID string
}

func (c JobResumeCommand) Execute() error {
	return fmt.Errorf("job resume not implemented")
}

type JobTerminateCommand struct {
	JobID string
}

func (c JobTerminateCommand) Execute() error {
	return fmt.Errorf("job terminate not implemented")
}

type JobSuspendCommand struct {
	JobID string
}

func (c JobSuspendCommand) Execute() error {
	return fmt.Errorf("job suspend not implemented")
}

// Interactive Access
