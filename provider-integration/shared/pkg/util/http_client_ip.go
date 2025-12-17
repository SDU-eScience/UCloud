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
	s = strings.TrimPrefix(s, "for=")

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

	// 1) RFC 7239 Forwarded header (may appear multiple times, comma-separated)
	// Example: Forwarded: for=203.0.113.60;proto=https;by=203.0.113.43
	if fwd := r.Header.Values("Forwarded"); len(fwd) > 0 {
		joined := strings.Join(fwd, ",")
		parts := strings.Split(joined, ",")
		for _, p := range parts {
			// Find "for=" parameter inside this element.
			semi := strings.Split(p, ";")
			for _, kv := range semi {
				kv = strings.TrimSpace(kv)
				if strings.HasPrefix(strings.ToLower(kv), "for=") {
					ip := cleanIPToken(kv)
					if ip == nil {
						continue
					}
					if !cfg.AllowPrivate && isPrivateOrLoopback(ip) {
						continue
					}
					return ip
				}
			}
		}
	}

	// 2) X-Forwarded-For: client, proxy1, proxy2
	// We typically want the left-most valid IP. If you have multiple proxy layers you control,
	// this still works as long as your edge proxy preserves the chain.
	if xff := r.Header.Get("X-Forwarded-For"); xff != "" {
		for _, token := range strings.Split(xff, ",") {
			ip := cleanIPToken(token)
			if ip == nil {
				continue
			}
			if !cfg.AllowPrivate && isPrivateOrLoopback(ip) {
				continue
			}
			return ip
		}
	}

	// 3) X-Real-IP (often set by nginx)
	if xrip := r.Header.Get("X-Real-IP"); xrip != "" {
		ip := cleanIPToken(xrip)
		if ip != nil && (cfg.AllowPrivate || !isPrivateOrLoopback(ip)) {
			return ip
		}
	}

	// Fallback: TCP peer.
	return remoteIP
}
