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
const InferenceSandboxAppName = "inference-sandbox"

const integratedTerminalLabel = "terminal sandbox"
const inferenceSandboxLabel = "inference sandbox"

const integratedSandboxDefaultLeaseDuration = 15 * time.Minute
const integratedSandboxLeaseRefreshInterval = 5 * time.Minute

type IntegratedSandboxConfig struct {
	Folders []string `json:"folders"`
}

type IntegratedSandbox struct {
	AppName    string
	Owner      orc.ResourceOwner
	JobId      string
	Rank       util.Option[int]
	ETag       string
	Folders    []string
	LeaseUntil time.Time
	AutoLease  bool

	Warnings []string
}

type IntegratedTerminalConfig = IntegratedSandboxConfig
type InferenceSandboxConfig = IntegratedSandboxConfig
type TerminalSandbox = IntegratedSandbox
type InferenceSandbox = IntegratedSandbox

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

var integratedSandboxLeaseMutex sync.Mutex
var integratedSandboxLeaseUntilByKey = map[string]time.Time{}

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

func (sandbox *IntegratedSandbox) Command(name string, arg ...string) *TerminalCmd {
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
	if err := TerminalLease(owner, integratedSandboxDefaultLeaseDuration); err != nil {
		return nil, err
	}
	sandbox, err := integratedSandboxMutate(IntegratedTerminalAppName, owner, util.OptNone[string](), func(config *IntegratedSandboxConfig) (bool, *util.HttpError) {
		oldFolders := config.Folders
		config.Folders = integratedSandboxNormalizeFolders(append(config.Folders, folders...))
		return !slices.Equal(oldFolders, config.Folders), nil
	})
	if err != nil {
		return nil, err
	}

	if leaseUntil := TerminalLeaseUntil(owner); leaseUntil.Present {
		sandbox.LeaseUntil = leaseUntil.Value
	}
	sandbox.AutoLease = false
	return sandbox, nil
}

func TerminalOpenToJob(jobId string, rank util.Option[int]) (*TerminalSandbox, *util.HttpError) {
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
		AppName:   IntegratedTerminalAppName,
		Owner:     job.Owner,
		JobId:     job.Id,
		Rank:      rank,
		AutoLease: false,
	}

	return sandbox, nil
}

func TerminalSetFolders(owner orc.ResourceOwner, etag util.Option[string], folders []string) (*TerminalSandbox, *util.HttpError) {
	if err := TerminalLease(owner, integratedSandboxDefaultLeaseDuration); err != nil {
		return nil, err
	}
	sandbox, err := integratedSandboxMutate(IntegratedTerminalAppName, owner, etag, func(config *IntegratedSandboxConfig) (bool, *util.HttpError) {
		oldFolders := config.Folders
		config.Folders = integratedSandboxNormalizeFolders(folders)
		return !slices.Equal(oldFolders, config.Folders), nil
	})
	if err != nil {
		return nil, err
	}

	if leaseUntil := TerminalLeaseUntil(owner); leaseUntil.Present {
		sandbox.LeaseUntil = leaseUntil.Value
	}
	sandbox.AutoLease = false
	return sandbox, nil
}

func TerminalAddFolder(owner orc.ResourceOwner, etag util.Option[string], folder string) (*TerminalSandbox, *util.HttpError) {
	if err := TerminalLease(owner, integratedSandboxDefaultLeaseDuration); err != nil {
		return nil, err
	}
	sandbox, err := integratedSandboxMutate(IntegratedTerminalAppName, owner, etag, func(config *IntegratedSandboxConfig) (bool, *util.HttpError) {
		oldFolders := config.Folders
		config.Folders = integratedSandboxNormalizeFolders(append(config.Folders, folder))
		return !slices.Equal(oldFolders, config.Folders), nil
	})
	if err != nil {
		return nil, err
	}

	if leaseUntil := TerminalLeaseUntil(owner); leaseUntil.Present {
		sandbox.LeaseUntil = leaseUntil.Value
	}
	sandbox.AutoLease = false
	return sandbox, nil
}

func TerminalRemoveFolder(owner orc.ResourceOwner, etag util.Option[string], folder string) (*TerminalSandbox, *util.HttpError) {
	if err := TerminalLease(owner, integratedSandboxDefaultLeaseDuration); err != nil {
		return nil, err
	}
	sandbox, err := integratedSandboxMutate(IntegratedTerminalAppName, owner, etag, func(config *IntegratedSandboxConfig) (bool, *util.HttpError) {
		filtered := make([]string, 0, len(config.Folders))
		for _, existing := range config.Folders {
			if existing != folder {
				filtered = append(filtered, existing)
			}
		}
		config.Folders = integratedSandboxNormalizeFolders(filtered)
		return true, nil
	})
	if err != nil {
		return nil, err
	}

	if leaseUntil := TerminalLeaseUntil(owner); leaseUntil.Present {
		sandbox.LeaseUntil = leaseUntil.Value
	}
	sandbox.AutoLease = false
	return sandbox, nil
}

func TerminalLease(owner orc.ResourceOwner, duration time.Duration) *util.HttpError {
	return integratedSandboxLease(IntegratedTerminalAppName, owner, duration)
}

func TerminalLeaseUntil(owner orc.ResourceOwner) util.Option[time.Time] {
	return integratedSandboxLeaseUntil(IntegratedTerminalAppName, owner)
}

func InferenceSandboxOpen(owner orc.ResourceOwner, folders []string) (*InferenceSandbox, *util.HttpError) {
	if err := InferenceSandboxLease(owner, integratedSandboxDefaultLeaseDuration); err != nil {
		return nil, err
	}
	sandbox, err := integratedSandboxMutate(InferenceSandboxAppName, owner, util.OptNone[string](), func(config *IntegratedSandboxConfig) (bool, *util.HttpError) {
		oldFolders := config.Folders
		config.Folders = integratedSandboxNormalizeFolders(append(config.Folders, folders...))
		return !slices.Equal(oldFolders, config.Folders), nil
	})
	if err != nil {
		return nil, err
	}

	if leaseUntil := InferenceSandboxLeaseUntil(owner); leaseUntil.Present {
		sandbox.LeaseUntil = leaseUntil.Value
	}
	sandbox.AutoLease = false
	return sandbox, nil
}

func InferenceSandboxSetFolders(owner orc.ResourceOwner, etag util.Option[string], folders []string) (*InferenceSandbox, *util.HttpError) {
	if err := InferenceSandboxLease(owner, integratedSandboxDefaultLeaseDuration); err != nil {
		return nil, err
	}
	sandbox, err := integratedSandboxMutate(InferenceSandboxAppName, owner, etag, func(config *IntegratedSandboxConfig) (bool, *util.HttpError) {
		oldFolders := config.Folders
		config.Folders = integratedSandboxNormalizeFolders(folders)
		return !slices.Equal(oldFolders, config.Folders), nil
	})
	if err != nil {
		return nil, err
	}

	if leaseUntil := InferenceSandboxLeaseUntil(owner); leaseUntil.Present {
		sandbox.LeaseUntil = leaseUntil.Value
	}
	sandbox.AutoLease = false
	return sandbox, nil
}

func InferenceSandboxLease(owner orc.ResourceOwner, duration time.Duration) *util.HttpError {
	return integratedSandboxLease(InferenceSandboxAppName, owner, duration)
}

func InferenceSandboxLeaseUntil(owner orc.ResourceOwner) util.Option[time.Time] {
	return integratedSandboxLeaseUntil(InferenceSandboxAppName, owner)
}

func integratedSandboxMutate(
	appName string,
	owner orc.ResourceOwner,
	etag util.Option[string],
	mutate func(config *IntegratedSandboxConfig) (bool, *util.HttpError),
) (*IntegratedSandbox, *util.HttpError) {
	if owner.CreatedBy == "" {
		return nil, util.UserHttpError("%s requires an explicit owner", integratedSandboxLabel(appName))
	}

	current := controller.IAppRetrieveConfiguration(appName, owner)
	config := IntegratedSandboxConfig{}
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
	if !current.Present {
		didUpdate = true
	}

	needsActivation := current.Present && integratedSandboxNeedsActivation(current.Value)
	if didUpdate || (current.Present && current.Value.IsDetached()) || needsActivation {
		validation := integratedSandboxValidateFolders(owner, config.Folders)
		config.Folders = validation.Folders

		data, err := json.Marshal(config)
		if err != nil {
			return nil, util.HttpErrorFromErr(err)
		}

		if err := controller.IAppConfigure(appName, owner, currentEtag, data); err != nil {
			return nil, err
		}

		updated := controller.IAppRetrieveConfiguration(appName, owner)
		if !updated.Present {
			return nil, util.ServerHttpError("error configuring %s", integratedSandboxLabel(appName))
		}
		sandbox := integratedSandboxFromConfiguration(appName, updated.Value)
		sandbox.Warnings = validation.Warnings
		return sandbox, nil
	} else {
		return integratedSandboxFromConfiguration(appName, current.Value), nil
	}
}

func integratedSandboxNeedsActivation(config controller.IAppRunningConfiguration) bool {
	if config.IsDetached() {
		return true
	}
	job, ok := controller.JobRetrieve(config.JobId)
	return !ok || job.Status.State.IsFinal() || job.Status.State == orc.JobStateSuspended
}

func integratedSandboxFromConfiguration(appName string, config controller.IAppRunningConfiguration) *IntegratedSandbox {
	var parsed IntegratedSandboxConfig
	_ = json.Unmarshal(config.Configuration, &parsed)
	sandbox := &IntegratedSandbox{
		AppName: appName,
		Owner:   config.Owner,
		JobId:   config.JobId,
		ETag:    config.ETag,
		Folders: append([]string{}, integratedSandboxNormalizeFolders(parsed.Folders)...),
	}

	if leaseUntil := integratedSandboxLeaseUntil(appName, config.Owner); leaseUntil.Present {
		sandbox.LeaseUntil = leaseUntil.Value
	}

	return sandbox
}

func integratedSandboxLease(appName string, owner orc.ResourceOwner, duration time.Duration) *util.HttpError {
	if owner.CreatedBy == "" {
		return util.UserHttpError("%s requires an explicit owner", integratedSandboxLabel(appName))
	}

	integratedSandboxLeaseMutex.Lock()
	integratedSandboxLeaseUntilByKey[integratedSandboxLeaseKey(appName, owner)] = time.Now().Add(duration)
	integratedSandboxLeaseMutex.Unlock()
	return nil
}

func integratedSandboxLabel(appName string) string {
	switch appName {
	case InferenceSandboxAppName:
		return inferenceSandboxLabel
	default:
		return integratedTerminalLabel
	}
}

func integratedSandboxLeaseUntil(appName string, owner orc.ResourceOwner) util.Option[time.Time] {
	integratedSandboxLeaseMutex.Lock()
	defer integratedSandboxLeaseMutex.Unlock()

	leaseUntil, ok := integratedSandboxLeaseUntilByKey[integratedSandboxLeaseKey(appName, owner)]
	if !ok {
		return util.OptNone[time.Time]()
	}

	return util.OptValue(leaseUntil)
}

func integratedSandboxLeaseKey(appName string, owner orc.ResourceOwner) string {
	project := ""
	if owner.Project.Present {
		project = owner.Project.Value
	}
	return appName + "|" + owner.CreatedBy + "|" + project
}

func integratedSandboxNormalizeFolders(folders []string) []string {
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

type integratedSandboxValidateFoldersResult struct {
	Warnings []string
	Folders  []string
}

func integratedSandboxValidateFolders(owner orc.ResourceOwner, folders []string) integratedSandboxValidateFoldersResult {
	sb := integratedSandboxValidateFoldersResult{}
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
		cmd.fail(util.ServerHttpError("sandbox is not available"))
		return
	}
	if terminalBackend == nil {
		cmd.fail(util.ServerHttpError("terminal backend is not registered"))
		return
	}
	if sandbox.AutoLease {
		if err := integratedSandboxLease(sandbox.AppName, sandbox.Owner, integratedSandboxDefaultLeaseDuration); err != nil {
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
	ticker := time.NewTicker(integratedSandboxLeaseRefreshInterval)
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
			if err := integratedSandboxLease(cmd.Sandbox.AppName, cmd.Sandbox.Owner, integratedSandboxDefaultLeaseDuration); err != nil {
				log.Warn("Failed to refresh sandbox lease: %s", err)
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
		_ = integratedSandboxLease(cmd.Sandbox.AppName, cmd.Sandbox.Owner, integratedSandboxDefaultLeaseDuration)
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
