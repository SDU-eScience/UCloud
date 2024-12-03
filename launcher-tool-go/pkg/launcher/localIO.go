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
	File *os.File
}

func NewLocalFile(path string) LocalFile {
	newFile, err := os.Create(path)
	HardCheck(err)
	return LocalFile{
		path: path,
		File: newFile,
	}
}

func (lf LocalFile) GetAbsolutePath() string {
	abs, err := filepath.Abs(lf.File.Name())
	HardCheck(err)
	return abs
}

func (lf LocalFile) GetFile() *os.File {
	file, err := os.Open(lf.File.Name())
	HardCheck(err)
	return file
}

func (lf LocalFile) Exists() bool {
	_, err := lf.File.Stat()
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

// Old Factory
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

func (l LocalExecutableCommand) setAllowFailure(allowFailure bool) {
	l.allowFailure = allowFailure
}

func (l LocalExecutableCommand) setStreamOutput(streamOutput bool) {
	l.streamOutput = streamOutput
}

func (l LocalExecutableCommand) ToBashScript() string {
	sb := new(strings.Builder)
	if l.workingDir != nil {
		sb.WriteString("cd " + l.workingDir.GetAbsolutePath())
	}
	for _, arg := range l.args {
		sb.WriteString(escapeBash(arg) + " ")
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

	deadline := time.Now().UnixMilli() + l.deadlineInMillis

	//TODO(PROCESS BUILDER)
	var procAttr *os.ProcAttr
	if l.workingDir != nil {
		procAttr.Dir = l.workingDir.GetAbsolutePath()
	}
	procAttr.Files = []*os.File{os.Stdin, os.Stdout, os.Stderr}

	proc, err := os.StartProcess("name", l.args, procAttr)
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
			return StringPair{first: "", second: outputBuilder.String() + errBuilder.String()}
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
