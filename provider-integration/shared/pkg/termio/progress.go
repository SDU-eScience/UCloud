package termio

import (
	"fmt"
	"io"
	"math"
	"os"
	"strings"
	"sync"
	"time"
	"unicode/utf8"
)

type ProgressState int

const (
	ProgressPending ProgressState = iota
	ProgressRunning
	ProgressSuccess
	ProgressWarning
	ProgressFailure
)

type ProgressItem struct {
	Key      string
	Label    string
	Status   string
	Progress float64
	Known    bool
	State    ProgressState
}

type progressSnapshot struct {
	Status string
	State  ProgressState
	Known  bool
	Bucket int
}

type ProgressList struct {
	out        io.Writer
	pty        bool
	color      bool
	mu         sync.Mutex
	title      string
	items      []ProgressItem
	milestones []string
	summary    string
	previous   map[string]progressSnapshot
	started    bool
	stopped    bool
	frame      int
	lastLines  int
	done       chan struct{}
	wg         sync.WaitGroup
}

const (
	progressBarWidth        = 24
	progressBarCompactWidth = 8
	progressBarPeriod       = 70 * time.Millisecond
)

var progressSpinnerFrames = []string{
	"⣾", "⣽", "⣻", "⢿", "⡿", "⣟", "⣯", "⣷",
	"⠁", "⠂", "⠄", "⡀", "⢀", "⠠", "⠐", "⠈",
}

func NewProgressList(out io.Writer, title string) *ProgressList {
	if out == nil {
		out = os.Stderr
	}
	return &ProgressList{
		out:      out,
		pty:      isPtyWriter(out),
		color:    ColorEnabled(out),
		title:    title,
		previous: map[string]progressSnapshot{},
		done:     make(chan struct{}),
	}
}

func (p *ProgressList) Start() {
	p.mu.Lock()
	if p.started || p.stopped {
		p.mu.Unlock()
		return
	}
	p.started = true

	if p.pty {
		_, _ = io.WriteString(p.out, "\033[?25l")
	} else {
		if p.title != "" {
			fmt.Fprintln(p.out, WriteStyledStringIf(p.color, Bold, DefaultColor, DefaultColor, "%s", p.title))
		}
		for _, milestone := range p.milestones {
			fmt.Fprintln(p.out, p.milestoneLineLocked(milestone))
		}
	}

	p.renderLocked()
	p.mu.Unlock()

	if p.pty {
		p.wg.Add(1)
		go p.animate()
	}
}

func (p *ProgressList) Stop() {
	p.mu.Lock()
	if p.stopped {
		p.mu.Unlock()
		return
	}
	p.stopped = true
	started := p.started

	if started && p.pty {
		p.renderPtyLocked()
		_, _ = io.WriteString(p.out, "\033[?25h")
	}
	p.mu.Unlock()

	if started {
		close(p.done)
		p.wg.Wait()
	}
}

func (p *ProgressList) Milestone(message string) {
	if message == "" {
		return
	}

	p.mu.Lock()
	defer p.mu.Unlock()

	if p.stopped {
		return
	}

	p.milestones = append(p.milestones, message)
	if !p.started {
		return
	}

	if p.pty {
		p.renderLocked()
	} else {
		fmt.Fprintln(p.out, p.milestoneLineLocked(message))
	}
}

func (p *ProgressList) Summary(message string) {
	if message == "" {
		return
	}

	p.mu.Lock()
	defer p.mu.Unlock()

	if p.stopped {
		return
	}

	p.summary = message
	if !p.started {
		return
	}

	if p.pty {
		p.renderLocked()
	} else {
		fmt.Fprintln(p.out)
		fmt.Fprintln(p.out, WriteStyledStringIf(p.color, Bold, DefaultColor, DefaultColor, "%s", message))
	}
}

func (p *ProgressList) SetItems(items []ProgressItem) {
	normalized := make([]ProgressItem, len(items))
	copy(normalized, items)
	for i := range normalized {
		if normalized[i].Key == "" {
			normalized[i].Key = normalized[i].Label
		}
	}

	p.mu.Lock()
	defer p.mu.Unlock()

	p.items = normalized
	if p.started && !p.stopped {
		p.renderLocked()
	}
}

func (p *ProgressList) SetItem(item ProgressItem) {
	if item.Key == "" {
		item.Key = item.Label
	}

	p.mu.Lock()
	defer p.mu.Unlock()

	for i := range p.items {
		if p.items[i].Key != item.Key {
			continue
		}
		p.items[i] = item
		if p.started && !p.stopped {
			p.renderLocked()
		}
		return
	}

	p.items = append(p.items, item)
	if p.started && !p.stopped {
		p.renderLocked()
	}
}

func (p *ProgressList) animate() {
	defer p.wg.Done()

	ticker := time.NewTicker(progressBarPeriod)
	defer ticker.Stop()

	for {
		select {
		case <-p.done:
			return
		case <-ticker.C:
			p.mu.Lock()
			if p.stopped {
				p.mu.Unlock()
				return
			}
			p.frame++
			p.renderPtyLocked()
			p.mu.Unlock()
		}
	}
}

func (p *ProgressList) renderLocked() {
	if p.pty {
		p.renderPtyLocked()
	} else {
		p.printChangesLocked()
	}
}

func (p *ProgressList) renderPtyLocked() {
	lines := p.linesLocked()

	if p.lastLines > 0 {
		fmt.Fprintf(p.out, "\033[%dA", p.lastLines)
	}

	for _, line := range lines {
		fmt.Fprintf(p.out, "\r\033[2K%s\n", line)
	}

	if p.lastLines > len(lines) {
		extra := p.lastLines - len(lines)
		for i := 0; i < extra; i++ {
			fmt.Fprint(p.out, "\r\033[2K\n")
		}
		fmt.Fprintf(p.out, "\033[%dA", extra)
	}

	p.lastLines = len(lines)
}

func (p *ProgressList) linesLocked() []string {
	lines := make([]string, 0, len(p.milestones)+len(p.items)+4)

	if p.title != "" {
		lines = append(lines, WriteStyledStringIf(p.color, Bold, DefaultColor, DefaultColor, "%s", p.title))
	}
	for _, milestone := range p.milestones {
		lines = append(lines, p.milestoneLineLocked(milestone))
	}
	if (p.title != "" || len(p.milestones) > 0) && len(p.items) > 0 {
		lines = append(lines, "")
	}

	items := p.items
	hidden := 0
	if p.pty {
		maxItems := writerHeight(p.out) - len(p.milestones) - 4
		if maxItems < 1 {
			maxItems = 1
		}
		if len(items) > maxItems {
			hidden = len(items) - maxItems
			items = items[:maxItems]
		}
	}

	maxLabelWidth := progressMaxLabelWidth(items)
	for _, item := range items {
		lines = append(lines, p.itemLineLocked(item, maxLabelWidth))
	}
	if hidden > 0 {
		lines = append(lines, Subtle("  … and %d more", hidden).Render(p.color))
	}

	if p.summary != "" {
		lines = append(lines, "")
		lines = append(lines, WriteStyledStringIf(p.color, Bold, DefaultColor, DefaultColor, "%s", p.summary))
	}

	return lines
}

func (p *ProgressList) milestoneLineLocked(message string) string {
	return WriteStyledStringIf(p.color, 0, Muted, DefaultColor, "  • %s", message)
}

func progressMaxLabelWidth(items []ProgressItem) int {
	width := 0
	for _, item := range items {
		if item.Label == "" {
			continue
		}
		itemWidth := utf8.RuneCountInString(item.Label) + 1
		if itemWidth > width {
			width = itemWidth
		}
	}
	return width
}

func (p *ProgressList) itemLineLocked(item ProgressItem, maxLabelWidth int) string {
	label := item.Label
	if label != "" {
		label += ":"
	}

	statusWidth := utf8.RuneCountInString(item.Status)
	terminalWidth := writerWidth(p.out)

	baseWidth := 2 + 1 + 2 + statusWidth
	if maxLabelWidth > 0 {
		baseWidth += maxLabelWidth + 2
	}

	barWidth := 0
	if terminalWidth >= baseWidth+progressBarWidth {
		barWidth = progressBarWidth
	} else if terminalWidth >= baseWidth+progressBarCompactWidth {
		barWidth = progressBarCompactWidth
	}

	prefixWidth := 2 + 1 + 2
	if barWidth > 0 {
		prefixWidth += barWidth + 2
	}
	available := terminalWidth - prefixWidth
	if available < 8 {
		available = 8
	}

	labelColumnWidth := maxLabelWidth
	const labelSpacing = 2
	if labelColumnWidth > 0 {
		if labelColumnWidth+labelSpacing+statusWidth > available {
			statusWidth = min(statusWidth, max(4, available/2))
			labelColumnWidth = available - labelSpacing - statusWidth
			if labelColumnWidth < 4 {
				labelColumnWidth = 4
				statusWidth = available - labelSpacing - labelColumnWidth
				if statusWidth < 0 {
					statusWidth = 0
				}
			}
		}

		label = truncateRunes(label, labelColumnWidth)
		labelWidth := utf8.RuneCountInString(label)
		statusWidth = min(statusWidth, available-labelSpacing-labelWidth)
		if statusWidth < 0 {
			statusWidth = 0
		}
	} else {
		statusWidth = min(statusWidth, available)
	}
	status := truncateRunes(item.Status, statusWidth)

	line := "  " + p.symbolLocked(item.State) + "  "
	if barWidth > 0 {
		line += p.barLocked(item, barWidth) + "  "
	}
	if label != "" {
		line += WriteStyledStringIf(p.color, Bold, DefaultColor, DefaultColor, "%s", label)
		line += strings.Repeat(" ", max(0, labelColumnWidth-utf8.RuneCountInString(label)))
		line += "  "
	}
	if status != "" {
		line += p.statusLocked(item.State, status)
	}

	return line
}

func (p *ProgressList) symbolLocked(state ProgressState) string {
	switch state {
	case ProgressSuccess:
		return Success("✓").Render(p.color)
	case ProgressWarning:
		return Warning("!").Render(p.color)
	case ProgressFailure:
		return Danger("✗").Render(p.color)
	case ProgressRunning:
		if p.pty {
			frame := progressSpinnerFrames[p.frame%len(progressSpinnerFrames)]
			return StyledWith(0, Cyan, "%s", frame).Render(p.color)
		}
		return StyledWith(0, Cyan, "…").Render(p.color)
	default:
		return Subtle("○").Render(p.color)
	}
}

func (p *ProgressList) statusLocked(state ProgressState, status string) string {
	switch state {
	case ProgressSuccess:
		return Success("%s", status).Render(p.color)
	case ProgressWarning:
		return Warning("%s", status).Render(p.color)
	case ProgressFailure:
		return Danger("%s", status).Render(p.color)
	case ProgressRunning:
		return StyledWith(0, Cyan, "%s", status).Render(p.color)
	default:
		return Subtle("%s", status).Render(p.color)
	}
}

func (p *ProgressList) barLocked(item ProgressItem, width int) string {
	if width <= 0 {
		return ""
	}

	if item.Known {
		fraction := item.Progress
		if fraction < 0 {
			fraction = 0
		}
		if fraction > 1 {
			fraction = 1
		}

		filled := int(math.Round(fraction * float64(width)))
		if !p.color {
			return strings.Repeat("━", filled) + strings.Repeat("·", width-filled)
		}
		if !progressStateAnimated(item.State) {
			color := progressStateColor(item.State)
			return styledProgressRun(p.color, "━", filled, color) +
				styledProgressRun(p.color, "·", width-filled, Muted)
		}
		return p.animatedKnownBarLocked(filled, width)
	}

	if !progressStateAnimated(item.State) {
		if item.State == ProgressSuccess || item.State == ProgressFailure {
			return styledProgressRun(p.color, "━", width, progressStateColor(item.State))
		}
		return styledProgressRun(p.color, "·", width, progressStateColor(item.State))
	}

	segment := 6
	if segment > width {
		segment = max(1, width/2)
	}
	span := width - segment
	if span <= 0 {
		return styledProgressRun(p.color, "·", width, Muted)
	}

	position := p.frame % (2 * span)
	if position > span {
		position = 2*span - position
	}

	if !p.color {
		return strings.Repeat("·", position) +
			strings.Repeat("━", segment) +
			strings.Repeat("·", width-position-segment)
	}

	center := float64(position) + float64(segment)/2
	return styledProgressRun(p.color, "·", position, Muted) +
		p.animatedSegmentLocked(position, segment, center) +
		styledProgressRun(p.color, "·", width-position-segment, Muted)
}

var progressBarRamp = []Color{
	1000 + 24,
	1000 + 25,
	1000 + 31,
	1000 + 32,
	1000 + 38,
	1000 + 44,
	1000 + 51,
	1000 + 87,
}

func progressStateAnimated(state ProgressState) bool {
	return state == ProgressRunning
}

func (p *ProgressList) animatedKnownBarLocked(filled int, width int) string {
	const glowRadius = 6.0
	span := float64(width) + 2*glowRadius
	center := float64(p.frame%28)/28*span - glowRadius

	builder := strings.Builder{}
	for i := 0; i < filled; i++ {
		builder.WriteString(p.glowCellLocked(i, center, glowRadius, "━"))
	}
	builder.WriteString(styledProgressRun(p.color, "·", width-filled, Muted))
	return builder.String()
}

func (p *ProgressList) animatedSegmentLocked(start int, count int, center float64) string {
	const glowRadius = 3.0
	builder := strings.Builder{}
	for i := start; i < start+count; i++ {
		builder.WriteString(p.glowCellLocked(i, center, glowRadius, "━"))
	}
	return builder.String()
}

func (p *ProgressList) glowCellLocked(index int, center float64, radius float64, cell string) string {
	brightness := 1 - math.Abs(float64(index)-center)/radius
	if brightness < 0 {
		brightness = 0
	}
	brightness *= brightness

	rampIndex := int(math.Round(brightness * float64(len(progressBarRamp)-1)))
	return WriteStyledStringIf(p.color, 0, progressBarRamp[rampIndex], DefaultColor, "%s", cell)
}

func styledProgressRun(color bool, cell string, count int, fg Color) string {
	if count <= 0 {
		return ""
	}
	return WriteStyledStringIf(color, 0, fg, DefaultColor, "%s", strings.Repeat(cell, count))
}

func progressStateColor(state ProgressState) Color {
	switch state {
	case ProgressSuccess:
		return Green
	case ProgressWarning:
		return Yellow
	case ProgressFailure:
		return Red
	case ProgressRunning:
		return Cyan
	default:
		return Muted
	}
}

func (p *ProgressList) printChangesLocked() {
	seen := make(map[string]bool, len(p.items))
	maxLabelWidth := progressMaxLabelWidth(p.items)

	for _, item := range p.items {
		snapshot := progressSnapshot{
			Status: item.Status,
			State:  item.State,
			Known:  item.Known,
			Bucket: progressBucket(item),
		}

		previous, ok := p.previous[item.Key]
		if !ok || previous != snapshot {
			fmt.Fprintln(p.out, p.compactItemLineLocked(item, maxLabelWidth))
		}

		p.previous[item.Key] = snapshot
		seen[item.Key] = true
	}

	for key := range p.previous {
		if !seen[key] {
			delete(p.previous, key)
		}
	}
}

func (p *ProgressList) compactItemLineLocked(item ProgressItem, maxLabelWidth int) string {
	label := item.Label
	if label != "" {
		label += ":"
	}

	line := "  " + p.symbolLocked(item.State) + "  "
	if label != "" {
		line += WriteStyledStringIf(p.color, Bold, DefaultColor, DefaultColor, "%s", label)
		line += strings.Repeat(" ", max(0, maxLabelWidth-utf8.RuneCountInString(label)))
		line += "  "
	}
	if item.Status != "" {
		line += p.statusLocked(item.State, item.Status)
	}

	return line
}

func progressBucket(item ProgressItem) int {
	if !item.Known {
		return -1
	}

	fraction := item.Progress
	if fraction < 0 {
		fraction = 0
	}
	if fraction > 1 {
		fraction = 1
	}

	bucket := int(fraction * 4)
	if bucket > 3 {
		bucket = 3
	}
	return bucket
}

func truncateRunes(value string, width int) string {
	if width <= 0 {
		return ""
	}
	if utf8.RuneCountInString(value) <= width {
		return value
	}
	if width == 1 {
		return "…"
	}

	runes := []rune(value)
	return string(runes[:width-1]) + "…"
}
