package filesystem

import (
	"encoding/json"
	"math"
	"net/http"
	"os"
	"path/filepath"
	"strconv"

	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

type taskFFProbeOutput struct {
	Streams []struct {
		Duration string `json:"duration"`
	} `json:"streams"`
	Format struct {
		Duration string `json:"duration"`
	} `json:"format"`
}

type TaskFFProbeDuration struct {
	DurationMilliseconds int64 `json:"durationMilliseconds"`
}

const TaskFFProbeDurationFile = "duration.json"

func taskFFProbe(spec TaskSpec) *util.HttpError {
	if os.Getenv(taskEnvId) == "" {
		log.Fatal("This code can only run inside of a task.")
		return util.HttpErr(http.StatusInternalServerError, "internal error")
	}

	taskProcessorPostUpdate(fnd.TaskStatus{
		State: fnd.TaskStateRunning,
		Title: util.OptValue("Measuring audio duration"),
	})

	stdout, stderr, ok := util.RunCommand([]string{
		"ffprobe",
		"-v", "error",
		"-protocol_whitelist", "file",
		"-safe", "1",
		"-select_streams", "a:0",
		"-show_entries", "stream=duration",
		"-show_entries", "format=duration",
		"-of", "json",
		spec.Source,
	})
	if !ok {
		log.Warn("ffprobe failed: stderr=%s", stderr)
		return util.HttpErr(http.StatusUnprocessableEntity, "unable to measure audio duration")
	}

	var probe taskFFProbeOutput
	if err := json.Unmarshal([]byte(stdout), &probe); err != nil {
		log.Warn("ffprobe returned invalid JSON: %v", err)
		return util.HttpErr(http.StatusUnprocessableEntity, "unable to measure audio duration")
	}

	seconds := 0.0
	if len(probe.Streams) > 0 {
		seconds, _ = strconv.ParseFloat(probe.Streams[0].Duration, 64)
	}
	if seconds <= 0 || math.IsNaN(seconds) || math.IsInf(seconds, 0) {
		seconds, _ = strconv.ParseFloat(probe.Format.Duration, 64)
	}
	if seconds <= 0 || math.IsNaN(seconds) || math.IsInf(seconds, 0) ||
		seconds > float64(math.MaxInt64)/1000 {
		return util.HttpErr(http.StatusUnprocessableEntity, "unable to measure audio duration")
	}

	milliseconds := int64(math.Round(seconds * 1000))
	if milliseconds <= 0 {
		return util.HttpErr(http.StatusUnprocessableEntity, "unable to measure audio duration")
	}

	data, err := json.Marshal(TaskFFProbeDuration{DurationMilliseconds: milliseconds})
	if err != nil {
		return util.HttpErr(http.StatusInternalServerError, "could not encode audio duration")
	}

	outputPath := filepath.Join(filepath.Dir(spec.Source), TaskFFProbeDurationFile)
	if writeErr := WriteFileAtomic(outputPath, data, 0660); writeErr != nil {
		return writeErr
	}
	return nil
}
