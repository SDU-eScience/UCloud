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

	"github.com/charmbracelet/x/term"
	"golang.org/x/net/websocket"
	fnd "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
)

type WsRequest struct {
	Call     string       `json:"call"`
	StreamId string       `json:"streamId"`
	Payload  WsShellEvent `json:"payload"`
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

func OpenJobShell(jobId string, provider string) error {
	cfg := InitializeUCloudClient()

	job, httpErr := orcapi.JobsRetrieve.Invoke(orcapi.JobsRetrieveRequest{
		Id: jobId,
	})
	if httpErr != nil {
		return fmt.Errorf("failed to retrieve job: %s", httpErr.Why)
	}
	if job.Status.State != orcapi.JobStateRunning {
		return fmt.Errorf("job %s is not running (state: %s), a shell can only be opened on a running job", jobId, job.Status.State)
	}

	origin := cfg.Environments[cfg.DefaultEnvironment].URL
	originSplit := strings.Split(origin, ".")
	suffixJoined := strings.Join(originSplit[1:], ".")
	usernameHint := base64.StdEncoding.EncodeToString([]byte(cfg.Username))
	res, httpErr := orcapi.JobsOpenInteractiveSession.Invoke(fnd.BulkRequest[orcapi.JobsOpenInteractiveSessionRequestItem]{
		Items: []orcapi.JobsOpenInteractiveSessionRequestItem{
			{
				Id:          jobId,
				Rank:        0,
				SessionType: orcapi.InteractiveSessionTypeShell,
			},
		},
	})

	if httpErr.AsError() != nil {
		return fmt.Errorf("failed to retrieve session: %s", httpErr.Why)
	}

	sessionIdentifier := res.Responses[0].Session.SessionIdentifier

	wsUrl := fmt.Sprintf(
		"wss://%s.%s/ucloud/%s/websocket?session=%s&usernameHint=%s",
		provider, suffixJoined, provider, sessionIdentifier, url.QueryEscape(usernameHint),
	)
	ws, err := websocket.Dial(wsUrl, "", origin)
	if err != nil {
		return err
	}
	defer ws.Close()

	callName := fmt.Sprintf("jobs.compute.%s.shell.open", provider)
	sendRequest := func(payload WsShellEvent) error {
		msg, err := json.Marshal(WsRequest{
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
		SessionIdentifier: sessionIdentifier,
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
