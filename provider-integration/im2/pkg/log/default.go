package log

import "os"

var logger = &Logger{
	Level:      LevelInfo,
	Console:    true,
	DateFormat: "2006-01-02T15:04:05.000",
	writer:     os.Stdout,
}

func SetDefaultLogger(l *Logger) {
	logger = l
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
