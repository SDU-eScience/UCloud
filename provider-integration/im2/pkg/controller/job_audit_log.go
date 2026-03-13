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

		ticker := time.NewTicker(4 * time.Hour)
		defer ticker.Stop()

		for range ticker.C {
			cleanupLogs(retentionDays)
		}
	}()
}

func cleanupLogs(retentionDays int) {
	cutoff := time.Now().AddDate(0, 0, -retentionDays)
	today := time.Now().Format("2006-01-02")

	walkErr := filepath.WalkDir(jobAuditLogFolder, func(path string, d os.DirEntry, err error) error {
		if err != nil {
			log.Error("Walk error: %s", err)
			return nil
		}
		// Only process files
		if !d.IsDir() {
			name := d.Name()
			matches := jobAuditFileRegex.FindStringSubmatch(name)
			if matches == nil {
				return nil
			}

			dateStr := matches[2]

			if dateStr == today {
				return nil
			}

			fileDate, err := time.Parse("2006-01-02", dateStr)
			if err != nil {
				return nil
			}

			if fileDate.Before(cutoff) {
				err := os.Remove(path)
				if err != nil {
					log.Error("Could not remove file: %s", err)
				} else {
					log.Info("Removed audit log file: %s, since it is older than %d days", path, retentionDays)
				}
			}

			return nil
		}
		// After walking a directory, check if it's empty (skip root)
		if path == jobAuditLogFolder {
			return nil
		}

		entries, err := os.ReadDir(path)
		if err != nil {
			return nil
		}

		// Delete the child folder if it is empty
		if len(entries) == 0 {
			err := os.Remove(path)
			if err == nil {
				log.Info("Removed empty audit log folder: %s", path)
			}
		}
		return nil
	})
	if walkErr != nil {
		log.Error("Error cleaning up job audit logs: %s", walkErr)
	}
}
