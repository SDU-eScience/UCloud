package shared

import (
	"fmt"
	"net/netip"
	"strings"
)

type IPRange struct {
	Start netip.Addr
	End   netip.Addr
}

func ParseIPRange(value string) (IPRange, error) {
	value = strings.TrimSpace(value)

	// CIDR: 10.0.0.0/24
	if strings.Contains(value, "/") {
		prefix, err := netip.ParsePrefix(value)
		if err != nil {
			return IPRange{}, fmt.Errorf("invalid CIDR %q: %w", value, err)
		}

		if !prefix.Addr().Is4() {
			return IPRange{}, fmt.Errorf("only IPv4 ranges are supported")
		}

		return IPRange{
			Start: prefix.Masked().Addr(),
			End:   prefixLastAddr(prefix),
		}, nil
	}

	// Explicit range: 10.0.0.1-10.0.0.12
	parts := strings.SplitN(value, "-", 2)
	fromIp, toIp := "", ""
	if len(parts) == 1 {
		fromIp = parts[0]
		toIp = parts[0]
	} else if len(parts) == 2 {
		fromIp, toIp = parts[0], parts[1]
	} else {
		return IPRange{}, fmt.Errorf(
			"invalid IP range %q: expected IP-IP or CIDR",
			value,
		)
	}

	start, err := netip.ParseAddr(strings.TrimSpace(fromIp))
	if err != nil {
		return IPRange{}, fmt.Errorf("invalid start IP %q: %w", fromIp, err)
	}

	end, err := netip.ParseAddr(strings.TrimSpace(toIp))
	if err != nil {
		return IPRange{}, fmt.Errorf("invalid end IP %q: %w", toIp, err)
	}

	if !start.Is4() || !end.Is4() {
		return IPRange{}, fmt.Errorf("only IPv4 ranges are supported")
	}

	if start.Compare(end) > 0 {
		return IPRange{}, fmt.Errorf(
			"start IP %s is greater than end IP %s",
			start,
			end,
		)
	}

	return IPRange{
		Start: start,
		End:   end,
	}, nil
}

func prefixLastAddr(prefix netip.Prefix) netip.Addr {
	addr := prefix.Masked().Addr()
	bits := prefix.Bits()

	v := uint32(addr.As4()[0])<<24 |
		uint32(addr.As4()[1])<<16 |
		uint32(addr.As4()[2])<<8 |
		uint32(addr.As4()[3])

	hostBits := 32 - bits
	v |= (uint32(1) << hostBits) - 1

	return netip.AddrFrom4([4]byte{
		byte(v >> 24),
		byte(v >> 16),
		byte(v >> 8),
		byte(v),
	})
}

func (r IPRange) Contains(ip string) bool {
	addr, err := netip.ParseAddr(strings.TrimSpace(ip))
	if err != nil {
		return false
	}

	return addr.Compare(r.Start) >= 0 &&
		addr.Compare(r.End) <= 0
}
