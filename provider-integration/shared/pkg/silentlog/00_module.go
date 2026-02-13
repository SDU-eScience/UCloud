package silentlog

import (
	"io"
	"log"
)

// The whole purpose of this module is to discard all output from normal log calls which. Import it as the first import
// to ensure that no 3rd party dependency start logging stuff in their init function.

func init() {
	log.SetOutput(io.Discard)
}
