package launcher

import (
	"encoding/json"
	"fmt"
	"math"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/go-pdf/fpdf"
)

func reportRequirements() []requirement {
	return []requirement{
		{
			ID:           "K9-1",
			Section:      "K-9",
			NameEN:       "All server and their components' hardware is healthy (no hardware failures)",
			NameDA:       "Al serverhardware og serverkomponenter er sunde (ingen hardwarefejl)",
			MatchPhrases: []string{"k9 1"},
		},
		{
			ID:           "K9-2",
			Section:      "K-9",
			NameEN:       "All networking equipment hardware is healthy (no hardware failures)",
			NameDA:       "Alt netværksudstyrs hardware er sundt (ingen hardwarefejl)",
			MatchPhrases: []string{"k9 2"},
		},
		{
			ID:           "K9-3",
			Section:      "K-9",
			NameEN:       "All internal network connectivity is functional at the nominal speed",
			NameDA:       "Al intern netværksforbindelse er funktionel ved nominel hastighed",
			MatchPhrases: []string{"k9 3"},
		},
		{
			ID:           "K9-4",
			Section:      "K-9",
			NameEN:       "Internet connectivity is functional",
			NameDA:       "Internetforbindelse er funktionel",
			MatchPhrases: []string{"k9 4"},
		},
		{
			ID:           "K10-1",
			Section:      "K-10",
			NameEN:       "All OS software components on all servers are working correctly",
			NameDA:       "Alle OS-softwarekomponenter på alle servere fungerer korrekt",
			MatchPhrases: []string{"k10 1"},
		},
		{
			ID:           "K10-2",
			Section:      "K-10",
			NameEN:       "All basic software services on management servers are working correctly",
			NameDA:       "Alle grundlæggende softwaretjenester på management-servere fungerer korrekt",
			MatchPhrases: []string{"k10 2"},
		},
		{
			ID:           "K10-3",
			Section:      "K-10",
			NameEN:       "All software components on compute cluster are functional",
			NameDA:       "Alle softwarekomponenter på compute-klyngen er funktionelle",
			MatchPhrases: []string{"k10 3"},
		},
		{
			ID:           "K10-4",
			Section:      "K-10",
			NameEN:       "K8s cluster for management and compute is healthy and functional",
			NameDA:       "K8s-klyngen for management og compute er sund og funktionel",
			MatchPhrases: []string{"k10 4"},
		},
		{
			ID:           "K10-5",
			Section:      "K-10",
			NameEN:       "All K8s network connectivity is functional",
			NameDA:       "Al K8s-netværksforbindelse er funktionel",
			MatchPhrases: []string{"k10 5"},
		},
		{
			ID:           "K11-1",
			Section:      "K-11",
			NameEN:       "The storage system is healthy (no disk failures, no connection failure, correct client drivers, no warnings)",
			NameDA:       "Lagringssystemet er sundt (ingen diskfejl, ingen forbindelsesfejl, korrekte klientdrivere, ingen advarsler)",
			MatchPhrases: []string{"k11 1"},
		},
		{
			ID:           "K11-2",
			Section:      "K-11",
			NameEN:       "The storage system clients are all functional (storage can be used by all servers)",
			NameDA:       "Lagringssystemets klienter er alle funktionelle (lager kan bruges af alle servere)",
			MatchPhrases: []string{"k11 2"},
		},
		{
			ID:           "K11-3",
			Section:      "K-11",
			NameEN:       "The K8s cluster can connect and use the storage system correctly",
			NameDA:       "K8s-klyngen kan forbinde til og bruge lagringssystemet korrekt",
			MatchPhrases: []string{"k11 3"},
		},
		{
			ID:           "K12-1",
			Section:      "K-12",
			NameEN:       "UCloud/Core, UCloud/IM and required databases are correctly deployed and healthy",
			NameDA:       "UCloud/Core, UCloud/IM og nødvendige databaser er korrekt deployeret og sunde",
			MatchPhrases: []string{"k12 1"},
		},
		{
			ID:           "K12-2",
			Section:      "K-12",
			NameEN:       "UCloud API endpoints are healthy",
			NameDA:       "UCloud API-endpoints er sunde",
			MatchPhrases: []string{"k12 2"},
		},
		{
			ID:           "K13-1",
			Section:      "K-13",
			NameEN:       "Files - basic file browsing and operations works",
			NameDA:       "Filer - grundlæggende filnavigation og operationer virker",
			MatchPhrases: []string{"files - basic", "file browsing", "file operations"},
		},
		{
			ID:           "K13-2",
			Section:      "K-13",
			NameEN:       "Files - upload/download works",
			NameDA:       "Filer - upload/download virker",
			MatchPhrases: []string{"upload", "download"},
		},
		{
			ID:           "K13-3",
			Section:      "K-13",
			NameEN:       "Files - search works",
			NameDA:       "Filer - søgning virker",
			MatchPhrases: []string{"files - search", "file search"},
		},
		{
			ID:           "K13-4",
			Section:      "K-13",
			NameEN:       "Files - transfer works",
			NameDA:       "Filer - overførsel virker",
			MatchPhrases: []string{"files - transfer", "transfer"},
		},
		{
			ID:           "K13-5",
			Section:      "K-13",
			NameEN:       "Files - accounting works",
			NameDA:       "Filer - accounting virker",
			MatchPhrases: []string{"files - accounting", "storage accounting"},
		},
		{
			ID:           "K13-6",
			Section:      "K-13",
			NameEN:       "Files - Syncthing works",
			NameDA:       "Filer - Syncthing virker",
			MatchPhrases: []string{"syncthing"},
		},
		{
			ID:           "K13-7",
			Section:      "K-13",
			NameEN:       "Files - folder sharing works",
			NameDA:       "Filer - mappe-deling virker",
			MatchPhrases: []string{"folder sharing", "sharing"},
		},
		{
			ID:           "K13-8",
			Section:      "K-13",
			NameEN:       "Drives - check change permissions works",
			NameDA:       "Drev - ændring af rettigheder virker",
			MatchPhrases: []string{"permissions"},
		},
		{
			ID:           "K13-9",
			Section:      "K-13",
			NameEN:       "Terminal - check integrated terminal works",
			NameDA:       "Terminal - integreret terminal virker",
			MatchPhrases: []string{"terminal", "terminal access"},
		},
		{
			ID:           "K13-10",
			Section:      "K-13",
			NameEN:       "Compute - check starting jobs works",
			NameDA:       "Compute - start af jobs virker",
			MatchPhrases: []string{"check starting jobs", "run job", "start application"},
		},
		{
			ID:           "K13-11",
			Section:      "K-13",
			NameEN:       "Compute - check accounting",
			NameDA:       "Compute - accounting virker",
			MatchPhrases: []string{"compute - check accounting", "compute accounting", "allocations"},
		},
		{
			ID:           "K13-12",
			Section:      "K-13",
			NameEN:       "Compute - check running jobs functionality",
			NameDA:       "Compute - kørende jobs fungerer",
			MatchPhrases: []string{"modules available", "network connectivity", "logs", "interface"},
		},
		{
			ID:           "K13-13",
			Section:      "K-13",
			NameEN:       "Compute - check job termination",
			NameDA:       "Compute - job-afslutning virker",
			MatchPhrases: []string{"check job termination", "stop job", "terminate job"},
		},
		{
			ID:           "K13-14",
			Section:      "K-13",
			NameEN:       "SSH - check SSH connections work",
			NameDA:       "SSH - forbindelser virker",
			MatchPhrases: []string{"ssh"},
		},
		{
			ID:           "K13-15",
			Section:      "K-13",
			NameEN:       "Public links - check public links work",
			NameDA:       "Offentlige links virker",
			MatchPhrases: []string{"public links"},
		},
		{
			ID:           "K13-16",
			Section:      "K-13",
			NameEN:       "Public IPs - check public IPs work",
			NameDA:       "Offentlige IP'er virker",
			MatchPhrases: []string{"public ips", "public ip"},
		},
		{
			ID:           "K13-17",
			Section:      "K-13",
			NameEN:       "Licenses - check license system works",
			NameDA:       "Licenser - licenssystemet virker",
			MatchPhrases: []string{"licenses - check license system works", "license"},
		},
		{
			ID:           "K13-18",
			Section:      "K-13",
			NameEN:       "Sensitive data projects are isolated",
			NameDA:       "Projekter med følsomme data er isolerede",
			MatchPhrases: []string{"sensitive data", "isolated"},
		},
	}
}

func localizer(lang language) reportText {
	if lang == langDA {
		return reportText{
			logo:              "LOGO",
			automatedInput:    "Automatisk input",
			manualInput:       "Manuelt input",
			execSummary:       "Ledelsessammendrag",
			overallResult:     "Samlet resultat",
			passed:            "Bestået",
			failed:            "Ikke bestået",
			notCovered:        "Ikke dækket",
			automatedExecuted: "Automatiske tests kørt",
			flaky:             "Flaky",
			manualComments:    "Manuelle kommentarer",
			appendixTitle:     "Appendiks: Testevidens",
			appendixIntro:     "Nedenfor vises en oversigt over den detaljerede evidens for hver testkategori.",
			noEvidence:        "Ingen direkte evidens registreret",
			pageLabel:         "Side",
		}
	}

	return reportText{
		logo:              "LOGO",
		automatedInput:    "Automated input",
		manualInput:       "Manual input",
		execSummary:       "Executive Summary",
		overallResult:     "Overall result",
		passed:            "Passed",
		failed:            "Failed",
		notCovered:        "Not covered",
		automatedExecuted: "Automated tests executed",
		flaky:             "Flaky",
		manualComments:    "Manual comments",
		appendixTitle:     "Appendix: Test evidence",
		appendixIntro:     "Detailed evidence per test category is listed below.",
		noEvidence:        "No direct evidence recorded",
		pageLabel:         "Page",
	}
}

func reportGenerator() {
	automatedPath, manualPath, outputDir, err := resolveReportGeneratorArgs(os.Args)
	if err != nil {
		fmt.Printf("report generation failed: %v\n", err)
		return
	}

	automated, err := loadAutomatedResults(automatedPath)
	if err != nil {
		fmt.Printf("report generation failed: %v\n", err)
		return
	}

	manual, err := loadManualResults(manualPath)
	if err != nil {
		fmt.Printf("report generation failed: %v\n", err)
		return
	}

	evidence := collectAutomatedEvidence(automated)
	requirements := reportRequirements()
	outcomes := evaluateRequirements(requirements, evidence, manual.Tests)

	if err = os.MkdirAll(outputDir, 0o750); err != nil {
		fmt.Printf("report generation failed: unable to create output dir: %v\n", err)
		return
	}

	enPath := filepath.Join(outputDir, "ucloud_test_report_en.pdf")
	daPath := filepath.Join(outputDir, "ucloud_test_report_da.pdf")

	report := reportDocument{
		GeneratedAt:    time.Now(),
		AutomatedPath:  automatedPath,
		ManualPath:     manualPath,
		Outcomes:       outcomes,
		Stats:          automated.Stats,
		ManualComments: manual.Comments,
	}

	report.Title = manual.TitleEN
	report.Subtitle = manual.SubtitleEN
	if err = renderReportPDF(report, langEN, enPath); err != nil {
		fmt.Printf("report generation failed: %v\n", err)
		return
	}

	report.Title = manual.TitleDA
	report.Subtitle = manual.SubtitleDA
	if err = renderReportPDF(report, langDA, daPath); err != nil {
		fmt.Printf("report generation failed: %v\n", err)
		return
	}

	fmt.Printf("report generated (EN): %s\n", enPath)
	fmt.Printf("report generated (DA): %s\n", daPath)
}

type automatedResults struct {
	Suites []suiteNode `json:"suites"`
	Stats  statsNode   `json:"stats"`
}

type statsNode struct {
	StartTime  string  `json:"startTime"`
	Duration   float64 `json:"duration"`
	Expected   int     `json:"expected"`
	Skipped    int     `json:"skipped"`
	Unexpected int     `json:"unexpected"`
	Flaky      int     `json:"flaky"`
}

type suiteNode struct {
	Title  string      `json:"title"`
	Specs  []specNode  `json:"specs"`
	Suites []suiteNode `json:"suites"`
}

type specNode struct {
	Title string     `json:"title"`
	Ok    bool       `json:"ok"`
	Tests []testNode `json:"tests"`
}

type testNode struct {
	Status  string       `json:"status"`
	Results []resultNode `json:"results"`
}

type resultNode struct {
	Status   string `json:"status"`
	Duration *int64 `json:"duration"`
}

type manualResultsFile struct {
	Tests      []manualTest `json:"tests"`
	Comments   []string     `json:"comments"`
	TitleDA    string       `json:"title_da"`
	TitleEN    string       `json:"title_en"`
	SubtitleDA string       `json:"subtitle_da"`
	SubtitleEN string       `json:"subtitle_en"`
}

type manualTest struct {
	Requirement string `json:"requirement"`
	Name        string `json:"name"`
	Status      string `json:"status"`
	Context     string `json:"context"`
	CommentEN   string `json:"comment_en"`
	CommentDA   string `json:"comment_da"`
	Comment     string `json:"comment"`
}

type language string

const (
	langEN language = "en"
	langDA language = "da"

	reportTitleFont = "ReportSerif"
	reportBodyFont  = "ReportSerif"
)

type requirement struct {
	ID           string
	Section      string
	NameEN       string
	NameDA       string
	MatchPhrases []string
}

type evidenceItem struct {
	Name       string
	SearchText string
	Context    string
	Status     checkStatus
	Source     string
	Comment    string
	DurationMs *int64
}

type checkStatus string

const (
	statusPass    checkStatus = "pass"
	statusFail    checkStatus = "fail"
	statusNotRun  checkStatus = "not_run"
	statusUnknown checkStatus = "unknown"
)

type requirementOutcome struct {
	Requirement requirement
	Status      checkStatus
	Evidence    []evidenceItem
	NoteEN      string
	NoteDA      string
}

type reportDocument struct {
	GeneratedAt    time.Time
	AutomatedPath  string
	ManualPath     string
	Outcomes       []requirementOutcome
	Stats          statsNode
	ManualComments []string
	Title          string
	Subtitle       string
}

func resolveReportGeneratorArgs(args []string) (automatedPath string, manualPath string, outputDir string, err error) {
	manualPath = ""
	automatedPath = firstExistingPath(
		"launcher-tool-go/pkg/launcher/report_generator_test.json",
		"pkg/launcher/report_generator_test.json",
		"report_generator_test.json",
	)
	if automatedPath == "" {
		return "", "", "", fmt.Errorf("could not locate report_generator_test.json")
	}

	outputDir = "."
	if len(args) >= 3 {
		automatedPath = args[2]
	}
	if len(args) >= 4 {
		manualPath = args[3]
	}
	if len(args) >= 5 {
		outputDir = args[4]
	}

	if !filepath.IsAbs(automatedPath) {
		automatedPath = filepath.Clean(automatedPath)
	}
	if manualPath != "" && !filepath.IsAbs(manualPath) {
		manualPath = filepath.Clean(manualPath)
	}
	if !filepath.IsAbs(outputDir) {
		outputDir = filepath.Clean(outputDir)
	}

	if _, statErr := os.Stat(automatedPath); statErr != nil {
		return "", "", "", fmt.Errorf("automated results file not found: %s", automatedPath)
	}
	if manualPath != "" {
		if _, statErr := os.Stat(manualPath); statErr != nil {
			return "", "", "", fmt.Errorf("manual results file not found: %s", manualPath)
		}
	}

	return automatedPath, manualPath, outputDir, nil
}

func firstExistingPath(paths ...string) string {
	for _, p := range paths {
		if _, err := os.Stat(p); err == nil {
			return p
		}
	}
	return ""
}

func loadAutomatedResults(path string) (automatedResults, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return automatedResults{}, err
	}

	var out automatedResults
	if err = json.Unmarshal(data, &out); err != nil {
		return automatedResults{}, err
	}
	return out, nil
}

func loadManualResults(path string) (manualResultsFile, error) {
	if path == "" || path == "/dev/null" {
		return manualResultsFile{}, nil
	}

	data, err := os.ReadFile(path)
	if err != nil {
		return manualResultsFile{}, err
	}

	var out manualResultsFile
	if err = json.Unmarshal(data, &out); err != nil {
		return manualResultsFile{}, err
	}
	return out, nil
}

func collectAutomatedEvidence(data automatedResults) []evidenceItem {
	var out []evidenceItem
	collectSuites(data.Suites, nil, &out)
	return out
}

func collectSuites(suites []suiteNode, trail []string, out *[]evidenceItem) {
	for _, s := range suites {
		currentTrail := append(append([]string{}, trail...), s.Title)
		context := detectContext(currentTrail)
		for _, spec := range s.Specs {
			durationMs := durationFromSpec(spec)
			evidenceName, evidenceSearch := composeEvidenceLabel(currentTrail, spec.Title)
			*out = append(*out, evidenceItem{
				Name:       evidenceName,
				SearchText: evidenceSearch,
				Context:    context,
				Status:     statusFromSpec(spec),
				Source:     "automated",
				DurationMs: durationMs,
			})
		}
		collectSuites(s.Suites, currentTrail, out)
	}
}

func durationFromSpec(spec specNode) *int64 {
	var total int64
	hasDuration := false
	for _, test := range spec.Tests {
		for _, run := range test.Results {
			if run.Duration != nil {
				total += *run.Duration
				hasDuration = true
			}
		}
	}
	if !hasDuration {
		return nil
	}
	return &total
}

func composeEvidenceLabel(trail []string, specTitle string) (string, string) {
	specTitle = strings.TrimSpace(specTitle)
	cleanTrail := make([]string, 0, len(trail))
	for _, item := range trail {
		t := strings.TrimSpace(item)
		if t == "" {
			continue
		}
		l := strings.ToLower(t)
		if strings.HasSuffix(l, ".ts") {
			continue
		}
		cleanTrail = append(cleanTrail, t)
	}

	prefix := ""
	if len(cleanTrail) > 0 {
		prefix = cleanTrail[len(cleanTrail)-1]
	}

	name := specTitle
	if prefix != "" && !strings.Contains(normalize(specTitle), normalize(prefix)) {
		name = prefix + ": " + specTitle
	}

	searchParts := append([]string{}, cleanTrail...)
	if specTitle != "" {
		searchParts = append(searchParts, specTitle)
	}
	return name, strings.Join(searchParts, " ")
}

func detectContext(path []string) string {
	for _, part := range path {
		n := normalize(part)
		switch {
		case strings.Contains(n, "personal workspace"):
			return "Personal workspace"
		case strings.Contains(n, "project pi"):
			return "Project (PI)"
		case strings.Contains(n, "project admin"):
			return "Project (admin)"
		case strings.Contains(n, "project user"):
			return "Project (user)"
		case strings.Contains(n, "share"):
			return "Share"
		case strings.Contains(n, "new user"):
			return "New user"
		case strings.Contains(n, "new project"):
			return "New Project"
		}
	}
	return "General"
}

func statusFromSpec(spec specNode) checkStatus {
	if len(spec.Tests) == 0 {
		if spec.Ok {
			return statusPass
		}
		return statusUnknown
	}

	anyPass := false
	for _, test := range spec.Tests {
		for _, run := range test.Results {
			s := normalize(run.Status)
			if s == "failed" || s == "timedout" || s == "interrupted" {
				return statusFail
			}
			if s == "passed" {
				anyPass = true
			}
		}
		if normalize(test.Status) == "unexpected" {
			return statusFail
		}
	}

	if anyPass {
		return statusPass
	}
	if spec.Ok {
		return statusPass
	}
	return statusUnknown
}

func evaluateRequirements(requirements []requirement, automated []evidenceItem, manual []manualTest) []requirementOutcome {
	out := make([]requirementOutcome, 0, len(requirements))
	for _, req := range requirements {
		matches := make([]evidenceItem, 0)
		for _, item := range automated {
			if requirementMatches(req, item.Name, item.SearchText) {
				matches = append(matches, item)
			}
		}

		for _, m := range manual {
			if manualRequirementMatches(req, m) {
				matches = append(matches, evidenceItem{
					Name:       m.Name,
					SearchText: firstNonEmpty(m.Requirement, m.Name),
					Context:    firstNonEmpty(m.Context, "Manual"),
					Status:     parseStatus(m.Status),
					Source:     "manual",
					Comment:    firstNonEmpty(m.CommentEN, m.CommentDA, m.Comment),
				})
			}
		}

		status := overallStatus(matches)
		noteEN, noteDA := notesForStatus(status, matches)
		out = append(out, requirementOutcome{
			Requirement: req,
			Status:      status,
			Evidence:    matches,
			NoteEN:      noteEN,
			NoteDA:      noteDA,
		})
	}
	return out
}

func manualRequirementMatches(req requirement, m manualTest) bool {
	ref := normalize(m.Requirement)
	if ref != "" {
		if ref == normalize(req.ID) {
			return true
		}
		if ref == normalize(strings.ReplaceAll(req.ID, "-", "")) {
			return true
		}
		if ref == normalize(req.NameEN) || ref == normalize(req.NameDA) {
			return true
		}
		return false
	}

	return requirementMatches(req, m.Name)
}

func requirementMatches(req requirement, texts ...string) bool {
	for _, text := range texts {
		n := normalize(text)
		if n == "" {
			continue
		}
		if strings.Contains(n, normalize(req.NameEN)) {
			return true
		}
		for _, phrase := range req.MatchPhrases {
			if strings.Contains(n, normalize(phrase)) {
				return true
			}
		}
	}
	return false
}

func normalize(s string) string {
	s = strings.ToLower(strings.TrimSpace(s))
	replacer := strings.NewReplacer("-", " ", "_", " ", "'", "", "\"", "", ",", " ", ".", " ", "(", " ", ")", " ", "/", " ")
	s = replacer.Replace(s)
	s = strings.Join(strings.Fields(s), " ")
	return s
}

func parseStatus(input string) checkStatus {
	s := normalize(input)
	s = strings.ReplaceAll(s, " ", "")
	switch s {
	case "pass", "passed", "ok", "success", "successful":
		return statusPass
	case "fail", "failed", "error", "unsuccessful":
		return statusFail
	case "notrun", "not_run", "notexecuted", "skipped", "n/a", "na":
		return statusNotRun
	default:
		return statusUnknown
	}
}

func sectionTitle(lang language, code string) string {
	if lang == langDA {
		switch code {
		case "K-9":
			return "K-9 Infrastrukturtests"
		case "K-10":
			return "K-10 Lavniveau softwaretests"
		case "K-11":
			return "K-11 Lagringssystem"
		case "K-12":
			return "K-12 Højniveau softwaretests"
		case "K-13":
			return "K-13 Applikationelle funktionstests"
		}
	}

	switch code {
	case "K-9":
		return "K-9 Infrastructure tests"
	case "K-10":
		return "K-10 Low-level software tests"
	case "K-11":
		return "K-11 Storage system"
	case "K-12":
		return "K-12 High-level software tests"
	case "K-13":
		return "K-13 Application functional tests"
	default:
		return code
	}
}

func overallStatus(items []evidenceItem) checkStatus {
	if len(items) == 0 {
		return statusNotRun
	}

	anyPass := false
	for _, item := range items {
		if item.Status == statusFail {
			return statusFail
		}
		if item.Status == statusPass {
			anyPass = true
		}
	}
	if anyPass {
		return statusPass
	}
	return statusNotRun
}

func notesForStatus(status checkStatus, evidence []evidenceItem) (string, string) {
	switch status {
	case statusPass:
		en := fmt.Sprintf("Pass based on %d related tests.", len(evidence))
		if len(evidence) == 1 {
			en = "Pass based on 1 related test."
		}
		return en, fmt.Sprintf("Bestået baseret på %d relaterede test.", len(evidence))
	case statusFail:
		return "Fail: one or more related tests did not pass.", "Ikke bestået: mindst en relateret test bestod ikke."
	case statusNotRun:
		return "Not covered in current automated run. Add as manual validation if executed separately.", "Ikke dækket i den nuværende automatiske kørsel. Tilføj som manuel validering hvis den er udført separat."
	default:
		return "Insufficient data to determine status.", "Utilstrækkelige data til at bestemme status."
	}
}

func renderReportPDF(report reportDocument, lang language, outputPath string) error {
	pdf := fpdf.New("P", "mm", "A4", "")
	if err := configureUTF8Fonts(pdf); err != nil {
		return err
	}
	r := localizer(lang)
	reportDate := formatHeaderDate(lang, report.GeneratedAt)
	pdf.SetHeaderFuncMode(func() {
		pdf.SetFont(reportBodyFont, "", 10)
		pdf.SetY(7)
		pdf.CellFormat(0, 6, reportDate, "", 0, "R", false, 0, "")
	}, true)
	pdf.SetFooterFunc(func() {
		pdf.SetY(-12)
		pdf.SetFont(reportBodyFont, "", 9)
		pdf.CellFormat(0, 5, fmt.Sprintf("%s %d", r.pageLabel, pdf.PageNo()), "", 0, "C", false, 0, "")
	})
	pdf.SetMargins(15, 15, 15)
	pdf.SetAutoPageBreak(true, 15)
	pdf.AddPage()

	leftLogoPath := firstExistingPath(
		"launcher-tool-go/pkg/launcher/report_generator_left.png",
		"pkg/launcher/report_generator_left.png",
		"report_generator_left.png",
	)
	rightLogoPath := firstExistingPath(
		"launcher-tool-go/pkg/launcher/report_generator_right.png",
		"pkg/launcher/report_generator_right.png",
		"report_generator_right.png",
	)

	drawLogoOrPlaceholder(pdf, leftLogoPath, 5, 15, 35, 18, r.logo)
	drawLogoOrPlaceholder(pdf, rightLogoPath, 160, 15, 35, 18, r.logo)

	pdf.SetFont(reportTitleFont, "B", 18)
	pdf.SetY(17)
	pdf.CellFormat(0, 8, report.Title, "", 1, "C", false, 0, "")

	pdf.SetFont(reportBodyFont, "", 11)
	pdf.CellFormat(0, 6, report.Subtitle, "", 1, "C", false, 0, "")

	pdf.Ln(8)

	passCount, failCount, notRunCount := statusCounts(report.Outcomes)
	overall := overallReportStatus(report.Outcomes)

	pdf.SetFillColor(240, 240, 240)
	pdf.SetFont(reportTitleFont, "B", 11)
	pdf.CellFormat(0, 8, r.execSummary, "1", 1, "L", true, 0, "")
	pdf.SetFont(reportBodyFont, "", 10)
	pdf.CellFormat(0, 6, fmt.Sprintf("%s: %s", r.overallResult, localizedStatus(lang, overall)), "1", 1, "L", false, 0, "")
	pdf.CellFormat(0, 6, fmt.Sprintf("%s: %d   %s: %d   %s: %d", r.passed, passCount, r.failed, failCount, r.notCovered, notRunCount), "1", 1, "L", false, 0, "")
	if report.Stats.Expected > 0 {
		pdf.CellFormat(0, 6, fmt.Sprintf("%s: %d   %s: %d", r.automatedExecuted, report.Stats.Expected, r.flaky, report.Stats.Flaky), "1", 1, "L", false, 0, "")
	}
	pdf.Ln(3)

	drawSection(pdf, lang, sectionTitle(lang, "K-9"), filterBySection(report.Outcomes, "K-9"))
	drawSection(pdf, lang, sectionTitle(lang, "K-10"), filterBySection(report.Outcomes, "K-10"))
	drawSection(pdf, lang, sectionTitle(lang, "K-11"), filterBySection(report.Outcomes, "K-11"))
	drawSection(pdf, lang, sectionTitle(lang, "K-12"), filterBySection(report.Outcomes, "K-12"))
	drawSection(pdf, lang, sectionTitle(lang, "K-13"), filterBySection(report.Outcomes, "K-13"))
	drawEvidenceAppendix(pdf, lang, report.Outcomes)

	if len(report.ManualComments) > 0 {
		pdf.SetFont(reportTitleFont, "B", 12)
		pdf.CellFormat(0, 8, r.manualComments, "", 1, "L", false, 0, "")
		pdf.SetFont(reportBodyFont, "", 10)
		for _, c := range report.ManualComments {
			pdf.MultiCell(0, 5, "- "+c, "", "L", false)
		}
	}

	return pdf.OutputFileAndClose(outputPath)
}

func drawSection(pdf *fpdf.Fpdf, lang language, sectionTitle string, outcomes []requirementOutcome) {
	const (
		leftMargin   = 15.0
		rightMargin  = 15.0
		bottomMargin = 15.0
		statusW      = 23.0
		lineH        = 4.8
	)

	pdf.SetFont(reportTitleFont, "B", 12)
	pdf.CellFormat(0, 8, sectionTitle, "", 1, "L", false, 0, "")

	pageW, pageH := pdf.GetPageSize()
	bodyW := pageW - leftMargin - rightMargin - statusW

	for _, o := range outcomes {
		statusText := localizedStatus(lang, o.Status)
		statusColor := statusColor(o.Status)
		pdf.SetFont(reportBodyFont, "B", 10)
		reqName := o.Requirement.NameEN
		note := o.NoteEN
		if lang == langDA {
			reqName = o.Requirement.NameDA
			note = o.NoteDA
		}

		titleLines := pdf.SplitLines([]byte(reqName), bodyW)
		pdf.SetFont(reportBodyFont, "", 10)
		noteLines := pdf.SplitLines([]byte(note), bodyW)
		rowH := float64(len(titleLines)+len(noteLines)) * lineH
		if rowH < 8 {
			rowH = 8
		}

		x := pdf.GetX()
		y := pdf.GetY()
		if y+rowH > pageH-bottomMargin {
			pdf.AddPage()
			pdf.SetFont(reportTitleFont, "B", 12)
			pdf.CellFormat(0, 8, sectionTitle, "", 1, "L", false, 0, "")
			x = pdf.GetX()
			y = pdf.GetY()
		}

		pdf.SetFillColor(statusColor[0], statusColor[1], statusColor[2])
		pdf.Rect(x, y, statusW, rowH, "DF")
		pdf.SetFont(reportBodyFont, "", 8)
		pdf.SetXY(x, y+(rowH-4)/2)
		pdf.CellFormat(statusW, 4, statusText, "", 0, "C", false, 0, "")

		pdf.SetXY(x+statusW, y)
		pdf.SetFont(reportBodyFont, "B", 10)
		pdf.MultiCell(bodyW, lineH, reqName, "", "L", false)
		pdf.SetX(x + statusW)
		pdf.SetFont(reportBodyFont, "", 10)
		pdf.MultiCell(bodyW, lineH, note, "", "L", false)
		pdf.Rect(x+statusW, y, bodyW, rowH, "D")

		pdf.Rect(x, y, statusW, rowH, "D")
		pdf.SetXY(x, y+rowH)
	}
	pdf.Ln(2)
}

func configureUTF8Fonts(pdf *fpdf.Fpdf) error {
	fontDir, regularFile, boldFile, err := resolveUTF8FontFiles()
	if err != nil {
		return err
	}

	pdf.SetFontLocation(fontDir)
	pdf.AddUTF8Font(reportBodyFont, "", regularFile)
	pdf.AddUTF8Font(reportTitleFont, "B", boldFile)
	if err := pdf.Error(); err != nil {
		return fmt.Errorf("unable to load UTF-8 serif fonts: %w", err)
	}
	return nil
}

func resolveUTF8FontFiles() (dir string, regular string, bold string, err error) {
	candidates := []struct {
		dir     string
		regular string
		bold    string
	}{
		{
			dir:     "/System/Library/Fonts/Supplemental",
			regular: "Georgia.ttf",
			bold:    "Georgia Bold.ttf",
		},
		{
			dir:     "/usr/share/fonts/truetype/dejavu",
			regular: "DejaVuSerif.ttf",
			bold:    "DejaVuSerif-Bold.ttf",
		},
		{
			dir:     "/usr/share/fonts/dejavu",
			regular: "DejaVuSerif.ttf",
			bold:    "DejaVuSerif-Bold.ttf",
		},
		{
			dir:     "/usr/share/fonts/truetype/freefont",
			regular: "FreeSerif.ttf",
			bold:    "FreeSerifBold.ttf",
		},
		{
			dir:     "/usr/share/fonts/opentype/noto",
			regular: "NotoSerif-Regular.ttf",
			bold:    "NotoSerif-Bold.ttf",
		},
	}

	for _, c := range candidates {
		regularPath := filepath.Join(c.dir, c.regular)
		boldPath := filepath.Join(c.dir, c.bold)
		if _, statErr := os.Stat(regularPath); statErr != nil {
			continue
		}
		if _, statErr := os.Stat(boldPath); statErr != nil {
			continue
		}
		return c.dir, c.regular, c.bold, nil
	}

	return "", "", "", fmt.Errorf("could not locate UTF-8 serif fonts on this system")
}

func drawEvidenceAppendix(pdf *fpdf.Fpdf, lang language, outcomes []requirementOutcome) {
	r := localizer(lang)
	pdf.AddPage()
	pdf.SetFont(reportTitleFont, "B", 14)
	pdf.CellFormat(0, 8, r.appendixTitle, "", 1, "L", false, 0, "")
	pdf.SetFont(reportBodyFont, "", 10)
	pdf.MultiCell(0, 5, r.appendixIntro, "", "L", false)
	pdf.Ln(4)

	for _, o := range outcomes {
		reqName := o.Requirement.NameEN
		if lang == langDA {
			reqName = o.Requirement.NameDA
		}

		pdf.SetFont(reportBodyFont, "B", 11)
		pdf.MultiCell(0, 6, reqName+" ["+localizedStatus(lang, o.Status)+"]", "", "L", false)
		pdf.SetFont(reportBodyFont, "", 10)
		pdf.Ln(2)

		if len(o.Evidence) == 0 {
			pdf.MultiCell(0, 5, "- "+r.noEvidence, "", "L", false)
			pdf.Ln(4)
			continue
		}

		for _, e := range o.Evidence {
			entry := fmt.Sprintf("- %s (%s, %s, %s)", e.Name, e.Context, e.Source, localizedStatus(lang, e.Status))
			if e.Source == "automated" && e.DurationMs != nil {
				entry += ", " + formatDurationLabel(lang, *e.DurationMs)
			}
			if strings.TrimSpace(e.Comment) != "" {
				entry += ": " + strings.TrimSpace(e.Comment)
			}
			pdf.MultiCell(0, 5, entry, "", "L", false)
		}
		pdf.Ln(4)
	}
}

func drawLogoOrPlaceholder(pdf *fpdf.Fpdf, logoPath string, x float64, y float64, w float64, h float64, placeholder string) {
	if logoPath != "" {
		if info := pdf.RegisterImageOptions(logoPath, fpdf.ImageOptions{ReadDpi: true}); info != nil {
			imgW, imgH := info.Extent()
			if imgW > 0 && imgH > 0 {
				scale := math.Min(w/imgW, h/imgH)
				drawW := imgW * scale
				drawH := imgH * scale
				drawX := x + (w-drawW)/2
				drawY := y + (h-drawH)/2
				pdf.ImageOptions(logoPath, drawX, drawY, drawW, drawH, false, fpdf.ImageOptions{ReadDpi: true}, 0, "")
			} else {
				pdf.ImageOptions(logoPath, x, y, w, h, false, fpdf.ImageOptions{ReadDpi: true}, 0, "")
			}
		} else {
			pdf.ImageOptions(logoPath, x, y, w, h, false, fpdf.ImageOptions{ReadDpi: true}, 0, "")
		}
		if pdf.Error() == nil {
			return
		}
		pdf.ClearError()
	}

	pdf.SetFont(reportBodyFont, "", 10)
	pdf.Rect(x, y, w, h, "D")
	pdf.Text(x+7, y+11, placeholder)
}

func formatDurationLabel(lang language, durationMs int64) string {
	seconds := float64(durationMs) / 1000.0
	if lang == langDA {
		return fmt.Sprintf("varighed %.1f s", seconds)
	}
	return fmt.Sprintf("duration %.1f s", seconds)
}

func formatHeaderDate(lang language, t time.Time) string {
	day := t.Day()
	year := t.Year()
	if lang == langDA {
		months := []string{"januar", "februar", "marts", "april", "maj", "juni", "juli", "august", "september", "oktober", "november", "december"}
		return fmt.Sprintf("%d. %s %d", day, months[int(t.Month())-1], year)
	}

	months := []string{"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"}
	return fmt.Sprintf("%s of %s %d", ordinal(day), months[int(t.Month())-1], year)
}

func ordinal(n int) string {
	if n%100 >= 11 && n%100 <= 13 {
		return fmt.Sprintf("%dth", n)
	}
	switch n % 10 {
	case 1:
		return fmt.Sprintf("%dst", n)
	case 2:
		return fmt.Sprintf("%dnd", n)
	case 3:
		return fmt.Sprintf("%drd", n)
	default:
		return fmt.Sprintf("%dth", n)
	}
}

func statusColor(s checkStatus) [3]int {
	switch s {
	case statusPass:
		return [3]int{214, 240, 214}
	case statusFail:
		return [3]int{248, 214, 214}
	case statusNotRun:
		return [3]int{242, 242, 242}
	default:
		return [3]int{235, 235, 235}
	}
}

func filterBySection(items []requirementOutcome, section string) []requirementOutcome {
	var out []requirementOutcome
	for _, item := range items {
		if item.Requirement.Section == section {
			out = append(out, item)
		}
	}
	return out
}

func statusCounts(items []requirementOutcome) (pass int, fail int, notRun int) {
	for _, item := range items {
		switch item.Status {
		case statusPass:
			pass++
		case statusFail:
			fail++
		default:
			notRun++
		}
	}
	return
}

func overallReportStatus(items []requirementOutcome) checkStatus {
	for _, item := range items {
		if item.Status == statusFail {
			return statusFail
		}
	}
	for _, item := range items {
		if item.Status == statusPass {
			return statusPass
		}
	}
	return statusNotRun
}

func localizedStatus(lang language, s checkStatus) string {
	if lang == langDA {
		switch s {
		case statusPass:
			return "BESTÅET"
		case statusFail:
			return "IKKE BESTÅET"
		case statusNotRun:
			return "IKKE DÆKKET"
		default:
			return "UKENDT"
		}
	}

	switch s {
	case statusPass:
		return "PASS"
	case statusFail:
		return "FAIL"
	case statusNotRun:
		return "NOT COVERED"
	default:
		return "UNKNOWN"
	}
}

type reportText struct {
	logo              string
	automatedInput    string
	manualInput       string
	execSummary       string
	overallResult     string
	passed            string
	failed            string
	notCovered        string
	automatedExecuted string
	flaky             string
	manualComments    string
	appendixTitle     string
	appendixIntro     string
	noEvidence        string
	pageLabel         string
}

func firstNonEmpty(values ...string) string {
	for _, v := range values {
		if strings.TrimSpace(v) != "" {
			return strings.TrimSpace(v)
		}
	}
	return ""
}
