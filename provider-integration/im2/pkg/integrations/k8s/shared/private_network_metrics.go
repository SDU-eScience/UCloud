package shared

import (
	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
	"ucloud.dk/pkg/controller"
)

var metricPrivateNetworkReconcileDuration = promauto.NewSummary(
	prometheus.SummaryOpts{
		Namespace: "ucloud_im",
		Subsystem: "private_networks",
		Name:      "reconcile_seconds",
		Help:      "Summary of the duration (in seconds) of a private network reconciliation pass",
		Objectives: map[float64]float64{
			0.5:  0.01,
			0.75: 0.01,
			0.95: 0.01,
			0.99: 0.01,
		},
	},
)

var metricPrivateNetworksByState = promauto.NewGaugeVec(
	prometheus.GaugeOpts{
		Namespace: "ucloud_im",
		Subsystem: "private_networks",
		Name:      "networks",
		Help:      "Number of tracked private networks by state",
	},
	[]string{"state"},
)

var metricPrivateNetworkLeasesByState = promauto.NewGaugeVec(
	prometheus.GaugeOpts{
		Namespace: "ucloud_im",
		Subsystem: "private_networks",
		Name:      "leases",
		Help:      "Number of private network IP leases by state",
	},
	[]string{"state"},
)

var metricPrivateNetworkOrphansDeleted = promauto.NewCounter(
	prometheus.CounterOpts{
		Namespace: "ucloud_im",
		Subsystem: "private_networks",
		Name:      "orphan_objects_deleted_total",
		Help:      "Orphaned Kube-OVN objects deleted by the private network reconciler",
	},
)

var metricPrivateNetworkPoolBlocks = promauto.NewGaugeVec(
	prometheus.GaugeOpts{
		Namespace: "ucloud_im",
		Subsystem: "private_networks",
		Name:      "pool_blocks",
		Help:      "Default-size blocks in configured CIDR pools by kind (total, used)",
	},
	[]string{"pool", "kind"},
)

func privateNetworkMetricsRefresh(
	networks []controller.PrivateNetworkSnapshotNetwork,
	leases []controller.PrivateNetworkLeaseRow,
	orphans int,
) {
	networkCounts := map[string]int{}
	for _, network := range networks {
		state := network.State
		if !network.CidrBlock.Present || network.CidrBlock.Value == "" {
			state = "legacy"
		}
		networkCounts[state]++
	}

	knownStates := []string{
		"legacy",
		controller.PrivateNetworkStateProvisioning,
		controller.PrivateNetworkStateReady,
		controller.PrivateNetworkStateDeleting,
	}
	for _, state := range knownStates {
		metricPrivateNetworksByState.WithLabelValues(state).Set(float64(networkCounts[state]))
	}

	leaseCounts := map[string]int{}
	for _, lease := range leases {
		leaseCounts[lease.State]++
	}

	leaseStates := []string{
		controller.PrivateNetworkLeaseStatePending,
		controller.PrivateNetworkLeaseStateReleasing,
	}
	for _, state := range leaseStates {
		metricPrivateNetworkLeasesByState.WithLabelValues(state).Set(float64(leaseCounts[state]))
	}

	metricPrivateNetworkOrphansDeleted.Add(float64(orphans))

	metricPrivateNetworkPoolBlocks.Reset()
	pools := controller.PrivateNetworkSnapshotPoolUtilization()
	for _, pool := range pools {
		metricPrivateNetworkPoolBlocks.WithLabelValues(pool.Pool, "total").Set(float64(pool.TotalBlocks))
		metricPrivateNetworkPoolBlocks.WithLabelValues(pool.Pool, "used").Set(float64(pool.UsedBlocks))
	}
}
