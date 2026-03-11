package controller

import (
	"os"
	"path/filepath"
	"regexp"
	"time"

	"ucloud.dk/pkg/config"
	"ucloud.dk/shared/pkg/log"
)

var jobAuditLogFolder = "/mnt/storage/audit"
var jobAuditFileRegex = regexp.MustCompile(`^audit-(\d+)-(\d{4}-\d{2}-\d{2})\.jsonl$`)

func initJobAuditLog() {

	retentionDays := config.Provider.JobAuditLog.RetentionPeriodInDays
	go func() {
		cleanupLogs(retentionDays) // run once at startup

		ticker := time.NewTicker(24 * time.Hour)
		defer ticker.Stop()

		for range ticker.C {
			cleanupLogs(retentionDays)
		}
	}()
}

func cleanupLogs(retentionDays int) {
	files, err := os.ReadDir(jobAuditLogFolder)
	if err != nil {
		log.Error("Could not read audit log folder: %s", err)
		return
	}

	cutoff := time.Now().AddDate(0, 0, -retentionDays)
	today := time.Now().Format("2006-01-02")

	for _, file := range files {
		name := file.Name()

		matches := jobAuditFileRegex.FindStringSubmatch(name)
		if matches == nil {
			continue
		}

		// matches[1] = rank (string)
		// matches[2] = date
		dateStr := matches[2]

		if dateStr == today {
			continue
		}

		fileDate, err := time.Parse("2006-01-02", dateStr)
		if err != nil {
			continue
		}

		if fileDate.Before(cutoff) {
			fullPath := filepath.Join(jobAuditLogFolder, name)
			err := os.Remove(fullPath)
			if err != nil {
				log.Error("Could not remove file: %s", err)
			} else {
				log.Info("Removed audit log file: %s, since it's older than %d days", fullPath, retentionDays)
			}
		}
	}
}
