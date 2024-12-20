package launcher

import (
	"bufio"
	"fmt"
	"io"
	"log"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"time"
)

type LocalFile struct {
	path string
}

func NewLocalFile(path string) LocalFile {
	return LocalFile{
		path: path,
	}
}

func (lf LocalFile) GetAbsolutePath() string {
	return lf.path
}

func (lf LocalFile) Exists() bool {
	file, err := os.Open(lf.path)
	defer file.Close()
	if err != nil {
		return false
	} else {
		return true
	}
}

func (lf LocalFile) Child(subPath string) LFile {
	var file *os.File
	file, err := os.Open(lf.GetAbsolutePath() + subPath)
	if err != nil {
		file, err = os.Create(filepath.Join(lf.GetAbsolutePath(), subPath))
		HardCheck(err)
	}
	return NewLocalFile(file.Name())
}

func (lf LocalFile) WriteBytes(bytes []byte) {
	f, err := os.Create(lf.path)
	HardCheck(err)
	_, err = f.Write(bytes)
	SoftCheck(err)
}

func (lf LocalFile) WriteText(str string) {
	f, err := os.Create(lf.path)
	HardCheck(err)
	_, err = f.WriteString(str)
	SoftCheck(err)
}

func (lf LocalFile) AppendText(str string) {
	f, err := os.OpenFile(lf.path,
		os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0644)
	SoftCheck(err)
	defer f.Close()
	if _, err := f.WriteString(str); err != nil {
		log.Println(err)
	}
}

func (lf LocalFile) Delete() {
	err := os.RemoveAll(lf.path)
	HardCheck(err)
}

func (lf LocalFile) MkDirs() {
	err := os.MkdirAll(lf.path, 0644)
	HardCheck(err)
}

func (lf LocalFile) Name() string {
	return filepath.Base(lf.path)
}

type LocalExecutableCommand struct {
	args             []string
	workingDir       LFile
	fn               postProcessor
	allowFailure     bool
	deadlineInMillis int64
	streamOutput     bool
}

func NewLocalExecutableCommand(
	args []string,
	workingDir LFile,
	fn postProcessor,
	allowFailure bool,
	deadlineInMillis int64,
	streamOutput bool,
) *LocalExecutableCommand {
	return &LocalExecutableCommand{
		args:             args,
		workingDir:       workingDir,
		fn:               fn,
		allowFailure:     allowFailure,
		deadlineInMillis: deadlineInMillis,
		streamOutput:     streamOutput,
	}
}

func (l LocalExecutableCommand) SetAllowFailure() {
	l.allowFailure = true
}

func (l LocalExecutableCommand) SetStreamOutput() {
	l.streamOutput = true
}

func (l LocalExecutableCommand) ToBashScript() string {
	sb := new(strings.Builder)
	if l.workingDir != nil {
		sb.WriteString("cd " + l.workingDir.GetAbsolutePath())
	}
	for _, arg := range l.args {
		sb.WriteString(EscapeBash(arg) + " ")
	}
	return sb.String()
}

func logBuilderThread(buf *bufio.Reader, stringBuilder *strings.Builder, deadline int64) {
	for time.Now().UnixMilli() < deadline {
		line, _, err := buf.ReadLine()
		if err != nil {
			if err == io.EOF {
				break
			} else {
				panic(err)
			}
		}
		stringBuilder.Write(line)
	}
}

func (l LocalExecutableCommand) ExecuteToText() StringPair {
	if DebugCommandsGiven() {
		fmt.Println("Command: " + strings.Join(l.args, " "))
	}

	fmt.Println("Command: " + strings.Join(l.args, " "))

	deadline := time.Now().UnixMilli() + l.deadlineInMillis

	//TODO(PROCESS BUILDER)
	var procAttr = os.ProcAttr{
		Dir:   "",
		Env:   nil,
		Files: nil,
		Sys:   nil,
	}

	if l.workingDir != nil {
		procAttr.Dir = l.workingDir.GetAbsolutePath()
	}
	procAttr.Files = []*os.File{os.Stdin, os.Stdout, os.Stderr}

	proc, err := os.StartProcess(l.args[0], l.args[1:], &procAttr)
	HardCheck(err)

	err = os.Stdout.Close()
	HardCheck(err)
	outputReader := bufio.NewReader(os.Stdin)
	errorReader := bufio.NewReader(os.Stderr)

	outputBuilder := &strings.Builder{}
	errBuilder := &strings.Builder{}

	var wg sync.WaitGroup

	wg.Add(1)
	go logBuilderThread(outputReader, outputBuilder, deadline)
	wg.Add(1)
	go logBuilderThread(errorReader, errBuilder, deadline)
	wg.Wait()

	err = os.Stdout.Close()
	SoftCheck(err)
	err = os.Stderr.Close()
	SoftCheck(err)
	procStat, err := proc.Wait()
	SoftCheck(err)
	if !procStat.Exited() {
		err = proc.Kill()
		SoftCheck(err)
	}
	exitCode := procStat.ExitCode()

	if DebugCommandsGiven() {
		fmt.Println("  Exit code ", strconv.Itoa(exitCode))
		fmt.Println("  Stdout ", outputBuilder.String())
		fmt.Println("  Stderr ", errBuilder.String())
	}

	if exitCode != 0 {
		if l.allowFailure {
			return StringPair{First: "", Second: outputBuilder.String() + errBuilder.String()}
		}

		fmt.Println("Command failed!")
		fmt.Println("Command ", strings.Join(l.args, " "))
		fmt.Println("Directory: ", l.workingDir.GetAbsolutePath())
		fmt.Println("Exit code: ", strconv.Itoa(exitCode))
		fmt.Println("Stdout ", outputBuilder.String())
		fmt.Println("Stderr ", errBuilder.String())
		os.Exit(exitCode)
	}

	return StringPair{
		l.fn(
			ProcessResultText{
				statusCode: exitCode,
				stdout:     outputBuilder.String(),
				stderr:     errBuilder.String(),
			},
		),
		"",
	}
}
