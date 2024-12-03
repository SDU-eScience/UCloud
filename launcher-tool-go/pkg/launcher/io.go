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
	setStreamOutput()
	setAllowFailure()
}

type postProcessor func(text ProcessResultText) string

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
