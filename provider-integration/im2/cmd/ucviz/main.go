package main

import (
	"os"
	"ucloud.dk/pkg/ucviz"
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

	ucviz.HandleCli(os.Args, uiChannel, dataChannel)
}
