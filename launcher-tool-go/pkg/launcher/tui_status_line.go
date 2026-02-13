package launcher

import "github.com/charmbracelet/lipgloss"

type StatusInfo struct {
	StatusLine string
}

var (
	statusNugget = lipgloss.NewStyle().
			Foreground(lipgloss.Color("#ffffff")).
			Background(lipgloss.Color("#2261cb")).
			Padding(0, 1)

	statusBarStyle = lipgloss.NewStyle().
			Foreground(lipgloss.AdaptiveColor{Light: "#343433", Dark: "#C1C6B2"}).
			Background(lipgloss.AdaptiveColor{Light: "#D9DCCF", Dark: "#353533"})

	statusStyle = lipgloss.NewStyle().
			Inherit(statusBarStyle).
			Foreground(lipgloss.Color("#ffffff")).
			Background(ucloudBlue).
			Padding(0, 1).
			MarginRight(1)

	statusText = lipgloss.NewStyle().Inherit(statusBarStyle)

	ucloudStyle = statusNugget.
			Background(lipgloss.Color("#ffffff")).
			Foreground(lipgloss.Color("#2261cb")).
			Bold(true)
)

func StatusLine(width int, info StatusInfo) string {
	w := lipgloss.Width

	statusKey := statusStyle.Render("SHELL")
	encoding := statusNugget.Render(Version)
	ucloud := ucloudStyle.Render("🔹 UCloud")
	statusVal := statusText.
		Width(width - w(statusKey) - w(encoding) - w(ucloud)).
		Render(info.StatusLine)

	bar := lipgloss.JoinHorizontal(lipgloss.Top,
		statusKey,
		statusVal,
		encoding,
		ucloud,
	)

	return statusBarStyle.Width(width).Render(bar)
}
