package dsky

import (
	"sync/atomic"
	"time"
)

// UDPSocketSummary describes a non-DNS UDP association, reported when it is discarded.
type UDPSocketSummary struct {
	UploadBytes   int64 // Amount uploaded (bytes)
	DownloadBytes int64 // Amount downloaded (bytes)
	Duration      int32 // How long the socket was open (seconds)
}

// UDPListener is notified when a non-DNS UDP association is discarded.
type UDPListener interface {
	OnUDPSocketClosed(*UDPSocketSummary)
}

type tracker struct {
	start    time.Time
	upload   atomic.Int64 // Non-DNS upload bytes
	download atomic.Int64 // Non-DNS download bytes
}

func makeTracker() *tracker {
	return &tracker{
		start: time.Now(),
	}
}
