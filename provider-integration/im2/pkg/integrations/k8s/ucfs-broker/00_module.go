//go:build linux
// +build linux

package ucfs_broker

import (
	"encoding/binary"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"os/signal"
	"path/filepath"
	"sort"
	"strings"
	"sync"
	"syscall"

	"golang.org/x/sys/unix"
	"ucloud.dk/shared/pkg/log"
)

const (
	autofsMagic           = 0x93
	autofsPtypeMissingInd = 3
	autofsPtypeExpireInd  = 4
	autofsMinProtocol     = 5
	autofsMaxProtocol     = 5
)

var (
	autofsIoctlReady = uintptr((autofsMagic << 8) | 0x60)
	autofsIoctlFail  = uintptr((autofsMagic << 8) | 0x61)
)

func Launch() {
	manifest := loadManifestFromEnv()
	runtime := &brokerRuntime{
		root:       envOrDefault(EnvRoot, DefaultRoot),
		readyFile:  envOrDefault(EnvReadyFile, filepath.Join(envOrDefault(EnvRoot, DefaultRoot), ".broker-ready")),
		workspaces: manifest.Workspaces,
	}

	if err := runtime.start(); err != nil {
		runtime.shutdown()
		log.Fatal("ucfs-broker failed to start: %s", err)
	}
}

type brokerRuntime struct {
	root       string
	readyFile  string
	workspaces []WorkspaceMount
	runs       []*workspaceRuntime
	pgid       int
}

type workspaceRuntime struct {
	rootPath string
	rootFD   *os.File
	pipeR    *os.File
	pipeW    *os.File
	mounts   map[string]MountSpec
	mounted  map[string]struct{}
	lock     sync.Mutex
	stop     chan struct{}
}

func loadManifestFromEnv() Manifest {
	raw := strings.TrimSpace(os.Getenv(EnvManifest))
	if raw == "" {
		return Manifest{}
	}

	var manifest Manifest
	if err := json.Unmarshal([]byte(raw), &manifest); err != nil {
		log.Warn("ucfs-broker received invalid manifest: %s", err)
		return Manifest{}
	}

	return manifest
}

func envOrDefault(name string, fallback string) string {
	if value := strings.TrimSpace(os.Getenv(name)); value != "" {
		return value
	}

	return fallback
}

func (r *brokerRuntime) start() error {
	if err := os.MkdirAll(r.root, 0755); err != nil {
		return fmt.Errorf("create broker root: %w", err)
	}

	if err := os.MkdirAll(filepath.Dir(r.readyFile), 0755); err != nil {
		return fmt.Errorf("prepare ready file directory: %w", err)
	}

	if err := os.Remove(r.readyFile); err != nil && !os.IsNotExist(err) {
		log.Warn("ucfs-broker could not remove stale ready file: %s", err)
	}

	sort.Slice(r.workspaces, func(i, j int) bool {
		return r.workspaces[i].RootPath < r.workspaces[j].RootPath
	})

	//if _, err := unix.Setsid(); err != nil {
	//	return fmt.Errorf("setsid: %w", err)
	//}

	pgid, err := unix.Getpgid(0)
	if err != nil {
		return fmt.Errorf("getpgid: %w", err)
	}

	r.pgid = pgid

	for _, workspace := range r.workspaces {
		runtime, err := r.startWorkspace(workspace)
		if err != nil {
			log.Warn("ucfs-broker failed to prepare workspace %s: %s", workspace.RootPath, err)
			continue
		}
		r.runs = append(r.runs, runtime)
	}

	if err := os.WriteFile(r.readyFile, []byte("ready\n"), 0644); err != nil {
		return fmt.Errorf("write broker ready file: %w", err)
	}

	log.Info("ucfs-broker ready with %d autofs roots", len(r.runs))

	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM, syscall.SIGQUIT)
	defer signal.Stop(sigCh)

	<-sigCh
	r.shutdown()
	return nil
}

func (r *brokerRuntime) startWorkspace(workspace WorkspaceMount) (*workspaceRuntime, error) {
	root := filepath.Clean(workspace.RootPath)
	if root == "." || root == "/" {
		return nil, fmt.Errorf("invalid workspace root %q", workspace.RootPath)
	}

	if err := os.MkdirAll(root, 0755); err != nil {
		return nil, fmt.Errorf("prepare workspace root: %w", err)
	}

	var fds [2]int
	if err := unix.Pipe2(fds[:], unix.O_CLOEXEC|unix.O_DIRECT); err != nil {
		return nil, fmt.Errorf("create autofs pipe: %w", err)
	}

	pipeR := os.NewFile(uintptr(fds[0]), "autofs-read")
	pipeW := os.NewFile(uintptr(fds[1]), "autofs-write")
	if pipeR == nil || pipeW == nil {
		_ = unix.Close(fds[0])
		_ = unix.Close(fds[1])
		return nil, fmt.Errorf("failed to wrap autofs pipe")
	}

	opts := fmt.Sprintf("fd=%d,pgrp=%d,minproto=%d,maxproto=%d", pipeW.Fd(), r.pgid, autofsMinProtocol, autofsMaxProtocol)
	if err := unix.Mount("none", root, "autofs", 0, opts); err != nil {
		_ = pipeR.Close()
		_ = pipeW.Close()
		return nil, fmt.Errorf("mount autofs: %w", err)
	}

	if err := unix.Mount("", root, "", uintptr(unix.MS_SHARED|unix.MS_REC), ""); err != nil {
		return nil, fmt.Errorf("make autofs mount shared: %w", err)
	}

	mounts := make(map[string]MountSpec, len(workspace.Mounts))
	for _, mount := range workspace.Mounts {
		if mount.Name == "" {
			continue
		}
		mounts[mount.Name] = mount
		if err := os.MkdirAll(filepath.Join(root, mount.Name), 0755); err != nil {
			_ = unix.Unmount(root, unix.MNT_DETACH)
			_ = pipeR.Close()
			_ = pipeW.Close()
			return nil, fmt.Errorf("prepare mount trap %s: %w", mount.Name, err)
		}
	}

	rootFD, err := os.Open(root)
	if err != nil {
		_ = unix.Unmount(root, unix.MNT_DETACH)
		_ = pipeR.Close()
		_ = pipeW.Close()
		return nil, fmt.Errorf("open autofs root: %w", err)
	}

	runtime := &workspaceRuntime{
		rootPath: root,
		rootFD:   rootFD,
		pipeR:    pipeR,
		pipeW:    pipeW,
		mounts:   mounts,
		mounted:  map[string]struct{}{},
		stop:     make(chan struct{}),
	}

	_ = pipeW.Close()
	go runtime.loop()
	return runtime, nil
}

func (r *brokerRuntime) shutdown() {
	if err := os.Remove(r.readyFile); err != nil && !os.IsNotExist(err) {
		log.Warn("ucfs-broker could not remove ready file: %s", err)
	}

	for i := len(r.runs) - 1; i >= 0; i-- {
		r.runs[i].shutdown()
	}
}

func (w *workspaceRuntime) shutdown() {
	close(w.stop)
	_ = w.pipeR.Close()
	_ = w.rootFD.Close()

	keys := make([]string, 0, len(w.mounted))
	for key := range w.mounted {
		keys = append(keys, key)
	}
	sort.Slice(keys, func(i, j int) bool { return len(keys[i]) > len(keys[j]) })
	for _, name := range keys {
		_ = unix.Unmount(filepath.Join(w.rootPath, name), unix.MNT_DETACH)
	}
	_ = unix.Unmount(w.rootPath, unix.MNT_DETACH)
}

func (w *workspaceRuntime) loop() {
	for {
		select {
		case <-w.stop:
			return
		default:
		}

		pkt, err := readAutofsPacket(w.pipeR)
		if err != nil {
			if err != io.EOF {
				log.Warn("autofs packet read failed on %s: %s", w.rootPath, err)
			}
			return
		}

		log.Info("New packet!")

		switch pkt.Header.Type {
		case autofsPtypeMissingInd:
			w.handleMissing(pkt)
		case autofsPtypeExpireInd:
			w.handleExpire(pkt)
		default:
			_ = w.fail(pkt.WaitQueueToken)
		}
	}
}

func (w *workspaceRuntime) handleMissing(pkt autofsPacketV5) {
	name := strings.TrimRight(string(pkt.Name[:]), "\x00")
	log.Info("Mounting %v", name)
	if mount, ok := w.mounts[name]; ok {
		if err := w.mountOne(name, mount); err != nil {
			log.Warn("autofs mount %s/%s failed: %s", w.rootPath, name, err)
			_ = w.fail(pkt.WaitQueueToken)
			return
		}
		_ = w.ready(pkt.WaitQueueToken)
		return
	}

	_ = w.fail(pkt.WaitQueueToken)
}

func (w *workspaceRuntime) handleExpire(pkt autofsPacketV5) {
	name := strings.TrimRight(string(pkt.Name[:]), "\x00")
	if err := unix.Unmount(filepath.Join(w.rootPath, name), unix.MNT_DETACH); err != nil {
		_ = w.fail(pkt.WaitQueueToken)
		return
	}

	w.lock.Lock()
	delete(w.mounted, name)
	w.lock.Unlock()
	_ = w.ready(pkt.WaitQueueToken)
}

func (w *workspaceRuntime) mountOne(name string, mount MountSpec) error {
	target := filepath.Join(w.rootPath, name)
	source := filepath.Clean(mount.SourcePath)

	if err := unix.Mount(source, target, "", unix.MS_BIND|unix.MS_REC, ""); err != nil {
		return fmt.Errorf("bind mount: %w", err)
	}

	if mount.ReadOnly {
		if err := unix.Mount("", target, "", unix.MS_BIND|unix.MS_REMOUNT|unix.MS_RDONLY, ""); err != nil {
			_ = unix.Unmount(target, unix.MNT_DETACH)
			return fmt.Errorf("remount read-only: %w", err)
		}
	}

	w.lock.Lock()
	w.mounted[name] = struct{}{}
	w.lock.Unlock()
	return nil
}

func (w *workspaceRuntime) ready(token uint32) error {
	_, _, errno := unix.Syscall(unix.SYS_IOCTL, w.rootFD.Fd(), autofsIoctlReady, uintptr(token))
	if errno != 0 {
		return errno
	}
	return nil
}

func (w *workspaceRuntime) fail(token uint32) error {
	_, _, errno := unix.Syscall(unix.SYS_IOCTL, w.rootFD.Fd(), autofsIoctlFail, uintptr(token))
	if errno != 0 {
		return errno
	}
	return nil
}

type autofsPacketHdr struct {
	ProtoVersion int32
	Type         int32
}

type autofsPacketV5 struct {
	Header         autofsPacketHdr
	WaitQueueToken uint32
	Dev            uint32
	Ino            uint64
	Uid            uint32
	Gid            uint32
	Pid            uint32
	Tgid           uint32
	Len            uint32
	Name           [256]byte
}

func readAutofsPacket(r io.Reader) (autofsPacketV5, error) {
	var pkt autofsPacketV5
	if err := binary.Read(r, binary.LittleEndian, &pkt); err != nil {
		return pkt, err
	}
	return pkt, nil
}
