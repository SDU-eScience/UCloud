package controller

import (
	"os"
	"path/filepath"
	"strings"
	"time"

	"ucloud.dk/pkg/config"
	"ucloud.dk/shared/pkg/log"
)

var auditLogFolder = "/mnt/storage/audit"

func initAuditLog() {

	retentionDays := config.Provider.AuditLog.RetentionPeriodInDays
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
	files, err := os.ReadDir(auditLogFolder)
	if err != nil {
		log.Error("Could not read audit log folder: %s", err)
		return
	}

	cutoff := time.Now().AddDate(0, 0, -retentionDays)
	today := time.Now().Format("2006-01-02")

	for _, file := range files {
		name := file.Name()

		if !strings.HasPrefix(name, "audit-") || !strings.HasSuffix(name, ".log") {
			continue
		}

		dateStr := strings.TrimSuffix(strings.TrimPrefix(name, "audit-"), ".log")

		if dateStr == today {
			continue
		}

		fileDate, err := time.Parse("2006-01-02", dateStr)
		if err != nil {
			continue
		}
		if fileDate.Before(cutoff) {
			fullPath := filepath.Join(auditLogFolder, name)
			err := os.Remove(fullPath)
			if err != nil {
				log.Error("Could not remove file: %s", err)
			} else {
				log.Info("Removed audit log file: %s, since it's older than %d days", fullPath, retentionDays)
			}
		}
	}
}
