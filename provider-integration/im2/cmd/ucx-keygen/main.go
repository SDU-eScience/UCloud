package main

import (
	"os"

	"ucloud.dk/pkg/ucxdelivery"
)

func main() {
	os.Exit(ucxdelivery.KeygenCli(os.Args[1:], os.Stdout, os.Stderr))
}
