package command

import (
	_ "ucloud.dk/shared/pkg/cli"
)

type JobCommand struct {
	Verb string
	Args []string
	Flag map[string]string
}
