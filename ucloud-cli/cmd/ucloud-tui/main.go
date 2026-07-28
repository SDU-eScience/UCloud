package main

import (
	"os"

	cli "ucloud.dk/ucloud_cli/pkg/ucloud_cli"
)

func main() {
	err := cli.ExecuteCommand(os.Args[1:]...)
	if err != nil {
		panic(err)
	}
}
