package upload

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"io"
	"math"
	"math/rand"
	"net/http"
	"net/http/httptest"
	"reflect"
	"sort"
	"strings"
	"sync"
	"testing"
	"time"

	ws "github.com/gorilla/websocket"
	fnd "ucloud.dk/shared/pkg/foundation"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

type clientFaults struct {
	listDirFail   map[string]bool
	openChildFail map[string]bool
	readErrAfter  map[string]int
}

type memClientNode struct {
	name      string
	isDir     bool
	data      []byte
	modified  time.Time
	children  map[string]*memClientNode
	readErrAt int
}

type memClientFile struct {
	node   *memClientNode
	path   string
	faults clientFaults
	offset int
}

func (m *memClientFile) ListChildren(_ context.Context) []string {
	if m.faults.listDirFail != nil && m.faults.listDirFail[m.path] {
		return nil
	}

	names := make([]string, 0, len(m.node.children))
	for name := range m.node.children {
		names = append(names, name)
	}
	sort.Strings(names)
	return names
}

func (m *memClientFile) OpenChild(_ context.Context, name string) (FileMetadata, ClientFile) {
	child, ok := m.node.children[name]
	if !ok {
		return FileMetadata{}, nil
	}

	childPath := name
	if m.path != "" {
		childPath = m.path + "/" + name
	}

	if m.faults.openChildFail != nil && m.faults.openChildFail[childPath] {
		return FileMetadata{}, nil
	}

	meta := FileMetadata{
		Size:         int64(len(child.data)),
		ModifiedAt:   fnd.Timestamp(child.modified),
		InternalPath: childPath,
		Type:         FileTypeFile,
	}
	if child.isDir {
		meta.Type = FileTypeDirectory
	}

	if m.faults.readErrAfter != nil {
		if v, ok := m.faults.readErrAfter[childPath]; ok {
			child.readErrAt = v
		}
	}

	return meta, &memClientFile{node: child, path: childPath, faults: m.faults}
}

func (m *memClientFile) Read(_ context.Context, target []byte) (int, bool, error) {
	if m.node.isDir {
		return 0, true, io.EOF
	}

	if m.node.readErrAt >= 0 && m.offset >= m.node.readErrAt {
		return 0, true, errors.New("injected read error")
	}

	if m.offset >= len(m.node.data) {
		return 0, true, io.EOF
	}

	if m.node.readErrAt >= 0 {
		remainingUntilFailure := m.node.readErrAt - m.offset
		if remainingUntilFailure > 0 && remainingUntilFailure < len(target) {
			target = target[:remainingUntilFailure]
		}
	}

	n := copy(target, m.node.data[m.offset:])
	m.offset += n
	return n, false, nil
}

func (m *memClientFile) Close() {}

func newClientTree(files map[string][]byte, modified time.Time, faults clientFaults) (ClientFile, FileMetadata) {
	root := &memClientNode{
		name:      "",
		isDir:     true,
		modified:  modified,
		children:  make(map[string]*memClientNode),
		readErrAt: -1,
	}

	for p, content := range files {
		clean := strings.TrimPrefix(strings.TrimSpace(p), "/")
		if clean == "" {
			continue
		}

		parts := strings.Split(clean, "/")
		cur := root
		for i := 0; i < len(parts)-1; i++ {
			part := parts[i]
			child := cur.children[part]
			if child == nil {
				child = &memClientNode{
					name:      part,
					isDir:     true,
					modified:  modified,
					children:  make(map[string]*memClientNode),
					readErrAt: -1,
				}
				cur.children[part] = child
			}
			cur = child
		}

		name := parts[len(parts)-1]
		cur.children[name] = &memClientNode{
			name:      name,
			isDir:     false,
			data:      bytes.Clone(content),
			modified:  modified,
			children:  nil,
			readErrAt: -1,
		}
	}

	return &memClientFile{node: root, path: "", faults: faults}, FileMetadata{
		Size:         0,
		ModifiedAt:   fnd.Timestamp(modified),
		InternalPath: "",
		Type:         FileTypeDirectory,
	}
}

type serverFaults struct {
	skipUnchanged  bool
	failOpen       map[string]bool
	failWriteAfter map[string]int
	writeDelay     time.Duration
	jitter         bool
}

type memServerFS struct {
	mu              sync.Mutex
	randMu          sync.Mutex
	files           map[string][]byte
	modified        map[string]time.Time
	openCalls       map[string]int
	sessionClosedOk int
	sessionClosedNo int
	faults          serverFaults
	rand            *rand.Rand
}

func (m *memServerFS) jitterDelay() time.Duration {
	if !m.faults.jitter {
		return 0
	}

	m.randMu.Lock()
	defer m.randMu.Unlock()
	return time.Duration(m.rand.Intn(3)) * time.Millisecond
}

func newMemServerFS(f serverFaults) *memServerFS {
	return &memServerFS{
		files:     make(map[string][]byte),
		modified:  make(map[string]time.Time),
		openCalls: make(map[string]int),
		faults:    f,
		rand:      rand.New(rand.NewSource(7)),
	}
}

func (m *memServerFS) OpenFileIfNeeded(_ ServerSession, fileMeta FileMetadata) ServerFile {
	m.mu.Lock()
	m.openCalls[fileMeta.InternalPath]++
	if m.faults.failOpen != nil && m.faults.failOpen[fileMeta.InternalPath] {
		m.mu.Unlock()
		return nil
	}

	if m.faults.skipUnchanged {
		existing, ok := m.files[fileMeta.InternalPath]
		if ok {
			mod := m.modified[fileMeta.InternalPath]
			if len(existing) == int(fileMeta.Size) && math.Abs(mod.Sub(fileMeta.ModifiedAt.Time()).Minutes()) < 1 {
				m.mu.Unlock()
				return nil
			}
		}
	}

	failAt := -1
	if m.faults.failWriteAfter != nil {
		if v, ok := m.faults.failWriteAfter[fileMeta.InternalPath]; ok {
			failAt = v
		}
	}
	m.mu.Unlock()

	return &memServerFile{
		parent:   m,
		path:     fileMeta.InternalPath,
		modified: fileMeta.ModifiedAt.Time(),
		failAt:   failAt,
	}
}

func (m *memServerFS) OnSessionClose(_ ServerSession, success bool) {
	m.mu.Lock()
	defer m.mu.Unlock()
	if success {
		m.sessionClosedOk++
	} else {
		m.sessionClosedNo++
	}
}

func (m *memServerFS) snapshotFiles() map[string][]byte {
	m.mu.Lock()
	defer m.mu.Unlock()

	result := make(map[string][]byte, len(m.files))
	for k, v := range m.files {
		result[k] = bytes.Clone(v)
	}
	return result
}

type memServerFile struct {
	parent   *memServerFS
	path     string
	modified time.Time
	failAt   int
	buf      []byte
}

func (m *memServerFile) Write(ctx context.Context, data []byte) error {
	if m.parent.faults.writeDelay > 0 {
		delay := m.parent.faults.writeDelay
		if m.parent.faults.jitter {
			delta := m.parent.jitterDelay()
			delay += delta
		}

		t := time.NewTimer(delay)
		defer t.Stop()
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-t.C:
		}
	}

	if m.failAt >= 0 {
		if len(m.buf) >= m.failAt {
			return errors.New("injected write error")
		}

		if len(m.buf)+len(data) > m.failAt {
			allowed := m.failAt - len(m.buf)
			if allowed > 0 {
				m.buf = append(m.buf, data[:allowed]...)
			}
			return errors.New("injected write error")
		}
	}

	m.buf = append(m.buf, data...)
	return nil
}

func (m *memServerFile) Close() {
	m.parent.mu.Lock()
	defer m.parent.mu.Unlock()
	m.parent.files[m.path] = bytes.Clone(m.buf)
	m.parent.modified[m.path] = m.modified
}

func newUploadWsServer(t *testing.T, fs ServerFileSystem) (string, func()) {
	t.Helper()

	upgrader := ws.Upgrader{CheckOrigin: func(r *http.Request) bool { return true }}
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		conn, err := upgrader.Upgrade(w, r, nil)
		if err != nil {
			return
		}

		session := ServerSession{
			Id:             "test-session",
			ConflictPolicy: orc.WriteConflictPolicyMergeRename,
			Path:           "/",
			UserData:       "",
		}

		_ = ProcessServer(conn, fs, session)
	}))

	endpoint := "ws" + strings.TrimPrefix(server.URL, "http")
	return endpoint, server.Close
}

func runClientUpload(
	t *testing.T,
	ctx context.Context,
	endpoint string,
	root ClientFile,
	meta FileMetadata,
) StatusReport {
	t.Helper()

	session := ClientSession{
		Endpoint:       endpoint,
		ConflictPolicy: orc.WriteConflictPolicyMergeRename,
	}

	return ProcessClient(ctx, session, root, meta, nil)
}

func requireEventually(t *testing.T, timeout time.Duration, condition func() bool) {
	t.Helper()

	deadline := time.Now().Add(timeout)
	for time.Now().Before(deadline) {
		if condition() {
			return
		}
		time.Sleep(10 * time.Millisecond)
	}
	t.Fatalf("condition not met before timeout")
}

func TestMessageRoundTripAllTypes(t *testing.T) {
	tests := []struct {
		name string
		t    messageType
		msg  message
	}{
		{name: "ok", t: messageTypeOk, msg: &messageOk{FileId: 7}},
		{name: "chunk", t: messageTypeChunk, msg: &messageChunk{FileId: 11, Data: []byte("payload")}},
		{name: "skip", t: messageTypeSkip, msg: &messageSkip{FileId: 13}},
		{name: "listing", t: messageTypeListing, msg: &messageListing{FileId: 17, Size: 44, ModifiedAt: 1234, Path: "x/y.txt"}},
		{name: "completed", t: messageTypeCompleted, msg: &messageCompleted{NumberOfProcessedFiles: 33}},
		{name: "close", t: messageTypeClose, msg: &messageClose{}},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			buf := util.NewBuffer(&bytes.Buffer{})
			tc.msg.Marshal(buf, true)

			input := util.NewBuffer(bytes.NewBuffer(buf.ReadRemainingBytes()))
			opcode := messageType(input.ReadU8())
			got := parseMessage(opcode, input)

			if got == nil {
				t.Fatalf("expected parsed message, got nil")
			}
			if opcode != tc.t {
				t.Fatalf("opcode mismatch: got %v want %v", opcode, tc.t)
			}
			if !reflect.DeepEqual(got, tc.msg) {
				t.Fatalf("message mismatch: got %#v want %#v", got, tc.msg)
			}
		})
	}
}

func TestUploadSingleFileSuccess(t *testing.T) {
	content := bytes.Repeat([]byte("a"), 8192)
	root, meta := newClientTree(map[string][]byte{"a.txt": content}, time.Now().UTC(), clientFaults{})

	fs := newMemServerFS(serverFaults{})
	endpoint, closeServer := newUploadWsServer(t, fs)
	defer closeServer()

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	report := runClientUpload(t, ctx, endpoint, root, meta)
	if !report.NormalExit {
		t.Fatalf("expected normal exit")
	}

	got := fs.snapshotFiles()
	if !bytes.Equal(got["a.txt"], content) {
		t.Fatalf("uploaded content mismatch")
	}
}

func TestUploadNestedTreeAllFilesTransferred(t *testing.T) {
	files := map[string][]byte{
		"a.txt":                    []byte("alpha"),
		"x/y/z.txt":                []byte("bravo"),
		"x/y/other.bin":            bytes.Repeat([]byte{0xAB}, 1024),
		"x/more/deep/file.log":     []byte("log"),
		"x/more/deep/another.data": bytes.Repeat([]byte("x"), 99),
	}
	root, meta := newClientTree(files, time.Now().UTC(), clientFaults{})

	fs := newMemServerFS(serverFaults{})
	endpoint, closeServer := newUploadWsServer(t, fs)
	defer closeServer()

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	report := runClientUpload(t, ctx, endpoint, root, meta)
	if !report.NormalExit {
		t.Fatalf("expected normal exit")
	}

	got := fs.snapshotFiles()
	if len(got) != len(files) {
		t.Fatalf("file count mismatch: got %d want %d", len(got), len(files))
	}
	for path, expected := range files {
		if !bytes.Equal(got[path], expected) {
			t.Fatalf("data mismatch for %s", path)
		}
	}
}

func TestUploadZeroByteFileCreated(t *testing.T) {
	files := map[string][]byte{"zero.dat": {}}
	root, meta := newClientTree(files, time.Now().UTC(), clientFaults{})

	fs := newMemServerFS(serverFaults{})
	endpoint, closeServer := newUploadWsServer(t, fs)
	defer closeServer()

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	report := runClientUpload(t, ctx, endpoint, root, meta)
	if !report.NormalExit {
		t.Fatalf("expected normal exit")
	}

	got := fs.snapshotFiles()
	data, ok := got["zero.dat"]
	if !ok {
		t.Fatalf("expected zero-byte file to exist")
	}
	if len(data) != 0 {
		t.Fatalf("expected empty file, got %d bytes", len(data))
	}
}

func TestUploadSkipUnchangedFile(t *testing.T) {
	modified := time.Now().UTC().Truncate(time.Second)
	data := []byte("unchanged")
	root, meta := newClientTree(map[string][]byte{"same.txt": data}, modified, clientFaults{})

	fs := newMemServerFS(serverFaults{skipUnchanged: true})
	fs.files["same.txt"] = bytes.Clone(data)
	fs.modified["same.txt"] = modified

	endpoint, closeServer := newUploadWsServer(t, fs)
	defer closeServer()

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	report := runClientUpload(t, ctx, endpoint, root, meta)
	if !report.NormalExit {
		t.Fatalf("expected normal exit")
	}
	if report.NewFilesUploaded != 0 {
		t.Fatalf("expected no new files uploaded, got %d", report.NewFilesUploaded)
	}
}

func TestCloseSessionFromClientSendsClose(t *testing.T) {
	upgrader := ws.Upgrader{CheckOrigin: func(r *http.Request) bool { return true }}
	gotClose := false
	var mu sync.Mutex

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		conn, err := upgrader.Upgrade(w, r, nil)
		if err != nil {
			return
		}
		defer conn.Close()

		_, data, err := conn.ReadMessage()
		if err != nil {
			return
		}

		buf := util.NewBuffer(bytes.NewBuffer(data))
		t := messageType(buf.ReadU8())
		if parseMessage(t, buf) != nil && t == messageTypeClose {
			mu.Lock()
			gotClose = true
			mu.Unlock()
		}
	}))
	defer server.Close()

	endpoint := "ws" + strings.TrimPrefix(server.URL, "http")
	closeServer := func() {}
	defer closeServer()

	success := CloseSessionFromClient(ClientSession{Endpoint: endpoint})
	if !success {
		t.Fatalf("expected close session to succeed")
	}

	requireEventually(t, 2*time.Second, func() bool {
		mu.Lock()
		defer mu.Unlock()
		return gotClose
	})
}

func TestUploadContextCancelMidTransfer(t *testing.T) {
	big := bytes.Repeat([]byte("z"), 1024*1024*64)
	root, meta := newClientTree(map[string][]byte{"big.bin": big}, time.Now().UTC(), clientFaults{})

	fs := newMemServerFS(serverFaults{writeDelay: 10 * time.Millisecond})
	endpoint, closeServer := newUploadWsServer(t, fs)
	defer closeServer()

	ctx, cancel := context.WithCancel(context.Background())
	go func() {
		time.Sleep(50 * time.Millisecond)
		cancel()
	}()

	report := runClientUpload(t, ctx, endpoint, root, meta)
	if report.NormalExit {
		t.Fatalf("expected non-normal exit after cancellation")
	}

	if !report.WasCancelledByUser && report.BytesTransferred >= int64(len(big)) {
		t.Fatalf("expected transfer interruption after cancellation")
	}
}

func TestUploadServerDisconnectMidTransfer(t *testing.T) {
	upgrader := ws.Upgrader{CheckOrigin: func(r *http.Request) bool { return true }}
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		conn, err := upgrader.Upgrade(w, r, nil)
		if err != nil {
			return
		}
		_ = conn.Close()
	}))
	defer server.Close()

	endpoint := "ws" + strings.TrimPrefix(server.URL, "http")
	root, meta := newClientTree(map[string][]byte{"x.bin": bytes.Repeat([]byte("x"), 1024*1024)}, time.Now().UTC(), clientFaults{})

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	report := runClientUpload(t, ctx, endpoint, root, meta)
	if report.NormalExit {
		t.Fatalf("expected abnormal exit on disconnect")
	}
}

func TestUploadClientReadErrorSendsSkip(t *testing.T) {
	files := map[string][]byte{
		"ok.txt":  bytes.Repeat([]byte("o"), 4096),
		"bad.txt": bytes.Repeat([]byte("b"), 4096),
	}
	faults := clientFaults{readErrAfter: map[string]int{"bad.txt": 128}}
	root, meta := newClientTree(files, time.Now().UTC(), faults)

	fs := newMemServerFS(serverFaults{})
	endpoint, closeServer := newUploadWsServer(t, fs)
	defer closeServer()

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	report := runClientUpload(t, ctx, endpoint, root, meta)
	if !report.NormalExit {
		t.Fatalf("expected normal exit")
	}

	got := fs.snapshotFiles()
	if !bytes.Equal(got["ok.txt"], files["ok.txt"]) {
		t.Fatalf("expected ok.txt to transfer")
	}
	if len(got["bad.txt"]) >= len(files["bad.txt"]) {
		t.Fatalf("expected bad.txt to be partial due to read error")
	}
}

func TestUploadDiscoveryOpenChildFailureDoesNotDropOthers(t *testing.T) {
	files := map[string][]byte{
		"batch/f1.txt": []byte("1"),
		"batch/f2.txt": []byte("2"),
		"batch/f3.txt": []byte("3"),
		"batch/f4.txt": []byte("4"),
	}
	faults := clientFaults{openChildFail: map[string]bool{"batch/f2.txt": true, "batch/f4.txt": true}}
	root, meta := newClientTree(files, time.Now().UTC(), faults)

	fs := newMemServerFS(serverFaults{})
	endpoint, closeServer := newUploadWsServer(t, fs)
	defer closeServer()

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	report := runClientUpload(t, ctx, endpoint, root, meta)
	if !report.NormalExit {
		t.Fatalf("expected normal exit")
	}

	got := fs.snapshotFiles()
	if _, ok := got["batch/f1.txt"]; !ok {
		t.Fatalf("expected batch/f1.txt to transfer")
	}
	if _, ok := got["batch/f3.txt"]; !ok {
		t.Fatalf("expected batch/f3.txt to transfer")
	}
	if _, ok := got["batch/f2.txt"]; ok {
		t.Fatalf("did not expect failed child batch/f2.txt")
	}
	if _, ok := got["batch/f4.txt"]; ok {
		t.Fatalf("did not expect failed child batch/f4.txt")
	}
}

func TestUploadLargeDirectoryDiscoveryNotTruncated(t *testing.T) {
	files := make(map[string][]byte, 2200)
	for i := 0; i < 2200; i++ {
		files[fmt.Sprintf("many/f-%04d.txt", i)] = []byte("ok")
	}
	root, meta := newClientTree(files, time.Now().UTC(), clientFaults{})

	fs := newMemServerFS(serverFaults{})
	endpoint, closeServer := newUploadWsServer(t, fs)
	defer closeServer()

	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	report := runClientUpload(t, ctx, endpoint, root, meta)
	if !report.NormalExit {
		t.Fatalf("expected normal exit")
	}
	if report.TotalFilesProcessed != int64(len(files)) {
		t.Fatalf("processed file mismatch: got %d want %d", report.TotalFilesProcessed, len(files))
	}

	got := fs.snapshotFiles()
	if len(got) != len(files) {
		t.Fatalf("uploaded file mismatch: got %d want %d", len(got), len(files))
	}
}

func TestUploadStressDiscoveryToWorkerDelivery(t *testing.T) {
	if testing.Short() {
		t.Skip("stress test skipped in short mode")
	}

	for iter := 0; iter < 8; iter++ {
		iter := iter
		t.Run(fmt.Sprintf("iter-%d", iter), func(t *testing.T) {
			rng := rand.New(rand.NewSource(int64(100 + iter)))
			files := make(map[string][]byte, 450)
			for i := 0; i < 450; i++ {
				sz := 32 + rng.Intn(2048)
				payload := make([]byte, sz)
				for j := 0; j < sz; j++ {
					payload[j] = byte(rng.Intn(256))
				}
				files[fmt.Sprintf("d%02d/f%03d.bin", i%11, i)] = payload
			}

			root, meta := newClientTree(files, time.Now().UTC(), clientFaults{})
			fs := newMemServerFS(serverFaults{writeDelay: 1 * time.Millisecond, jitter: true})
			endpoint, closeServer := newUploadWsServer(t, fs)
			defer closeServer()

			ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
			defer cancel()

			report := runClientUpload(t, ctx, endpoint, root, meta)
			if !report.NormalExit {
				t.Fatalf("expected normal exit")
			}

			got := fs.snapshotFiles()
			if len(got) != len(files) {
				t.Fatalf("file count mismatch: got %d want %d", len(got), len(files))
			}
			for path, expected := range files {
				if !bytes.Equal(got[path], expected) {
					t.Fatalf("payload mismatch for %s", path)
				}
			}
		})
	}
}
