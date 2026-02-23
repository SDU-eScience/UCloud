package ucmetrics

import (
	"os"
	"path/filepath"
	"time"

	"ucloud.dk/shared/pkg/log"
)

func startExecutableUpdateWatcher(interval time.Duration) {
	exePath, err := os.Executable()
	if err != nil {
		log.Warn("Unable to resolve executable path: %v", err)
		return
	}

	resolvedExePath, err := filepath.EvalSymlinks(exePath)
	if err != nil {
		log.Warn("Unable to resolve executable symlinks, using original path (%s): %v", exePath, err)
		resolvedExePath = exePath
	}

	initialModTime, err := executableModTime(resolvedExePath)
	if err != nil {
		log.Warn("Unable to read executable timestamp (%s): %v", resolvedExePath, err)
		return
	}

	checkForUpdate := func() {
		currentModTime, err := executableModTime(resolvedExePath)
		if err != nil {
			log.Warn("Unable to read executable timestamp (%s): %v", resolvedExePath, err)
			return
		}

		if !currentModTime.Equal(initialModTime) {
			log.Info("Executable update detected (%s). Exiting.", resolvedExePath)
			os.Exit(0)
		}
	}

	checkForUpdate()

	go func() {
		ticker := time.NewTicker(interval)
		defer ticker.Stop()

		for range ticker.C {
			checkForUpdate()
		}
	}()
}

func executableModTime(path string) (time.Time, error) {
	info, err := os.Stat(path)
	if err != nil {
		return time.Time{}, err
	}

	return info.ModTime(), nil
}
