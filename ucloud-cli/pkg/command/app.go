package command

type AppCommand struct {
	Command string // list | search | get
	Args    []string
}

func appHelp() string {
	return "app list | search | get"
}
