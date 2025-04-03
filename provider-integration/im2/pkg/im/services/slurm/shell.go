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
	log.Info("Folder shell")
	homedir, err := os.UserHomeDir()
	workDir := util.OptNone[string]()
	if err == nil {
		log.Info("Fs 1")
		workDir.Set(homedir)
	}

	internalPath, ok := UCloudToInternal(session.Folder)
	if ok {
		log.Info("Fs 2")
		workDir.Set(internalPath)
	}

	command := []string{"/bin/bash", "--login"}

	log.Info("Fs create and fork pty")
	masterFd, pid, err := CreateAndForkPty(command, nil, workDir)
	if err != nil {
		log.Info("Fs 3: %s", err)
		fmt.Println("Error:", err)
		return
	}

	log.Info("Fs 4")
	ResizePty(masterFd, cols, rows)
	log.Info("Fs 5")

	go func() {
		log.Info("Fs read ready")
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
		log.Info("Fs read break")
	}()

	for util.IsAlive && session.Alive {
		select {
		case event := <-session.InputEvents:
			switch event.Type {
			case ctrl.ShellEventTypeInput:
				log.Info("Fs 7")
				_, err = masterFd.Write([]byte(event.Data))
				if err != nil {
					log.Info("Fs 8")
					session.Alive = false
					log.Info("Error while writing to master: %v", err)
					break
				}

			case ctrl.ShellEventTypeResize:
				log.Info("Fs 9")
				ResizePty(masterFd, event.Cols, event.Rows)
			}

		case _ = <-time.After(1 * time.Second):
			log.Info("Fs 10")
			continue
		}
	}

	log.Info("Fs 11")
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

func handleShell(session *ctrl.ShellSession, cols, rows int) {
	log.Info("handleShell(%v, %v, %v, %v)", session.Folder, session.Job, cols, rows)
	if session.Folder == "" {
		handleJobShell(session, cols, rows)
	} else {
		handleFolderShell(session, cols, rows)
	}
}
