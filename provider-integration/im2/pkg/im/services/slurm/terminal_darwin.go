package slurm

import "C"
import (
	"fmt"
	"os"
	ctrl "ucloud.dk/pkg/im/controller"
)

func CreateAndForkPty(command []string, envArray []string) (*os.File, int32, error) {
	return nil, 0, fmt.Errorf("unimplemented")
}

func ResizePty(masterFd *os.File, cols, rows int) {

}

func handleShell(session *ctrl.ShellSession, cols, rows int) {

}
