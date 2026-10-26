package command

import (
	"fmt"
	"io"
	"os"
	"sort"
	"strings"
	"text/tabwriter"
)

var commandDescriptions = map[string]string{
	"app":             "Browse and search applications",
	"completion":      "Generate shell completion",
	"compute":         "Manage compute resources",
	"connect":         "Authenticate the CLI with UCloud",
	"environment":     "Manage CLI environments",
	"folder":          "Open folders",
	"help":            "Show help",
	"job":             "Manage jobs",
	"k8s":             "Manage Kubernetes clusters (alias for k8s-clusters)",
	"k8s-clusters":    "Manage Kubernetes clusters",
	"private-network": "Manage private networks",
	"public-ip":       "Manage public IPs",
	"public-link":     "Manage public links",
	"ssh-key":         "Manage SSH keys",
	"update":          "Check for and install updates",
	"version":         "Print the CLI version",
	"vm":              "Manage virtual machines",
	"workspace":       "Manage workspaces",

	"k8s create":              "Create a Kubernetes cluster",
	"k8s kubeconfig":          "Print the kubeconfig of a Kubernetes cluster",
	"k8s list":                "List Kubernetes clusters",
	"k8s node":                "Maintain a cluster node",
	"k8s pool":                "Manage worker pools",
	"k8s releases":            "List supported Kubernetes releases",
	"k8s scale":               "Change the number of nodes in a pool",
	"k8s status":              "Show the status of a Kubernetes cluster",
	"k8s-clusters create":     "Create a Kubernetes cluster",
	"k8s-clusters kubeconfig": "Print the kubeconfig of a Kubernetes cluster",
	"k8s-clusters list":       "List Kubernetes clusters",
	"k8s-clusters node":       "Maintain a cluster node",
	"k8s-clusters pool":       "Manage worker pools",
	"k8s-clusters releases":   "List supported Kubernetes releases",
	"k8s-clusters scale":      "Change the number of nodes in a pool",
	"k8s-clusters status":     "Show the status of a Kubernetes cluster",
}

type HelpCommand struct {
	Path []string `positional:"path" usage:"Command path"`
}

var HelpCommands = map[string]CommandFunc{
	"help": func() Command { return &HelpCommand{} },
}

func (c HelpCommand) Execute() error {
	return ShowHelp(c.Path)
}

func HelpPath(args []string) ([]string, bool) {
	if len(args) == 0 {
		return nil, true
	}

	if args[0] == "help" {
		return helpPathPrefix(args[1:]), true
	}

	for i, arg := range args {
		if arg == "-h" || arg == "--help" {
			return helpPathPrefix(args[:i]), true
		}
	}

	if len(args) == 1 && isGroupCommand(args[0]) {
		return []string{args[0]}, true
	}

	return nil, false
}

func helpPathPrefix(args []string) []string {
	var path []string
	for _, arg := range args {
		if strings.HasPrefix(arg, "-") {
			break
		}
		path = append(path, arg)
	}
	return path
}

func isGroupCommand(name string) bool {
	route, ok := CommandRegistry()[name]
	if !ok {
		return false
	}
	if len(route) != 1 {
		return true
	}
	_, isLeaf := route[name]
	return !isLeaf
}

func ShowHelp(path []string) error {
	return renderHelp(os.Stdout, path)
}

func renderHelp(w io.Writer, path []string) error {
	specs, err := commandsToCommandSpec()
	if err != nil {
		return err
	}

	sort.Slice(specs, func(i, j int) bool {
		return specs[i].Name < specs[j].Name
	})
	for i := range specs {
		sort.Slice(specs[i].SubCommands, func(a, b int) bool {
			return specs[i].SubCommands[a].Name < specs[i].SubCommands[b].Name
		})
	}

	if len(path) == 0 {
		renderTopLevelHelp(w, specs)
		return nil
	}

	var top *CommandSpec
	for i := range specs {
		if specs[i].Name == path[0] {
			top = &specs[i]
			break
		}
	}
	if top == nil {
		return fmt.Errorf("unknown command %s", path[0])
	}

	if len(path) == 1 {
		if isLeafCommand(*top) {
			renderCommandHelp(w, top.Name, top.SubCommands[0])
		} else {
			renderGroupHelp(w, *top)
		}
		return nil
	}

	for _, sub := range top.SubCommands {
		if sub.Name == path[1] {
			renderCommandHelp(w, top.Name, sub)
			return nil
		}
	}
	return fmt.Errorf("unknown command %s %s", path[0], path[1])
}

func isLeafCommand(spec CommandSpec) bool {
	return len(spec.SubCommands) == 1 && spec.SubCommands[0].Name == spec.Name
}

func renderTopLevelHelp(w io.Writer, specs []CommandSpec) {
	fmt.Fprintln(w, "Usage: ucloud <command> [flags]")
	fmt.Fprintln(w)
	fmt.Fprintln(w, "Commands:")
	tw := tabwriter.NewWriter(w, 0, 0, 2, ' ', 0)
	for _, spec := range specs {
		writeCommandLine(tw, spec.Name, commandDescriptions[spec.Name])
	}
	tw.Flush()
	fmt.Fprintln(w)
	fmt.Fprintln(w, "Run 'ucloud help <command>' for more information.")
}

func renderGroupHelp(w io.Writer, spec CommandSpec) {
	fmt.Fprintf(w, "Usage: ucloud %s <command> [flags]\n\n", spec.Name)
	if description := commandDescriptions[spec.Name]; description != "" {
		fmt.Fprintln(w, description)
		fmt.Fprintln(w)
	}
	fmt.Fprintln(w, "Commands:")
	tw := tabwriter.NewWriter(w, 0, 0, 2, ' ', 0)
	for _, sub := range spec.SubCommands {
		writeCommandLine(tw, sub.Name, commandDescriptions[spec.Name+" "+sub.Name])
	}
	tw.Flush()
	fmt.Fprintln(w)
	fmt.Fprintf(w, "Run 'ucloud help %s <command>' for more information.\n", spec.Name)
}

func writeCommandLine(w io.Writer, name string, description string) {
	if description == "" {
		fmt.Fprintf(w, "  %s\n", name)
		return
	}
	fmt.Fprintf(w, "  %s\t%s\n", name, description)
}

func renderCommandHelp(w io.Writer, topName string, sub SubCommand) {
	var positionals []FlagSpec
	var flags []FlagSpec
	for _, flagSpec := range sub.Flags {
		if flagSpec.FlagType == "positional" {
			positionals = append(positionals, flagSpec)
		} else {
			flags = append(flags, flagSpec)
		}
	}
	sort.Slice(flags, func(i, j int) bool {
		return flags[i].Name < flags[j].Name
	})

	usage := "Usage: ucloud " + topName
	if sub.Name != topName {
		usage += " " + sub.Name
	}
	for _, positional := range positionals {
		placeholder := "<" + positional.Name + ">"
		if positional.InputType == "slice" {
			placeholder = "<" + positional.Name + "...>"
		}
		usage += " " + placeholder
	}
	if len(flags) > 0 {
		usage += " [flags]"
	}
	fmt.Fprintln(w, usage)
	fmt.Fprintln(w)

	description := commandDescriptions[topName]
	if sub.Name != topName {
		description = commandDescriptions[topName+" "+sub.Name]
	}
	if description != "" {
		fmt.Fprintln(w, description)
		fmt.Fprintln(w)
	}

	if len(sub.Notes) > 0 {
		fmt.Fprintln(w, "Notes:")
		for _, note := range sub.Notes {
			fmt.Fprintln(w, "  "+note)
		}
		fmt.Fprintln(w)
	}

	if len(positionals) > 0 {
		fmt.Fprintln(w, "Arguments:")
		tw := tabwriter.NewWriter(w, 0, 0, 2, ' ', 0)
		for _, positional := range positionals {
			fmt.Fprintf(tw, "  %s\t%s%s\n", positional.Name, positional.Usage, requiredSuffix(positional))
		}
		tw.Flush()
		fmt.Fprintln(w)
	}

	if len(flags) > 0 {
		fmt.Fprintln(w, "Flags:")
		tw := tabwriter.NewWriter(w, 0, 0, 2, ' ', 0)
		for _, flagSpec := range flags {
			fmt.Fprintf(tw, "  %s\t%s\n", flagLabel(flagSpec), flagSpec.Usage)
		}
		tw.Flush()
		fmt.Fprintln(w)
	}

	fmt.Fprintln(w, "Global Flags:")
	tw := tabwriter.NewWriter(w, 0, 0, 2, ' ', 0)
	fmt.Fprintln(tw, "  -h, --help\tShow help")
	tw.Flush()
}

func flagLabel(flagSpec FlagSpec) string {
	label := flagSpec.Name
	switch flagSpec.InputType {
	case "slice":
		label += " string (repeatable)"
	case "map":
		label += " key=value (repeatable)"
	case "bool":
	default:
		label += " " + flagSpec.InputType
	}
	return label + requiredSuffix(flagSpec)
}

func requiredSuffix(flagSpec FlagSpec) string {
	if flagSpec.Required {
		return " (required)"
	}
	return ""
}
