package vm_agent

import (
	"bytes"
	"context"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
)

type MountOverrideOptions struct {
	TimeoutSec    string // example: "5s"
	LazyUnmount   bool
	ForceUnmount  bool
	RestartUnits  bool // restart affected mount units after reload
	SudoBinary    string
	SystemctlPath string
	InstallPath   string
}

func ApplyMountOverrides(ctx context.Context, mountPoints []string, opts MountOverrideOptions) error {
	if len(mountPoints) == 0 {
		return nil
	}

	opts = withDefaults(opts)

	tmpRoot, err := os.MkdirTemp("", "systemd-mount-overrides-*")
	if err != nil {
		return fmt.Errorf("create temp dir: %w", err)
	}
	defer os.RemoveAll(tmpRoot)

	var unitNames []string

	for _, mp := range mountPoints {
		unitName, err := mountUnitName(ctx, opts, mp)
		if err != nil {
			return fmt.Errorf("resolve unit name for %q: %w", mp, err)
		}
		unitNames = append(unitNames, unitName)

		if err := writeAndInstallOverride(ctx, tmpRoot, unitName, opts); err != nil {
			return fmt.Errorf("install override for %q (%s): %w", mp, unitName, err)
		}
	}

	if err := runCmd(ctx, opts.SudoBinary, opts.SystemctlPath, "daemon-reload"); err != nil {
		return fmt.Errorf("daemon-reload failed: %w", err)
	}

	if opts.RestartUnits {
		for _, unit := range unitNames {
			if err := runCmd(ctx, opts.SudoBinary, opts.SystemctlPath, "restart", unit); err != nil {
				return fmt.Errorf("restart %s failed: %w", unit, err)
			}
		}
	}

	return nil
}

func withDefaults(opts MountOverrideOptions) MountOverrideOptions {
	if opts.TimeoutSec == "" {
		opts.TimeoutSec = "5s"
	}
	if opts.SudoBinary == "" {
		opts.SudoBinary = "sudo"
	}
	if opts.SystemctlPath == "" {
		opts.SystemctlPath = "systemctl"
	}
	if opts.InstallPath == "" {
		opts.InstallPath = "install"
	}
	return opts
}

func mountUnitName(ctx context.Context, opts MountOverrideOptions, mountPoint string) (string, error) {
	out, err := runCmdOutput(ctx, opts.SudoBinary, "systemd-escape", "--path", "--suffix=mount", mountPoint)
	if err != nil {
		return "", err
	}
	unit := strings.TrimSpace(out)
	if unit == "" {
		return "", fmt.Errorf("empty unit name for mount point %q", mountPoint)
	}
	return unit, nil
}

func writeAndInstallOverride(ctx context.Context, tmpRoot, unitName string, opts MountOverrideOptions) error {
	content := buildOverrideContent(opts)

	localDir := filepath.Join(tmpRoot, unitName+".d")
	if err := os.MkdirAll(localDir, 0o755); err != nil {
		return fmt.Errorf("create local temp dir %s: %w", localDir, err)
	}

	localFile := filepath.Join(localDir, "override.conf")
	if err := os.WriteFile(localFile, []byte(content), 0o644); err != nil {
		return fmt.Errorf("write temp override %s: %w", localFile, err)
	}

	destDir := filepath.Join("/etc/systemd/system", unitName+".d")
	destFile := filepath.Join(destDir, "override.conf")

	if err := runCmd(ctx, opts.SudoBinary, opts.InstallPath, "-d", "-m", "0755", destDir); err != nil {
		return fmt.Errorf("create destination dir %s: %w", destDir, err)
	}

	if err := runCmd(ctx, opts.SudoBinary, opts.InstallPath, "-m", "0644", localFile, destFile); err != nil {
		return fmt.Errorf("install %s to %s: %w", localFile, destFile, err)
	}

	return nil
}

func buildOverrideContent(opts MountOverrideOptions) string {
	var b strings.Builder
	b.WriteString("[Mount]\n")
	b.WriteString(fmt.Sprintf("TimeoutSec=%s\n", opts.TimeoutSec))
	if opts.LazyUnmount {
		b.WriteString("LazyUnmount=yes\n")
	}
	if opts.ForceUnmount {
		b.WriteString("ForceUnmount=yes\n")
	}
	return b.String()
}

func runCmdOutput(ctx context.Context, name string, args ...string) (string, error) {
	cmd := exec.CommandContext(ctx, name, args...)
	var stdout bytes.Buffer
	var stderr bytes.Buffer
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr

	if err := cmd.Run(); err != nil {
		msg := strings.TrimSpace(stderr.String())
		if msg == "" {
			msg = err.Error()
		}
		return "", fmt.Errorf("%s %s: %s", name, strings.Join(args, " "), msg)
	}

	return stdout.String(), nil
}

func runCmd(ctx context.Context, name string, args ...string) error {
	_, err := runCmdOutput(ctx, name, args...)
	return err
}
