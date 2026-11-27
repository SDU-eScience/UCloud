package main

import (
	"fmt"
	"os"

	"ucloud.dk/pkg/ptyhost"
)

func main() {
	if len(os.Args) < 2 {
		os.Exit(2)
	}

	cmd := os.Args[1]
	switch cmd {
	case "daemon":
		err := ptyhost.PtyHostRunDaemon()
		if err != nil {
			fmt.Fprintln(os.Stderr, err)
			os.Exit(1)
		}

	case "mux":
		err := ptyhost.PtyHostRunMuxStdio()
		if err != nil {
			fmt.Fprintln(os.Stderr, err)
			os.Exit(1)
		}

	case "kill":
		if len(os.Args) >= 3 {
			ptyhost.PtyHostKillHandles(os.Args[2:])
		}

	default:
		os.Exit(2)
	}
}
