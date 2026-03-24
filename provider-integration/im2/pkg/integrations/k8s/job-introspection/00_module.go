package job_introspection

import (
	"encoding/json"
	"flag"
	"fmt"
	"net/http"
	"os"
	"slices"
	"strings"
	"time"

	"ucloud.dk/shared/pkg/cli"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/termio"
)

func writeJSON(v any) {
	data, err := json.MarshalIndent(v, "", "  ")
	cli.HandleError("encoding output", err)
	termio.WriteLine("%s", string(data))
}

func writeHelp() {
	f := termio.Frame{}
	f.AppendTitle("introspect help")
	f.AppendField("", "Inspect details about the current job and private networks")
	f.AppendSeparator()
	f.AppendField("private-network ls", "List private networks")
	f.AppendField("private-network get <name>", "Show details of a private network")
	f.AppendField("job", "Show details about the current job")
	f.AppendSeparator()
	f.AppendField("Examples", "ucloud introspect private-network ls")
	f.AppendField("", "ucloud introspect private-network get foo -json")
	f.AppendField("", "ucloud introspect job -json")
	f.Print()
}

var token string

func Launch() {
	{
		tokBytes, err := os.ReadFile("/etc/ucloud/token")
		cli.HandleError("reading token", err)

		tokLines := strings.Split(strings.TrimSpace(string(tokBytes)), "\n")
		if len(tokLines) == 0 || tokLines[0] == "" {
			termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Invalid token file: /etc/ucloud/token")
			os.Exit(1)
		}

		token = strings.TrimSpace(tokLines[0])
	}

	{
		ipBytes, err := os.ReadFile("/opt/ucloud/provider-hostname.txt")
		cli.HandleError("reading provider ip", err)

		providerIP := strings.TrimSpace(string(ipBytes))
		rpc.DefaultClient = &rpc.Client{
			BasePath: fmt.Sprintf("http://%s:42000", providerIP),
			Client: &http.Client{
				Timeout: 10 * time.Second,
			},
		}
	}

	if len(os.Args) < 3 {
		writeHelp()
		return
	}

	resource := os.Args[2]
	switch resource {
	case "private-network", "private-networks", "network", "networks":
		NetworkCommand()
	case "job", "jobs":
		JobCommand()
	default:
		writeHelp()
		os.Exit(1)
	}
}

func NetworkCommand() {
	args := os.Args[3:]
	command := "ls"
	commandArgs := args
	if len(args) > 0 && !strings.HasPrefix(args[0], "-") {
		command = args[0]
		commandArgs = args[1:]
	}

	switch {
	case cli.IsListCommand(command):
		fs := flag.NewFlagSet("introspect private-network ls", flag.ContinueOnError)
		jsonOutput := fs.Bool("json", false, "Print JSON output")
		gerr := fs.Parse(commandArgs)
		cli.HandleError("parsing flags", gerr)
		if fs.NArg() > 0 {
			cli.HandleError("parsing flags", fmt.Errorf("unexpected argument: %s", fs.Arg(0)))
		}

		response, err := IntrospectNetworks.Invoke(IntrospectAuthRequest{Token: token})
		cli.HandleError("listing private networks", err.AsError())

		slices.SortFunc(response.Networks, func(a, b IntrospectedNetwork) int {
			return strings.Compare(a.Name, b.Name)
		})

		if *jsonOutput {
			writeJSON(response)
			return
		}

		t := termio.Table{}
		t.AppendHeader("Name")
		t.AppendHeader("Subdomain")

		for _, network := range response.Networks {
			t.Cell("%v", network.Name)
			t.Cell("%v", network.Subdomain)
		}

		t.Print()

	case cli.IsGetCommand(command):
		if len(commandArgs) == 0 {
			termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Missing parameter: network name")
			os.Exit(1)
		}

		networkName := commandArgs[0]

		fs := flag.NewFlagSet("introspect private-network get", flag.ContinueOnError)
		jsonOutput := fs.Bool("json", false, "Print JSON output")
		gerr := fs.Parse(commandArgs[1:])
		cli.HandleError("parsing flags", gerr)
		if fs.NArg() > 0 {
			cli.HandleError("parsing flags", fmt.Errorf("unexpected argument: %s", fs.Arg(0)))
		}

		response, err := IntrospectNetworks.Invoke(IntrospectAuthRequest{Token: token})
		cli.HandleError("retrieving private network", err.AsError())

		var match *IntrospectedNetwork
		for i := range response.Networks {
			network := &response.Networks[i]
			if network.Name == networkName || network.Subdomain == networkName || network.Id == networkName {
				match = network
				break
			}
		}

		if match == nil {
			termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Private network not found: %s", networkName)
			os.Exit(1)
		}

		slices.SortFunc(match.Members, func(a, b IntrospectedNetworkMember) int {
			return strings.Compare(a.Name, b.Name)
		})

		if *jsonOutput {
			writeJSON(match)
			return
		}

		f := termio.Frame{}
		f.AppendTitle("Private network")
		f.AppendField("ID", match.Id)
		f.AppendField("Name", match.Name)
		f.AppendField("Subdomain", match.Subdomain)
		f.AppendField("Members", fmt.Sprintf("%d", len(match.Members)))
		f.Print()

		if len(match.Members) == 0 {
			return
		}

		termio.WriteLine("")
		mt := termio.Table{}
		mt.AppendHeader("ID")
		mt.AppendHeader("Name")
		mt.AppendHeader("FQDN")
		mt.AppendHeader("Labels")

		for _, member := range match.Members {
			mt.Cell("%v", member.Id)
			mt.Cell("%v", member.Name)
			mt.Cell("%v", member.Fqdn)
			mt.Cell("%v", member.Labels)
		}

		mt.Print()

	default:
		writeHelp()
		os.Exit(1)
	}
}

func JobCommand() {
	fs := flag.NewFlagSet("introspect job", flag.ContinueOnError)
	jsonOutput := fs.Bool("json", false, "Print JSON output")
	gerr := fs.Parse(os.Args[3:])
	cli.HandleError("parsing flags", gerr)
	if fs.NArg() > 0 {
		cli.HandleError("parsing flags", fmt.Errorf("unexpected argument: %s", fs.Arg(0)))
	}

	response, err := IntrospectJob.Invoke(IntrospectAuthRequest{Token: token})
	cli.HandleError("retrieving job", err.AsError())

	if *jsonOutput {
		writeJSON(response)
		return
	}

	f := termio.Frame{}
	f.AppendTitle("Job")
	f.AppendField("ID", response.Job.Id)
	f.AppendField("State", string(response.Job.Status.State))
	f.AppendField("Application", response.Job.Specification.Application.Name)
	f.AppendField("Product", response.Job.Specification.Product.Id)
	f.AppendField("Replicas", fmt.Sprintf("%d", response.Job.Specification.Replicas))
	f.AppendField("Service IP", response.ServiceIp)
	f.AppendField("Created at", cli.FormatTime(response.Job.CreatedAt))
	if response.Job.Status.StartedAt.IsSet() {
		f.AppendField("Started at", cli.FormatTime(response.Job.Status.StartedAt.Value))
	}
	f.Print()
	return
}
