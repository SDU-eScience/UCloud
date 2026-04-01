package main

import (
	"os"
	"strconv"

	"ucloud.dk/shared/pkg/ucx"
	ucxdemo "ucloud.dk/shared/pkg/ucx/demo"
	"ucloud.dk/shared/pkg/util"
)

func main() {
	port := util.OptNone[int]()
	if len(os.Args) >= 2 {
		converted, err := strconv.Atoi(os.Args[1])
		if err == nil {
			port.Set(converted)
		}
	}

	ucx.AppServe(ucxdemo.Demo, port)
}
