package connect

import (
	"embed"
	"fmt"
	"net/http"
)

//go:embed success.html success.css
var files embed.FS

func ConnectSuccessHTML() ([]byte, error) {
	content, err := files.ReadFile("success.html")
	if err != nil {
		return nil, fmt.Errorf("read connect success page: %w", err)
	}

	return content, nil
}

func ConnectAssetsHandler() http.Handler {
	return http.FileServer(http.FS(files))
}
