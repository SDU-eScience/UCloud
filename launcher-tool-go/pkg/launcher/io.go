package launcher

import (
	"fmt"
	"os"
)

type LFile interface {
	GetAbsolutePath() string
	GetFile() *os.File
	Exists() bool
	Child(subPath string) LFile
	WriteText(text string)
	WriteBytes(bytes []byte)
	AppendText(text string)
	Delete()
	MkDirs()
	Name() string
}

type ExecutableCommandInterface interface {
	ToBashScript() string
	ExecuteToText() StringPair
}

type ExecutableCommand struct {
	args             []string
	workingDir       LFile
	fn               postProcessor
	allowFailure     bool
	deadlineInMillis int64
	streamOutput     bool
}

func (e ExecutableCommand) setStreamOutput() ExecutableCommand {
	e.streamOutput = true
	return e
}

func (e ExecutableCommand) setAllowFailure() ExecutableCommand {
	e.allowFailure = true
	return e
}

type ProcessResultText struct {
	statusCode int
	stdout     string
	stderr     string
}

func escapeBash(value string) string {
	//TODO()
	fmt.Println("FUCK OU")
	return value
}
