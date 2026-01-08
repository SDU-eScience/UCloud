package launcher2

import (
	_ "embed"
	"github.com/charmbracelet/glamour"
	"github.com/charmbracelet/glamour/styles"
	"sync"
	"ucloud.dk/shared/pkg/util"
)

//go:embed docs/k8s-ollama-short.md
var k8sOllamaShort []byte

//go:embed docs/k8s-ollama-long.md
var k8sOllamaLong []byte

//go:embed docs/core-short.md
var coreShort []byte

//go:embed docs/core-long.md
var coreLong []byte

//go:embed docs/frontend-short.md
var frontendShort []byte

//go:embed docs/frontend-long.md
var frontendLong []byte

//go:embed docs/gateway-short.md
var gatewayShort []byte

//go:embed docs/gateway-long.md
var gatewayLong []byte

//go:embed docs/k3s-short.md
var k3sShort []byte

//go:embed docs/k3s-long.md
var k3sLong []byte

//go:embed docs/k8s-short.md
var k8sShort []byte

//go:embed docs/k8s-long.md
var k8sLong []byte

//go:embed docs/pg-short.md
var pgShort []byte

//go:embed docs/pg-long.md
var pgLong []byte

var DocumentationShort = map[string]string{
	"core":         string(coreShort),
	"postgres":     string(pgShort),
	"frontend":     string(frontendShort),
	"gateway":      string(gatewayShort),
	"k8s":          string(k8sShort),
	"k3s":          string(k3sShort),
	"ollama":       string(k8sOllamaShort),
	"k8s-postgres": string(pgShort),
}

var documentationLongWg = sync.WaitGroup{}
var documentationLongRaw = map[string]string{
	"core":         string(coreLong),
	"postgres":     string(pgLong),
	"frontend":     string(frontendLong),
	"gateway":      string(gatewayLong),
	"k8s":          string(k8sLong),
	"k3s":          string(k3sLong),
	"ollama":       string(k8sOllamaLong),
	"k8s-postgres": string(pgLong),
}

var documentationLongRendered = map[string]string{}

func DocumentationStartRenderer() {
	// NOTE(Dan): This appears to be extremely slow, so we start it in the background early.

	documentationLongWg.Add(1)
	go func() {
		defer documentationLongWg.Done()

		s := *styles.DefaultStyles[styles.DraculaStyle]
		s.Document.Margin = util.Pointer(uint(0))
		s.Paragraph.Margin = util.Pointer(uint(0))
		s.Text.Color = util.Pointer(string(normal))

		r, _ := glamour.NewTermRenderer(
			glamour.WithStyles(s),
			glamour.WithWordWrap(120),
		)

		for svc, md := range documentationLongRaw {
			documentationLongRendered[svc], _ = r.Render(md)
		}

		_ = r.Close()
	}()
}

func DocumentationLong(name string) string {
	documentationLongWg.Wait()
	return documentationLongRendered[name]
}
