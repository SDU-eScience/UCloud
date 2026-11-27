package shared

import (
	"errors"
	"io"
	"net"
	"os"
	"regexp"
	"strconv"
	"strings"
	"syscall"
	"time"

	"ucloud.dk/pkg/config"
)

const WebDiagnosticsMaxLogBytes = 32 * 1024

var webDiagnosticsAnsiRegex = regexp.MustCompile(`\x1b\[[0-9;?]*[ -/]*[@-~]|\x1b\][^\x07\x1b]*(?:\x07|\x1b\\)`)

func ProbeTcpTarget(target config.HostInfo) string {
	if !K8sInCluster || target.Address == "" || target.Port <= 0 {
		return ""
	}

	address := net.JoinHostPort(target.Address, strconv.Itoa(target.Port))
	conn, err := net.DialTimeout("tcp", address, 400*time.Millisecond)
	if err == nil {
		_ = conn.Close()
		return "reachable"
	}

	if errors.Is(err, syscall.ECONNREFUSED) {
		return "connection_refused"
	}

	var netErr net.Error
	if errors.As(err, &netErr) && netErr.Timeout() {
		return "timeout"
	}

	return "error"
}

func ReadStdoutLogTail(file *os.File, maxBytes int64) (string, bool) {
	info, statErr := file.Stat()
	if statErr != nil {
		return "", false
	}

	size := info.Size()
	if size <= 0 {
		return "", false
	}

	truncated := false
	if size > maxBytes {
		_, seekErr := file.Seek(size-maxBytes, io.SeekStart)
		if seekErr != nil {
			return "", false
		}
		truncated = true
	}

	data, readErr := io.ReadAll(io.LimitReader(file, maxBytes))
	if readErr != nil {
		return "", false
	}

	if truncated {
		if newlineIdx := strings.IndexByte(string(data), '\n'); newlineIdx >= 0 && newlineIdx+1 < len(data) {
			data = data[newlineIdx+1:]
		}
	}

	cleaned := strings.ToValidUTF8(string(data), "")
	cleaned = webDiagnosticsAnsiRegex.ReplaceAllString(cleaned, "")
	cleaned = strings.ReplaceAll(cleaned, "\r\n", "\n")
	cleaned = strings.ReplaceAll(cleaned, "\r", "\n")

	return cleaned, truncated
}
