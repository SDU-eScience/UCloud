package filesystem

import (
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"

	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

func taskMarkItDown(spec TaskSpec) *util.HttpError {
	if os.Getenv(taskEnvId) == "" {
		log.Fatal("This code can only run inside of a task.")
		return util.HttpErr(http.StatusInternalServerError, "internal error")
	}

	sourcePath := spec.Source
	fileName := filepath.Base(sourcePath)
	fileName = strings.TrimSuffix(fileName, filepath.Ext(fileName)) + ".md"

	taskProcessorPostUpdate(fnd.TaskStatus{
		State: fnd.TaskStateRunning,
		Title: util.OptValue("Extracting text from attachment"),
	})

	stdout, stderr, ok := util.RunCommand([]string{
		"markitdown",
		sourcePath,
		"-o",
		filepath.Join(filepath.Dir(spec.Source), fileName),
	})

	log.Info("Stdout: %v", stdout)
	log.Info("Stderr: %v", stderr)
	log.Info("Ok: %v", ok)

	if !ok {
		time.Sleep(15 * time.Minute)
	}

	return nil
}
