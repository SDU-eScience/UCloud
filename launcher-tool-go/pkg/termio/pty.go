package termio

/*
#cgo LDFLAGS: -lutil

#include <stdlib.h>
#include <sys/ioctl.h>

int queryPtySize(int *cols, int *rows) {
	struct winsize winp = {0};
	int result = ioctl(0, TIOCGWINSZ, &winp);

	*cols = (int) winp.ws_col;
	*rows = (int) winp.ws_row;
	return result;
}
*/
import "C"

func queryPtySize() (int, int, bool) {
	var cols C.int
	var rows C.int

	ok := C.queryPtySize(&cols, &rows) == 0
	if !ok {
		return 160, 48, ok // Default size for non-ptys
	}

	return int(cols), int(rows), ok
}

func safeQueryPtySize() (int, int, bool) {
	// Returns the real pty size, if possible and reasonably sized. If this is not possible, a reasonable minimum
	// is selected instead.
	cols, rows, ispty := queryPtySize()
	return max(80, cols), max(24, rows), ispty
}
