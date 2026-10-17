package ipmap

import (
	"context"
	"dns-backend/logging"
	"math/rand"
	"net"
	"sync"
)

type IPMap interface {
	Get(hostname string) *IPSet
}

func NewIPMap(r *net.Resolver) IPMap {
	return &ipMap{
		m: make(map[string]*IPSet),
		r: r,
	}
}

type ipMap struct {
	sync.RWMutex
	m map[string]*IPSet
	r *net.Resolver
}

func (m *ipMap) Get(hostname string) *IPSet {
	m.RLock()
	s := m.m[hostname]
	m.RUnlock()
	if s != nil {
		return s
	}

	s = &IPSet{r: m.r}
	s.Add(hostname)

	m.Lock()
	s2 := m.m[hostname]
	if s2 == nil {
		m.m[hostname] = s
	} else {
		s = s2
	}
	m.Unlock()

	return s
}

type IPSet struct {
	sync.RWMutex
	ips       []net.IP      // All known IPs for the server.
	confirmed net.IP        // IP address confirmed to be working
	r         *net.Resolver // Resolver to use for hostname resolution
}

func (s *IPSet) has(ip net.IP) bool {
	for _, oldIP := range s.ips {
		if oldIP.Equal(ip) {
			return true
		}
	}
	return false
}

func (s *IPSet) add(ip net.IP) {
	if !s.has(ip) {
		s.ips = append(s.ips, ip)
	}
}

func (s *IPSet) Add(hostname string) {
	resolved, err := s.r.LookupIPAddr(context.TODO(), hostname)
	if err != nil {
		logging.Warnf("Failed to resolve %s: %v", hostname, err)
	}
	s.Lock()
	for _, addr := range resolved {
		s.add(addr.IP)
	}
	s.Unlock()
}

func (s *IPSet) Empty() bool {
	s.RLock()
	defer s.RUnlock()
	return len(s.ips) == 0
}

func (s *IPSet) GetAll() []net.IP {
	s.RLock()
	c := append([]net.IP{}, s.ips...)
	s.RUnlock()
	rand.Shuffle(len(c), func(i, j int) {
		c[i], c[j] = c[j], c[i]
	})
	return c
}

func (s *IPSet) Confirmed() net.IP {
	s.RLock()
	defer s.RUnlock()
	return s.confirmed
}

func (s *IPSet) Confirm(ip net.IP) {
	if ip.Equal(s.Confirmed()) {
		return
	}
	s.Lock()
	// Add is O(N)
	s.add(ip)
	s.confirmed = ip
	s.Unlock()
}

func (s *IPSet) Disconfirm(ip net.IP) {
	s.Lock()
	if ip.Equal(s.confirmed) {
		s.confirmed = nil
	}
	s.Unlock()
}
