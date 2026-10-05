package util

import (
	"net"
	"net/http"
	"strings"
)

// ClientIPConfig controls when and how proxy headers are trusted.
type ClientIPConfig struct {
	// TrustedProxies are IPs or CIDRs (e.g. "10.0.0.0/8", "192.168.1.10", "fd00::/8")
	// that are allowed to supply Forwarded/X-Forwarded-For/X-Real-IP.
	TrustedProxies []string

	// If true, allows private/loopback IPs from headers; usually keep false for logging/rate-limit purposes.
	AllowPrivate bool
}

type cidrOrIP struct {
	ip   net.IP
	netw *net.IPNet
}

func parseTrusted(list []string) []cidrOrIP {
	out := make([]cidrOrIP, 0, len(list))
	for _, s := range list {
		s = strings.TrimSpace(s)
		if s == "" {
			continue
		}
		if strings.Contains(s, "/") {
			_, n, err := net.ParseCIDR(s)
			if err == nil {
				out = append(out, cidrOrIP{netw: n})
			}
			continue
		}
		if ip := net.ParseIP(s); ip != nil {
			out = append(out, cidrOrIP{ip: ip})
		}
	}
	return out
}

func isTrustedProxy(remoteIP net.IP, trusted []cidrOrIP) bool {
	if remoteIP == nil {
		return false
	}
	for _, t := range trusted {
		if t.ip != nil && t.ip.Equal(remoteIP) {
			return true
		}
		if t.netw != nil && t.netw.Contains(remoteIP) {
			return true
		}
	}
	return false
}

func parseRemoteAddrIP(remoteAddr string) net.IP {
	host, _, err := net.SplitHostPort(strings.TrimSpace(remoteAddr))
	if err != nil {
		// Might already be a bare IP without port.
		return net.ParseIP(strings.TrimSpace(remoteAddr))
	}
	return net.ParseIP(host)
}

func isPrivateOrLoopback(ip net.IP) bool {
	if ip == nil {
		return true
	}
	ip = ip.To16()
	if ip == nil {
		return true
	}
	// IPv4-mapped addresses should still work with IsPrivate/IsLoopback in modern Go,
	// but normalize for safety.
	if v4 := ip.To4(); v4 != nil {
		ip = v4
	}
	return ip.IsLoopback() || ip.IsPrivate() || ip.IsLinkLocalUnicast() || ip.IsLinkLocalMulticast()
}

func cleanIPToken(s string) net.IP {
	s = strings.TrimSpace(s)
	if s == "" {
		return nil
	}
	// Forwarded: for= may be quoted and may include port.
	s = strings.Trim(s, "\"")
	// Parameter names are case-insensitive per RFC 7239 ("For=", "FOR=").
	if len(s) >= 4 && strings.EqualFold(s[:4], "for=") {
		s = s[4:]
	}

	// Remove IPv6 brackets [::1]:1234
	s = strings.TrimPrefix(s, "[")
	if idx := strings.IndexByte(s, ']'); idx >= 0 {
		s = s[:idx]
	}

	// Remove :port for IPv4/hostname-ish tokens (best-effort)
	if h, _, err := net.SplitHostPort(s); err == nil {
		s = h
	}

	// Remove possible obfuscated identifiers (Forwarded allows "for=_hidden")
	if strings.HasPrefix(s, "_") {
		return nil
	}

	return net.ParseIP(s)
}

func ClientIP(r *http.Request) net.IP {
	return ClientIPEx(r, ClientIPConfig{
		TrustedProxies: []string{
			"10.0.0.0/8",
			"172.16.0.0/12",
			"192.168.0.0/16",
			"127.0.0.1",
			"::1",
		},
		AllowPrivate: false,
	})
}

// ClientIPEx returns the best-effort client IP.
// It only trusts proxy headers if the TCP peer (RemoteAddr) is a trusted proxy.
func ClientIPEx(r *http.Request, cfg ClientIPConfig) net.IP {
	remoteIP := parseRemoteAddrIP(r.RemoteAddr)
	trusted := parseTrusted(cfg.TrustedProxies)

	// If we can't trust the proxy, do not read forwarded headers.
	if !isTrustedProxy(remoteIP, trusted) {
		return remoteIP
	}

	// Build the hop chain in wire order (leftmost = original client side,
	// rightmost = closest to us, i.e. what the trusted proxy appended).
	// Pick exactly ONE header family; never fall through between them.
	var chain []net.IP
	switch {
	case len(r.Header.Values("Forwarded")) > 0:
		joined := strings.Join(r.Header.Values("Forwarded"), ",")
		for _, p := range strings.Split(joined, ",") {
			for _, kv := range strings.Split(p, ";") {
				kv = strings.TrimSpace(kv)
				if len(kv) >= 4 && strings.EqualFold(kv[:4], "for=") {
					chain = append(chain, cleanIPToken(kv))
				}
			}
		}
	case r.Header.Get("X-Forwarded-For") != "":
		for _, token := range strings.Split(r.Header.Get("X-Forwarded-For"), ",") {
			chain = append(chain, cleanIPToken(token))
		}
	default:
		// X-Real-IP: single value owned by the trusted proxy; no chain to
		// validate. Only consulted when both chain headers are absent.
		if xrip := r.Header.Get("X-Real-IP"); xrip != "" {
			if ip := cleanIPToken(xrip); ip != nil &&
				(cfg.AllowPrivate || !isPrivateOrLoopback(ip)) {
				return ip
			}
		}
		return remoteIP
	}

	// Walk right-to-left: skip hops that are trusted proxies. The first
	// untrusted hop is the actual client. Client-injected entries at the
	// FRONT of the chain are never selected, because the proxy-appended
	// entry at the back is processed first.
	for i := len(chain) - 1; i >= 0; i-- {
		ip := chain[i]
		if ip == nil {
			// Malformed entry breaks chain integrity; stop and fall back.
			break
		}
		if isTrustedProxy(ip, trusted) {
			continue
		}
		if !cfg.AllowPrivate && isPrivateOrLoopback(ip) {
			break
		}
		return ip
	}

	// Fallback: TCP peer.
	return remoteIP
}
