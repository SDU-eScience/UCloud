package util

import "os"

var devModeEnabled = func() bool {
	if os.Getenv("DEV_MODE") == "true" {
		return true
	}
	hostname, _ := os.Hostname()
	if hostname == "go-slurm.ucloud" {
		return true
	}
	return false
}()

func DevelopmentModeEnabled() bool {
	return devModeEnabled
}
