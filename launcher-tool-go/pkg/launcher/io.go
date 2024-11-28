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

type abstractLFile struct {
	LFile
}

type ExecutableCommand struct {
	args             []string
	workingDir       LFile
	postProcessor    string
	allowFailure     bool
	deadlineInMillis int64
	streamOutput     bool
}

func NewExecutableCommand(
	args []string,
	workingDir LFile,
	postProcessor string,
	allowFailure bool,
	deadlineInMillis int64,
	streamOutput bool,
) ExecutableCommand {
	return ExecutableCommand{
		args:             args,
		workingDir:       workingDir,
		postProcessor:    postProcessor,
		allowFailure:     allowFailure,
		deadlineInMillis: deadlineInMillis,
		streamOutput:     streamOutput,
	}
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
