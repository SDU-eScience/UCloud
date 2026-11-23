package accounting

import (
	"bytes"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"

	"ucloud.dk/shared/pkg/cfgutil"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const accountingScenarioLocalURL = "http://localhost:8080"

func AccountingScenariosMain(args []string) {
	if len(args) == 0 || args[0] == "list" {
		for _, definition := range accountingScenarioDefinitions() {
			fmt.Printf("%-24s %s\n", definition.Name, definition.Description)
		}
		return
	}
	if len(args) < 2 || args[0] != "run" {
		fmt.Fprintln(os.Stderr, "Usage: ucloud accounting-scenarios list | run NAME|all [--url URL] [--token TOKEN] [--out DIRECTORY]")
		os.Exit(2)
	}
	if !util.DevelopmentModeEnabled() {
		fmt.Fprintln(os.Stderr, "Accounting scenarios can only run in a development environment.")
		os.Exit(2)
	}
	flags := flag.NewFlagSet("accounting-scenarios", flag.ExitOnError)
	baseUrl := flags.String("url", accountingScenarioLocalURL, "URL of the running development core")
	token := flags.String("token", os.Getenv("UCLOUD_ACCESS_TOKEN"), "administrator access token (defaults to UCLOUD_ACCESS_TOKEN)")
	output := flags.String("out", "accounting-scenarios", "directory for checkpoint JSON and resource manifests")
	parseErr := flags.Parse(args[2:])
	if parseErr != nil {
		os.Exit(2)
	}
	base := strings.TrimRight(*baseUrl, "/")
	client := &http.Client{Timeout: 2 * time.Minute}
	useLauncherCredentials := *token == "" && base == accountingScenarioLocalURL && accountingScenarioLauncherCore()
	if useLauncherCredentials {
		client.CheckRedirect = func(_ *http.Request, _ []*http.Request) error {
			return http.ErrUseLastResponse
		}
		*token = accountingScenarioLauncherAccessToken(client)
		if *token == "" {
			fmt.Fprintln(os.Stderr, "Could not get an access token from the launcher core configuration.")
		}
	}
	if *token == "" {
		fmt.Fprintln(os.Stderr, "Set UCLOUD_ACCESS_TOKEN to a development administrator access token, or pass --token.")
		os.Exit(2)
	}
	names := []string{args[1]}
	if names[0] == "all" {
		names = nil
		for _, definition := range accountingScenarioDefinitions() {
			if !definition.ExplicitRun {
				names = append(names, definition.Name)
			}
		}
	}
	if args[1] == accountingScenarioPortfolioName {
		client.Timeout = accountingScenarioPortfolioTimeout + 10*time.Second
	}
	for _, name := range names {
		payload, marshalErr := json.Marshal(accountingScenarioRequest{Scenario: name})
		if marshalErr != nil {
			panic(marshalErr)
		}
		request, requestErr := http.NewRequest(http.MethodPost, base+"/api/accounting/v2/usage/runScenario", bytes.NewReader(payload))
		if requestErr != nil {
			fmt.Fprintln(os.Stderr, requestErr)
			os.Exit(1)
		}
		request.Header.Set("Authorization", "Bearer "+*token)
		request.Header.Set("Content-Type", "application/json")
		response, responseErr := client.Do(request)
		if responseErr != nil {
			fmt.Fprintln(os.Stderr, responseErr)
			os.Exit(1)
		}
		body, readErr := io.ReadAll(response.Body)
		response.Body.Close()
		if readErr != nil {
			fmt.Fprintln(os.Stderr, readErr)
			os.Exit(1)
		}
		if response.StatusCode != http.StatusOK {
			fmt.Fprintf(os.Stderr, "%s: HTTP %d: %s\n", name, response.StatusCode, body)
			os.Exit(1)
		}
		var result accountingScenarioResult
		decodeErr := json.Unmarshal(body, &result)
		if decodeErr != nil {
			fmt.Fprintln(os.Stderr, decodeErr)
			os.Exit(1)
		}
		directory := filepath.Join(*output, result.RunId)
		directoryErr := os.MkdirAll(directory, 0700)
		if directoryErr != nil {
			fmt.Fprintln(os.Stderr, directoryErr)
			os.Exit(1)
		}
		pretty, prettyErr := json.MarshalIndent(result, "", "  ")
		if prettyErr != nil {
			panic(prettyErr)
		}
		path := filepath.Join(directory, "checkpoints.json")
		writeErr := os.WriteFile(path, pretty, 0600)
		if writeErr != nil {
			fmt.Fprintln(os.Stderr, writeErr)
			os.Exit(1)
		}
		if result.Failure != "" {
			fmt.Fprintf(os.Stderr, "%s failed: %s\nPartial resource manifest: %s\n", name, result.Failure, path)
			os.Exit(1)
		}
		fmt.Printf("%s: %d checkpoints passed\nProject: %s (%s)\nSnapshots and resource manifest: %s\n",
			name, len(result.Checkpoints), result.ProjectInfo[result.ProjectId].Title, result.ProjectId, path)
		fmt.Println("Open /app/allocations/sub-allocated in this project.")
		fmt.Println("Load the JSON on /app/playground/allocations to inspect the checkpoints.")
		for _, checkpoint := range result.Checkpoints {
			if len(checkpoint.ChecksByProject) > 0 {
				fmt.Printf("  %s: %d funding relationships matched\n", checkpoint.Label, len(checkpoint.ChecksByProject))
			} else {
				fmt.Printf("  %s: %+v\n", checkpoint.Label, checkpoint.Expected)
			}
		}
	}
}

func accountingScenarioLauncherCore() bool {
	hostname, _ := os.Hostname()
	if hostname != "core2" {
		return false
	}
	addresses, _ := net.InterfaceAddrs()
	for _, address := range addresses {
		ip, _, err := net.ParseCIDR(address.String())
		if err == nil && ip.Equal(net.IPv4(172, 18, 0, 4)) {
			return true
		}
	}
	return false
}

func accountingScenarioLauncherAccessToken(client *http.Client) string {
	path, document := cfgutil.ReadAndParse("/etc/ucloud", "config")
	if document == nil {
		return ""
	}
	success := true
	refreshToken := cfgutil.RequireChildText(path, document, "refreshToken", &success)
	if !success || refreshToken == "" {
		return ""
	}
	core := rpc.Client{
		BasePath:     accountingScenarioLocalURL,
		RefreshToken: refreshToken,
		Client:       client,
	}
	return core.RetrieveAccessTokenOrRefresh()
}
