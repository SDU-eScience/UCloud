package controller

import (
	"bytes"
	"encoding/json"
	"os"
	"os/exec"
	"strings"
	"ucloud.dk/pkg/log"
	"ucloud.dk/pkg/util"
)

type Extension[Req any, Resp any] struct {
	Script string
}

func prepareFile(req any) (string, bool) {
	file, err := os.CreateTemp("", "*.json")
	defer util.SilentClose(file)
	if err != nil {
		log.Warn("Failed to create temporary file: %v", err)
		return "", false
	}
	err = file.Chmod(0600)
	if err != nil {
		log.Warn("Failed to chmod temporary file: %v", err)
		return "", false
	}

	jsonBytes, err := json.Marshal(req)
	if err != nil {
		log.Warn("Failed to marshal JSON request: %v", err)
		return "", false
	}

	_, err = file.Write(jsonBytes)
	if err != nil {
		log.Warn("Failed to write extension request: %v", err)
		return "", false
	}

	return file.Name(), true
}

func (e *Extension[Req, Resp]) Invoke(req Req) (Resp, bool) {
	var resp Resp
	if e.Script == "" {
		log.Error(
			"You must set the Script property of an extension after NewExtension but it was never set: %v %v",
			req,
			resp,
		)
		return resp, false
	}

	requestFile, ok := prepareFile(req)
	if !ok {
		return resp, false
	}

	var stdout bytes.Buffer
	var stderr bytes.Buffer

	cmd := exec.Command(e.Script, requestFile)
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr

	err := cmd.Run()
	if err != nil {
		s := strings.TrimSpace(stderr.String())
		log.Warn("%v extension failed: %s %v", e.Script, s, err)
		return resp, false
	}

	err = json.Unmarshal(stdout.Bytes(), &resp)
	if err != nil {
		log.Warn(
			"%v extension returned invalid response: \n\terr=%v\n\tstdout%v\n\tstderr=%v",
			e.Script,
			err,
			stdout.String(),
			stderr.String(),
		)
		return resp, false
	}

	return resp, true
}

func NewExtension[Req any, Resp any]() Extension[Req, Resp] {
	return Extension[Req, Resp]{}
}
