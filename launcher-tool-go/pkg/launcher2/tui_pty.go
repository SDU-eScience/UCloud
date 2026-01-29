package launcher2

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"os"
	"os/exec"
	"os/signal"
	"sync"
	"syscall"
	"time"

	"github.com/creack/pty"
	"golang.org/x/sys/unix"
	"golang.org/x/term"
)

func PtyExecCommand(args []string, info *StatusInfo) error {
	if len(args) == 0 {
		return errors.New("no args provided")
	}

	fd := int(os.Stdin.Fd())
	oldState, err := term.MakeRaw(fd)
	if err != nil {
		return fmt.Errorf("term.MakeRaw: %w", err)
	}

	defer func() {
		_ = term.Restore(fd, oldState)
	}()

	cmd := exec.Command(args[0], args[1:]...)
	cmd.Env = os.Environ()
	cmd.Env = append(cmd.Env, "DOCKER_CLI_HINTS=false")

	ptmx, err := pty.Start(cmd)
	if err != nil {
		return fmt.Errorf("pty.Start(docker ...): %w", err)
	}

	realPtySize := &pty.Winsize{}

	resize := func() {
		rows, cols, _ := pty.Getsize(os.Stdin)
		realPtySize.Rows = uint16(rows)
		realPtySize.Cols = uint16(cols)

		_ = pty.Setsize(ptmx, &pty.Winsize{
			Rows: uint16(rows - 1),
			Cols: uint16(cols),
		})
	}
	resize()

	winch := make(chan os.Signal, 1)
	sig := make(chan os.Signal, 2)
	signal.Notify(winch, syscall.SIGWINCH)
	signal.Notify(sig, os.Interrupt, syscall.SIGTERM)
	defer signal.Stop(winch)
	defer signal.Stop(sig)

	// NOTE(Dan): This is me begging the terminal to size itself correctly. It exits, enters and exits the alt screen
	// and places the cursor at the top.
	_, _ = fmt.Fprintf(os.Stdout, "\x1b[?1049l\x1b[?1049h\x1b[?1049l\x1b[2J\x1b[3;1H")

	isFullScreen := info.StatusLine == ""

	// Bottom status line.
	drawStatus := func() {
		if isFullScreen || info.StatusLine == "" {
			return
		}

		_, _ = fmt.Fprint(os.Stdout, "\x1b[?25l")
		_, _ = fmt.Fprint(os.Stdout, "\x1b[s")
		_, _ = fmt.Fprintf(os.Stdout, "\x1b[1;1H")
		_, _ = fmt.Fprint(os.Stdout, "\x1b[2K")
		_, _ = fmt.Fprint(os.Stdout, StatusLine(int(realPtySize.Cols), *info))
		_, _ = fmt.Fprint(os.Stdout, "\x1b[u")
		_, _ = fmt.Fprint(os.Stdout, "\x1b[?25h")
	}
	drawStatus()

	ctx, cancel := context.WithCancel(context.Background())

	ptyOutput := make(chan []byte, 32)

	// PTY output -> stdout
	go func() {
		buf := make([]byte, 32*1024)
		for {
			n, rerr := ptmx.Read(buf)
			if n > 0 {
				copied := make([]byte, n)
				copy(copied, buf[:n])
				select {
				case <-ctx.Done():
					return
				case ptyOutput <- copied:
				}
			}
			if rerr != nil {
				return
			}
			select {
			case <-ctx.Done():
				return
			default:
			}
		}
	}()

	go func() {
		lastWrite := time.Time{}
		ticker := time.NewTicker(10 * time.Millisecond)
		dirty := false
		first := true

		for {
			select {
			case <-ctx.Done():
				return

			case buf := <-ptyOutput:
				// NOTE: We don't have to handle these being in the same buffer (yet)
				if bytes.Contains(buf, []byte("\x1b[?1049h")) {
					isFullScreen = true
				} else if bytes.Contains(buf, []byte("\x1b[?1049l")) {
					isFullScreen = false
				}

				if !isFullScreen {
					buf = bytes.ReplaceAll(buf, []byte("\x1b[1;1H"), []byte("\x1b[3;1H"))
					buf = bytes.ReplaceAll(buf, []byte("\x1b[H"), []byte("\x1b[3;1H"))
				}

				if first {
					drawStatus()
					first = false
				}

				_, _ = os.Stdout.Write(buf)
				drawStatus()
				lastWrite = time.Now()
				dirty = true

			case <-ticker.C:
				if dirty && time.Now().Sub(lastWrite) > 1*time.Millisecond {
					drawStatus()
					dirty = false
				}
			}
		}
	}()

	// stdin -> PTY, intercept detach only
	stopStdin, _ := startStdinToPTY(ctx, cancel, ptmx)

	go func() {
		_ = cmd.Wait()
		cancel()
		_ = ptmx.Close()
		stopStdin()
	}()

	for {
		select {
		case <-winch:
			resize()

		case _ = <-ctx.Done():
			if cmd.Process != nil {
				_ = cmd.Process.Kill()
			}

			fmt.Printf("\x1b[!p\x1b[?1049l\x1b[0m\x1b[?25h")
			return nil
		}
	}
}

func startStdinToPTY(ctx context.Context, cancel func(), output *os.File) (stop func(), err error) {
	// wake pipe: write to wakeW to force poll() to return
	wakeR, wakeW, err := os.Pipe()
	if err != nil {
		return nil, err
	}

	// Use a dup so we don't mess with os.Stdin's lifecycle
	dupFD, err := unix.Dup(int(os.Stdin.Fd()))
	if err != nil {
		_ = wakeR.Close()
		_ = wakeW.Close()
		return nil, err
	}
	stdinDup := os.NewFile(uintptr(dupFD), "stdin-dup")

	var once sync.Once
	stop = func() {
		once.Do(func() {
			// Wake the poller first (so it can exit immediately)
			_, _ = wakeW.Write([]byte{1})
			_ = wakeW.Close()
			_ = wakeR.Close()
			_ = stdinDup.Close()
		})
	}

	go func() {
		defer stop()

		const detachKey = byte(0x1D) // Ctrl+]

		pfds := []unix.PollFd{
			{Fd: int32(stdinDup.Fd()), Events: unix.POLLIN},
			{Fd: int32(wakeR.Fd()), Events: unix.POLLIN},
		}

		buf := make([]byte, 4096)

		for {
			_, perr := unix.Poll(pfds, -1)
			if perr != nil {
				if errors.Is(perr, syscall.EINTR) {
					select {
					case <-ctx.Done():
						return
					default:
						continue
					}
				}
				cancel()
				return
			}

			// Wake requested?
			if pfds[1].Revents&(unix.POLLIN|unix.POLLHUP|unix.POLLERR) != 0 {
				return
			}

			// Stdin readable?
			if pfds[0].Revents&(unix.POLLIN|unix.POLLHUP|unix.POLLERR) == 0 {
				continue
			}

			n, rerr := stdinDup.Read(buf)
			if n > 0 {
				chunk := buf[:n]
				if i := bytes.IndexByte(chunk, detachKey); i >= 0 {
					if i > 0 {
						_, _ = output.Write(chunk[:i])
					}
					cancel()
					return
				}
				_, _ = output.Write(chunk)
			}
			if rerr != nil {
				cancel()
				return
			}

			select {
			case <-ctx.Done():
				return
			default:
			}
		}
	}()

	return stop, nil
}
