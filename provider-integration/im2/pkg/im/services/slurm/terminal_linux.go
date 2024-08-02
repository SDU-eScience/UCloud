package slurm

/*
#cgo LDFLAGS: -lutil

#include <stdio.h>
#include <stdlib.h>
#include <pty.h>
#include <unistd.h>
#include <string.h>
#include <sys/ioctl.h>

int createAndForkPty(char **cmd, char **envp, int *masterFd) {
	struct winsize winp;
	memset(&winp, 0, sizeof(winp));
	winp.ws_col = 80;
	winp.ws_row = 25;

	pid_t pid = forkpty(masterFd, NULL, NULL, &winp);
	if (pid == 0) {
		setenv("TERM", "xterm", 1);

		for (char **env = envp; *env != 0; env++) {
			putenv(*env);
		}

		execvp(cmd[0], cmd);

		printf("Failed to start command!\n");
		exit(1);
	}
	return pid;
}

void resizePty(int masterFd, int cols, int rows) {
	struct winsize winp = {0};
	winp.ws_col = cols;
	winp.ws_row = rows;
	ioctl(masterFd, TIOCSWINSZ, &winp);
}
*/
import "C"
import (
	"fmt"
	"os"
	"time"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/log"
	"ucloud.dk/pkg/util"
	"unsafe"
)

func CreateAndForkPty(command []string, envArray []string) (*os.File, error) {
	cmd := make([]*C.char, len(command)+1)
	for i, s := range command {
		cmd[i] = C.CString(s)
		defer C.free(unsafe.Pointer(cmd[i]))
	}
	cmd[len(command)] = nil

	env := make([]*C.char, len(envArray)+1)
	for i, s := range envArray {
		env[i] = C.CString(s)
		defer C.free(unsafe.Pointer(env[i]))
	}
	env[len(envArray)] = nil

	var masterFd C.int
	pid := C.createAndForkPty(&cmd[0], &env[0], &masterFd)
	if pid == 0 {
		return nil, fmt.Errorf("fork failed")
	}

	return os.NewFile(uintptr(masterFd), "pty"), nil
}

func ResizePty(masterFd *os.File, cols, rows int) {
	if masterFd == nil {
		return
	}

	C.resizePty(C.int(masterFd.Fd()), C.int(cols), C.int(rows))
}

func handleShell(session *ctrl.ShellSession, cols, rows int) {
	parsedId, ok := parseProviderId(session.Job.ProviderGeneratedId)
	if !ok {
		return
	}

	nodes := SlurmClient.JobGetNodeList(parsedId.SlurmId)
	if len(nodes) == 0 {
		return
	}

	log.Info("Starting shell to %v with size %v x %v", session.Job.Id, cols, rows)

	command := []string{
		"srun", "--overlap", "--pty", "--jobid", fmt.Sprint(parsedId.SlurmId), "-w", nodes[session.Rank],
		"/bin/bash", "--login",
	}
	// envArray := []string{"VAR1=value1", "VAR2=value2"}

	masterFd, err := CreateAndForkPty(command, nil)
	if err != nil {
		fmt.Println("Error:", err)
		return
	}

	ResizePty(masterFd, cols, rows)

	go func() {
		readBuffer := make([]byte, 1024*4)
		for util.IsAlive && session.Alive {
			_ = masterFd.SetReadDeadline(time.Now().Add(1 * time.Second))
			count, err := masterFd.Read(readBuffer)
			if err != nil {
				session.Alive = false
				log.Info("Error while reading from master: %v", err)
				break
			}

			if count > 0 {
				session.EmitData(readBuffer[:count])
			}
		}
	}()

	for util.IsAlive && session.Alive {
		select {
		case event := <-session.InputEvents:
			log.Info("Got event: %v", event)
			switch event.Type {
			case ctrl.ShellEventTypeInput:
				_, err = masterFd.Write([]byte(event.Data))
				if err != nil {
					session.Alive = false
					log.Info("Error while writing to master: %v", err)
					break
				}

			case ctrl.ShellEventTypeResize:
				ResizePty(masterFd, event.Cols, event.Rows)
			}

		case _ = <-time.After(1 * time.Second):
			continue
		}
	}
}
