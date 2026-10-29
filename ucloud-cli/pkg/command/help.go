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
	"progress-test":   "Show the progress bar test",
	"public-ip":       "Manage public IPs",
	"public-link":     "Manage public links",
	"ssh-key":         "Manage SSH keys",
	"update":          "Check for and install updates",
	"version":         "Print the CLI version",
	"vm":              "Manage virtual machines",
	"workspace":       "Manage workspaces",

	"k8s maintenance":                   "Show maintenance, start runs and change the schedule",
	"k8s maintenance status":            "Show maintenance status",
	"k8s maintenance run":               "Start a maintenance run",
	"k8s maintenance cancel":            "Request cancellation of a maintenance run",
	"k8s maintenance retry":             "Retry a blocked or failed maintenance run",
	"k8s maintenance schedule":          "Change scheduled maintenance",
	"k8s maintenance backup":            "Start a cluster backup",
	"k8s-clusters maintenance":          "Show maintenance, start runs and change the schedule",
	"k8s-clusters maintenance status":   "Show maintenance status",
	"k8s-clusters maintenance run":      "Start a maintenance run",
	"k8s-clusters maintenance cancel":   "Request cancellation of a maintenance run",
	"k8s-clusters maintenance retry":    "Retry a blocked or failed maintenance run",
	"k8s-clusters maintenance schedule": "Change scheduled maintenance",
	"k8s-clusters maintenance backup":   "Start a cluster backup",
	"k8s create":                        "Create a Kubernetes cluster, or generate a template with --template",
	"k8s delete":                        "Delete a Kubernetes cluster",
	"k8s kubeconfig":                    "Print the kubeconfig of a Kubernetes cluster",
	"k8s list":                          "List Kubernetes clusters",
	"k8s pool":                          "Manage worker pools",
	"k8s recovery":                      "Show or start cluster recovery",
	"k8s releases":                      "List supported Kubernetes releases",
	"k8s scale":                         "Change the number of nodes in a pool",
	"k8s status":                        "Show the status of a Kubernetes cluster",
	"k8s-clusters create":               "Create a Kubernetes cluster, or generate a template with --template",
	"k8s-clusters delete":               "Delete a Kubernetes cluster",
	"k8s-clusters kubeconfig":           "Print the kubeconfig of a Kubernetes cluster",
	"k8s-clusters list":                 "List Kubernetes clusters",
	"k8s-clusters pool":                 "Manage worker pools",
	"k8s-clusters recovery":             "Show or start cluster recovery",
	"k8s-clusters releases":             "List supported Kubernetes releases",
	"k8s-clusters scale":                "Change the number of nodes in a pool",
	"k8s-clusters status":               "Show the status of a Kubernetes cluster",
}

var commandGroupNotes = map[string][]string{
	"k8s maintenance": {
		"Place flags after the action name, for example: ucloud k8s maintenance run dev --yes.",
	},
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
	node, ok := CommandTree()[name]
	if !ok {
		return false
	}
	return len(node.Children) > 0
}

func ShowHelp(path []string) error {
	return renderHelp(os.Stdout, path)
}

func renderHelp(w io.Writer, path []string) error {
	specs, err := commandsToCommandSpec()
	if err != nil {
		return err
	}

	if len(path) == 0 {
		renderTopLevelHelp(w, specs)
		return nil
	}

	current, ok := findCommandSpec(specs, path[0])
	if !ok {
		return fmt.Errorf("unknown command %s", path[0])
	}
	fullPath := []string{path[0]}
	for _, part := range path[1:] {
		child, ok := findCommandSpec(current.SubCommands, part)
		if !ok {
			return fmt.Errorf("unknown command %s", strings.Join(append(fullPath, part), " "))
		}
		current = child
		fullPath = append(fullPath, part)
	}

	if len(current.SubCommands) > 0 {
		renderGroupHelp(w, current, fullPath)
	} else {
		renderCommandHelp(w, current, fullPath)
	}
	return nil
}

func findCommandSpec(specs []CommandSpec, name string) (CommandSpec, bool) {
	for _, spec := range specs {
		if spec.Name == name {
			return spec, true
		}
	}
	return CommandSpec{}, false
}

func renderTopLevelHelp(w io.Writer, specs []CommandSpec) {
	fmt.Fprintln(w, "Usage: ucloud <command> [flags]")
	fmt.Fprintln(w)
	fmt.Fprintln(w, "Commands:")
	tw := tabwriter.NewWriter(w, 0, 0, 2, ' ', 0)
	for _, spec := range specs {
		writeCommandLine(tw, spec.Name, commandDescription([]string{spec.Name}))
	}
	tw.Flush()
	fmt.Fprintln(w)
	fmt.Fprintln(w, "Run 'ucloud help <command>' for more information.")
}

func renderGroupHelp(w io.Writer, spec CommandSpec, path []string) {
	fmt.Fprintf(w, "Usage: ucloud %s <command> [flags]\n\n", strings.Join(path, " "))
	if description := commandDescription(path); description != "" {
		fmt.Fprintln(w, description)
		fmt.Fprintln(w)
	}
	if notes := commandGroupHelpNotes(path); len(notes) > 0 {
		fmt.Fprintln(w, "Notes:")
		for _, note := range notes {
			fmt.Fprintln(w, "  "+note)
		}
		fmt.Fprintln(w)
	}
	fmt.Fprintln(w, "Commands:")
	tw := tabwriter.NewWriter(w, 0, 0, 2, ' ', 0)
	for _, sub := range spec.SubCommands {
		writeCommandLine(tw, sub.Name, commandDescription(append(path, sub.Name)))
	}
	tw.Flush()
	fmt.Fprintln(w)
	fmt.Fprintf(w, "Run 'ucloud help %s <command>' for more information.\n", strings.Join(path, " "))
}

func writeCommandLine(w io.Writer, name string, description string) {
	if description == "" {
		fmt.Fprintf(w, "  %s\n", name)
		return
	}
	fmt.Fprintf(w, "  %s\t%s\n", name, description)
}

func renderCommandHelp(w io.Writer, spec CommandSpec, path []string) {
	var positionals []FlagSpec
	var flags []FlagSpec
	for _, flagSpec := range spec.Flags {
		if flagSpec.FlagType == "positional" {
			positionals = append(positionals, flagSpec)
		} else {
			flags = append(flags, flagSpec)
		}
	}
	sort.Slice(flags, func(i, j int) bool {
		return flags[i].Name < flags[j].Name
	})

	usage := "Usage: ucloud " + strings.Join(path, " ")
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

	if description := commandDescription(path); description != "" {
		fmt.Fprintln(w, description)
		fmt.Fprintln(w)
	}

	if len(spec.Notes) > 0 {
		fmt.Fprintln(w, "Notes:")
		for _, note := range spec.Notes {
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
	fmt.Fprintln(tw, "  --color string\tColor mode: auto, always or never (default auto)")
	fmt.Fprintln(tw, "  -h, --help\tShow help")
	tw.Flush()
}

func commandGroupHelpNotes(path []string) []string {
	key := strings.Join(path, " ")
	if notes, ok := commandGroupNotes[key]; ok {
		return notes
	}
	if len(path) > 1 && path[0] == "k8s-clusters" {
		canonical := append([]string{"k8s"}, path[1:]...)
		return commandGroupNotes[strings.Join(canonical, " ")]
	}
	return nil
}

func commandDescription(path []string) string {
	key := strings.Join(path, " ")
	if description := commandDescriptions[key]; description != "" {
		return description
	}
	if len(path) > 1 && path[0] == "k8s-clusters" {
		canonical := append([]string{"k8s"}, path[1:]...)
		return commandDescriptions[strings.Join(canonical, " ")]
	}
	return ""
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
