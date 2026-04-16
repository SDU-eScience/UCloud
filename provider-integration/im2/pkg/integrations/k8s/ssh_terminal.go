package k8s

import (
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	wish "charm.land/wish/v2"
	"github.com/charmbracelet/ssh"
	"ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/filesystem"
	"ucloud.dk/pkg/integrations/k8s/shared"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

const sshTerminalPort = 43202

const sshTerminalMenuSequence = "\x1b]ucloud;menu\x07"

func initSshTerminal() {
	if !shared.ServiceConfig.Compute.IntegratedTerminal.Enabled || !util.DevelopmentModeEnabled() {
		return
	}

	server, err := wish.NewServer(
		wish.WithAddress(fmt.Sprintf(":%d", sshTerminalPort)),
		wish.WithHostKeyPath(filepath.Join(os.TempDir(), "ucloud-k8s-integrated-terminal-ssh-hostkey")),
		wish.WithPublicKeyAuth(func(ctx ssh.Context, key ssh.PublicKey) bool {
			owner, ok := controller.SshKeyResolveOwner(key)
			if !ok {
				return false
			}

			ctx.SetValue("owner", owner)
			return true
		}),
		wish.WithMiddleware(func(next ssh.Handler) ssh.Handler {
			return func(sess ssh.Session) {
				if err := handleSshTerminalSession(sess); err != nil {
					wish.Fatalln(sess, err.Error())
					return
				}
			}
		}),
	)
	if err != nil {
		log.Warn("Failed to create integrated terminal SSH server: %s", err)
		return
	}

	go func() {
		log.Info("Starting integrated terminal SSH server on port %d", sshTerminalPort)
		if err := server.ListenAndServe(); err != nil && !errors.Is(err, ssh.ErrServerClosed) {
			log.Warn("Integrated terminal SSH server stopped unexpectedly: %s", err)
		}
	}()
}

func handleSshTerminalSession(sess ssh.Session) *util.HttpError {
	defer func() {
		_ = sess.Exit(0)
		_ = sess.Close()
	}()

	ownerName, ok := sess.Context().Value("owner").(string)
	if !ok {
		return util.UserHttpError("Internal error")
	}
	owner := orc.ResourceOwner{CreatedBy: ownerName}

	if sess.Subsystem() == "sftp" {
		log.Info("SFTP!")
		return handleSshTerminalSftpSession(sess, owner)
	}

	log.Info("subsystem: %s", sess.Subsystem())

	var folders []string
	{
		internalPath, drive, err := filesystem.InitializeMemberFiles(owner.CreatedBy, owner.Project)
		if err == nil {
			ucloudPath, ok := filesystem.InternalToUCloudWithDrive(drive, internalPath)
			if ok {
				folders = []string{ucloudPath}
			}
		}
	}
	sandbox, err := shared.TerminalOpen(owner, folders)
	if err != nil {
		return util.UserHttpError("No active integrated terminal folder session was found")
	}

	command := sess.Command()
	if len(command) == 0 {
		command = []string{"/bin/bash"}
	}

	log.Info("Command is: %#v", command)

	hasCommand := len(sess.Command()) > 0
	pty, winCh, hasPty := sess.Pty()

	cmd := sandbox.Command(command[0], command[1:]...)
	cmd.TTY = hasPty
	if hasPty {
		cmd.Cols = pty.Window.Width
		cmd.Rows = pty.Window.Height
	}

	stdinReader, stdinWriter := io.Pipe()
	stdoutReader, stdoutWriter := io.Pipe()
	stderrReader, stderrWriter := io.Pipe()
	defer util.SilentClose(stdinReader)
	defer util.SilentClose(stdinWriter)
	defer util.SilentClose(stdoutReader)
	defer util.SilentClose(stdoutWriter)
	defer util.SilentClose(stderrReader)
	defer util.SilentClose(stderrWriter)

	cmd.Stdin = stdinReader
	cmd.Stdout = stdoutWriter
	cmd.Stderr = stderrWriter

	done := make(chan struct{})
	commandDone := make(chan struct{})
	outputChannel := make(chan []byte, 32)
	outputStarted := make(chan struct{})
	var outputStartedOnce sync.Once
	var outputReaders sync.WaitGroup
	outputReaders.Add(2)

	if !hasCommand {
		go func() {
			clearLine := []byte("\033[2K\r")
			spinnerFrames := []string{"[    ]", "[=   ]", "[==  ]", "[=== ]", "[ ===]", "[  ==]", "[   =]", "[    ]"}
			ticker := time.NewTicker(120 * time.Millisecond)
			defer ticker.Stop()

			if _, err := sess.Write(clearLine); err != nil {
				return
			}

			waitCount := 0
			for {
				select {
				case <-outputStarted:
					return
				case <-ticker.C:
					frame := spinnerFrames[waitCount%len(spinnerFrames)]
					dots := strings.Repeat(".", (waitCount%3)+1)
					if _, err := sess.Write([]byte(fmt.Sprintf("%s%s Connecting to server%s", string(clearLine), frame, dots))); err != nil {
						return
					}

					waitCount++
				}
			}
		}()
	}

	cmd.Start()
	if err := cmd.Err(); err != nil {
		return err
	}

	go func() {
		cmd.Wait()
		close(commandDone)
	}()

	go func() {
		buf := make([]byte, 4096)
		for {
			n, err := sess.Read(buf)
			if n > 0 {
				_ = shared.TerminalLease(sandbox.Owner, 2*time.Minute)
				log.Info("Input: %s", string(buf[:n]))
				if _, writeErr := stdinWriter.Write(buf[:n]); writeErr != nil {
					break
				}
			}
			if err != nil {
				break
			}
		}
		cmd.Kill()
		close(done)
	}()

	if hasPty {
		go func() {
			for win := range winCh {
				cmd.Resize(win.Width, win.Height)
			}
		}()
	}

	go func() {
		defer outputReaders.Done()
		buf := make([]byte, 4096)
		for {
			n, err := stdoutReader.Read(buf)
			if n > 0 {
				outputStartedOnce.Do(func() { close(outputStarted) })
				data := make([]byte, n)
				copy(data, buf[:n])
				outputChannel <- data
			}
			if err != nil {
				break
			}
		}
		cmd.Kill()
	}()

	go func() {
		defer outputReaders.Done()
		buf := make([]byte, 4096)
		for {
			n, err := stderrReader.Read(buf)
			if n > 0 {
				outputStartedOnce.Do(func() { close(outputStarted) })
				data := make([]byte, n)
				copy(data, buf[:n])
				outputChannel <- data
			}
			if err != nil {
				break
			}
		}
		cmd.Kill()
	}()

	go func() {
		outputReaders.Wait()
		close(outputChannel)
	}()

	go func() {
		clearLine := []byte("\033[2K\r")
		didClear := false
		for {
			select {
			case data, ok := <-outputChannel:
				if !ok {
					return
				}

				if !didClear && !hasCommand {
					didClear = true
					if _, err := sess.Write(clearLine); err != nil {
						return
					}
				}

				if len(data) > 0 {
					if _, err := sess.Write(data); err != nil {
						return
					}
				}
			}
		}
	}()

	select {
	case <-commandDone:
	case <-done:
		<-commandDone
	}
	return nil
}
