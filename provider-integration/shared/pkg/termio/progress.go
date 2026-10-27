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
	Known  bool
	Bucket int
}

type ProgressList struct {
	out        io.Writer
	pty        bool
	mu         sync.Mutex
	title      string
	items      []ProgressItem
	milestones []string
	previous   map[string]progressSnapshot
	started    bool
	stopped    bool
	frame      int
	lastLines  int
	done       chan struct{}
	wg         sync.WaitGroup
}

const (
	progressBarWidth  = 24
	progressBarPeriod = 70 * time.Millisecond
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
			fmt.Fprintln(p.out, p.title)
		}
		for _, milestone := range p.milestones {
			fmt.Fprintln(p.out, milestone)
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
		fmt.Fprintln(p.out, message)
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
	lines := make([]string, 0, len(p.milestones)+len(p.items)+3)

	if p.title != "" {
		lines = append(lines, Emphasis("%s", p.title).Render(p.pty))
	}
	for _, milestone := range p.milestones {
		lines = append(lines, Subtle("  %s", milestone).Render(p.pty))
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

	for _, item := range items {
		lines = append(lines, p.itemLineLocked(item))
	}
	if hidden > 0 {
		lines = append(lines, Subtle("  … and %d more", hidden).Render(p.pty))
	}

	return lines
}

func (p *ProgressList) itemLineLocked(item ProgressItem) string {
	label := item.Label
	if label != "" {
		label += ":"
	}

	const fixedWidth = 2 + 1 + 2 + progressBarWidth + 2
	available := writerWidth(p.out) - fixedWidth
	if available < 8 {
		available = 8
	}

	labelWidth := utf8.RuneCountInString(label)
	statusWidth := utf8.RuneCountInString(item.Status)
	if labelWidth+1+statusWidth > available {
		statusWidth = min(statusWidth, max(4, available/2))
		labelWidth = available - 1 - statusWidth
		if labelWidth < 4 {
			labelWidth = 4
			statusWidth = available - 5
		}
		label = truncateRunes(label, labelWidth)
		statusWidth = available - 1 - utf8.RuneCountInString(label)
		if statusWidth < 0 {
			statusWidth = 0
		}
		item.Status = truncateRunes(item.Status, statusWidth)
	}

	line := "  "
	line += p.symbolLocked(item.State)
	line += "  "
	line += p.barLocked(item)
	line += "  "
	if label != "" {
		line += Emphasis("%s", label).Render(p.pty)
	}
	if item.Status != "" {
		if label != "" {
			line += " "
		}
		line += p.statusLocked(item.State, item.Status)
	}

	return line
}

func (p *ProgressList) symbolLocked(state ProgressState) string {
	switch state {
	case ProgressSuccess:
		return Success("✔").Render(p.pty)
	case ProgressWarning:
		return Warning("!").Render(p.pty)
	case ProgressFailure:
		return Danger("✘").Render(p.pty)
	default:
		frame := progressSpinnerFrames[p.frame%len(progressSpinnerFrames)]
		return StyledWith(0, Cyan, "%s", frame).Render(p.pty)
	}
}

func (p *ProgressList) statusLocked(state ProgressState, status string) string {
	switch state {
	case ProgressSuccess:
		return Success("%s", status).Render(p.pty)
	case ProgressWarning:
		return Warning("%s", status).Render(p.pty)
	case ProgressFailure:
		return Danger("%s", status).Render(p.pty)
	case ProgressRunning:
		return StyledWith(0, Cyan, "%s", status).Render(p.pty)
	default:
		return Subtle("%s", status).Render(p.pty)
	}
}

func (p *ProgressList) barLocked(item ProgressItem) string {
	if item.Known {
		fraction := item.Progress
		if fraction < 0 {
			fraction = 0
		}
		if fraction > 1 {
			fraction = 1
		}

		filled := int(math.Round(fraction * progressBarWidth))
		if !p.pty {
			return strings.Repeat("━", filled) + strings.Repeat("·", progressBarWidth-filled)
		}
		if !progressStateAnimated(item.State) {
			color := progressStateColor(item.State)
			return styledProgressRun(p.pty, "━", filled, color) +
				styledProgressRun(p.pty, "·", progressBarWidth-filled, Muted)
		}
		return p.animatedKnownBarLocked(filled)
	}

	const segment = 6
	span := progressBarWidth - segment
	position := p.frame % (2 * span)
	if position > span {
		position = 2*span - position
	}

	if !p.pty {
		return strings.Repeat("·", position) +
			strings.Repeat("━", segment) +
			strings.Repeat("·", progressBarWidth-position-segment)
	}

	center := float64(position) + float64(segment)/2
	return styledProgressRun(p.pty, "·", position, Muted) +
		p.animatedSegmentLocked(position, segment, center) +
		styledProgressRun(p.pty, "·", progressBarWidth-position-segment, Muted)
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
	return state == ProgressRunning || state == ProgressPending
}

func (p *ProgressList) animatedKnownBarLocked(filled int) string {
	const glowRadius = 6.0
	span := float64(progressBarWidth) + 2*glowRadius
	center := float64(p.frame%28)/28*span - glowRadius

	builder := strings.Builder{}
	for i := 0; i < filled; i++ {
		builder.WriteString(p.glowCellLocked(i, center, glowRadius, "━"))
	}
	builder.WriteString(styledProgressRun(p.pty, "·", progressBarWidth-filled, Muted))
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
	return WriteStyledStringIfPty(p.pty, 0, progressBarRamp[rampIndex], DefaultColor, "%s", cell)
}

func styledProgressRun(pty bool, cell string, count int, color Color) string {
	if count <= 0 {
		return ""
	}
	return WriteStyledStringIfPty(pty, 0, color, DefaultColor, "%s", strings.Repeat(cell, count))
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

	for _, item := range p.items {
		snapshot := progressSnapshot{
			Status: item.Status,
			Known:  item.Known,
			Bucket: progressBucket(item),
		}

		previous, ok := p.previous[item.Key]
		if !ok || previous != snapshot {
			fmt.Fprintf(p.out, "%s: %s\n", item.Label, item.Status)
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
