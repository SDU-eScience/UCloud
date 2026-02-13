package launcher

import (
	"fmt"
	"os"
	"regexp"
	"strings"

	"github.com/charmbracelet/bubbles/spinner"
	"github.com/charmbracelet/bubbles/viewport"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
)

type logTuiStreamMsg struct {
	line string
}

type logTuiStreamClose struct{}

type logTuiAttachStreamMsg struct {
	msgCh <-chan tea.Msg
}

var logTuiSidebar = ""

type logTuiModel struct {
	vp            viewport.Model
	sidebarVp     viewport.Model
	spinner       spinner.Model
	content       string
	done          bool
	scrolling     bool
	streamChannel <-chan tea.Msg
	title         *string
	TermHeight    int
	TermWidth     int
}

const (
	logTuiFailure = "❌"
	logTuiPrompt  = "❓"
	logTuiSuccess = "✅"
)

func logTuiInitModel() logTuiModel {
	vp := viewport.New(0, 0)
	vp.SetContent("")

	sidebarVp := viewport.New(0, 0)
	sidebarVp.SetContent("")

	s := spinner.New()
	s.Spinner = spinner.Dot
	s.Style = lipgloss.NewStyle().Foreground(lipgloss.Color("205"))

	return logTuiModel{
		vp:        vp,
		sidebarVp: sidebarVp,
		spinner:   s,
		scrolling: true,
	}
}

func logTuiReceiveOne(msgCh <-chan tea.Msg) tea.Cmd {
	return func() tea.Msg {
		msg, ok := <-msgCh
		if !ok {
			return logTuiStreamClose{}
		}
		return msg
	}
}

func logTuiStartStream(in <-chan string) <-chan tea.Msg {
	msgCh := make(chan tea.Msg, 256)

	go func() {
		defer close(msgCh)
		for s := range in {
			msgCh <- logTuiStreamMsg{line: s}
		}
	}()

	return msgCh
}

func (m logTuiModel) Init() tea.Cmd {
	return m.spinner.Tick
}

const logTuiSidebarWidth = 40

func (m logTuiModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		headerHeight := lipgloss.Height(m.headerView())
		footerHeight := lipgloss.Height(m.footerView())
		verticalMarginHeight := headerHeight + footerHeight

		m.sidebarVp.Width = logTuiSidebarWidth - 2
		m.sidebarVp.Height = msg.Height - verticalMarginHeight
		m.sidebarVp.YPosition = headerHeight

		m.vp.Width = msg.Width - logTuiSidebarWidth - 1
		m.vp.Height = msg.Height - verticalMarginHeight
		m.vp.YPosition = headerHeight
		m.TermHeight = msg.Height
		m.TermWidth = msg.Width

		m.vp.GotoBottom()
		m.sidebarVp.GotoBottom()
		return m, nil

	case logTuiAttachStreamMsg:
		return m, logTuiReceiveOne(msg.msgCh)

	case logTuiStreamMsg:
		newLine := msg.line
		newLine = stripANSI(newLine)
		m.content += newLine

		m.vp.SetContent(m.content)
		if m.scrolling {
			m.vp.GotoBottom()
		}

		return m, logTuiReceiveOne(m.streamChannel)

	case logTuiStreamClose:
		m.done = true
		return m, tea.Quit

	case tea.KeyMsg:
		switch msg.String() {
		case "ctrl+c", "q":
			return m, tea.Quit
		case "up", "k":
			m.vp.ScrollUp(1)
		case "down", "j":
			m.vp.ScrollDown(1)
		case "left", "h":
			m.vp.ScrollLeft(1)
		case "right", "l":
			m.vp.ScrollRight(1)
		case "pgup":
			m.vp.HalfPageUp()
		case "pgdown":
			m.vp.HalfPageDown()
		case "home":
			m.vp.GotoTop()
		case "end":
			m.vp.GotoBottom()
		}
		m.scrolling = m.vp.ScrollPercent() == 1.0
		return m, nil
	}

	var (
		cmds []tea.Cmd
		cmd  tea.Cmd
	)

	m.vp, cmd = m.vp.Update(msg)
	cmds = append(cmds, cmd)

	m.spinner, cmd = m.spinner.Update(msg)
	cmds = append(cmds, cmd)

	return m, tea.Batch(cmds...)
}

var (
	logTitleStyle = func() lipgloss.Style {
		b := lipgloss.RoundedBorder()
		b.Right = "├"
		b.BottomLeft = "├"
		return lipgloss.NewStyle().BorderStyle(b).Padding(0, 1)
	}()

	logInfoStyle = func() lipgloss.Style {
		b := lipgloss.RoundedBorder()
		b.Left = "┤"
		b.Right = "├"
		return logTitleStyle.BorderStyle(b)
	}()
)

func (m logTuiModel) headerView() string {
	titleText := "Running command..."
	if m.title != nil {
		titleText = *m.title
	}

	titleWithSpinner := fmt.Sprintf("%s %s %s", m.spinner.View(), logTuiPrompt, titleText)
	if strings.HasPrefix(titleText, logTuiFailure) {
		titleWithSpinner = titleText
	}
	title := logTitleStyle.Render(titleWithSpinner)
	line := strings.Repeat("─", max(0, m.vp.Width-lipgloss.Width(title))+1)
	return lipgloss.JoinHorizontal(lipgloss.Center, title, line)
}

func (m logTuiModel) footerView() string {
	info := logInfoStyle.Render(fmt.Sprintf("%3.f%%", m.vp.ScrollPercent()*100))
	line := "╰" + strings.Repeat("─", max(0, m.vp.Width-lipgloss.Width(info)))
	result := lipgloss.JoinHorizontal(lipgloss.Center, line, info)
	result = "│" + result[1:]
	return result
}

func (m logTuiModel) sidebarView() string {
	b := lipgloss.RoundedBorder()
	b.TopLeft = "┬"
	b.BottomLeft = "┴"
	s := lipgloss.NewStyle().BorderStyle(b).Padding(0, 1).Width(logTuiSidebarWidth - 2).Height(m.TermHeight - 4)
	m.sidebarVp.SetContent(logTuiSidebar)
	m.sidebarVp.GotoBottom()
	return s.Render(m.sidebarVp.View())
}

func (m logTuiModel) View() string {
	vp := lipgloss.NewStyle().BorderStyle(lipgloss.NormalBorder()).BorderLeft(true).Render(m.vp.View())

	left := fmt.Sprintf("%s\n%s\n%s", m.headerView(), vp, m.footerView())
	right := "\n" + m.sidebarView()
	return lipgloss.JoinHorizontal(lipgloss.Top, left, right)
}

func LogOutputTui(titleRunning *string, out chan string) {
	if HasPty {
		fmt.Printf("\x1bc")
		m := logTuiInitModel()
		m.streamChannel = logTuiStartStream(out)
		m.title = titleRunning
		p := tea.NewProgram(m, tea.WithAltScreen())
		go func() {
			p.Send(logTuiAttachStreamMsg{msgCh: m.streamChannel})
		}()

		_, _ = p.Run()

		left := "Running command..."
		if titleRunning != nil {
			left = *titleRunning
		}

		if strings.HasPrefix(left, logTuiFailure) {
			os.Exit(1)
		}
	} else {
		if titleRunning != nil {
			fmt.Println(*titleRunning)
		}

		for message := range out {
			fmt.Print(message)
		}

		left := "Running command..."
		if titleRunning != nil {
			left = *titleRunning
		}

		if strings.HasPrefix(left, logTuiFailure) {
			os.Exit(1)
		}
	}
}

func LogOutputRunWork(title string, work func(ch chan string) error) {
	mutableTitle := &title
	ch := make(chan string)
	go func() {
		err := work(ch)
		if err != nil {
			*mutableTitle = logTuiFailure + " " + title
			ch <- "\n\n"
			ch <- err.Error()
			if !HasPty {
				close(ch)
			}
		} else {
			close(ch)
		}
	}()

	LogOutputTui(mutableTitle, ch)
}

// =====================================================================================================================

var ansiRegexp = regexp.MustCompile("[\u001B\u009B][[\\]()#;?]*(?:(?:(?:[a-zA-Z\\d]*(?:;[a-zA-Z\\d]*)*)?\u0007)|" +
	"(?:(?:\\d{1,4}(?:;\\d{0,4})*)?[\\dA-PRZcf-ntqry=><~]))")

func stripANSI(s string) string {
	return ansiRegexp.ReplaceAllString(s, "")
}
