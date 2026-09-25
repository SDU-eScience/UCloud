package shared

import (
	"net/netip"
	"strings"
)

type K3sRelease struct {
	Release     string
	Sha256Amd64 string
	Sha256Arm64 string
}

var K3sCatalog = []K3sRelease{
	{
		Release:     "v1.36.4+k3s1",
		Sha256Amd64: "835873f37245fc615f547a2fe2af9402a347875f13fa64a1f136de644955ea3f",
		Sha256Arm64: "c920706346d5ad4e5cd3c7bf1bb09ce71ebe07fec829e513e40f1caf98aed8bb",
	},
	{
		Release:     "v1.35.8+k3s1",
		Sha256Amd64: "12f81c9bb5b71e098a4f7a2d187e350902a9339a6c3d5e56c20a03f7a417e07f",
		Sha256Arm64: "898476e008704289382377ef19946f23b511cf2678042cb5c8aef991e64f840a",
	},
}

func ReleaseByExactVersion(version string) (K3sRelease, bool) {
	for _, release := range K3sCatalog {
		if release.Release == version {
			return release, true
		}
	}
	return K3sRelease{}, false
}

func NodeIpForAllocation(allocationId int) string {
	host := ipFromOffset(allocationId + 2)
	return host.String()
}

func ipFromOffset(offset int) netip.Addr {
	third := offset / 256
	fourth := offset % 256
	return netip.AddrFrom4([4]byte{10, 199, byte(third), byte(fourth)})
}

const ClusterVmCidr = "10.199.0.0/16"
const ClusterPodCidr = "10.200.0.0/16"
const ClusterServiceCidr = "10.201.0.0/16"
const ClusterMaxNodes = 256

const maxAllocationOffset = 65534

func AllocationIdIsValid(allocationId int) bool {
	if allocationId < 1 {
		return false
	}

	offset := allocationId + 2
	return offset <= maxAllocationOffset
}

func SanitizeForPath(value string) string {
	replacer := strings.NewReplacer("+", "-", "/", "-", " ", "-")
	return replacer.Replace(value)
}
