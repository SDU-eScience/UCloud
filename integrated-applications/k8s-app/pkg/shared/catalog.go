package shared

import (
	"fmt"
	"net/netip"
	"sort"
	"strings"
	"time"

	"k8s.io/apimachinery/pkg/util/version"
)

type K3sRelease struct {
	Release       string
	Sha256Amd64   string
	Sha256Arm64   string
	SupportedFrom time.Time
	EolAt         time.Time
}

var K3sCatalog = []K3sRelease{
	{
		Release:       "v1.37.2+k3s1",
		Sha256Amd64:   "265b3ee6a3198ef14033c63ef46cbb32370d83795199fb6d1b01f21575eddbda",
		Sha256Arm64:   "a779ccf1e5e2e33a62f5a29c2f1c0bbf99431e100579d4c10f0aa0742a6aa84b",
		SupportedFrom: time.Date(2026, 8, 26, 0, 0, 0, 0, time.UTC),
		EolAt:         time.Date(2027, 10, 26, 0, 0, 0, 0, time.UTC),
	},
	{
		Release:       "v1.36.6+k3s1",
		Sha256Amd64:   "66ceb8e67edfa547195135d74783bfadc2ecc04a91059e90ae5f70a3dbc98c15",
		Sha256Arm64:   "49dcecdb4f64f5e92bdc52d204d407fb39729e492f09074488125ee58b6f2895",
		SupportedFrom: time.Date(2026, 4, 22, 0, 0, 0, 0, time.UTC),
		EolAt:         time.Date(2027, 6, 22, 0, 0, 0, 0, time.UTC),
	},
	{
		Release:       "v1.35.10+k3s1",
		Sha256Amd64:   "5cd49f0faca5b2ea4355d4761e7b1445b8aa05cd50e612fd9c370551db49e5e2",
		Sha256Arm64:   "0c780ef4b6275bd27c77ed64a647fa1d859b1bd60597ad420f3243c261e5ffb6",
		SupportedFrom: time.Date(2025, 12, 17, 0, 0, 0, 0, time.UTC),
		EolAt:         time.Date(2027, 2, 17, 0, 0, 0, 0, time.UTC),
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

type ReleaseFlag uint64

const (
	ReleaseFlagReleased ReleaseFlag = 1 << iota
	ReleaseFlagSupported
)

func Releases(now time.Time, flags ReleaseFlag) []K3sRelease {
	now = now.UTC()

	result := make([]K3sRelease, 0, len(K3sCatalog))
	for _, release := range K3sCatalog {
		if _, err := version.ParseSemantic(release.Release); err != nil {
			continue
		}

		released := !release.SupportedFrom.After(now)
		supported := released && (release.EolAt.IsZero() || release.EolAt.After(now))

		if flags&ReleaseFlagReleased != 0 && released {
			result = append(result, release)
			continue
		}
		if flags&ReleaseFlagSupported != 0 && supported {
			result = append(result, release)
		}
	}

	sort.SliceStable(result, func(i, j int) bool {
		left, _ := version.ParseSemantic(result[i].Release)
		right, _ := version.ParseSemantic(result[j].Release)
		return left.GreaterThan(right)
	})

	return result
}

func ReleasesNewestPatchPerMinor(releases []K3sRelease) []K3sRelease {
	seen := make(map[string]bool, len(releases))
	result := make([]K3sRelease, 0, len(releases))
	for _, release := range releases {
		parsed, err := version.ParseSemantic(release.Release)
		if err != nil {
			continue
		}
		minor := fmt.Sprintf("%d.%d", parsed.Major(), parsed.Minor())
		if seen[minor] {
			continue
		}
		seen[minor] = true
		result = append(result, release)
	}
	return result
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

func ReleaseEquals(left string, right string) bool {
	leftVersion, leftErr := version.ParseSemantic(left)
	rightVersion, rightErr := version.ParseSemantic(right)
	if leftErr != nil || rightErr != nil {
		return left == right
	}
	return leftVersion.EqualTo(rightVersion)
}

func ReleaseRecommendedTargetForNode(current string, now time.Time) (K3sRelease, bool) {
	if !strings.HasPrefix(current, "v") {
		return K3sRelease{}, false
	}
	if _, err := version.ParseSemantic(current); err != nil {
		return K3sRelease{}, false
	}

	for _, release := range Releases(now, ReleaseFlagSupported) {
		if release.Release == current || NodeAgentUpgradeAllowed(current, release.Release) {
			return release, true
		}
	}

	return K3sRelease{}, false
}

func ReleaseRecommendedTargetForCluster(current []string, now time.Time) (K3sRelease, bool) {
	if len(current) == 0 {
		return K3sRelease{}, false
	}

	for _, release := range Releases(now, ReleaseFlagSupported) {
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

func ClusterUpgradePathGuidance(versions map[string]string, now time.Time) []string {
	names := make([]string, 0, len(versions))
	for name := range versions {
		names = append(names, name)
	}
	sort.Strings(names)

	var guidance []string
	for _, name := range names {
		if versions[name] == "" {
			guidance = append(guidance, fmt.Sprintf("The Kubernetes version of node %s is unavailable. Check the node status and try again.", name))
		}
	}

	releases := Releases(now, ReleaseFlagSupported)
	if len(releases) == 0 {
		return append(guidance, "No supported Kubernetes version is currently available. Contact your service provider.")
	}

	latest := releases[0].Release
	for _, name := range names {
		current := versions[name]
		if current == "" {
			continue
		}
		if current == latest || NodeAgentUpgradeAllowed(current, latest) {
			continue
		}

		next, ok := ReleaseRecommendedTargetForNode(current, now)
		if ok && !ReleaseEquals(current, next.Release) {
			guidance = append(guidance, fmt.Sprintf(
				"Node %s runs Kubernetes version %s and cannot upgrade directly to %s. Its next supported version is %s. Check node maintenance to upgrade it first.",
				name,
				current,
				latest,
				next.Release,
			))
		} else {
			guidance = append(guidance, fmt.Sprintf(
				"Node %s runs Kubernetes version %s and has no supported upgrade path to %s. Contact your service provider.",
				name,
				current,
				latest,
			))
		}
	}
	return guidance
}

func SupportEnds(target string) (time.Time, bool) {
	release, ok := ReleaseByExactVersion(target)
	if !ok || release.EolAt.IsZero() {
		return time.Time{}, false
	}
	return release.EolAt.UTC(), true
}

func NodeIpForInternalId(internalId int) string {
	host := ipFromOffset(internalId + 2)
	return host.String()
}

func ipFromOffset(offset int) netip.Addr {
	third := offset / 256
	fourth := offset % 256
	return netip.AddrFrom4([4]byte{172, 31, byte(third), byte(fourth)})
}

const ClusterVmCidr = "172.31.0.0/16"

var ClusterVmPrefix = netip.MustParsePrefix(ClusterVmCidr)

const ClusterPodCidr = "10.14.0.0/16"
const ClusterServiceCidr = "10.15.0.0/16"

const maxInternalOffset = 65534

func InternalIdIsValid(internalId int) bool {
	if internalId < 1 {
		return false
	}

	offset := internalId + 2
	return offset <= maxInternalOffset
}

func SanitizeForPath(value string) string {
	replacer := strings.NewReplacer("+", "-", "/", "-", " ", "-")
	return replacer.Replace(value)
}
