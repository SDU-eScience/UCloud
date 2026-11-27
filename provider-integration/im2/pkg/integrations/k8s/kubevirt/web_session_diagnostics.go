package kubevirt

import (
	"os"
	"path/filepath"

	"golang.org/x/sys/unix"
	cfg "ucloud.dk/pkg/config"
	ctrl "ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/filesystem"
	"ucloud.dk/pkg/integrations/k8s/shared"
	orc "ucloud.dk/shared/pkg/orchestrators"
)

func kubevirtInspectWebSession(
	job *orc.Job,
	rank int,
	suffix string,
	target cfg.HostInfo,
	tls bool,
) ctrl.WebSessionDiagnostics {
	result := ctrl.WebSessionDiagnostics{
		PodPhase:      "Running",
		PodStateLabel: "Running",
		ProbeStatus:   shared.ProbeTcpTarget(target),
	}

	jobFolder, httpErr := JobFolder(job)
	if httpErr != nil {
		return result
	}

	logsFolder := filepath.Join(jobFolder, "logs")
	workFolder := filepath.Join(jobFolder, "work")
	folders := []string{workFolder, logsFolder}
	if info, statErr := os.Stat(logsFolder); statErr == nil && info.IsDir() {
		folders = []string{logsFolder, workFolder}
	}

	for _, folder := range folders {
		file, opened := filesystem.OpenFile(filepath.Join(folder, "stdout-0.log"), unix.O_RDONLY, 0)
		if !opened {
			continue
		}
		logs, truncated := shared.ReadStdoutLogTail(file, shared.WebDiagnosticsMaxLogBytes)
		_ = file.Close()
		result.LogsAvailable = true
		result.Logs = logs
		result.LogsTruncated = truncated
		return result
	}

	return result
}
