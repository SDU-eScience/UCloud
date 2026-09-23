package orchestrators

import (
	"encoding/binary"
	"net/netip"
)

func PrivateNetworkParseIpv4(address string) (netip.Addr, bool) {
	addr, err := netip.ParseAddr(address)
	if err != nil || !addr.Is4() {
		return netip.Addr{}, false
	}

	return addr, true
}

func PrivateNetworkParseCidr(cidr string) (netip.Prefix, bool) {
	prefix, err := netip.ParsePrefix(cidr)
	if err != nil || !prefix.Addr().Is4() {
		return netip.Prefix{}, false
	}

	return prefix.Masked(), true
}

func PrivateNetworkHostAddress(cidr netip.Prefix, ip netip.Addr) bool {
	hostBits := 32 - cidr.Bits()
	if hostBits <= 0 || hostBits >= 32 {
		return false
	}

	if !ip.Is4() {
		return false
	}

	first, last := privateNetworkCidrRange(cidr)
	numeric := uint64(privateNetworkAddrToUint32(ip))
	if numeric < first || numeric > last {
		return false
	}

	if numeric == first {
		return false
	}

	if numeric == last {
		return false
	}

	if numeric == first+1 {
		return false
	}

	return true
}

func PrivateNetworkCidrsOverlap(a netip.Prefix, b netip.Prefix) bool {
	aFirst, aLast := privateNetworkCidrRange(a)
	bFirst, bLast := privateNetworkCidrRange(b)
	return aFirst <= bLast && bFirst <= aLast
}

func privateNetworkCidrRange(prefix netip.Prefix) (uint64, uint64) {
	if prefix.Bits() < 0 || prefix.Bits() > 32 {
		return 1, 0
	}

	first := uint64(privateNetworkAddrToUint32(prefix.Addr()))
	mask := uint64(0xFFFFFFFF) << uint64(32-prefix.Bits())
	masked := first & mask
	return masked, masked | (^mask & 0xFFFFFFFF)
}

func privateNetworkAddrToUint32(addr netip.Addr) uint32 {
	bytes := addr.As4()
	return binary.BigEndian.Uint32(bytes[:])
}
