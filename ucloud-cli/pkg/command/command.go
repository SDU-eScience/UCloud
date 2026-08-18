package command

type Command interface {
	Execute() error
}
type CommandFunc func() Command
