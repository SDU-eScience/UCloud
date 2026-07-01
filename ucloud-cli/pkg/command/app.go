package command

func appHelp() string {
	return "app list | search | get"
}

type AppListCommand struct {
}
type AppSearchCommand struct {
	Application string
}

type AppGetCommand struct {
	Application string
}
