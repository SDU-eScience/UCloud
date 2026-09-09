package tui

import (
	"fmt"
	"strings"

	tea "charm.land/bubbletea/v2"
)

type ListModel struct {
	Items    []string
	Selected int
}

func (m ListModel) Init() tea.Cmd {
	return nil
}

func (m ListModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.KeyPressMsg:
		switch msg.String() {

		case "q", "ctrl+c":
			return m, tea.Quit
		}
	}

	return m, nil
}

func (m ListModel) View() tea.View {
	var b strings.Builder

	b.WriteString("Workspaces:\n\n")

	b.WriteString("\n(q to quit)")

	return tea.NewView(b.String())
}

func List(model *ListModel) {
	p := tea.NewProgram(model)

	if _, err := p.Run(); err != nil {
		fmt.Println("Error running program:", err)
	}
}
