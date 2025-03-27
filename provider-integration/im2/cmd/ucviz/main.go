package main

import (
	"os"
	"ucloud.dk/pkg/ucviz"
	"ucloud.dk/pkg/util"
)

func main() {
	uiChannel, err := os.OpenFile("/work/.ucviz-ui", os.O_WRONLY|os.O_APPEND|os.O_CREATE, 0600)
	if err != nil {
		panic(err)
	}

	dataChannel, err := os.OpenFile("/work/.ucviz-data", os.O_WRONLY|os.O_APPEND|os.O_CREATE, 0600)
	if err != nil {
		panic(err)
	}

	// NOTE(Dan): Do not place the lock somewhere that we suspect could be a distributed filesystem. The reason for
	// this is that flock (file-lock) requires FS support, and it is likely to not be implemented on many distributed
	// filesystems.
	ucviz.HandleCli(os.Args[1:], uiChannel, dataChannel, util.OptValue[string]("/tmp/.ucviz-lock"))
}
