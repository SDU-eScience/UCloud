package launcher

import (
	"strings"
)

type LFile interface {
	GetAbsolutePath() string
	Exists() bool
	Child(subPath string, isDir bool) LFile
	WriteText(text string)
	WriteBytes(bytes []byte)
	AppendText(text string)
	Delete()
	MkDirs()
	Name() string
}

type ExecutableCommandInterface interface {
	ToBashScript() string
	ExecuteToText() StringPair
	SetStreamOutput()
	SetAllowFailure()
	SetDeadline(DeadlineInMillis int64)
}

func NewFile(path string) LFile {
	if environmentIsRemote {
		conn := GetSSHConnection()
		return RemoteFile{
			connection: conn,
			path:       path,
		}
	} else {
		return LocalFile{
			path: path,
		}
	}
}

func NewExecutableCommand(
	args []string,
	workingDir LFile,
	fn postProcessor,
) ExecutableCommandInterface {
	if environmentIsRemote {
		conn := GetSSHConnection()
		return NewRemoteExecutableCommand(
			conn,
			args,
			workingDir,
			fn,
		)
	} else {
		return NewLocalExecutableCommand(
			args,
			workingDir,
			fn,
		)
	}
}

type postProcessor func(text ProcessResultText) string

func PostProcessorFunc(text ProcessResultText) string {
	return text.stdout
}

type ProcessResultText struct {
	statusCode int
	stdout     string
	stderr     string
}

// NOTE(Dan): This is slightly different from how escapeBash works in the Kotlin version since this also automatically
// wraps it in single quotes. This is how it was used in all cases anyway, so this makes it slightly simpler to use.

func EscapeBash(s string) string {
	builder := &strings.Builder{}
	builder.WriteRune('\'')
	for _, c := range []rune(s) {
		if c == '\'' {
			builder.WriteString("'\"'\"'")
		} else {
			builder.WriteRune(c)
		}
	}
	builder.WriteRune('\'')
	return builder.String()
}
