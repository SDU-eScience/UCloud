package command

type Command interface {
	run() error
}
