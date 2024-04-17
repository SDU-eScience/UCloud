package log

import (
	"compress/gzip"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"sync"
	"time"
)

const (
	RotateHourly  = 1
	RotateDaily   = 2
	RotateWeekly  = 3
	RotateMonthly = 4
)

const (
	LevelError = 1
	LevelWarn  = 2
	LevelInfo  = 3
	LevelDebug = 4
)

type Logger struct {
	Level          int
	DateFormat     string
	Console        bool
	LogFile        string
	LogPath        string
	RotatePeriod   int
	RotateCompress bool
	RotateRetain   int
	file           *os.File
	writer         io.Writer
	mu             sync.Mutex
}

func NewLogger(level int, console bool) *Logger {
	var w io.Writer
	if console {
		w = os.Stdout
	}

	return &Logger{
		Level:      level,
		Console:    console,
		DateFormat: "2006-01-02T15:04:05.000",
		writer:     w,
	}
}

func (log *Logger) SetLogFile(path string) error {
	log.mu.Lock()
	defer log.mu.Unlock()

	log.LogFile = filepath.Base(path)
	log.LogPath = filepath.Dir(path)

	return log.open()
}

func (log *Logger) SetRotation(period, retain int, compress bool) {
	if log.RotatePeriod > 0 {
		return
	}

	log.RotatePeriod = period
	log.RotateCompress = compress
	log.RotateRetain = retain

	go log.rotate()
}

func (log *Logger) SetLevel(level int) {
	log.Level = level
}

func (log *Logger) Error(format string, args ...any) {
	if LevelError > log.Level {
		return
	}
	log.write(LevelError, format, args...)
}

func (log *Logger) Warn(format string, args ...any) {
	if LevelWarn > log.Level {
		return
	}
	log.write(LevelWarn, format, args...)
}

func (log *Logger) Info(format string, args ...any) {
	if LevelInfo > log.Level {
		return
	}
	log.write(LevelInfo, format, args...)
}

func (log *Logger) Debug(format string, args ...any) {
	if LevelDebug > log.Level {
		return
	}
	log.write(LevelDebug, format, args...)
}

func (log *Logger) open() error {
	fn := log.LogPath + "/" + log.LogFile
	perms := os.O_WRONLY | os.O_APPEND | os.O_CREATE

	f, err := os.OpenFile(fn, perms, 0o640)
	if err != nil {
		return err
	}

	if log.Console {
		log.writer = io.MultiWriter(os.Stdout, f)
	} else {
		log.writer = f
	}

	log.file = f
	return nil
}

func (log *Logger) write(level int, format string, args ...any) {
	var lvlstr string

	if log.writer == nil {
		return
	}

	switch level {
	case LevelError:
		lvlstr = "ERROR"
	case LevelWarn:
		lvlstr = "WARN"
	case LevelInfo:
		lvlstr = "INFO"
	case LevelDebug:
		lvlstr = "DEBUG"
	}

	log.mu.Lock()
	defer log.mu.Unlock()

	dt := time.Now() // possibility of using UTC here?
	f := fmt.Sprintf(format, args...)
	f = fmt.Sprintf("%s %-5s %s\n", dt.Format(log.DateFormat), lvlstr, strings.TrimSpace(f))
	log.writer.Write([]byte(f))
}

func (log *Logger) rotate() {
	var ts time.Time
	now := time.Now().UTC()

	switch log.RotatePeriod {
	case RotateHourly:
		ts = now.Truncate(time.Hour).Add(time.Hour)
	case RotateDaily:
		ts = now.Truncate(24 * time.Hour).Add(24 * time.Hour)
	case RotateWeekly:
		ts = now.Truncate(24 * time.Hour)
		ts = ts.AddDate(0, 0, int(7-now.Weekday()))
	case RotateMonthly:
		ts = time.Date(now.Year(), now.Month(), 1, 0, 0, 0, 0, time.UTC).AddDate(0, 1, 0)
	default:
		return
	}

	ts = ts.Add(5 * time.Second)
	time.Sleep(time.Until(ts))
	go log.rotate()

	// No open file
	if log.file == nil {
		return
	}

	// Rotate file
	log.mu.Lock()
	log.file.Close()
	log.file = nil

	oldfn := fmt.Sprintf("%s/%s", log.LogPath, log.LogFile)
	newfn := fmt.Sprintf("%s-%d", oldfn, time.Now().Unix())

	err := os.Rename(oldfn, newfn)
	if err != nil {
		return
	}

	log.open()
	log.mu.Unlock()

	// Compress
	if log.RotateCompress {
		oldfn = newfn
		newfn = newfn + ".gz"

		f, err := os.Open(oldfn)
		if err != nil {
			return
		}

		data, err := io.ReadAll(f)
		if err != nil {
			return
		}
		f.Close()

		f, err = os.Create(newfn)
		if err != nil {
			return
		}

		w := gzip.NewWriter(f)
		w.Write(data)
		w.Close()

		f.Close()
		os.Remove(oldfn)
	}

	// Remove old logs
	files, err := os.ReadDir(log.LogPath)
	if err != nil {
		return
	}

	logfiles := []string{}
	prefix := fmt.Sprintf("%s-", log.LogFile)
	for _, f := range files {
		if strings.HasPrefix(f.Name(), prefix) {
			logfiles = append(logfiles, f.Name())
		}
	}

	if n := len(logfiles); n > log.RotateRetain {
		n = n - log.RotateRetain
		sort.Strings(logfiles)
		logfiles = logfiles[0:n]

		for _, v := range logfiles {
			os.Remove(log.LogPath + "/" + v)
		}
	}
}
