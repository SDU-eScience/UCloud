package shared

import (
	"bufio"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/url"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"github.com/charmbracelet/x/term"
	"golang.org/x/net/websocket"
	fnd "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type JobOpenDefaultType string

const (
	JobOpenWebType    JobOpenDefaultType = "WEB"
	JobOpenVNCType    JobOpenDefaultType = "VNC"
	JobOpenShellType  JobOpenDefaultType = "SHELL"
	JobOpenCustomType JobOpenDefaultType = "CUSTOM"
)

type WsShellRequest struct {
	Call     string       `json:"call"`
	StreamId string       `json:"streamId"`
	Payload  WsShellEvent `json:"payload"`
}
type WsLogsRequest struct {
	Call     string              `json:"call"`
	StreamId string              `json:"streamId"`
	Payload  fnd.FindByStringId  `json:"payload"`
	Bearer   string              `json:"bearer"`
	Project  util.Option[string] `json:"project,omitempty"`
}

type WsResponse struct {
	Type     string          `json:"type"`
	StreamId string          `json:"streamId"`
	Payload  json.RawMessage `json:"payload"`
}

type WsShellEvent struct {
	Type              string `json:"type,omitempty"`
	SessionIdentifier string `json:"sessionIdentifier,omitempty"`
	Cols              int    `json:"cols,omitempty"`
	Rows              int    `json:"rows,omitempty"`
	Data              string `json:"data,omitempty"`
}

type WsLogEvent struct {
	Type      string `json:"type"`
	SessionID string `json:"sessionID"`
	Data      string `json:"data"`
}

func retrieveInteractiveSession(jobId string, rank int, targetName string, openDefaultType JobOpenDefaultType) (*orcapi.OpenSessionWithProvider, error) {
	sessionType := orcapi.InteractiveSessionTypeWeb
	target := util.OptStringIfNotEmpty("")
	switch openDefaultType {
	case JobOpenVNCType:
		sessionType = orcapi.InteractiveSessionTypeVnc
	case JobOpenShellType:
		sessionType = orcapi.InteractiveSessionTypeShell
	case JobOpenCustomType:
		if targetName == "" {
			return nil, fmt.Errorf("target name is required for custom type")
		}
		target = util.OptStringIfNotEmpty(targetName)
	case JobOpenWebType:
		sessionType = orcapi.InteractiveSessionTypeWeb
	default:
		return nil, fmt.Errorf("invalid open default type")
	}

	session, httpErr := orcapi.JobsOpenInteractiveSession.Invoke(fnd.BulkRequest[orcapi.JobsOpenInteractiveSessionRequestItem]{
		Items: []orcapi.JobsOpenInteractiveSessionRequestItem{
			{
				Id:          jobId,
				Rank:        rank,
				SessionType: sessionType,
				Target:      target,
			},
		},
	})
	if httpErr.AsError() != nil {
		return nil, fmt.Errorf("failed to retrieve session: %s", httpErr.Why)
	}
	return &session.Responses[0], nil
}

func JobShell(jobId string, rank int, browser bool) error {
	cfg := InitializeUCloudClient()
	job, err := findJobById(jobId)
	if err != nil {
		return err
	}
	if job.Status.State != orcapi.JobStateRunning {
		return fmt.Errorf("job %s is not running (state: %s), a shell can only be opened on a running job", jobId, job.Status.State)
	}
	origin := cfg.Environments[cfg.DefaultEnvironment].URL
	usernameHint := base64.StdEncoding.EncodeToString([]byte(cfg.Username))
	if browser {
		OpenBrowser(fmt.Sprintf("%s/app/applications/shell/%s/%d?hide-frame", origin, rank, jobId))
		return nil
	}

	session, err := retrieveInteractiveSession(jobId, rank, "", JobOpenShellType)
	if err != nil {
		return err
	}
	if browser {
		OpenBrowser(session.Session.RedirectClientTo)
		return nil
	}
	return OpenWebsocketShellSession(session, usernameHint, origin, job.Specification.Product.Provider)
}

func OpenWebsocketShellSession(session *orcapi.OpenSessionWithProvider, usernameHint string, origin string, provider string) error {
	wsUrl := fmt.Sprintf("%s/ucloud/%s/websocket?session=%s&usernameHint=%s", session.ProviderDomain, session.ProviderId, session.Session.SessionIdentifier, url.QueryEscape(usernameHint))
	ws, err := websocket.Dial(wsUrl, "", origin)
	if err != nil {
		return err
	}
	defer ws.Close()

	callName := fmt.Sprintf("jobs.compute.%s.shell.open")
	sendRequest := func(payload WsShellEvent) error {
		msg, err := json.Marshal(WsShellRequest{
			Call:     callName,
			StreamId: "0",
			Payload:  payload,
		})
		if err != nil {
			return err
		}
		return websocket.Message.Send(ws, string(msg))
	}

	cols, rows := 80, 24
	if width, height, err := term.GetSize(os.Stdin.Fd()); err == nil {
		cols, rows = width, height
	}
	if err := sendRequest(WsShellEvent{
		Type:              "initialize",
		SessionIdentifier: session.Session.SessionIdentifier,
		Cols:              cols,
		Rows:              rows,
	}); err != nil {
		return err
	}

	doneRead := make(chan struct{})
	go func() {
		defer close(doneRead)

		for {
			var raw string
			if err := websocket.Message.Receive(ws, &raw); err != nil {
				return
			}

			var envelope WsResponse
			if err := json.Unmarshal([]byte(raw), &envelope); err != nil {
				continue
			}

			var payload WsShellEvent
			if err := json.Unmarshal(envelope.Payload, &payload); err != nil {
				continue
			}

			if payload.Type == "data" {
				_, _ = os.Stdout.WriteString(payload.Data)
			}
		}
	}()

	inputDone := make(chan error, 1)
	if term.IsTerminal(os.Stdin.Fd()) {
		oldState, err := term.MakeRaw(os.Stdin.Fd())
		if err != nil {
			return err
		}
		defer func() { _ = term.Restore(os.Stdin.Fd(), oldState) }()

		resize := make(chan os.Signal, 1)
		signal.Notify(resize, syscall.SIGWINCH)
		defer signal.Stop(resize)
		go func() {
			for range resize {
				if width, height, err := term.GetSize(os.Stdin.Fd()); err == nil {
					_ = sendRequest(WsShellEvent{
						Type: "resize",
						Cols: width,
						Rows: height,
					})
				}
			}
		}()

		go func() {
			buf := make([]byte, 4096)
			for {
				n, err := os.Stdin.Read(buf)
				if err != nil {
					if errors.Is(err, io.EOF) {
						inputDone <- nil
					} else {
						inputDone <- err
					}
					return
				}
				if err := sendRequest(WsShellEvent{
					Type: "input",
					Data: string(buf[:n]),
				}); err != nil {
					inputDone <- err
					return
				}
			}
		}()
	} else {
		go func() {
			scanner := bufio.NewScanner(os.Stdin)
			for scanner.Scan() {
				if err := sendRequest(WsShellEvent{
					Type: "input",
					Data: scanner.Text() + "\r",
				}); err != nil {
					inputDone <- err
					return
				}
			}
			inputDone <- scanner.Err()
		}()
	}

	select {
	case err := <-inputDone:
		return err
	case <-doneRead:
		fmt.Fprintf(os.Stderr, "\r\nConnection closed.\r\n")
		return nil
	}
}

func JobLogs(jobId string, workspaceName, provider string, follow bool) error {
	cfg := InitializeUCloudClient()
	origin := cfg.Environments[cfg.DefaultEnvironment].URL
	wsUrl := strings.Replace(origin, "https://", "wss://", 1)
	wsUrl = wsUrl + "/api/jobs"

	ws, err := websocket.Dial(wsUrl, "", origin)
	if err != nil {
		return err
	}
	defer ws.Close()

	projectName := workspaceName
	if projectName == "" {
		projectName = cfg.CurrentWorkspace.GetOrDefault(PersonalWorkspaceName)
	}

	var project util.Option[string]
	if projectName != PersonalWorkspaceName {
		ws2, err := FindWorkspaceByName(projectName)
		if err != nil {
			return err
		}
		project = util.OptValue(ws2.Id)
	}

	accessToken := rpc.DefaultClient.RetrieveAccessTokenOrRefresh()
	if accessToken == "" {
		return errors.New("failed to retrieve an access token, please log in again")
	}

	msg, err := json.Marshal(WsLogsRequest{
		Call:     "jobs.follow",
		StreamId: "0",
		Payload:  fnd.FindByStringId{Id: jobId},
		Bearer:   accessToken,
		Project:  project,
	})
	if err != nil {
		return err
	}

	if err := websocket.Message.Send(ws, string(msg)); err != nil {
		return err
	}

	const (
		firstLogTimeout = 5 * time.Second
		logIdleTimeout  = 500 * time.Millisecond
	)

	logsWaitUntil := time.Time{}
	if !follow {
		logsWaitUntil = time.Now().Add(firstLogTimeout)
	}

	for {
		if !logsWaitUntil.IsZero() {
			if err := ws.SetReadDeadline(logsWaitUntil); err != nil {
				return err
			}
		}

		var raw string
		if err := websocket.Message.Receive(ws, &raw); err != nil {
			// timed out waiting for (more) logs without --follow, or the connection closed
			return nil
		}

		var envelope WsResponse
		if err := json.Unmarshal([]byte(raw), &envelope); err != nil {
			continue
		}

		var message orcapi.JobsFollowMessage
		if err := json.Unmarshal(envelope.Payload, &message); err != nil {
			continue
		}

		if message.InitialJob.Present {
			job := message.InitialJob.Value
			for _, update := range job.Updates {
				printJobUpdate(&job, update)
			}
		}

		for _, update := range message.Updates {
			printJobUpdate(&message.InitialJob.Value, update)
		}

		if message.NewStatus.Present && len(message.Updates) == 0 {
			fmt.Printf(
				"[%s] %s\n",
				formatTimestamp(fnd.Timestamp(time.Now())),
				jobStateToMessage(message.NewStatus.Value.State),
			)
		}

		gotLogEntry := false

		for _, log := range message.Log {
			if log.Channel.Present {
				// channel data currently not rendered
				continue
			}
			if log.Stdout.Present {
				gotLogEntry = true
				_, _ = os.Stdout.WriteString(log.Stdout.Value)
			}
			if log.Stderr.Present {
				gotLogEntry = true
				_, _ = os.Stderr.WriteString(log.Stderr.Value)
			}
		}

		if !follow {
			if gotLogEntry {
				logsWaitUntil = time.Now().Add(logIdleTimeout)
			} else if message.InitialJob.Present {
				logsWaitUntil = time.Now().Add(firstLogTimeout)
			}
		}

		if state := currentJobState(message); state != "" && orcapi.JobState(state).IsFinal() {
			return nil
		}
	}
}

func printJobUpdate(job *orcapi.Job, update orcapi.JobUpdate) {
	if update.Status.Present {
		status := update.Status.Value
		if strings.HasPrefix(status, "SSH:") || strings.HasPrefix(status, "Target:") {
			return
		}
		fmt.Printf("[%s] %s\n", formatTimestamp(update.Timestamp), status)
		return
	}

	if update.State.Present {
		fmt.Printf("[%s] %s\n", formatTimestamp(update.Timestamp), jobStateToMessage(update.State.Value))
		return
	}

	if job != nil {
		fmt.Printf(
			"[%s] %s has requested %dx %s from %s\n",
			formatTimestamp(update.Timestamp),
			job.Owner.CreatedBy,
			job.Specification.Replicas,
			job.Specification.Product.Id,
			providerTitle(job.Specification.Product.Provider),
		)
	}
}

func jobStateToMessage(state orcapi.JobState) string {
	switch state {
	case orcapi.JobStateInQueue:
		return "Your job is now in the queue"
	case orcapi.JobStateRunning:
		return "Your job is now running"
	case orcapi.JobStateSuccess:
		return "Your job has been processed successfully"
	case orcapi.JobStateFailure:
		return "Your job has failed"
	case orcapi.JobStateExpired:
		return "Your job has expired"
	case orcapi.JobStateSuspended:
		return "Your job has been suspended"
	default:
		return "Your job is now: " + string(state)
	}
}

func providerTitle(providerId string) string {
	return strings.ToUpper(string(providerId[0])) + strings.ReplaceAll(
		strings.ReplaceAll(providerId[1:], "_", " "), "-", " ",
	)
}

func formatTimestamp(timestamp fnd.Timestamp) string {
	return timestamp.Time().Format("15:04")
}

func currentJobState(message orcapi.JobsFollowMessage) string {
	if message.NewStatus.Present {
		return string(message.NewStatus.Value.State)
	}
	for _, update := range message.Updates {
		if update.State.Present {
			return string(update.State.Value)
		}
	}
	if message.InitialJob.Present {
		return string(message.InitialJob.Value.Status.State)
	}
	return ""
}

func JobOpenWebLink(jobId string, provider string) error {
	InitializeUCloudClient()
	session, err := retrieveInteractiveSession(jobId, 0, "", JobOpenWebType)
	if err != nil {
		return fmt.Errorf("failed to retrieve session: %s", err)
	}
	OpenBrowser(session.Session.RedirectClientTo)
	return nil
}

func JobOpenVNCLink(jobId string, provider string) error {
	InitializeUCloudClient()
	session, err := retrieveInteractiveSession(jobId, 0, "", JobOpenVNCType)
	if err != nil {
		return fmt.Errorf("failed to retrieve session: %s", err)
	}
	OpenBrowser(session.Session.RedirectClientTo)
	return nil
}

func findJobById(jobId string) (*orcapi.Job, error) {
	job, httpErr := orcapi.JobsRetrieve.Invoke(orcapi.JobsRetrieveRequest{
		Id: jobId,
	})
	if httpErr != nil {
		return nil, fmt.Errorf("failed to retrieve job: %s", httpErr.Why)
	}
	return &job, nil
}

func getSessionType(job *orcapi.Job) JobOpenDefaultType {
	if job.Status.ResolvedApplication.Present {
		switch job.Status.ResolvedApplication.Value.Invocation.ApplicationType {
		case orcapi.ApplicationTypeVnc:
			return JobOpenVNCType
		case orcapi.ApplicationTypeBatch:
			return JobOpenCustomType

		default:
			return JobOpenWebType
		}
	}
	return JobOpenWebType
}

func OpenCreatorInterface(jobID string, version string) error {
	cfg := InitializeUCloudClient()
	job, err := findJobById(jobID)
	if err != nil {
		return err
	}
	inputVersion := job.Specification.Application.Version
	if version != "" {
		inputVersion = version
	}

	origin := cfg.Environments[cfg.DefaultEnvironment].URL
	OpenBrowser(origin + ApplicationCreatorURL(ApplicationCreatorRoute{
		Operation:       "newVersion",
		ApplicationKind: "managed",
		Workspace:       "personal",
		Name:            job.Specification.Application.Name,
		Version:         inputVersion,
		ReturnTo:        "/applications/studio/a/" + job.Specification.Application.Name,
	}))
	return nil
}

func JobOpenInterface(jobId string) error {
	cfg := InitializeUCloudClient()
	job, err := findJobById(jobId)
	if err != nil {
		return err
	}
	openDefaultType := JobOpenWebType
	if job.Status.ResolvedApplication.Present {
		openDefaultType = getSessionType(job)
	}
	if openDefaultType == JobOpenVNCType {
		OpenBrowser(vncURL(cfg.Environments[cfg.DefaultEnvironment].URL, jobId))
		return nil
	}
	// These are for shell, and dynamic types
	session, err := retrieveInteractiveSession(jobId, 0, "", openDefaultType)
	if err != nil {
		return fmt.Errorf("failed to retrieve session: %s", err)
	}
	url := session.Session.RedirectClientTo
	if session.Session.RedirectClientTo == "" {
		url = session.Session.Url
	}
	OpenBrowser(url)
	return nil
}
