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

	"ucloud.dk/iapp/k8s/pkg/shared"
	"ucloud.dk/shared/pkg/log"
)

const (
	workMount       = "/work"
	uiChannelName   = ".ucviz-ui"
	stdoutLogName   = "stdout-0.log"
	progressLabelId = "ucloud-init-stage"
	progressBarId   = "ucloud-init-progress"
)

var progressMu sync.Mutex

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

func progressStage(text string, percent int) {
	progressMu.Lock()
	defer progressMu.Unlock()

	progressAppendLine(fmt.Sprintf("[ucloud-k8s] %s", text))
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

	progressAppendLine(fmt.Sprintf("[ucloud-k8s] %s", text))
	progressWidget(progressLabelId, 0, widgetLabel{Text: text})
}

func progressText(text string) {
	progressMu.Lock()
	defer progressMu.Unlock()

	progressAppendLine(fmt.Sprintf("[ucloud-k8s] %s", text))
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
		progressAppendLine(trimmed)
		written++
	}
	return written
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

func progressAppendLine(line string) {
	if !strings.HasSuffix(line, "\n") {
		line = fmt.Sprintf("%s\n", line)
	}
	progressAppendFile(stdoutLogName, []byte(line), 0644)
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

func handleLog(writer http.ResponseWriter, request *http.Request) {
	if !authorizePeer(writer, request) {
		return
	}
	if request.Method != http.MethodPost {
		writeError(writer, http.StatusMethodNotAllowed, "this endpoint only accepts POST")
		return
	}

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
