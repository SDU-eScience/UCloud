package log

import (
	"compress/gzip"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"runtime"
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
	LevelFatal = 0
	LevelError = 1
	LevelWarn  = 2
	LevelInfo  = 3
	LevelDebug = 4
)

const (
	FlagUTC uint = (1 << iota)
	FlagShortFile
	FlagLongFile
)

type Logger struct {
	level          int
	flags          uint
	dateFormat     string
	logConsole     bool
	logFile        string
	logPath        string
	rotatePeriod   int
	rotateCompress bool
	rotateRetain   int
	file           *os.File
	writer         io.Writer
	mu             sync.Mutex
	calldepth      int
}

func NewLogger(level int, console bool) *Logger {
	var w io.Writer
	if console {
		w = os.Stdout
	}

	return &Logger{
		level:      level,
		logConsole: console,
		dateFormat: "2006-01-02T15:04:05.000",
		writer:     w,
		calldepth:  2,
	}
}

func (log *Logger) SetLogFile(path string) error {
	log.mu.Lock()
	defer log.mu.Unlock()

	log.logFile = filepath.Base(path)
	log.logPath = filepath.Dir(path)

	return log.open()
}

func (log *Logger) SetRotation(period, retain int, compress bool) {
	if log.rotatePeriod > 0 {
		return
	}

	log.rotatePeriod = period
	log.rotateCompress = compress
	log.rotateRetain = retain

	go log.rotate()
}

func (log *Logger) SetLevel(level int) {
	if level >= LevelFatal && level <= LevelDebug {
		log.level = level
	}
}

func (log *Logger) SetFlags(flags uint) {
	log.flags = flags
}

func (log *Logger) Fatal(format string, args ...any) {
	log.write(LevelFatal, format, args...)
	if log.file != nil {
		_ = log.file.Close()
	}
	os.Exit(1)
}

func (log *Logger) Error(format string, args ...any) {
	if LevelError > log.level {
		return
	}
	log.write(LevelError, format, args...)
}

func (log *Logger) Warn(format string, args ...any) {
	if LevelWarn > log.level {
		return
	}
	log.write(LevelWarn, format, args...)
}

func (log *Logger) Info(format string, args ...any) {
	if LevelInfo > log.level {
		return
	}
	log.write(LevelInfo, format, args...)
}

func (log *Logger) Debug(format string, args ...any) {
	if LevelDebug > log.level {
		return
	}
	log.write(LevelDebug, format, args...)
}

func (log *Logger) open() error {
	fn := log.logPath + "/" + log.logFile
	perms := os.O_WRONLY | os.O_APPEND | os.O_CREATE

	f, err := os.OpenFile(fn, perms, 0o640)
	if err != nil {
		return err
	}

	if log.logConsole {
		log.writer = io.MultiWriter(os.Stdout, f)
	} else {
		log.writer = f
	}

	log.file = f
	return nil
}

func (log *Logger) write(level int, format string, args ...any) {
	var lvlstr string

	switch level {
	case LevelFatal:
		lvlstr = "FATAL"
	case LevelError:
		lvlstr = "ERROR"
	case LevelWarn:
		lvlstr = "WARN"
	case LevelInfo:
		lvlstr = "INFO"
	case LevelDebug:
		lvlstr = "DEBUG"
	}

	dt := time.Now()
	if (log.flags & FlagUTC) > 0 {
		dt = dt.UTC()
	}

	if (log.flags & (FlagShortFile | FlagLongFile)) > 0 {
		_, file, no, _ := runtime.Caller(log.calldepth)
		if (log.flags & FlagShortFile) > 0 {
			file = filepath.Base(file)
		}
		lvlstr = fmt.Sprintf("%s:%d %s", file, no, lvlstr)
	}

	f := fmt.Sprintf(format, args...)
	f = fmt.Sprintf("%s %-5s %s\n", dt.Format(log.dateFormat), lvlstr, strings.TrimSpace(f))

	log.mu.Lock()
	if log.writer != nil {
		log.writer.Write([]byte(f))
	}
	log.mu.Unlock()
}

func (log *Logger) rotate() {
	var ts time.Time
	now := time.Now().UTC()

	switch log.rotatePeriod {
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

	oldfn := fmt.Sprintf("%s/%s", log.logPath, log.logFile)
	newfn := fmt.Sprintf("%s-%d", oldfn, time.Now().Unix())

	err := os.Rename(oldfn, newfn)
	if err != nil {
		return
	}

	log.open()
	log.mu.Unlock()

	// Compress
	if log.rotateCompress {
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
	files, err := os.ReadDir(log.logPath)
	if err != nil {
		return
	}

	logfiles := []string{}
	prefix := fmt.Sprintf("%s-", log.logFile)
	for _, f := range files {
		if strings.HasPrefix(f.Name(), prefix) {
			logfiles = append(logfiles, f.Name())
		}
	}

	if n := len(logfiles); n > log.rotateRetain {
		n = n - log.rotateRetain
		sort.Strings(logfiles)
		logfiles = logfiles[0:n]

		for _, v := range logfiles {
			os.Remove(log.logPath + "/" + v)
		}
	}
}
