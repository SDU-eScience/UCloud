package launcher2

import (
	_ "embed"
	"fmt"
	"io"
	"os"
	"strings"

	"github.com/charmbracelet/bubbles/spinner"
	"github.com/charmbracelet/bubbles/viewport"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
)

type controlsHelp struct {
	Key  string
	Help string
}

type tuiMenu struct {
	Width  int
	Height int
	Tab    int

	Home struct {
		SectionActive     bool
		Section           int
		SectionItem       int
		ServiceLoading    string
		ServiceStartError string
	}

	Service struct {
		Open             Service
		LogCh            chan string
		LogContent       string
		CancelCurrentLog func()
		ShowDescription  bool
	}

	Management struct {
		SectionItem int
	}

	Spinner  spinner.Model
	ViewPort viewport.Model
}

func (m *tuiMenu) Init() tea.Cmd {
	m.Spinner = spinner.New()
	m.Spinner.Spinner = spinner.Dot

	m.ViewPort = viewport.New(1, 1)
	m.Service.LogCh = make(chan string)
	return tea.Batch(m.Spinner.Tick, m.receiveLogMessage)
}

type tuiMenuLogMessage struct {
	Message string
}

func (m *tuiMenu) receiveLogMessage() tea.Msg {
	msg := <-m.Service.LogCh
	return tuiMenuLogMessage{Message: msg}
}

func (m *tuiMenu) toggleServiceDescription() {
	m.Service.ShowDescription = !m.Service.ShowDescription
	if !m.Service.ShowDescription {
		m.ViewPort.SetContent(m.Service.LogContent)
		m.ViewPort.GotoBottom()
	} else {
		service := m.Service.Open
		doc := DocumentationLong(service.Name)
		if doc == "" {
			doc = "No documentation written (yet)"
		}

		m.ViewPort.SetContent(doc)
		m.ViewPort.GotoTop()
	}
}

func (m *tuiMenu) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		m.Width, m.Height = msg.Width, msg.Height
		return m, nil

	case tuiMenuLogMessage:
		m.Service.LogContent += msg.Message
		if !m.Service.ShowDescription && m.Home.ServiceStartError != m.Service.Open.Name {
			isFollowing := m.ViewPort.ScrollPercent() == 1
			m.ViewPort.SetContent(m.Service.LogContent)
			if isFollowing {
				m.ViewPort.GotoBottom()
			}
		}
		return m, m.receiveLogMessage

	case TeaExecFinished:
		return m, func() tea.Msg {
			return tea.ResumeMsg{}
		}

	case tea.KeyMsg:
		switch msg.String() {
		case "ctrl+c":
			return m, tea.Quit
		case "f1":
			m.Tab = 0
		case "f2":
			m.Tab = 1
		case "f3":
			m.Tab = 2
		}

		switch m.Tab {
		case 0:
			h := &m.Home
			switch msg.String() {
			case "left", "h":
				h.Section--
				h.Section = max(h.Section, 0)
			case "up", "k":
				if h.SectionActive {
					h.SectionItem--
					if h.SectionItem < 0 {
						h.SectionItem = 0
						h.SectionActive = false
					}
				}
			case "right", "l":
				h.Section++
				h.Section = min(h.Section, 2)
			case "down", "j":
				if h.SectionActive {
					h.SectionItem++
				} else {
					h.SectionActive = true
				}
			case "enter":
				if !h.SectionActive {
					h.SectionActive = true
				} else {
					// Enter service
					svc, ok := m.SelectedService()
					if ok {
						m.Service.Open = svc
						m.Tab = 2

						if m.Service.CancelCurrentLog != nil {
							m.Service.CancelCurrentLog()
							m.Service.CancelCurrentLog = nil
						}
						m.Service.LogContent = ""

						if svc.Enabled() {
							var inpChannel chan string
							if svc.Flags&SvcNative != 0 {
								ch, cancel := ExecuteWithLog(
									ComposeExecCommand(svc.Name, []string{"tail", "-n", "1000", "-F", "/var/log/ucloud/server.log"}, false),
								)

								inpChannel = ch
								m.Service.CancelCurrentLog = cancel
							} else {
								cmd := ComposeBaseCommand()
								cmd = append(cmd, "logs", svc.Name, "--follow", "--no-log-prefix")
								ch, cancel := ExecuteWithLog(cmd)

								inpChannel = ch
								m.Service.CancelCurrentLog = cancel
							}

							go func() {
								for {
									logMsg, ok := <-inpChannel
									if !ok {
										break
									} else {
										m.Service.LogCh <- logMsg
									}
								}
							}()

							m.Service.ShowDescription = true
							m.toggleServiceDescription()
						}
					}
				}

			case "esc":
				if h.SectionActive {
					h.SectionActive = false
					h.SectionItem = 0
				}

			case "s":
				svc, ok := m.SelectedService()
				if ok && svc.Enabled() && svc.Flags&SvcExec != 0 {
					return m, tea.Exec(
						&TeaExecCommand{Runner: func() error {
							return ServiceOpenShell(svc)
						}},

						func(err error) tea.Msg {
							return TeaExecFinished{}
						},
					)
				} else {
					return m, nil
				}

			case "r":
				svc, ok := m.SelectedService()
				if ok && svc.Enabled() {
					h.ServiceLoading = svc.Name
					h.ServiceStartError = ""
					return m, func() tea.Msg {
						result := StartServiceEx(svc, false)
						if result.ExitCode != 0 {
							h.ServiceStartError = svc.Name
							return nil
						}
						h.ServiceLoading = ""
						return nil
					}
				} else {
					return m, nil
				}
			}

		case 1:
			mgmt := &m.Management

			switch msg.String() {
			case "up", "k":
				mgmt.SectionItem--
			case "down", "j":
				mgmt.SectionItem++
			case "enter":
				runner := tuiManagementActions[mgmt.SectionItem].Runner
				if runner != nil {
					MenuActionRequested = runner
					return m, tea.Quit
				}
			}

		case 2:
			switch msg.String() {
			case "esc":
				m.Tab = 0
			case "up", "k":
				m.ViewPort.ScrollUp(1)
			case "down", "j":
				m.ViewPort.ScrollDown(1)
			case "pgdown":
				m.ViewPort.PageDown()
			case "pgup":
				m.ViewPort.PageUp()
			case "home":
				m.ViewPort.GotoTop()
			case "end":
				m.ViewPort.GotoBottom()
			}

			if m.Service.Open.Enabled() {
				switch msg.String() {
				case "d":
					m.toggleServiceDescription()
				case "s":
					svc := m.Service.Open
					if svc.Name != "" {
						return m, tea.Exec(
							&TeaExecCommand{Runner: func() error {
								ServiceOpenShell(svc)
								return nil
							}},

							func(err error) tea.Msg {
								return TeaExecFinished{}
							},
						)
					} else {
						return m, nil
					}

				case "r":
					svc := m.Service.Open
					if svc.Name != "" {
						h := &m.Home
						h.ServiceLoading = svc.Name
						h.ServiceStartError = ""
						return m, func() tea.Msg {
							result := StartServiceEx(svc, false)
							h.ServiceLoading = ""
							if result.ExitCode != 0 {
								h.ServiceStartError = svc.Name
								m.Service.LogContent = result.Stdout + result.Stderr
								m.ViewPort.SetContent(m.Service.LogContent)
							}
							return nil
						}
					} else {
						return m, nil
					}
				}
			} else {
				switch msg.String() {
				case "e":
					svc := m.Service.Open
					ClusterFeatures[svc.Feature] = true
					if svc.DependsOn.Present {
						ClusterFeatures[svc.DependsOn.Value] = true
					}
					MenuActionRequested = func() {
						RegisterServices()
						ClusterStart(false)
					}
					return m, tea.Quit
				}
			}
		}

		return m, nil
	}

	var (
		cmds []tea.Cmd
		cmd  tea.Cmd
	)

	m.Spinner, cmd = m.Spinner.Update(msg)
	cmds = append(cmds, cmd)

	m.ViewPort, cmd = m.ViewPort.Update(msg)
	cmds = append(cmds, cmd)

	return m, tea.Batch(cmds...)
}

func ServiceOpenShell(svc Service) error {
	SetTerminalTitle(fmt.Sprintf("%s: %s", svc.UiParent, svc.Name))
	args := ComposeExecCommand(svc.Name, []string{"/bin/sh", "-c", "bash || sh"}, true)
	_ = PtyExecCommand(args, &StatusInfo{})
	SetTerminalTitle("UCloud")
	return nil
}

func (m *tuiMenu) SelectedService() (Service, bool) {
	if m.Tab != 0 || !m.Home.SectionActive {
		return Service{}, false
	}

	sectionParent := ""
	switch m.Home.Section {
	case 0:
		sectionParent = UiParentCore
	case 1:
		sectionParent = UiParentK8s
	case 2:
		sectionParent = UiParentSlurm
	}

	idx := 0
	for _, svc := range AllServices {
		if svc.UiParent == sectionParent {
			if idx == m.Home.SectionItem {
				return svc, true
			}
			idx++
		}
	}
	return Service{}, false
}

func (m *tuiMenu) serviceTab(b *strings.Builder, bodyContainer lipgloss.Style, bodyWidth int) {
	s := &m.Service
	svc := s.Open

	{
		statusIcon := base.Bold(true).Foreground(green).MarginRight(1).Render("✔")
		if m.Home.ServiceLoading == svc.Name {
			statusIcon = base.Bold(true).Foreground(green).Render(m.Spinner.View())
		}
		if !m.Service.Open.Enabled() {
			statusIcon = base.Bold(true).Foreground(red).MarginRight(1).Render("⛌")
		}

		header := header.Width(bodyWidth).MarginLeft(2).MarginRight(2).Render(statusIcon + svc.UiParent + ": " + svc.Title)
		b.WriteString(header + "\n")
	}

	{
		description := DocumentationShort[svc.Name]

		if description == "" {
			description = base.Italic(true).Render("No description written (yet)")
		}

		if !svc.Enabled() {
			description += base.Align(lipgloss.Center).Bold(true).Width(bodyWidth).Foreground(red).
				Render("\n\nThis service is not installed. Press 'E' to install it.")
		}

		b.WriteString(bodyContainer.Height(2).Render(description) + "\n")

		var controls []controlsHelp
		if svc.Enabled() {
			controls = append(controls, controlsHelp{
				Key:  "R",
				Help: "Restart",
			})

			if svc.Flags&SvcExec != 0 {
				controls = append(controls, controlsHelp{
					Key:  "S",
					Help: "Shell",
				})
			}

			if !m.Service.ShowDescription {
				controls = append(controls, controlsHelp{
					Key:  "D",
					Help: "Documentation",
				})
			} else {
				controls = append(controls, controlsHelp{
					Key:  "D",
					Help: "Show logs",
				})
			}
		}

		if len(controls) > 0 {
			b.WriteString(bodyContainer.Render(base.Bold(true).Render("Controls: ")+m.controlsView(controls)) + "\n\n")
		}
	}

	m.ViewPort.Height = m.Height - lipgloss.Height(b.String()) - 2
	m.ViewPort.Width = m.Width - 4

	if !svc.Enabled() && !s.ShowDescription {
		m.toggleServiceDescription()
	}

	if s.ShowDescription {
		{
			header := header.Width(bodyWidth).MarginLeft(2).MarginRight(2).Render("Description")
			b.WriteString(header + "\n")
		}
	} else {
		{
			header := header.Width(bodyWidth).MarginLeft(2).MarginRight(2).Render("Logs")
			b.WriteString(header + "\n")
		}
	}

	{
		b.WriteString(bodyContainer.Render(m.ViewPort.View()) + "\n")
		b.WriteString(bodyContainer.Align(lipgloss.Right).Render(fmt.Sprintf("%d%%", int(m.ViewPort.ScrollPercent()*100))))
	}
}

func (m *tuiMenu) homeTab(b *strings.Builder, bodyContainer lipgloss.Style, bodyWidth int) {
	h := &m.Home
	{
		checkmark := lipgloss.NewStyle().Bold(true).Render("🌎 ")
		link := base.Underline(true).Render("https://ucloud.localhost.direct/")

		subtitle := lipgloss.NewStyle().Bold(true).
			Foreground(lipgloss.Color("#389F1A")).
			Render(lipgloss.JoinHorizontal(lipgloss.Top, checkmark, link))

		statusLeft := base.Bold(true).Width(bodyWidth - lipgloss.Width(subtitle)).Render("Status")

		row := header.Width(bodyWidth).MarginLeft(2).MarginRight(2).Render(
			lipgloss.JoinHorizontal(lipgloss.Top, statusLeft, subtitle),
		)
		b.WriteString(row + "\n\n")
	}

	var controls []controlsHelp

	{
		if !h.SectionActive {
			controls = append(controls, controlsHelp{
				Key:  "←",
				Help: "Previous component",
			})
			controls = append(controls, controlsHelp{
				Key:  "→",
				Help: "Next component",
			})
			controls = append(controls, controlsHelp{
				Key:  "⏎",
				Help: "Select",
			})
		} else {
			controls = append(controls, controlsHelp{
				Key:  "Esc",
				Help: "Previous menu",
			})
			controls = append(controls, controlsHelp{
				Key:  "↑",
				Help: "Previous component",
			})
			controls = append(controls, controlsHelp{
				Key:  "↓",
				Help: "Next component",
			})
		}

		var sections []string
		renderSection := func(title string) {
			innerWidth := 38

			var children []string
			children = append(children)

			if h.Section == len(sections) && !h.SectionActive {
				children = append(children, activeSection.Width(innerWidth).MarginBottom(1).Render(title))
			} else {
				children = append(children, bold.Width(innerWidth).MarginBottom(1).Render(title))
			}

			svcCount := 0
			for _, service := range AllServices {
				if service.UiParent == title {
					svcCount++
				}
			}

			if h.Section == len(sections) && h.SectionActive {
				h.SectionItem = min(h.SectionItem, svcCount-1)
			}

			for _, service := range AllServices {
				if service.UiParent != title {
					continue
				}

				text := ""
				enabled := service.Enabled()
				hover := h.Section == len(sections) && h.SectionItem == len(children)-1 && h.SectionActive

				itemBase := base
				if hover {
					itemBase = activeSection
				}

				iconBaseStyle := itemBase.Width(3).PaddingLeft(1)
				if h.ServiceStartError == service.Name {
					text = iconBaseStyle.Foreground(yellow).Render("!")
				} else if h.ServiceLoading == service.Name {
					text = iconBaseStyle.Foreground(green).Bold(true).Render(m.Spinner.View())
				} else if enabled {
					text = iconBaseStyle.Foreground(green).Render("✔")
				} else {
					text = iconBaseStyle.Foreground(red).Render("⛌")
				}

				var options []string
				optsText := ""
				if hover {
					if service.Enabled() {
						options = append(options, "R")
						controls = append(controls, controlsHelp{
							Key:  "R",
							Help: "Restart",
						})

						if service.Flags&SvcExec != 0 {
							options = append(options, "S")
							controls = append(controls, controlsHelp{
								Key:  "S",
								Help: "Shell",
							})
						}
					}
					options = append(options, "⏎")
					controls = append(controls, controlsHelp{
						Key:  "⏎",
						Help: "Open service",
					})

					optsText = strings.Join(options, " | ") + "  "
				}

				text += lipgloss.JoinHorizontal(
					lipgloss.Top,
					itemBase.Width(innerWidth-lipgloss.Width(text)-lipgloss.Width(optsText)).Render(service.Title),
					itemBase.Render(optsText),
				)

				children = append(children, text)
			}
			section := bodyContainer.Width(innerWidth).MarginRight(2).Render(lipgloss.JoinVertical(lipgloss.Left, children...))
			sections = append(sections, section)
		}

		renderSection(UiParentCore)
		renderSection(UiParentK8s)
		renderSection(UiParentSlurm)

		b.WriteString(lipgloss.JoinHorizontal(lipgloss.Top, sections...) + "\n\n\n")
	}

	{
		emptySpaceToAdd := m.Height - lipgloss.Height(b.String()) - 3
		b.WriteString(strings.Repeat("\n", max(0, emptySpaceToAdd)))

		header := header.Width(bodyWidth).MarginLeft(2).MarginRight(2).Render("Controls")
		b.WriteString(header + "\n")

		row := m.controlsView(controls)
		b.WriteString(bodyContainer.Render(row))
		b.WriteString("\n")
	}
}

type tuiManagementAction struct {
	Text   string
	Runner func()
}

var tuiManagementDiv = strings.Repeat("-", 40)

var tuiManagementActions = []tuiManagementAction{
	{
		Text: "Restart environment",
		Runner: func() {
			ClusterStart(true)
		},
	},
	{
		Text: "Stop environment",
		Runner: func() {
			ClusterStop()
		},
	},
	{
		Text: tuiManagementDiv,
	},

	{
		Text: "Delete environment",
		Runner: func() {
			ClusterDelete()
			os.Exit(0)
		},
	},
}

func (m *tuiMenu) managementTab(b *strings.Builder, bodyContainer lipgloss.Style, bodyWidth int) {
	mgmt := &m.Management

	{
		header := header.Width(bodyWidth).MarginLeft(2).MarginRight(2).Render("Actions")
		b.WriteString(header + "\n\n")
	}

	if mgmt.SectionItem < 0 {
		mgmt.SectionItem = 0
	} else if mgmt.SectionItem >= len(tuiManagementActions) {
		mgmt.SectionItem = len(tuiManagementActions) - 1
	}

	var children []string
	for i, act := range tuiManagementActions {
		hover := mgmt.SectionItem == i

		itemBase := base
		if hover {
			itemBase = activeSection
		}

		itemBase = itemBase.Width(40)

		if act.Text == tuiManagementDiv {
			children = append(children, "\n"+itemBase.Render(act.Text)+"\n")
		} else {
			children = append(children, itemBase.Render(act.Text))
		}
	}

	section := bodyContainer.Width(bodyWidth).MarginRight(2).Render(lipgloss.JoinVertical(lipgloss.Left, children...))
	b.WriteString(section + "\n\n")

	var controls []controlsHelp
	controls = append(controls, controlsHelp{
		Key:  "↑",
		Help: "Previous action",
	})
	controls = append(controls, controlsHelp{
		Key:  "↓",
		Help: "Next action",
	})
	controls = append(controls, controlsHelp{
		Key:  "Enter",
		Help: "Select action",
	})

	{
		emptySpaceToAdd := m.Height - lipgloss.Height(b.String()) - 3
		b.WriteString(strings.Repeat("\n", max(0, emptySpaceToAdd)))

		header := header.Width(bodyWidth).MarginLeft(2).MarginRight(2).Render("Controls")
		b.WriteString(header + "\n")

		row := m.controlsView(controls)
		b.WriteString(bodyContainer.Render(row))
	}
}

func (m *tuiMenu) controlsView(controls []controlsHelp) string {
	row := ""
	for i, c := range controls {
		sty := base.MarginRight(1)
		if i != 0 {
			sty = sty.PaddingLeft(1).Border(lipgloss.NormalBorder(), false, false, false, true)
		}
		row += sty.Render(fmt.Sprintf("%s %s", base.Italic(true).Render(c.Key), c.Help))
	}
	return row
}

func (m *tuiMenu) View() string {
	b := &strings.Builder{}

	{
		components := []string{}
		addTab := func(name string) {
			title := fmt.Sprintf("%s [F%d]", name, len(components)+1)
			if m.Tab == len(components) {
				components = append(components, activeTab.Render(title))
			} else {
				components = append(components, tab.Render(title))
			}
		}

		addTab("Dashboard")
		addTab("Management")
		if m.Service.Open.Name != "" {
			addTab("Service")
		}

		bannerText := bannerStyle.Render(string(banner))
		bannerWidth := lipgloss.Width(bannerText)

		row := lipgloss.JoinHorizontal(
			lipgloss.Top,
			components...,
		)
		gap := tabGap.Render(strings.Repeat(" ", max(0, m.Width-lipgloss.Width(row)-bannerWidth-2)))
		row = lipgloss.JoinHorizontal(lipgloss.Bottom, row, gap)
		row = lipgloss.JoinHorizontal(lipgloss.Bottom, row, bannerText)
		b.WriteString(row + "\n\n")
	}

	bodyWidth := m.Width - 4

	bodyContainer := lipgloss.NewStyle().Width(bodyWidth).Margin(0, 2)

	switch m.Tab {
	case 0:
		m.homeTab(b, bodyContainer, bodyWidth)
	case 1:
		m.managementTab(b, bodyContainer, bodyWidth)
	case 2:
		m.serviceTab(b, bodyContainer, bodyWidth)
	}

	return b.String()
}

// Styling
// =====================================================================================================================

var (
	normal     = lipgloss.Color("#ffffff")
	subtle     = lipgloss.AdaptiveColor{Light: "#D9DCCF", Dark: "#767676"}
	highlight  = lipgloss.AdaptiveColor{Light: "#874BFD", Dark: "#2261cb"}
	ucloudBlue = lipgloss.Color("#2261cb")
	red        = lipgloss.Color("#E11005")
	green      = lipgloss.Color("#389F1A")
	yellow     = lipgloss.Color("#E6B704")

	bannerStyle = lipgloss.NewStyle().Padding(1, 1, 0, 1)

	base          = lipgloss.NewStyle().Foreground(normal)
	bold          = lipgloss.NewStyle().Foreground(normal).Bold(true)
	activeSection = bold.Background(lipgloss.Color("#ffffff")).Foreground(lipgloss.Color("#000000"))

	header = base.
		Bold(true).
		BorderStyle(lipgloss.NormalBorder()).
		BorderBottom(true).
		BorderForeground(subtle).
		MarginRight(2)

	activeTabBorder = lipgloss.Border{
		Top:         "─",
		Bottom:      " ",
		Left:        "│",
		Right:       "│",
		TopLeft:     "╭",
		TopRight:    "╮",
		BottomLeft:  "┘",
		BottomRight: "└",
	}

	tabBorder = lipgloss.Border{
		Top:         "─",
		Bottom:      "─",
		Left:        "│",
		Right:       "│",
		TopLeft:     "╭",
		TopRight:    "╮",
		BottomLeft:  "┴",
		BottomRight: "┴",
	}

	tab = lipgloss.NewStyle().
		Border(tabBorder, true).
		BorderForeground(highlight).
		Padding(0, 1)

	activeTab = tab.Border(activeTabBorder, true).Bold(true)

	tabGap = tab.
		BorderTop(false).
		BorderLeft(false).
		BorderRight(false)
)

//go:embed config/banner.txt
var banner []byte

type TeaExecCommand struct {
	Runner func() error
}

func (c TeaExecCommand) SetStdin(r io.Reader)  {}
func (c TeaExecCommand) SetStdout(w io.Writer) {}
func (c TeaExecCommand) SetStderr(w io.Writer) {}

func (c TeaExecCommand) Run() error {
	return c.Runner()
}

type TeaExecFinished struct{}
