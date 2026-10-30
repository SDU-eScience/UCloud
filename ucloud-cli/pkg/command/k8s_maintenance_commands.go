package command

import (
	"fmt"
	"io"
)

type K8sMaintenanceStatusCommand struct {
	Name      string `positional:"cluster" usage:"Cluster name" required:"true"`
	Workspace string `flag:"workspace" usage:"Workspace to use"`
	Output    string `flag:"output" usage:"Output format: table or json"`

	visited map[string]bool
}

type K8sMaintenanceRunCommand struct {
	Name                    string   `positional:"cluster" usage:"Cluster name" required:"true"`
	Task                    []string `flag:"task" usage:"Task: update, k8s-upgrade, os-update, cordon, uncordon or remove. Omit to run the tasks enabled in the schedule. Repeat or comma-separate to combine updates."`
	Nodes                   []string `flag:"node" usage:"Target node. Repeat for multiple nodes."`
	Pools                   []string `flag:"pool" usage:"Target node pool. Repeat for multiple pools."`
	All                     bool     `flag:"all" usage:"Target every node"`
	K8sVersion              string   `flag:"k8s-version" usage:"Target Kubernetes release for Kubernetes upgrades"`
	Timeout                 int      `flag:"timeout" usage:"Task timeout in seconds, 30 to 3600 (default 600)"`
	Drain                   bool     `flag:"drain" usage:"Drain the node for a cordon task"`
	DeleteVolatilePods      bool     `flag:"delete-volatile-pods" usage:"Also evict pods with emptyDir data and pods without a controller"`
	BypassDisruptionBudgets bool     `flag:"bypass-disruption-budgets" usage:"Evict pods even when a disruption budget forbids it"`
	Force                   bool     `flag:"force" usage:"Use a zero grace period while draining; requires --bypass-disruption-budgets"`
	MaxParallel             int      `flag:"max-parallel" usage:"Maximum parallel nodes per group (default 1)"`
	Wait                    bool     `flag:"wait" usage:"Wait until the run finishes"`
	Yes                     bool     `flag:"yes" short:"y" usage:"Skip the run confirmation"`
	Workspace               string   `flag:"workspace" usage:"Workspace to use"`
	Output                  string   `flag:"output" usage:"Output format: table or json"`

	visited map[string]bool
}

type K8sMaintenanceCancelCommand struct {
	Name      string   `positional:"cluster" usage:"Cluster name" required:"true"`
	RunUid    string   `flag:"run" usage:"Maintenance run uid (defaults to the active update run)"`
	Nodes     []string `flag:"node" usage:"Target node"`
	Wait      bool     `flag:"wait" usage:"Wait until cancellation finishes"`
	Workspace string   `flag:"workspace" usage:"Workspace to use"`
	Output    string   `flag:"output" usage:"Output format: table or json"`

	visited map[string]bool
}

type K8sMaintenanceRetryCommand struct {
	Name      string   `positional:"cluster" usage:"Cluster name" required:"true"`
	RunUid    string   `flag:"run" usage:"Maintenance run uid (defaults to the active update run)"`
	Nodes     []string `flag:"node" usage:"Target node"`
	Wait      bool     `flag:"wait" usage:"Wait until the run finishes"`
	Workspace string   `flag:"workspace" usage:"Workspace to use"`
	Output    string   `flag:"output" usage:"Output format: table or json"`

	visited map[string]bool
}

type K8sMaintenanceScheduleCommand struct {
	Name       string `positional:"cluster" usage:"Cluster name" required:"true"`
	K8sEnabled bool   `flag:"k8s-enabled" usage:"Include Kubernetes upgrades in scheduled maintenance"`
	OsEnabled  bool   `flag:"os-enabled" usage:"Include operating-system updates in scheduled maintenance"`
	Day        string `flag:"day" usage:"Maintenance day: mon, tue, wed, thu, fri, sat or sun"`
	Hour       int    `flag:"hour" usage:"Maintenance period start hour UTC, 0 to 23"`
	Workspace  string `flag:"workspace" usage:"Workspace to use"`
	Output     string `flag:"output" usage:"Output format: table or json"`

	visited map[string]bool
}

type K8sMaintenanceBackupCommand struct {
	Name      string `positional:"cluster" usage:"Cluster name" required:"true"`
	Wait      bool   `flag:"wait" usage:"Wait until the backup finishes"`
	Workspace string `flag:"workspace" usage:"Workspace to use"`
	Output    string `flag:"output" usage:"Output format: table or json"`

	visited map[string]bool
}

func k8sMaintenanceCommandGroup() *CommandNode {
	return commandGroup(map[string]*CommandNode{
		"status": commandLeaf(func() Command {
			return &K8sMaintenanceStatusCommand{}
		}),
		"run": commandLeaf(func() Command {
			return &K8sMaintenanceRunCommand{}
		}),
		"cancel": commandLeaf(func() Command {
			return &K8sMaintenanceCancelCommand{}
		}),
		"retry": commandLeaf(func() Command {
			return &K8sMaintenanceRetryCommand{}
		}),
		"schedule": commandLeaf(func() Command {
			return &K8sMaintenanceScheduleCommand{}
		}),
		"backup": commandLeaf(func() Command {
			return &K8sMaintenanceBackupCommand{}
		}),
	})
}

func (c *K8sMaintenanceStatusCommand) ParseArguments(args []string) error {
	name, visited, err := k8sMaintenanceParseClusterArgs(args, c)
	if err != nil {
		return err
	}
	c.Name = name
	c.visited = visited
	return nil
}

func (c *K8sMaintenanceStatusCommand) Execute() error {
	return k8sMaintenanceExecute("status", c.Name, c.Workspace, c.Output, c.visited, nil)
}

func (c *K8sMaintenanceStatusCommand) HelpNotes() []string {
	return []string{
		"Shows the schedule, active runs, per-node progress and the last run.",
	}
}

func (c *K8sMaintenanceRunCommand) ParseArguments(args []string) error {
	name, visited, err := k8sMaintenanceParseClusterArgs(args, c)
	if err != nil {
		return err
	}
	c.Name = name
	c.visited = visited
	return nil
}

func (c *K8sMaintenanceRunCommand) Execute() error {
	return k8sMaintenanceExecute("run", c.Name, c.Workspace, c.Output, c.visited, func(cmd *k8sMaintenanceRequest) {
		cmd.Task = c.Task
		cmd.Nodes = c.Nodes
		cmd.Pools = c.Pools
		cmd.All = c.All
		cmd.K8sVersion = c.K8sVersion
		cmd.Timeout = c.Timeout
		cmd.Drain = c.Drain
		cmd.DeleteVolatilePods = c.DeleteVolatilePods
		cmd.BypassDisruptionBudgets = c.BypassDisruptionBudgets
		cmd.Force = c.Force
		cmd.MaxParallel = c.MaxParallel
		cmd.Wait = c.Wait
		cmd.Yes = c.Yes
	})
}

func (c *K8sMaintenanceRunCommand) HelpNotes() []string {
	return []string{
		"Without --task, run starts the tasks enabled in the scheduled policy on all nodes.",
		"Run asks for confirmation when standard input is a terminal. Use -y or --yes to skip it.",
		"Node actions target exactly one node and cannot be combined with updates.",
		"Kubernetes upgrades always require a fresh cluster backup before they start.",
		"Use --wait to block until the run finishes.",
	}
}

func (c *K8sMaintenanceCancelCommand) ParseArguments(args []string) error {
	name, visited, err := k8sMaintenanceParseClusterArgs(args, c)
	if err != nil {
		return err
	}
	c.Name = name
	c.visited = visited
	return nil
}

func (c *K8sMaintenanceCancelCommand) Execute() error {
	return k8sMaintenanceExecute("cancel", c.Name, c.Workspace, c.Output, c.visited, func(cmd *k8sMaintenanceRequest) {
		cmd.RunUid = c.RunUid
		cmd.Nodes = c.Nodes
		cmd.Wait = c.Wait
	})
}

func (c *K8sMaintenanceCancelCommand) HelpNotes() []string {
	return []string{
		"Without --run or --node, cancels the active update run.",
		"Use --node to cancel a node action.",
		"Use --wait to block until the cancellation finishes.",
	}
}

func (c *K8sMaintenanceRetryCommand) ParseArguments(args []string) error {
	name, visited, err := k8sMaintenanceParseClusterArgs(args, c)
	if err != nil {
		return err
	}
	c.Name = name
	c.visited = visited
	return nil
}

func (c *K8sMaintenanceRetryCommand) Execute() error {
	return k8sMaintenanceExecute("retry", c.Name, c.Workspace, c.Output, c.visited, func(cmd *k8sMaintenanceRequest) {
		cmd.RunUid = c.RunUid
		cmd.Nodes = c.Nodes
		cmd.Wait = c.Wait
	})
}

func (c *K8sMaintenanceRetryCommand) HelpNotes() []string {
	return []string{
		"Without --run or --node, retries the active update run.",
		"Use --node to retry a node action.",
		"Use --wait to block until the run finishes.",
	}
}

func (c *K8sMaintenanceScheduleCommand) ParseArguments(args []string) error {
	name, visited, err := k8sMaintenanceParseClusterArgs(args, c)
	if err != nil {
		return err
	}
	c.Name = name
	c.visited = visited
	return nil
}

func (c *K8sMaintenanceScheduleCommand) Execute() error {
	return k8sMaintenanceExecute("schedule", c.Name, c.Workspace, c.Output, c.visited, func(cmd *k8sMaintenanceRequest) {
		cmd.K8sEnabled = c.K8sEnabled
		cmd.OsEnabled = c.OsEnabled
		cmd.Day = c.Day
		cmd.Hour = c.Hour
	})
}

func (c *K8sMaintenanceScheduleCommand) HelpNotes() []string {
	return []string{
		"Only the supplied policy options are changed.",
		"Use --k8s-enabled=false or --os-enabled=false to disable a scheduled task.",
	}
}

func (c *K8sMaintenanceBackupCommand) ParseArguments(args []string) error {
	name, visited, err := k8sMaintenanceParseClusterArgs(args, c)
	if err != nil {
		return err
	}
	c.Name = name
	c.visited = visited
	return nil
}

func (c *K8sMaintenanceBackupCommand) Execute() error {
	return k8sMaintenanceExecute("backup", c.Name, c.Workspace, c.Output, c.visited, func(cmd *k8sMaintenanceRequest) {
		cmd.Wait = c.Wait
	})
}

func (c *K8sMaintenanceBackupCommand) HelpNotes() []string {
	return []string{
		"Use --wait to block until the backup finishes.",
	}
}

func k8sMaintenanceParseClusterArgs(args []string, cmd any) (string, map[string]bool, error) {
	parsed, err := ArgsBind(args, cmd, ArgsBindOptions{
		FlagSetName:  "k8s maintenance",
		FlagsOnly:    true,
		Interspersed: true,
		ShortFlags:   true,
		ErrorOutput:  io.Discard,
	})
	if err != nil {
		return "", nil, err
	}
	if len(parsed.Positionals) != 1 {
		return "", nil, fmt.Errorf("expected one cluster name, got %d arguments", len(parsed.Positionals))
	}
	return parsed.Positionals[0], parsed.Visited, nil
}

func k8sMaintenanceExecute(
	action string,
	name string,
	workspace string,
	output string,
	visited map[string]bool,
	configure func(cmd *k8sMaintenanceRequest),
) error {
	cmd := &k8sMaintenanceRequest{
		Action:      action,
		Name:        name,
		Workspace:   workspace,
		Output:      output,
		Timeout:     -1,
		MaxParallel: 1,
		Hour:        -1,
		visited:     visited,
	}
	if configure != nil {
		configure(cmd)
	}
	return cmd.execute()
}
