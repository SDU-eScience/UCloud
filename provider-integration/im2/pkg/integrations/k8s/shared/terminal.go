package shared

import (
	"encoding/json"
	"fmt"
	"io"
	"slices"
	"sync"
	"time"

	"ucloud.dk/pkg/controller"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

const IntegratedTerminalAppName = "terminal"

const terminalDefaultLeaseDuration = 15 * time.Minute
const terminalLeaseRefreshInterval = 5 * time.Minute

type IntegratedTerminalConfig struct {
	Folders []string `json:"folders"`
}

type TerminalSandbox struct {
	Owner      orc.ResourceOwner
	JobId      string
	ETag       string
	Folders    []string
	LeaseUntil time.Time
	AutoLease  bool

	Warnings []string
}

// NOTE(Dan): I tend to not like using interfaces, especially like this. Unfortunately, the API surface has to live in
// shared for various reasons while the implementation code cannot. In practice, it is safe to assume that there is only
// one implementation for the integrated terminal, you do not need to work with an assumption that this is generic and
// can be swapped around.

type TerminalCommandHandle interface {
	Wait()
	Resize(cols, rows int)
	Kill()
	Err() *util.HttpError
}

type TerminalBackend interface {
	Start(cmd *TerminalCmd) (TerminalCommandHandle, *util.HttpError)
}

var terminalBackend TerminalBackend

var terminalLeaseMutex sync.Mutex
var terminalLeaseUntil = map[string]time.Time{}

func TerminalRegisterBackend(backend TerminalBackend) {
	terminalBackend = backend
}

type TerminalCmd struct {
	Sandbox *TerminalSandbox
	Path    string
	Args    []string
	Env     []string
	Dir     string
	Stdin   io.Reader
	Stdout  io.Writer
	Stderr  io.Writer
	TTY     bool
	Cols    int
	Rows    int

	stateMutex sync.Mutex
	started    bool
	finished   bool
	failure    *util.HttpError
	handle     TerminalCommandHandle
	leaseStop  chan util.Empty
	leaseOnce  sync.Once
}

func (sandbox *TerminalSandbox) Command(name string, arg ...string) *TerminalCmd {
	return &TerminalCmd{
		Sandbox: sandbox,
		Path:    name,
		Args:    append([]string{name}, arg...),
		TTY:     false,
		Cols:    80,
		Rows:    24,
	}
}

func TerminalOpen(owner orc.ResourceOwner, folders []string) (*TerminalSandbox, *util.HttpError) {
	sandbox, err := terminalMutate(owner, util.OptNone[string](), func(config *IntegratedTerminalConfig) (bool, *util.HttpError) {
		oldFolders := config.Folders
		config.Folders = terminalNormalizeFolders(append(config.Folders, folders...))
		return !slices.Equal(oldFolders, config.Folders), nil
	})
	if err != nil {
		return nil, err
	}

	_ = TerminalLease(owner, terminalDefaultLeaseDuration)
	if leaseUntil := TerminalLeaseUntil(owner); leaseUntil.Present {
		sandbox.LeaseUntil = leaseUntil.Value
	}
	sandbox.AutoLease = false
	return sandbox, nil
}

func TerminalOpenToJob(jobId string) (*TerminalSandbox, *util.HttpError) {
	if jobId == "" {
		return nil, util.UserHttpError("job id must not be empty")
	}

	job, ok := controller.JobRetrieve(jobId)
	if !ok {
		return nil, util.UserHttpError("job not found")
	}
	if job.Owner.CreatedBy == "" {
		return nil, util.UserHttpError("terminal sandbox requires an explicit owner")
	}

	sandbox := &TerminalSandbox{
		Owner:     job.Owner,
		JobId:     job.Id,
		AutoLease: false,
	}

	return sandbox, nil
}

func TerminalSetFolders(owner orc.ResourceOwner, etag util.Option[string], folders []string) (*TerminalSandbox, *util.HttpError) {
	sandbox, err := terminalMutate(owner, etag, func(config *IntegratedTerminalConfig) (bool, *util.HttpError) {
		oldFolders := config.Folders
		config.Folders = terminalNormalizeFolders(folders)
		return !slices.Equal(oldFolders, config.Folders), nil
	})
	if err != nil {
		return nil, err
	}

	_ = TerminalLease(owner, terminalDefaultLeaseDuration)
	if leaseUntil := TerminalLeaseUntil(owner); leaseUntil.Present {
		sandbox.LeaseUntil = leaseUntil.Value
	}
	sandbox.AutoLease = false
	return sandbox, nil
}

func TerminalAddFolder(owner orc.ResourceOwner, etag util.Option[string], folder string) (*TerminalSandbox, *util.HttpError) {
	sandbox, err := terminalMutate(owner, etag, func(config *IntegratedTerminalConfig) (bool, *util.HttpError) {
		oldFolders := config.Folders
		config.Folders = terminalNormalizeFolders(append(config.Folders, folder))
		return !slices.Equal(oldFolders, config.Folders), nil
	})
	if err != nil {
		return nil, err
	}

	_ = TerminalLease(owner, terminalDefaultLeaseDuration)
	if leaseUntil := TerminalLeaseUntil(owner); leaseUntil.Present {
		sandbox.LeaseUntil = leaseUntil.Value
	}
	sandbox.AutoLease = false
	return sandbox, nil
}

func TerminalRemoveFolder(owner orc.ResourceOwner, etag util.Option[string], folder string) (*TerminalSandbox, *util.HttpError) {
	sandbox, err := terminalMutate(owner, etag, func(config *IntegratedTerminalConfig) (bool, *util.HttpError) {
		filtered := make([]string, 0, len(config.Folders))
		for _, existing := range config.Folders {
			if existing != folder {
				filtered = append(filtered, existing)
			}
		}
		config.Folders = terminalNormalizeFolders(filtered)
		return true, nil
	})
	if err != nil {
		return nil, err
	}

	_ = TerminalLease(owner, terminalDefaultLeaseDuration)
	if leaseUntil := TerminalLeaseUntil(owner); leaseUntil.Present {
		sandbox.LeaseUntil = leaseUntil.Value
	}
	sandbox.AutoLease = false
	return sandbox, nil
}

func TerminalLease(owner orc.ResourceOwner, duration time.Duration) *util.HttpError {
	if owner.CreatedBy == "" {
		return util.UserHttpError("terminal sandbox requires an explicit owner")
	}

	terminalLeaseMutex.Lock()
	terminalLeaseUntil[terminalLeaseKey(owner)] = time.Now().Add(duration)
	terminalLeaseMutex.Unlock()
	return nil

}

func TerminalLeaseUntil(owner orc.ResourceOwner) util.Option[time.Time] {
	terminalLeaseMutex.Lock()
	defer terminalLeaseMutex.Unlock()

	leaseUntil, ok := terminalLeaseUntil[terminalLeaseKey(owner)]
	if !ok {
		return util.OptNone[time.Time]()
	}

	return util.OptValue(leaseUntil)
}

func terminalMutate(
	owner orc.ResourceOwner,
	etag util.Option[string],
	mutate func(config *IntegratedTerminalConfig) (bool, *util.HttpError),
) (*TerminalSandbox, *util.HttpError) {
	if owner.CreatedBy == "" {
		return nil, util.UserHttpError("terminal sandbox requires an explicit owner")
	}

	current := controller.IAppRetrieveConfiguration(IntegratedTerminalAppName, owner)
	config := IntegratedTerminalConfig{}
	currentEtag := util.OptNone[string]()

	if current.Present {
		currentEtag.Set(current.Value.ETag)
		if etag.Present && etag.Value != current.Value.ETag {
			return nil, util.UserHttpError("The application configuration has changed since you last loaded the page. Please reload the page and try again.")
		}

		if err := json.Unmarshal(current.Value.Configuration, &config); err != nil {
			return nil, util.HttpErrorFromErr(err)
		}
	} else if etag.Present {
		return nil, util.UserHttpError("The application configuration has changed since you last loaded the page. Please reload the page and try again.")
	}

	didUpdate, err := mutate(&config)
	if err != nil {
		return nil, err
	}

	if didUpdate || (current.Present && current.Value.IsDetached()) {
		validation := terminalValidateFolders(owner, config.Folders)
		config.Folders = validation.Folders

		data, err := json.Marshal(config)
		if err != nil {
			return nil, util.HttpErrorFromErr(err)
		}

		if err := controller.IAppConfigure(IntegratedTerminalAppName, owner, currentEtag, data); err != nil {
			return nil, err
		}

		updated := controller.IAppRetrieveConfiguration(IntegratedTerminalAppName, owner)
		if !updated.Present {
			return nil, util.ServerHttpError("error configuring terminal sandbox")
		}
		sandbox := terminalSandboxFromConfiguration(updated.Value)
		sandbox.Warnings = validation.Warnings
		return sandbox, nil
	} else {
		return terminalSandboxFromConfiguration(current.Value), nil
	}
}

func terminalSandboxFromConfiguration(config controller.IAppRunningConfiguration) *TerminalSandbox {
	var parsed IntegratedTerminalConfig
	_ = json.Unmarshal(config.Configuration, &parsed)
	sandbox := &TerminalSandbox{
		Owner:   config.Owner,
		JobId:   config.JobId,
		ETag:    config.ETag,
		Folders: append([]string{}, terminalNormalizeFolders(parsed.Folders)...),
	}

	if leaseUntil := TerminalLeaseUntil(config.Owner); leaseUntil.Present {
		sandbox.LeaseUntil = leaseUntil.Value
	}

	return sandbox
}

func terminalLeaseKey(owner orc.ResourceOwner) string {
	project := ""
	if owner.Project.Present {
		project = owner.Project.Value
	}
	return owner.CreatedBy + "|" + project
}

func terminalNormalizeFolders(folders []string) []string {
	result := make([]string, 0, len(folders))
	seen := map[string]util.Empty{}
	for _, folder := range folders {
		if folder == "" {
			continue
		}
		if _, ok := seen[folder]; ok {
			continue
		}
		seen[folder] = util.Empty{}
		result = append(result, folder)
	}
	return result
}

type terminalValidateFoldersResult struct {
	Warnings []string
	Folders  []string
}

func terminalValidateFolders(owner orc.ResourceOwner, folders []string) terminalValidateFoldersResult {
	sb := terminalValidateFoldersResult{}
	newFolders := make([]string, 0, len(folders))
	for _, folder := range folders {
		driveId, ok := orc.DriveIdFromUCloudPath(folder)
		if !ok {
			sb.Warnings = append(sb.Warnings, fmt.Sprintf("Unable to mount '%s'. The path is invalid.", folder))
			continue
		}

		drive, ok := controller.DriveRetrieve(driveId)
		if !ok {
			sb.Warnings = append(sb.Warnings, fmt.Sprintf("Unable to mount '%s'. The path is invalid.", folder))
			continue
		}

		if controller.ResourceIsLocked(drive.Resource, drive.Specification.Product) {
			sb.Warnings = append(sb.Warnings, fmt.Sprintf("Unable to mount '%s'. You do not have enough storage resources.", folder))
			continue
		}

		if !controller.DriveCanUse(owner, driveId, false) && !controller.DriveCanUse(owner, driveId, true) {
			sb.Warnings = append(sb.Warnings, fmt.Sprintf("Unable to mount '%s'. You do not have sufficient permissions.", folder))
			continue
		}

		newFolders = append(newFolders, folder)
	}

	sb.Folders = newFolders
	return sb
}

func (cmd *TerminalCmd) Err() *util.HttpError {
	cmd.stateMutex.Lock()
	defer cmd.stateMutex.Unlock()
	if cmd.failure != nil {
		return cmd.failure
	}
	if cmd.handle != nil {
		return cmd.handle.Err()
	}
	return nil
}

func (cmd *TerminalCmd) fail(err *util.HttpError) {
	if err == nil {
		return
	}

	cmd.stateMutex.Lock()
	if cmd.failure == nil {
		cmd.failure = err
	}
	cmd.stateMutex.Unlock()
}

func (cmd *TerminalCmd) stopLeaseLoop() {
	cmd.leaseOnce.Do(func() {
		cmd.stateMutex.Lock()
		leaseStop := cmd.leaseStop
		cmd.stateMutex.Unlock()
		if leaseStop != nil {
			close(leaseStop)
		}
	})
}

func (cmd *TerminalCmd) Start() {
	cmd.stateMutex.Lock()
	if cmd.failure != nil || cmd.started || cmd.finished {
		cmd.stateMutex.Unlock()
		return
	}
	cmd.stateMutex.Unlock()

	sandbox := cmd.Sandbox
	if sandbox == nil {
		cmd.fail(util.ServerHttpError("terminal sandbox is not available"))
		return
	}
	if terminalBackend == nil {
		cmd.fail(util.ServerHttpError("terminal backend is not registered"))
		return
	}
	if sandbox.AutoLease {
		if err := TerminalLease(sandbox.Owner, terminalDefaultLeaseDuration); err != nil {
			cmd.fail(err)
			return
		}
	}

	handle, err := terminalBackend.Start(cmd)
	if err != nil {
		cmd.fail(err)
		return
	}

	leaseStop := make(chan util.Empty)
	cmd.stateMutex.Lock()
	cmd.handle = handle
	cmd.leaseStop = leaseStop
	cmd.started = true
	cmd.stateMutex.Unlock()

	if sandbox.AutoLease {
		go terminalLeaseLoop(cmd, leaseStop)
	}
	go func() {
		handle.Wait()
		if err := handle.Err(); err != nil {
			cmd.fail(err)
		}
		cmd.stateMutex.Lock()
		cmd.finished = true
		cmd.stateMutex.Unlock()
		cmd.stopLeaseLoop()
	}()
}

func terminalLeaseLoop(cmd *TerminalCmd, stop chan util.Empty) {
	ticker := time.NewTicker(terminalLeaseRefreshInterval)
	defer ticker.Stop()

	for {
		select {
		case <-stop:
			return
		case <-ticker.C:
			if cmd.Sandbox == nil || !cmd.Sandbox.AutoLease {
				return
			}
			if cmd.Err() != nil {
				return
			}
			if err := TerminalLease(cmd.Sandbox.Owner, terminalDefaultLeaseDuration); err != nil {
				log.Warn("Failed to refresh terminal lease: %s", err)
			}
		}
	}
}

func (cmd *TerminalCmd) Run() {
	cmd.Start()
	cmd.Wait()
}

func (cmd *TerminalCmd) Wait() {
	cmd.stateMutex.Lock()
	if cmd.failure != nil || !cmd.started || cmd.finished || cmd.handle == nil {
		cmd.stateMutex.Unlock()
		return
	}
	handle := cmd.handle
	cmd.stateMutex.Unlock()

	handle.Wait()
	if err := handle.Err(); err != nil {
		cmd.fail(err)
	}
	cmd.stateMutex.Lock()
	cmd.finished = true
	cmd.stateMutex.Unlock()
	cmd.stopLeaseLoop()
}

func (cmd *TerminalCmd) Resize(cols, rows int) {
	cmd.stateMutex.Lock()
	if cmd.failure != nil || !cmd.started || cmd.finished || cmd.handle == nil {
		cmd.stateMutex.Unlock()
		return
	}
	handle := cmd.handle
	cmd.Cols = cols
	cmd.Rows = rows
	cmd.stateMutex.Unlock()

	handle.Resize(cols, rows)
	if cmd.Sandbox != nil && cmd.Sandbox.AutoLease {
		_ = TerminalLease(cmd.Sandbox.Owner, terminalDefaultLeaseDuration)
	}
}

func (cmd *TerminalCmd) Kill() {
	cmd.stateMutex.Lock()
	if cmd.failure != nil || !cmd.started || cmd.finished || cmd.handle == nil {
		cmd.stateMutex.Unlock()
		return
	}
	handle := cmd.handle
	cmd.stateMutex.Unlock()

	handle.Kill()
	cmd.stopLeaseLoop()
}
