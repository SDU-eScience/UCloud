package command

type Command interface {
	Execute() error
}
type CommandFunc func() Command

type ArgumentParser interface {
	ParseArguments(args []string) error
}

type HelpNotes interface {
	HelpNotes() []string
}
