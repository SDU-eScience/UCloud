package termio

import (
	"golang.org/x/sys/unix"
)

func queryPtySize() (int, int, bool) {
	ws, err := unix.IoctlGetWinsize(0, unix.TIOCGWINSZ)
	if err != nil {
		return 160, 48, false // Default size for non-ptys
	}

	return int(ws.Col), int(ws.Row), true
}

func safeQueryPtySize() (int, int, bool) {
	// Returns the real pty size, if possible and reasonably sized. If this is not possible, a reasonable minimum
	// is selected instead.
	cols, rows, ispty := queryPtySize()
	return max(80, cols), max(24, rows), ispty
}
