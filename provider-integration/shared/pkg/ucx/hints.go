package ucx

import (
	"encoding/json"
	"strings"
)

const (
	clientKindHeadless = "headless"
	clientUpdatesUi    = "ui"
	clientUpdatesMsgs  = "messages"
)

type ClientHints struct {
	Headless bool
	Updates  string
}

func parseClientHints(payload string) ClientHints {
	hints := ClientHints{
		Updates: clientUpdatesUi,
	}

	var raw struct {
		ClientKind string `json:"clientKind"`
		Updates    string `json:"updates"`
	}

	if err := json.Unmarshal([]byte(payload), &raw); err != nil {
		return hints
	}

	hints.Headless = strings.EqualFold(strings.TrimSpace(raw.ClientKind), clientKindHeadless)

	switch strings.TrimSpace(raw.Updates) {
	case clientUpdatesUi:
		hints.Updates = clientUpdatesUi
	case clientUpdatesMsgs:
		hints.Updates = clientUpdatesMsgs
	case "none":
		hints.Updates = "none"
	default:
		if hints.Headless {
			hints.Updates = clientUpdatesMsgs
		}
	}

	return hints
}

func (s *Session) SetClientHints(hints ClientHints) {
	if hints.Updates == "" {
		if hints.Headless {
			hints.Updates = clientUpdatesMsgs
		} else {
			hints.Updates = clientUpdatesUi
		}
	}

	s.mu.Lock()
	s.hints = hints
	s.mu.Unlock()
}

func (s *Session) Headless() bool {
	s.mu.RLock()
	result := s.hints.Headless
	s.mu.RUnlock()
	return result
}

func (s *Session) Updates() string {
	s.mu.RLock()
	result := s.hints.Updates
	s.mu.RUnlock()

	if result == "" {
		return clientUpdatesUi
	}
	return result
}

func AppHeadless(app Application) bool {
	if app == nil {
		return false
	}

	session := *app.Session()
	if session == nil {
		return false
	}

	return session.Headless()
}

func (s *Session) sendHeadlessMount() {
	s.sendUiMount(UiMount{
		InterfaceId: "-",
		Root:        Text("headless"),
		Model:       map[string]Value{},
	})
}
