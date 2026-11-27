package gateway

import (
	"bytes"
	"encoding/json"
	"fmt"
	"html/template"
	"sync"

	cfg "ucloud.dk/pkg/config"
)

type SessionStatusResponse struct {
	State         string `json:"state"`
	JobId         string `json:"jobId,omitempty"`
	JobTitle      string `json:"jobTitle,omitempty"`
	Rank          int    `json:"rank,omitempty"`
	TargetName    string `json:"targetName,omitempty"`
	TargetPort    int    `json:"targetPort,omitempty"`
	TargetTls     bool   `json:"targetTls,omitempty"`
	JobState      string `json:"jobState,omitempty"`
	StartedAt     string `json:"startedAt,omitempty"`
	FinishedAt    string `json:"finishedAt,omitempty"`
	UcloudUrl     string `json:"ucloudUrl,omitempty"`
	UcloudJobUrl  string `json:"ucloudJobUrl,omitempty"`
	PodPhase      string `json:"podPhase,omitempty"`
	PodStateLabel string `json:"podStateLabel,omitempty"`
	PodDetail     string `json:"podDetail,omitempty"`
	RestartCount  int32  `json:"restartCount,omitempty"`
	ProbeStatus   string `json:"probeStatus,omitempty"`
	Logs          string `json:"logs,omitempty"`
	LogsTruncated bool   `json:"logsTruncated,omitempty"`
	LogsAvailable bool   `json:"logsAvailable,omitempty"`
}

type UnavailablePageMetaItem struct {
	Label string
	Value string
	Mono  bool
}

type UnavailablePageModel struct {
	PageTitle          string
	BrandHref          string
	Heading            string
	MetaItems          []UnavailablePageMetaItem
	CalloutClass       string
	CalloutTitle       string
	CalloutBody        string
	TipsTitle          string
	Tips               []string
	PrimaryActionHref  string
	PrimaryActionLabel string
	ShowRetry          bool
	PollStatusText     string
	StatusEndpointJson template.JS
	UcloudUrlJson      template.JS
	EnableProbe        bool
}

var unavailableTemplateOnce sync.Once
var unavailableTemplate *template.Template

var cachedBadGatewayHtmlOnce sync.Once
var cachedBadGatewayHtml string

func gatewayUnavailableTemplate() *template.Template {
	unavailableTemplateOnce.Do(func() {
		parsed, err := template.New("bad-gateway").Parse(string(badGatewayHtml))
		if err != nil {
			panic("failed to parse bad-gateway.html template: " + err.Error())
		}
		unavailableTemplate = parsed
	})
	return unavailableTemplate
}

func gatewayJsonJs(value string) template.JS {
	encoded, err := json.Marshal(value)
	if err != nil {
		return template.JS(`""`)
	}
	return template.JS(encoded)
}

func RenderUnavailablePage(model UnavailablePageModel) string {
	ucloudUrl := ""
	if cfg.Provider != nil {
		ucloudUrl = cfg.Provider.Hosts.UCloudPublic.ToURL()
	}

	if model.BrandHref == "" {
		model.BrandHref = ucloudUrl
	}
	if model.StatusEndpointJson == "" {
		model.StatusEndpointJson = template.JS(`""`)
	}
	if model.UcloudUrlJson == "" {
		model.UcloudUrlJson = gatewayJsonJs(ucloudUrl)
	}

	var buf bytes.Buffer
	err := gatewayUnavailableTemplate().Execute(&buf, model)
	if err != nil {
		return "Your job is currently unavailable."
	}
	return buf.String()
}

func RenderBadGatewayHtml() string {
	if cfg.Provider == nil {
		return gatewayBuildBadGatewayHtml()
	}
	cachedBadGatewayHtmlOnce.Do(func() {
		cachedBadGatewayHtml = gatewayBuildBadGatewayHtml()
	})
	return cachedBadGatewayHtml
}

func gatewayBuildBadGatewayHtml() string {
	ucloudUrl := ""
	statusEndpoint := ""
	if cfg.Provider != nil {
		ucloudUrl = cfg.Provider.Hosts.UCloudPublic.ToURL()
		statusEndpoint = fmt.Sprintf("/ucloud/%v/session-status", cfg.Provider.Id)
	}

	return RenderUnavailablePage(UnavailablePageModel{
		PageTitle: "Job is unavailable",
		BrandHref: ucloudUrl,
		Heading:   "Your job is currently unavailable",
		TipsTitle: "If this persists",
		Tips: []string{
			"Wait a moment if your job just started and is still initializing.",
			"Open the job on UCloud and check the output for errors.",
			"Open the job on UCloud and click 'Open interface'.",
		},
		PrimaryActionHref:  ucloudUrl,
		PrimaryActionLabel: "Open UCloud",
		ShowRetry:          true,
		PollStatusText:     "Checking job status…",
		StatusEndpointJson: gatewayJsonJs(statusEndpoint),
		UcloudUrlJson:      gatewayJsonJs(ucloudUrl),
		EnableProbe:        true,
	})
}
