package termio

import (
	"fmt"
	"strings"
	"testing"
)

func TestFrame(t *testing.T) {
	f := Frame{}
	f.Title("Testing")
	for i := 0; i < 30; i++ {
		f.AppendField(fmt.Sprintf("Field %d", i), strings.Repeat("ABCD", i+1))
	}

	f.Print()
}
