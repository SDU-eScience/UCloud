package slurm

import (
	"fmt"
	"golang.org/x/sys/unix"
	"os"
	"time"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/log"
	"ucloud.dk/pkg/util"
)

func handleFolderShell(session *ctrl.ShellSession, cols, rows int) {
	homedir, err := os.UserHomeDir()
	workDir := util.OptNone[string]()
	if err == nil {
		workDir.Set(homedir)
	}

	internalPath, ok := UCloudToInternal(session.Folder)
	if ok {
		workDir.Set(internalPath)
	}

	command := []string{"/bin/bash", "--login"}

	masterFd, pid, err := CreateAndForkPty(command, nil, workDir)
	if err != nil {
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

	masterFd.Close()
	unix.Kill(int(pid), unix.SIGTERM)
}

func handleJobShell(session *ctrl.ShellSession, cols, rows int) {
	parsedId, ok := parseJobProviderId(session.Job.ProviderGeneratedId)
	if !ok {
		return
	}

	nodes := SlurmClient.JobGetNodeList(parsedId.SlurmId)
	if len(nodes) == 0 {
		return
	}

	command := []string{
		"srun", "--overlap", "--pty", "--jobid", fmt.Sprint(parsedId.SlurmId), "-w", nodes[session.Rank],
	}

	homedir, err := os.UserHomeDir()
	if err == nil {
		command = append(command, "--chdir", homedir)
	}

	command = append(command, "/bin/bash", "--login")

	masterFd, pid, err := CreateAndForkPty(command, nil, util.OptNone[string]())
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
			switch event.Type {
			case ctrl.ShellEventTypeInput:
				_, err = masterFd.Write([]byte(event.Data))
				if err != nil {
					session.Alive = false
					break
				}

			case ctrl.ShellEventTypeResize:
				ResizePty(masterFd, event.Cols, event.Rows)
			}

		case _ = <-time.After(1 * time.Second):
			continue
		}
	}

	masterFd.Close()
	unix.Kill(int(pid), unix.SIGTERM)
}

func handleShell(session *ctrl.ShellSession, cols, rows int) {
	if session.Folder == "" {
		handleJobShell(session, cols, rows)
	} else {
		handleFolderShell(session, cols, rows)
	}
}
