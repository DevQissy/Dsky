package backend

import (
	"errors"
	"io"
	"io/fs"
	"dns-backend/dsky"
    "dns-backend/dsky/protect"
    "dns-backend/logging"
    "dns-backend/tuntap"
	"os"

	"github.com/Jigsaw-Code/outline-sdk/network"
)

type Session struct {
	*dsky.Tunnel
}

func (s *Session) SetDoHServer(svr *DoHServer) { s.SetDNS(svr.r) }

func ConnectSession(
	fd int, fakedns string, dohdns *DoHServer, protector protect.Protector, listener dsky.Listener,
) (*Session, error) {
	tun, err := tuntap.MakeTunDeviceFromFD(fd)
	if err != nil {
		return nil, err
	}
	if dohdns == nil {
		return nil, errors.New("dohdns must not be nil")
	}
	t, err := dsky.NewTunnel(fakedns, dohdns.r, tun, protector, listener)
	if err != nil {
		return nil, err
	}
	go copyUntilEOF(t, tun)
	go copyUntilEOF(tun, t)
	return &Session{t}, nil
}

func copyUntilEOF(dst, src io.ReadWriteCloser) {
	logging.Debug("IntraSession(copyUntilEOF) - start relaying traffic", "src", src, "dst", dst)
	defer logging.Debug("IntraSession(copyUntilEOF) - stop relaying traffic", "src", src, "dst", dst)

	const commonMTU = 1500
	buf := make([]byte, commonMTU)
	for {
		_, err := io.CopyBuffer(dst, src, buf)
		if err == nil || isErrClosed(err) {
			return
		}
	}
}

func isErrClosed(err error) bool {
	return errors.Is(err, os.ErrClosed) || errors.Is(err, fs.ErrClosed) || errors.Is(err, network.ErrClosed)
}
