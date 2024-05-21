package log

import "os"

var logger = &Logger{
	level:      LevelInfo,
	logConsole: true,
	dateFormat: "2006-01-02T15:04:05.000",
	writer:     os.Stdout,
	calldepth:  3,
}

func SetDefaultLogger(l *Logger) {
	logger = l
	logger.calldepth = 3
}

func SetLogFile(path string) error {
	return logger.SetLogFile(path)
}

func SetRotation(period, retain int, compress bool) {
	logger.SetRotation(period, retain, compress)
}

func SetLevel(level int) {
	logger.SetLevel(level)
}

func SetFlags(flags uint) {
	logger.SetFlags(flags)
}

func Fatal(format string, args ...any) {
	logger.Fatal(format, args...)
}

func Error(format string, args ...any) {
	logger.Error(format, args...)
}

func Warn(format string, args ...any) {
	logger.Warn(format, args...)
}

func Info(format string, args ...any) {
	logger.Info(format, args...)
}

func Debug(format string, args ...any) {
	logger.Debug(format, args...)
}
