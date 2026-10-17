package logging

import (
	"context"
	"fmt"
	"log/slog"
	"os"
)

var logger = slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{
	Level: slog.LevelWarn,
}))

func Debug(msg string, args ...any) {
	logger.Debug(msg, args...)
}

func Debugf(format string, args ...any) {
	if !logger.Enabled(context.Background(), slog.LevelDebug) {
		return
	}
	logger.Debug(fmt.Sprintf(format, args...))
}

func Info(msg string, args ...any) {
	logger.Info(msg, args...)
}

func Infof(format string, args ...any) {
	if !logger.Enabled(context.Background(), slog.LevelInfo) {
		return
	}
	logger.Info(fmt.Sprintf(format, args...))
}

func Warn(msg string, args ...any) {
	logger.Warn(msg, args...)
}

func Warnf(format string, args ...any) {
	if !logger.Enabled(context.Background(), slog.LevelWarn) {
		return
	}
	logger.Warn(fmt.Sprintf(format, args...))
}

func Error(msg string, args ...any) {
	logger.Error(msg, args...)
}

func Errorf(format string, args ...any) {
	if !logger.Enabled(context.Background(), slog.LevelError) {
		return
	}
	logger.Error(fmt.Sprintf(format, args...))
}
