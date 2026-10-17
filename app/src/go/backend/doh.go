package backend

import (
	"context"
	"errors"
	"fmt"
	"strings"

	"dns-backend/doh"
    "dns-backend/dsky/protect"
)

type DoHServer struct {
	r doh.Resolver
}


func NewDoHServer(
	url string, ipsStr string, protector protect.Protector, listener DoHListener,
) (*DoHServer, error) {
	ips := []string{}
	if len(ipsStr) > 0 {
		ips = strings.Split(ipsStr, ",")
	}
	dialer := protect.MakeDialer(protector)
	t, err := doh.NewResolver(url, ips, dialer, nil, makeInternalDoHListener(listener))
	if err != nil {
		return nil, err
	}
	return &DoHServer{t}, nil
}

var dohQuery = []byte{
	0, 0, // [0-1]   query ID
	1, 0, // [2-3]   flags, RD=1
	0, 1, // [4-5]   QDCOUNT (number of queries) = 1
	0, 0, // [6-7]   ANCOUNT (number of answers) = 0
	0, 0, // [8-9]   NSCOUNT (number of authoritative answers) = 0
	0, 0, // [10-11] ARCOUNT (number of additional records) = 0

	// Start of first query
	7, 'y', 'o', 'u', 't', 'u', 'b', 'e',
	3, 'c', 'o', 'm',
	0,    // null terminator of FQDN (DNS root)
	0, 1, // QTYPE = A
	0, 1, // QCLASS = IN (Internet)
}

func Probe(s *DoHServer) error {
	resp, err := s.r.Query(context.Background(), dohQuery)
	if err != nil {
		return fmt.Errorf("failed to send query: %w", err)
	}
	if len(resp) == 0 {
		return errors.New("invalid DoH response")
	}
	return nil
}

type DoHQueryToken doh.Token

type DoHListener interface {
	OnQuery(url string) DoHQueryToken

	OnResponse(DoHQueryToken, *DoHQuerySumary)
}

type DoHStatus = int

const (
	DoHStatusComplete      DoHStatus = doh.Complete      // Transaction completed successfully
	DoHStatusSendFailed    DoHStatus = doh.SendFailed    // Failed to send query
	DoHStatusHTTPError     DoHStatus = doh.HTTPError     // Got a non-200 HTTP status
	DoHStatusBadQuery      DoHStatus = doh.BadQuery      // Malformed input
	DoHStatusBadResponse   DoHStatus = doh.BadResponse   // Response was invalid
	DoHStatusInternalError DoHStatus = doh.InternalError // This should never happen
)

type DoHQuerySumary struct {
	summ *doh.Summary
}

func (q DoHQuerySumary) GetQuery() []byte     { return q.summ.Query }
func (q DoHQuerySumary) GetResponse() []byte  { return q.summ.Response }
func (q DoHQuerySumary) GetServer() string    { return q.summ.Server }
func (q DoHQuerySumary) GetStatus() DoHStatus { return q.summ.Status }
func (q DoHQuerySumary) GetHTTPStatus() int   { return q.summ.HTTPStatus }
func (q DoHQuerySumary) GetLatency() float64  { return q.summ.Latency }

type dohListenerAdapter struct {
	l DoHListener
}

func makeInternalDoHListener(l DoHListener) doh.Listener {
	if l == nil {
		return nil
	}
	return &dohListenerAdapter{l}
}

func (e dohListenerAdapter) OnQuery(url string) doh.Token {
	return e.l.OnQuery(url)
}

func (e dohListenerAdapter) OnResponse(t doh.Token, s *doh.Summary) {
	e.l.OnResponse(t, &DoHQuerySumary{s})
}
