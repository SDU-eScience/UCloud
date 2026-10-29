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

type CommandNode struct {
	Factory  CommandFunc
	Children map[string]*CommandNode
}

func commandLeaf(factory CommandFunc) *CommandNode {
	return &CommandNode{Factory: factory}
}

func commandGroup(children map[string]*CommandNode) *CommandNode {
	return &CommandNode{Children: children}
}
