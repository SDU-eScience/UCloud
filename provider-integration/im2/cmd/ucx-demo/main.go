package main

import (
	"ucloud.dk/shared/pkg/ucx"
	ucxdemo "ucloud.dk/shared/pkg/ucx/demo"
)

func main() {
	ucx.AppServe(ucxdemo.Demo)
}
