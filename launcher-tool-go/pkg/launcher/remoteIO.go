package launcher

import (
	"fmt"
	"github.com/google/uuid"
	"golang.org/x/crypto/ssh"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"ucloud.dk/launcher/pkg/termio"
)

var disableRemoteFileWriting = false

type SSHConnection struct {
	username   string
	host       string
	ssh        *ssh.Client
	remoteRoot string
}

func newSSHConnection(username string, host string) SSHConnection {
	config := &ssh.ClientConfig{
		User:              username,
		Auth:              ssh.AuthMethod(
								ssh.),
		HostKeyCallback:   nil,
		BannerCallback:    nil,
		ClientVersion:     "",
		HostKeyAlgorithms: nil,
		Timeout:           0,
	}
	connector := ssh.Client{}
	return SSHConnection{
		username:   username,
		host:       host,
		ssh:        ssh,
		remoteRoot: remoteRoot,
	}
}

func SyncRepository() {
	command, ok := commandType.(RemoteExecutableCommand)
	if ok {
		conn := command.connection
		termio.LoadingIndicator(
			"Synchronizing repository with remote",
			func(output *os.File) error {
				local := NewLocalExecutableCommand(
					[]string{
						"rsync",
						"-zvhPr",
						"--no-o",
						"--no-g",
						"--exclude=/.git",
						"--exclude=/.compose",
						"--filter=:- .gitignore",
						"--delete",
						".",
						conn.username + "@" + conn.host + ":ucloud",
					},
					nil,
					postProcessor(),
					false,
					1000*60*5,
					false,
				)
				local.setAllowFailure()
				local.setStreamOutput()
				local.ExecuteToText()
				return nil
			})
	} else {
		return
	}

}

type RemoteFile struct {
	connection SSHConnection
	path       string
}

func NewRemoteFile(connection SSHConnection, path string) RemoteFile {
	return RemoteFile{connection, path}
}

func (r RemoteFile) Name() string {
	return filepath.Base(r.path)
}

func (r RemoteFile) GetAbsolutePath() string {
	if (r.path[0] != '/') {
		return r.connection.remoteRoot + "/" + r.path[1:]
	} else {
		return r.path
	}
}

func (r RemoteFile) Exists() bool {

}

func (r RemoteFile) Child(subPath string) LFile {}

func (r RemoteFile) WriteText(text string) {}

func (r RemoteFile) WriteBytes(bytes []byte) {}

func (r RemoteFile) AppendText(text string) {}

func (r RemoteFile) Delete() {}

func (r RemoteFile) MkDirs() {}


type RemoteExecutableCommand struct {
	connection 		 SSHConnection
	args             []string
	workingDir       LFile
	fn               postProcessor
	allowFailure     bool
	deadlineInMillis int64
	streamOutput     bool
}

// Old Factory
func NewRemoteExecutableCommand(
	connection SSHConnection,
	args []string,
	workingDir LFile,
	fn postProcessor,
	allowFailure bool,
	deadlineInMillis int64,
	streamOutput bool,
) *RemoteExecutableCommand {
	return &RemoteExecutableCommand{
		connection: 	  connection,
		args:             args,
		workingDir:       workingDir,
		fn:               fn,
		allowFailure:     allowFailure,
		deadlineInMillis: deadlineInMillis,
		streamOutput:     streamOutput,
	}
}

func (r RemoteExecutableCommand) setStreamOutput()  {
	r.streamOutput = true
}

func (r RemoteExecutableCommand) setAllowFailure() {
	r.allowFailure = true
}

func (r RemoteExecutableCommand) ToBashScript() string {
	sb := new(strings.Builder)
	sb.WriteString("ssh -t ")
	sb.WriteString(r.connection.username)
	sb.WriteString("@")
	sb.WriteString(r.connection.host)
	sb.WriteString(" ")
	sb.WriteString(`"`)
	if r.workingDir != nil {
		sb.WriteString("cd '" +  escapeBash(r.workingDir.GetAbsolutePath()) + "'; ")
	}
	for _, arg := range r.args {
		sb.WriteString("'" + escapeBash(arg) + "'")
		sb.WriteString(" ")
	}
	sb.WriteString(`"`)
	return sb.String()
}

func stdoutThread(connection SSHConnection, sb *strings.Builder, boundary string, exitCode *int, command RemoteExecutableCommand) {
	for {
		line := connection.shellOutput.readLine()
		if line[:len(boundary)] == boundary {
			exitCode = line[len(boundary)+1:]
			break
		} else {
			if command.streamOutput { printStatus(line)  }
			sb.WriteString(line)
		}
	}
}

func stderrThread(connection SSHConnection, sb *strings.Builder, boundary string, command RemoteExecutableCommand) {
	for {
		line := connection.shellOutput.readLine()
		if line[:len(boundary)] == boundary {
			break
		} else {
			if command.streamOutput { printStatus(line)  }
			sb.WriteString(line)
		}
	}
}

func (r RemoteExecutableCommand) ExecuteToText() StringPair {
	boundary, err := uuid.NewUUID()
	HardCheck(err)

	sb := new(strings.Builder)
	if r.workingDir != nil {
		sb.WriteString("cd '" + escapeBash(r.workingDir.GetAbsolutePath())+ "';")
	}
	for _, arg := range r.args {
		sb.WriteString("'" +  arg + "'")
		sb.WriteString(" ")
	}
	sb.WriteString(" < /dev/null")
	sb.WriteString("st=${'$'}?")
	sb.WriteString("echo")
	sb.WriteString("echo "+ boundary.String() + "-$st")
	sb.WriteString("echo 1>&2 " + boundary.String())

	r.connection.shell.outputStream.write(
		[]byte(sb.String()),
	)

	r.connection.shell.outputStream.flush()

	outputBuilder := &strings.Builder{}
	errBuilder := &strings.Builder{}
	exitCode := 0

	var wg sync.WaitGroup

	wg.Add(1)
	go stdoutThread(r.connection, outputBuilder, boundary.String(), &exitCode, r)
	wg.Add(1)
	go stderrThread(r.connection, errBuilder, boundary.String(), r)
	wg.Wait()

	output := outputBuilder.String()
	errors := errBuilder.String()

	if exitCode != 0 {
		if r.allowFailure {
			return StringPair{"", output + errors}
		} else {
			fmt.Println("Command failed!")
			builder := strings.Builder{}
			for _, arg := range r.args {
				builder.WriteString("'" + escapeBash(arg) + "'")
			}
			fmt.Println("Command ", builder.String())
			fmt.Println("Directory: ", r.workingDir.GetAbsolutePath())
			fmt.Println("Exit Code: ", exitCode)
			fmt.Println("Stdout: ", output)
			fmt.Println("Stderr: ", errors)
			os.Exit(exitCode)
		}
	}
	return StringPair{output, ""}
}