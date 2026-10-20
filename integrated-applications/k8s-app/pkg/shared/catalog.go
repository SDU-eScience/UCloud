package shared

import (
	"net/netip"
	"sort"
	"strings"
	"time"

	"k8s.io/apimachinery/pkg/util/version"
)

const (
	K3sChannelStable  = "stable"
	K3sChannelStaging = "staging"
)

type K3sRelease struct {
	Release       string
	Sha256Amd64   string
	Sha256Arm64   string
	SupportedFrom time.Time
	EolAt         time.Time
	Channel       string
	SecurityFixes bool
}

var K3sCatalog = []K3sRelease{
	{
		Release:       "v1.36.4+k3s1",
		Sha256Amd64:   "835873f37245fc615f547a2fe2af9402a347875f13fa64a1f136de644955ea3f",
		Sha256Arm64:   "c920706346d5ad4e5cd3c7bf1bb09ce71ebe07fec829e513e40f1caf98aed8bb",
		SupportedFrom: time.Date(2026, 4, 22, 0, 0, 0, 0, time.UTC),
		EolAt:         time.Date(2027, 6, 22, 0, 0, 0, 0, time.UTC),
		Channel:       K3sChannelStable,
	},
	{
		Release:       "v1.35.8+k3s1",
		Sha256Amd64:   "12f81c9bb5b71e098a4f7a2d187e350902a9339a6c3d5e56c20a03f7a417e07f",
		Sha256Arm64:   "898476e008704289382377ef19946f23b511cf2678042cb5c8aef991e64f840a",
		SupportedFrom: time.Date(2025, 12, 17, 0, 0, 0, 0, time.UTC),
		EolAt:         time.Date(2027, 2, 17, 0, 0, 0, 0, time.UTC),
		Channel:       K3sChannelStable,
	},
}

func ReleaseByExactVersion(target string) (K3sRelease, bool) {
	for _, release := range K3sCatalog {
		if release.Release == target {
			return release, true
		}
	}
	return K3sRelease{}, false
}

func StableReleases(now time.Time) []K3sRelease {
	return stableReleases(now, true)
}

func stableReleases(now time.Time, applySupportWindow bool) []K3sRelease {
	now = now.UTC()

	var result []K3sRelease
	for _, release := range K3sCatalog {
		if release.Channel != K3sChannelStable {
			continue
		}
		if applySupportWindow && release.SupportedFrom.After(now) {
			continue
		}
		if _, err := version.ParseSemantic(release.Release); err != nil {
			continue
		}
		result = append(result, release)
	}

	sort.Slice(result, func(i, j int) bool {
		left, _ := version.ParseSemantic(result[i].Release)
		right, _ := version.ParseSemantic(result[j].Release)
		return left.GreaterThan(right)
	})

	return result
}

func SupportedReleases(now time.Time) []K3sRelease {
	now = now.UTC()

	var result []K3sRelease
	for _, release := range StableReleases(now) {
		if release.EolAt.IsZero() || !release.EolAt.After(now) {
			continue
		}
		result = append(result, release)
	}

	return result
}

func CreationReleases(now time.Time) []K3sRelease {
	releases := SupportedReleases(now)
	if len(releases) > 0 {
		return releases
	}

	releases = StableReleases(now)
	if len(releases) > 0 {
		return releases
	}

	return stableReleases(now, false)
}

func ReleaseSupportLabel(release K3sRelease, now time.Time) string {
	if release.EolAt.IsZero() {
		return release.Release
	}
	if !release.EolAt.After(now.UTC()) {
		return release.Release + " (out of support since " + release.EolAt.UTC().Format("Jan 2, 2006") + ")"
	}
	return release.Release + " (supported until " + release.EolAt.UTC().Format("Jan 2, 2006") + ")"
}

func ReleaseOptionLabel(release K3sRelease, now time.Time) string {
	if !release.EolAt.IsZero() && !release.EolAt.After(now.UTC()) {
		return ReleaseSupportLabel(release, now)
	}
	return release.Release
}

func VersionSupportLabel(target string, now time.Time) string {
	eolAt, ok := SupportEnds(target)
	if !ok {
		return target
	}
	return ReleaseSupportLabel(K3sRelease{Release: target, EolAt: eolAt}, now)
}

func SameRelease(left string, right string) bool {
	leftVersion, leftErr := version.ParseSemantic(left)
	rightVersion, rightErr := version.ParseSemantic(right)
	if leftErr != nil || rightErr != nil {
		return left == right
	}
	return leftVersion.EqualTo(rightVersion)
}

func RecommendedTarget(current string, now time.Time) (K3sRelease, bool) {
	if !strings.HasPrefix(current, "v") {
		return K3sRelease{}, false
	}
	if _, err := version.ParseSemantic(current); err != nil {
		return K3sRelease{}, false
	}

	for _, release := range SupportedReleases(now) {
		if release.Release == current || NodeAgentUpgradeAllowed(current, release.Release) {
			return release, true
		}
	}

	return K3sRelease{}, false
}

func RecommendedClusterTarget(current []string, now time.Time) (K3sRelease, bool) {
	if len(current) == 0 {
		return K3sRelease{}, false
	}

	for _, release := range SupportedReleases(now) {
		releaseVersion, err := version.ParseSemantic(release.Release)
		if err != nil {
			continue
		}

		reachable := true
		for _, nodeVersion := range current {
			parsed, parseErr := version.ParseSemantic(nodeVersion)
			if parseErr != nil {
				reachable = false
				break
			}
			if parsed.EqualTo(releaseVersion) {
				continue
			}
			if parsed.GreaterThan(releaseVersion) {
				reachable = false
				break
			}
			if !NodeAgentUpgradeAllowed(nodeVersion, release.Release) {
				reachable = false
				break
			}
		}

		if reachable {
			return release, true
		}
	}

	return K3sRelease{}, false
}

func SupportEnds(target string) (time.Time, bool) {
	release, ok := ReleaseByExactVersion(target)
	if !ok || release.EolAt.IsZero() {
		return time.Time{}, false
	}
	return release.EolAt.UTC(), true
}

func NodeIpForAllocation(allocationId int) string {
	host := ipFromOffset(allocationId + 2)
	return host.String()
}

func ipFromOffset(offset int) netip.Addr {
	third := offset / 256
	fourth := offset % 256
	return netip.AddrFrom4([4]byte{172, 31, byte(third), byte(fourth)})
}

const ClusterVmCidr = "172.31.0.0/16"
const ClusterPodCidr = "10.14.0.0/16"
const ClusterServiceCidr = "10.15.0.0/16"
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
