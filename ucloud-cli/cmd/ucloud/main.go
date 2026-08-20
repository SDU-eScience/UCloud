package main

import (
	"os"

	cli "ucloud.dk/ucloud_cli/pkg/ucloud_cli"
)

func main() {
	if len(os.Args) == 1 {
		println("Usage: <command>")
		return
	}
	err := cli.ExecuteCommand(os.Args[0:]...) // Command execution
	if err != nil {
		panic(err)
	}
}
