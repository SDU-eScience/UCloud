package launcher

import (
	"bufio"
	"fmt"
	"log"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"time"

	"ucloud.dk/launcher/pkg/termio"
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
	_, err := os.Open(lf.path)
	if err != nil {
		return false
	} else {
		return true
	}
}

func (lf LocalFile) Child(subPath string, isDir bool) LFile {
	if isDir {
		err := os.MkdirAll(filepath.Join(lf.path, subPath), 0755)
		HardCheck(err)
		return NewLocalFile(filepath.Join(lf.path, subPath))
	} else {
		file, err := os.OpenFile(filepath.Join(lf.path, subPath), os.O_APPEND|os.O_CREATE|os.O_RDWR, 0666)
		HardCheck(err)
		return NewLocalFile(file.Name())
	}
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
	err := os.MkdirAll(lf.path, 0755)
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
) *LocalExecutableCommand {
	return &LocalExecutableCommand{
		args:             args,
		workingDir:       workingDir,
		fn:               fn,
		allowFailure:     false,
		deadlineInMillis: 1000 * 60 * 5,
		streamOutput:     false,
	}
}

func (l *LocalExecutableCommand) SetDeadline(deadlineInMillis int64) {
	l.deadlineInMillis = deadlineInMillis
}

func (l *LocalExecutableCommand) SetAllowFailure() {
	l.allowFailure = true
}

func (l *LocalExecutableCommand) SetStreamOutput() {
	l.streamOutput = true
}

func (l *LocalExecutableCommand) ToBashScript() string {
	sb := new(strings.Builder)
	if l.workingDir != nil {
		sb.WriteString("cd '" + l.workingDir.GetAbsolutePath() + "'\n")
	}
	for _, arg := range l.args {
		sb.WriteString(EscapeBash(arg) + " ")
	}
	sb.WriteString("\n")
	return sb.String()
}

func (l *LocalExecutableCommand) ExecuteToText() StringPair {
	if DebugCommandsGiven() {
		fmt.Println("Command: " + strings.Join(l.args, " "))
	}

	deadline := time.Now().UnixMilli() + l.deadlineInMillis

	dir := ""
	if l.workingDir != nil {
		dir = l.workingDir.GetAbsolutePath()
	}

	cmd := exec.Command(l.args[0], l.args[1:]...)
	cmd.Dir = dir

	stdout, err := cmd.StdoutPipe()
	SoftCheck(err)
	outputReader := bufio.NewReader(stdout)
	stderr, err := cmd.StderrPipe()
	SoftCheck(err)
	errorReader := bufio.NewReader(stderr)

	outputBuilder := &strings.Builder{}
	errBuilder := &strings.Builder{}

	err = cmd.Start()
	SoftCheck(err)

	wg := sync.WaitGroup{}

	wg.Add(1)
	go func() {
		defer wg.Done()
		for time.Now().UnixMilli() < deadline {
			str, err := outputReader.ReadString('\n')
			if len(str) == 0 && err != nil {
				break
			}
			if l.streamOutput {
				termio.Write("%s", str)
			}
			outputBuilder.Write([]byte(str))
		}
	}()

	wg.Add(1)
	go func() {
		defer wg.Done()
		for time.Now().UnixMilli() < deadline {
			str, err := errorReader.ReadString('\n')
			if len(str) == 0 && err != nil {
				break
			}
			if l.streamOutput {
				termio.Write("%s", str)
			}
			errBuilder.Write([]byte(str))
		}
	}()

	err = cmd.Wait()
	SoftCheck(err)

	wg.Wait()

	exitCode := cmd.ProcessState.ExitCode()

	if DebugCommandsGiven() {
		fmt.Println("  Exit code ", strconv.Itoa(exitCode))
		fmt.Println("  Stdout ", outputBuilder.String())
		fmt.Println("  Stderr ", errBuilder.String())
	}

	if exitCode != 0 {
		if l.allowFailure {
			return StringPair{First: "", Second: outputBuilder.String() + errBuilder.String(), StatusCode: exitCode}
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
		exitCode,
	}
}
