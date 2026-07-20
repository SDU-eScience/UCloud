package ucx_syncthing

import (
	"os"
	"time"
)

func Launch() {
	_ = os.WriteFile("/tmp/stub-has-started.txt", []byte("success!"), 0660)
	for {
		time.Sleep(1 * time.Second)
	}
}
