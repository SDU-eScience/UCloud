package k8s

import (
	"errors"
	"io"
	"io/fs"
	"path"
	"strings"

	"github.com/charmbracelet/ssh"
	sftp "github.com/pkg/sftp"
	"golang.org/x/sys/unix"
	"ucloud.dk/pkg/integrations/k8s/filesystem"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

type terminalSftpRoot struct {
	rootUCloud   string
	rootInternal string
}

type terminalSftpLister struct{ root *terminalSftpRoot }
type terminalSftpReader struct{ root *terminalSftpRoot }
type terminalSftpWriter struct{ root *terminalSftpRoot }
type terminalSftpCmder struct{ root *terminalSftpRoot }

type terminalSftpListAt struct{ entries []fs.FileInfo }

func (l *terminalSftpListAt) ListAt(dst []fs.FileInfo, offset int64) (int, error) {
	if offset < 0 || offset >= int64(len(l.entries)) {
		return 0, io.EOF
	}
	off := int(offset)
	n := copy(dst, l.entries[off:])
	if off+n >= len(l.entries) {
		return n, io.EOF
	}
	return n, nil
}

// TODO(Dan): NOT PRODUCTION READY
// TODO(Dan): NOT PRODUCTION READY
// TODO(Dan): NOT PRODUCTION READY
// TODO(Dan): NOT PRODUCTION READY
// TODO(Dan): NOT PRODUCTION READY

func handleSshTerminalSftpSession(sess ssh.Session, owner orc.ResourceOwner) *util.HttpError {
	if !util.DevelopmentModeEnabled() {
		return util.UserHttpError("Not yet implemented")
	}
	if sess.Subsystem() != "sftp" {
		return util.UserHttpError("Only sftp subsystem sessions are supported")
	}

	root := &terminalSftpRoot{}
	{
		success := false
		internalPath, drive, err := filesystem.InitializeMemberFiles(owner.CreatedBy, owner.Project)
		if err == nil {
			ucloudPath, ok := filesystem.InternalToUCloudWithDrive(drive, internalPath)
			if ok {
				root.rootInternal = internalPath
				root.rootUCloud = ucloudPath
				success = true
			}
		}

		if !success {
			return util.UserHttpError("No active folder")
		}
	}

	server := sftp.NewRequestServer(sess, sftp.Handlers{
		FileGet:  &terminalSftpReader{root: root},
		FilePut:  &terminalSftpWriter{root: root},
		FileCmd:  &terminalSftpCmder{root: root},
		FileList: &terminalSftpLister{root: root},
	}, sftp.WithStartDirectory("/"))
	defer func() { _ = server.Close() }()

	if err := server.Serve(); err != nil {
		if errors.Is(err, io.EOF) {
			return nil
		}
		return util.HttpErrorFromErr(err)
	}

	return nil
}

func (r *terminalSftpRoot) resolve(requestPath string) (string, bool) {
	p := path.Clean("/" + requestPath)
	if p == "/" {
		return r.rootInternal, true
	}

	resolved := path.Join(r.rootUCloud, strings.TrimPrefix(p, "/"))
	internals, ok, _ := filesystem.UCloudToInternal(resolved)
	return internals, ok
}

func (l *terminalSftpLister) Filelist(req *sftp.Request) (sftp.ListerAt, error) {
	internal, ok := l.root.resolve(req.Filepath)
	if !ok {
		return nil, util.UserHttpError("Could not find directory").AsError()
	}

	info, herr := filesystem.StatInternal(internal)
	if herr != nil {
		return nil, herr.AsError()
	}

	if req.Method == "Stat" || !info.IsDir() {
		return &terminalSftpListAt{entries: []fs.FileInfo{info}}, nil
	}

	entries, herr := filesystem.ReadDirInternal(internal)
	if herr != nil {
		return nil, herr.AsError()
	}

	return &terminalSftpListAt{entries: entries}, nil
}

func (l *terminalSftpLister) Readlink(string) (string, error) {
	return "", util.UserHttpError("symlinks are not supported").AsError()
}

func (l *terminalSftpLister) RealPath(p string) (string, error) {
	return path.Clean("/" + p), nil
}

func (r *terminalSftpReader) Fileread(req *sftp.Request) (io.ReaderAt, error) {
	internal, ok := r.root.resolve(req.Filepath)
	if !ok {
		return nil, util.UserHttpError("Could not find file").AsError()
	}

	file, ok := filesystem.OpenFile(internal, unix.O_RDONLY, 0)
	if !ok {
		return nil, util.UserHttpError("Could not find file").AsError()
	}

	return file, nil
}

func openFlagsFromRequest(req *sftp.Request) int {
	flags := unix.O_RDONLY
	if req.Pflags().Read && req.Pflags().Write {
		flags = unix.O_RDWR
	} else if req.Pflags().Write {
		flags = unix.O_WRONLY
	} else {
		flags = unix.O_RDONLY
	}

	if req.Pflags().Append {
		flags |= unix.O_APPEND
	}
	if req.Pflags().Creat {
		flags |= unix.O_CREAT
	}
	if req.Pflags().Trunc {
		flags |= unix.O_TRUNC
	}
	if req.Pflags().Excl {
		flags |= unix.O_EXCL
	}

	return flags
}

func (w *terminalSftpWriter) Filewrite(req *sftp.Request) (io.WriterAt, error) {
	internal, ok := w.root.resolve(req.Filepath)
	if !ok {
		return nil, util.UserHttpError("Could not find file").AsError()
	}

	perm := uint32(0660)
	if req.AttrFlags().Permissions {
		perm = uint32(req.Attributes().FileMode().Perm())
	}

	file, ok := filesystem.OpenFile(internal, openFlagsFromRequest(req), perm)
	if !ok {
		return nil, util.UserHttpError("Could not open file").AsError()
	}

	return file, nil
}

func (w *terminalSftpWriter) OpenFile(req *sftp.Request) (sftp.WriterAtReaderAt, error) {
	internal, ok := w.root.resolve(req.Filepath)
	if !ok {
		return nil, util.UserHttpError("Could not find file").AsError()
	}

	file, ok := filesystem.OpenFile(internal, openFlagsFromRequest(req), 0660)
	if !ok {
		return nil, util.UserHttpError("Could not open file").AsError()
	}

	return file, nil
}

func (c *terminalSftpCmder) Filecmd(req *sftp.Request) error {
	switch req.Method {
	case "Mkdir":
		internal, ok := c.root.resolve(req.Filepath)
		if !ok {
			return util.UserHttpError("Could not create folder").AsError()
		}
		return filesystem.DoCreateFolder(internal).AsError()

	case "Rmdir", "Remove":
		internal, ok := c.root.resolve(req.Filepath)
		if !ok {
			return util.UserHttpError("Could not remove file").AsError()
		}
		return filesystem.RemoveInternal(internal).AsError()

	case "Rename":
		oldInternal, ok := c.root.resolve(req.Filepath)
		if !ok {
			return util.UserHttpError("Could not rename file").AsError()
		}
		newInternal, ok := c.root.resolve(req.Target)
		if !ok {
			return util.UserHttpError("Could not rename file").AsError()
		}
		return filesystem.RenameInternal(oldInternal, newInternal).AsError()

	case "PosixRename":
		oldInternal, ok := c.root.resolve(req.Filepath)
		if !ok {
			return util.UserHttpError("Could not rename file").AsError()
		}
		newInternal, ok := c.root.resolve(req.Target)
		if !ok {
			return util.UserHttpError("Could not rename file").AsError()
		}
		return filesystem.RenameInternal(oldInternal, newInternal).AsError()

	case "Setstat":
		internal, ok := c.root.resolve(req.Filepath)
		if !ok {
			return util.UserHttpError("Could not find file").AsError()
		}
		attrs := req.Attributes()
		if req.AttrFlags().Permissions {
			if err := filesystem.ChmodInternal(internal, attrs.FileMode()); err != nil {
				return err.AsError()
			}
		}
		if req.AttrFlags().Acmodtime {
			if err := filesystem.ChtimesInternal(internal, attrs.AccessTime(), attrs.ModTime()); err != nil {
				return err.AsError()
			}
		}
		return nil

	case "Link", "Symlink":
		return util.UserHttpError("symlinks are not supported").AsError()
	}

	return nil
}

func (c *terminalSftpCmder) PosixRename(req *sftp.Request) error {
	return c.Filecmd(req)
}
