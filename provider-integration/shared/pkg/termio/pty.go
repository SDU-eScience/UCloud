package termio

import (
	"io"
	"os"

	"golang.org/x/sys/unix"
)

func queryPtySize() (int, int, bool) {
	return queryPtySizeFd(1)
}

func queryPtySizeFd(fd int) (int, int, bool) {
	ws, err := unix.IoctlGetWinsize(fd, unix.TIOCGWINSZ)
	if err != nil {
		return 160, 48, false // Default size for non-ptys
	}

	return int(ws.Col), int(ws.Row), true
}

func isPtyWriter(w io.Writer) bool {
	file, ok := w.(*os.File)
	if !ok {
		return false
	}
	_, _, isPty := queryPtySizeFd(int(file.Fd()))
	return isPty
}

func IsPtyWriter(w io.Writer) bool {
	return isPtyWriter(w)
}

func writerWidth(w io.Writer) int {
	file, ok := w.(*os.File)
	if !ok {
		return 160
	}
	cols, _, _ := queryPtySizeFd(int(file.Fd()))
	return max(40, cols)
}

func writerHeight(w io.Writer) int {
	file, ok := w.(*os.File)
	if !ok {
		return 48
	}
	_, rows, _ := queryPtySizeFd(int(file.Fd()))
	return max(12, rows)
}

func safeQueryPtySize() (int, int, bool) {
	// Returns the real pty size, if possible and reasonably sized. If this is not possible, a reasonable minimum
	// is selected instead.
	cols, rows, ispty := queryPtySize()
	return max(80, cols), max(24, rows), ispty
}
