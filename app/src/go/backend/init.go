package backend

import (
	"runtime/debug"
)

func init() {
	debug.SetGCPercent(10)
}
