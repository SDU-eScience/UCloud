package nodeagent

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"ucloud.dk/iapp/k8s/pkg/shared"
	"ucloud.dk/shared/pkg/log"
)

// Constants and state
// =====================================================================================================================
// The constants name the work mount and the two output files. The progress state is protected by its own mutex. The
// record is loaded once. It is written back with a rate limit unless a caller forces a write.

const (
	workMount       = "/work"
	uiChannelName   = ".ucviz-ui"
	stdoutLogName   = "stdout-0.log"
	progressLabelId = "ucloud-init-stage"
	progressBarId   = "ucloud-init-progress"
)

var progressMu sync.Mutex
var progressCurrent shared.NodeAgentProgress
var progressLoaded bool
var progressPersistedAt time.Time

// Progress state
// =====================================================================================================================
// progressLoad returns a copy of the record. progressBegin starts a record for a new operation. progressStageRecord
// appends a stage event and caps the event list. progressPersist writes the record to disk.

func progressLoad() shared.NodeAgentProgress {
	progressMu.Lock()
	defer progressMu.Unlock()
	progressRead()
	result := progressCurrent
	result.Events = append([]shared.NodeAgentProgressEvent(nil), progressCurrent.Events...)
	return result
}

func progressRead() {
	if progressLoaded {
		return
	}
	data, err := os.ReadFile(filepath.Join(stagingDir, "progress.json"))
	if err == nil {
		_ = json.Unmarshal(data, &progressCurrent)
	}
	progressLoaded = true
}

func progressPersist(force bool) {
	if !force && time.Since(progressPersistedAt) < 2*time.Second {
		return
	}
	data, err := json.Marshal(progressCurrent)
	if err == nil {
		err = writeFileAtomic(filepath.Join(stagingDir, "progress.json"), data, 0600)
	}
	if err == nil {
		progressPersistedAt = time.Now()
	}
}

func progressBegin(operationUid string) {
	progressMu.Lock()
	defer progressMu.Unlock()
	progressRead()
	if progressCurrent.OperationUid == operationUid {
		return
	}
	progressCurrent = shared.NodeAgentProgress{OperationUid: operationUid, UpdatedAt: time.Now().UTC()}
	progressPersist(true)
}

func progressStageRecord(text string) {
	progressRead()
	if text != progressCurrent.Stage {
		progressCurrent.Stage = text
		progressCurrent.Detail = ""
		progressCurrent.Events = append(progressCurrent.Events, shared.NodeAgentProgressEvent{At: time.Now().UTC(), Text: text})
		if len(progressCurrent.Events) > 32 {
			progressCurrent.Events = progressCurrent.Events[len(progressCurrent.Events)-32:]
		}
	}
}

// Stage updates
// =====================================================================================================================
// A stage update names the current step. It also sets the percent and writes the dashboard widgets. progressText
// records a detail without a stage change. progressLines appends a batch from the coordinator. progressCommandOutput
// records the output of a system command.

func progressStage(text string, percent int) {
	progressMu.Lock()
	defer progressMu.Unlock()

	progressStageRecord(text)
	progressCurrent.Percent = max(progressCurrent.Percent, percent)
	progressCurrent.UpdatedAt = time.Now().UTC()
	progressPersist(true)
	progressAppendLine(text)
	progressWidget(progressLabelId, 0, widgetLabel{Text: text})
	progressWidget(
		progressBarId,
		1,
		widgetProgress{Progress: float64(percent) / 100.0},
	)
}

func progressStageLabel(text string) {
	progressMu.Lock()
	defer progressMu.Unlock()

	progressStageRecord(text)
	progressCurrent.UpdatedAt = time.Now().UTC()
	progressPersist(true)
	progressAppendLine(text)
	progressWidget(progressLabelId, 0, widgetLabel{Text: text})
}

func progressText(text string) {
	progressMu.Lock()
	defer progressMu.Unlock()

	progressRead()
	progressCurrent.Detail = shared.NodeAgentTruncateLine(text)
	progressCurrent.UpdatedAt = time.Now().UTC()
	progressPersist(false)
	progressAppendLine(text)
}

func progressCommandOutput(name string, output []byte) {
	trimmed := strings.TrimSpace(string(output))
	if trimmed == "" {
		return
	}
	trimmed = shared.NodeAgentTruncateLine(trimmed)
	progressText(fmt.Sprintf("%s: %s", name, trimmed))
}

func progressLines(lines []string) int {
	progressMu.Lock()
	defer progressMu.Unlock()

	written := 0
	for _, line := range lines {
		trimmed := strings.TrimSpace(line)
		if trimmed == "" {
			continue
		}
		trimmed = shared.NodeAgentTruncateLine(trimmed)
		progressCurrent.Detail = trimmed
		progressCurrent.UpdatedAt = time.Now().UTC()
		progressAppendLine(trimmed)
		written++
	}
	if written > 0 {
		progressPersist(false)
	}
	return written
}

// Widgets
// =====================================================================================================================
// A widget update has two JSON documents. The first names the widget and its location. The second carries the value.
// The agent appends both to the widget channel. The dashboard reads that channel.

type widgetLocation struct {
	Window int    `json:"window"`
	Tab    string `json:"tab"`
	Icon   int    `json:"icon"`
}

type widgetRef struct {
	Id       string         `json:"id"`
	Type     int            `json:"type"`
	Location widgetLocation `json:"location"`
}

type widgetLabel struct {
	Align int    `json:"align"`
	Text  string `json:"text"`
}

type widgetProgress struct {
	Progress float64 `json:"progress"`
}

func progressWidget(id string, widgetType int, spec any) {
	ref, err := json.Marshal(widgetRef{
		Id:       id,
		Type:     widgetType,
		Location: widgetLocation{},
	})
	if err != nil {
		return
	}
	specJson, err := json.Marshal(spec)
	if err != nil {
		return
	}

	widgets := fmt.Sprintf("{\"action\":0}\n%s\n%s\n", ref, specJson)
	progressAppendFile(uiChannelName, []byte(widgets), 0600)
}

// Output files
// =====================================================================================================================
// The text log gets a timestamp in front of each line. The files are created with the owner of the cluster service. A
// write failure is ignored. progressReset starts a new log and resets the widgets for a new operation.

func progressAppendLine(line string) {
	line = strings.TrimPrefix(strings.TrimSpace(line), "[ucloud-k8s] ")
	if line == "" {
		return
	}
	line = time.Now().UTC().Format("15:04:05") + "  " + line
	progressAppendFile(stdoutLogName, []byte(line+"\n"), 0644)
}

func progressAppendFile(name string, data []byte, mode os.FileMode) {
	path := filepath.Join(workMount, name)
	file, err := os.OpenFile(path, os.O_WRONLY|os.O_APPEND|os.O_CREATE, mode)
	if err != nil {
		return
	}

	_, err = file.Write(data)
	if closeErr := file.Close(); err == nil {
		err = closeErr
	}
	if err != nil {
		return
	}

	_ = os.Chown(path, serviceUid, serviceGid)
	return
}

func progressTruncate(name string, mode os.FileMode) {
	path := filepath.Join(workMount, name)
	file, err := os.OpenFile(path, os.O_WRONLY|os.O_TRUNC|os.O_CREATE, mode)
	if err != nil {
		return
	}

	if closeErr := file.Close(); closeErr != nil {
		return
	}

	_ = os.Chown(path, serviceUid, serviceGid)
}

func progressReset(operationUid string) {
	progressMu.Lock()
	defer progressMu.Unlock()
	progressLoaded = true
	progressCurrent = shared.NodeAgentProgress{OperationUid: operationUid, UpdatedAt: time.Now().UTC()}
	progressPersist(true)

	progressTruncate(stdoutLogName, 0644)
	progressTruncate(uiChannelName, 0600)
	progressWidget(progressLabelId, 0, widgetLabel{Text: "Maintenance in progress"})
	progressWidget(progressBarId, 1, widgetProgress{Progress: 0})
}

// Log endpoints
// =====================================================================================================================
// Both endpoints authenticate the peer and check the command ownership. The append endpoint limits the request size
// and the number of lines. It returns the number of lines that it wrote.

func handleLog(writer http.ResponseWriter, request *http.Request) {
	body := request.Body
	if request.ContentLength > shared.NodeAgentMaxLogBody {
		writeError(writer, http.StatusRequestEntityTooLarge, "the request body is too large")
		return
	}
	body = http.MaxBytesReader(writer, body, shared.NodeAgentMaxLogBody)

	var parsed struct {
		Lines []string `json:"lines"`
	}
	decoder := json.NewDecoder(body)
	decoder.DisallowUnknownFields()
	err := decoder.Decode(&parsed)
	if err != nil {
		writeError(writer, http.StatusBadRequest, fmt.Sprintf("invalid request body: %s", err))
		return
	}
	if decoder.Decode(&struct{}{}) != io.EOF {
		writeError(writer, http.StatusBadRequest, "the request body must contain a single JSON object")
		return
	}
	if len(parsed.Lines) == 0 {
		writeError(writer, http.StatusBadRequest, "at least one log line is required")
		return
	}
	if len(parsed.Lines) > 16 {
		writeError(writer, http.StatusBadRequest, "too many log lines in one request")
		return
	}

	operationUid, ok := readCommandAuthorization(writer, request)
	if !ok {
		return
	}

	if fenceErr := validateCommandOwnership(operationUid); fenceErr != nil {
		writeError(writer, http.StatusForbidden, fenceErr.Error())
		return
	}

	written := progressLines(parsed.Lines)
	log.Info(
		"k8s-app node agent: appended %d coordinator log lines for node operation %s",
		written,
		operationUid,
	)

	writer.Header().Set("Content-Type", "application/json")
	writer.WriteHeader(http.StatusOK)
	_ = json.NewEncoder(writer).Encode(struct {
		Written int `json:"written"`
	}{Written: written})
}

func handleLogReset(writer http.ResponseWriter, request *http.Request) {
	operationUid, ok := readCommandAuthorization(writer, request)
	if !ok {
		return
	}

	if fenceErr := validateCommandOwnership(operationUid); fenceErr != nil {
		writeError(writer, http.StatusForbidden, fenceErr.Error())
		return
	}

	progressReset(operationUid)
	log.Info(
		"k8s-app node agent: reset the node log for node operation %s",
		operationUid,
	)

	writer.Header().Set("Content-Type", "application/json")
	writer.WriteHeader(http.StatusOK)
	_ = json.NewEncoder(writer).Encode(struct {
		Reset bool `json:"reset"`
	}{Reset: true})
}
