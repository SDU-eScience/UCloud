package main

import (
	"log"
	"os"
	"os/exec"
	"os/signal"
	"syscall"
	"time"
)

func main() {
	if len(os.Args) < 2 {
		log.Fatal("Missing argument: config path")
	}

	configFolder := os.Args[1]
	if _, err := os.Stat(configFolder); os.IsNotExist(err) {
		log.Fatalf("Config folder not found: %s", configFolder)
	}

	cmd := exec.Command(
		"/opt/syncthing/syncthing",
		"--home", configFolder,
		"--gui-address", "0.0.0.0:8384",
	)

	cmd.Stderr = os.Stderr
	cmd.Stdout = os.Stdout

	if err := cmd.Start(); err != nil {
		log.Fatalf("Failed to start Syncthing: %v", err)
	}

	go func() {
		sigChan := make(chan os.Signal, 1)
		signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)
		<-sigChan
		_ = cmd.Process.Kill()
		_, _ = cmd.Process.Wait()
		os.Exit(0)
	}()

	ucloudConfigService, err := NewUCloudConfigService(configFolder)
	if err != nil {
		log.Fatalf("Failed to create UCloud Config Service: %v", err)
	} else {
		err = ucloudConfigService.Run()
		if err != nil {
			log.Fatalf("Error while configuring Syncthing: %v", err)
		}
	}
}

func RetryOrPanic[R any](action string, fn func() (R, error)) R {
	wait := 10
	var lastErr error
	for attempt := 0; attempt < 30; attempt++ {
		res, err := fn()
		if err != nil {
			lastErr = err
			time.Sleep(time.Duration(wait) * time.Millisecond)
			log.Printf("Failed %s: %v\n", action, err)
			wait *= 2
			if wait > 5000 {
				wait = 5000
			}
			continue
		}

		return res
	}

	panic(lastErr)
}

type Empty struct{}
