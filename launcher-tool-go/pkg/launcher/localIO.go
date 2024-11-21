package launcher

import (
	"fmt"
	"log"
	"os"
	"path/filepath"
	"strings"
	"time"
)

type LocalFile struct {
	File *os.File
	abstractLFile
}

func (lf LocalFile) GetFile() *os.File {
	file, err := os.Open(lf.File.Name())
	HardCheck(err)
	return file
}

func NewLocalFile(path string) LocalFile {
	newFile, err := os.Create(path)
	HardCheck(err)
	return LocalFile{
		newFile,
		abstractLFile{},
	}
}

func (lf LocalFile) GetAbsolutePath() string {
	abs, err := filepath.Abs(lf.File.Name())
	HardCheck(err)
	return abs
}

func (lf LocalFile) Exists() bool {
	_, err := lf.File.Stat()
	if err != nil {
		return false
	} else {
		return true
	}
}

func (lf LocalFile) Child(subPath string) LocalFile {
	var file *os.File
	file, err := os.Open(lf.GetAbsolutePath() + subPath)
	if err != nil {
		file, err = os.Create(filepath.Join(lf.GetAbsolutePath(), subPath))
		HardCheck(err)
	}
	return NewLocalFile(file.Name())
}

func (lf LocalFile) WriteBytes(bytes []byte) {
	f, err := os.Create(lf.File.Name())
	HardCheck(err)
	_, err = f.Write(bytes)
	SoftCheck(err)
}

func (lf LocalFile) WriteText(str string) {
	f, err := os.Create(lf.File.Name())
	HardCheck(err)
	_, err = f.WriteString(str)
	SoftCheck(err)
}

func (lf LocalFile) AppendText(str string) {
	f, err := os.OpenFile(lf.File.Name(),
		os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0644)
	SoftCheck(err)
	defer f.Close()
	if _, err := f.WriteString(str); err != nil {
		log.Println(err)
	}
}

func (lf LocalFile) Delete() {
	err := os.RemoveAll(lf.File.Name())
	HardCheck(err)
}

func (lf LocalFile) MkDirs() {
	err := os.MkdirAll(lf.File.Name(), 0644)
	HardCheck(err)
}

type LocalExecutableCommand struct {
	ExecutableCommand
}

func (lec LocalExecutableCommand) ToBashScript() string {
	sb := new(strings.Builder)
	if lec.workingDir != nil {
		sb.WriteString("cd " + lec.workingDir.GetAbsolutePath())
	}
	for _, arg := range lec.args {
		sb.WriteString(escapeBash(arg) + " ")
	}
	return sb.String()
}

func (lec LocalExecutableCommand) ExecuteToText() {
	if DebugCommandsGiven() {
		fmt.Println("Command: " + strings.Join(lec.args, " "))
	}

	deadline := time.Now().UnixMilli() + lec.deadlineInMillis

	//TODO()
	fmt.Println(deadline)
}
