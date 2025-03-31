package main

import (
	"os"
	"ucloud.dk/pkg/ucmetrics"
)

func main() {
	ucmetrics.HandleCli(os.Args[1:])
}
