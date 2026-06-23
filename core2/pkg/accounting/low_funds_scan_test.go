package accounting

import (
	"testing"
	"time"

	accapi "ucloud.dk/shared/pkg/accounting"
)

func TestLowFundsEvaluate(t *testing.T) {
	projectOwner := accapi.WalletOwnerProject("00000000-0000-0000-0000-000000000001")
	userOwner := accapi.WalletOwnerUser("user")
	cfg := lowFundsConfig{
		LowThresholdPercent:      10,
		RecoveryThresholdPercent: 20,
		MinimumComputeHours:      1000,
		MinimumStorageBytes:      50 * 1000 * 1000 * 1000,
		MinimumInferenceTokens:   1000 * 1000,
	}
	now := time.Date(2024, time.January, 1, 0, 0, 0, 0, time.UTC)

	compute := accapi.ProductCategory{
		Name:                "compute",
		Provider:            "provider",
		ProductType:         accapi.ProductTypeCompute,
		AccountingUnit:      accapi.AccountingUnit{Name: "Core"},
		AccountingFrequency: accapi.AccountingFrequencyPeriodicHour,
	}
	storage := accapi.ProductCategory{
		Name:                "storage",
		Provider:            "provider",
		ProductType:         accapi.ProductTypeStorage,
		AccountingUnit:      accapi.AccountingUnit{Name: "GB"},
		AccountingFrequency: accapi.AccountingFrequencyOnce,
	}
	inference := accapi.ProductCategory{
		Name:                "inference",
		Provider:            "provider",
		ProductType:         accapi.ProductTypeLicense,
		AccountingUnit:      accapi.AccountingUnit{Name: "token"},
		AccountingFrequency: accapi.AccountingFrequencyOnce,
	}
	license := accapi.ProductCategory{
		Name:                "license",
		Provider:            "provider",
		ProductType:         accapi.ProductTypeLicense,
		AccountingUnit:      accapi.AccountingUnit{Name: "license"},
		AccountingFrequency: accapi.AccountingFrequencyOnce,
	}

	tests := []struct {
		name     string
		snapshot lowFundsWalletSnapshot
		previous lowFundsStoredState
		eligible bool
		state    lowFundsState
		notify   bool
	}{
		{
			name:     "user wallet is not eligible",
			snapshot: lowFundsWalletSnapshot{Owner: userOwner, Category: storage, MaxUsable: 4, ActiveUsage: 96},
			state:    lowFundsHealthy,
		},
		{
			name:     "project compute wallet below threshold notifies",
			snapshot: lowFundsWalletSnapshot{Owner: projectOwner, Category: compute, MaxUsable: 100, ActiveUsage: 1900},
			eligible: true,
			state:    lowFundsLow,
			notify:   true,
		},
		{
			name:     "project storage wallet below threshold notifies",
			snapshot: lowFundsWalletSnapshot{Owner: projectOwner, Category: storage, MaxUsable: 5, ActiveUsage: 95},
			eligible: true,
			state:    lowFundsLow,
			notify:   true,
		},
		{
			name:     "project inference wallet below threshold notifies",
			snapshot: lowFundsWalletSnapshot{Owner: projectOwner, Category: inference, MaxUsable: 50_000, ActiveUsage: 950_000},
			eligible: true,
			state:    lowFundsLow,
			notify:   true,
		},
		{
			name:     "non-target product is not eligible",
			snapshot: lowFundsWalletSnapshot{Owner: projectOwner, Category: license, MaxUsable: 1, ActiveUsage: 99},
			state:    lowFundsHealthy,
		},
		{
			name:     "tiny storage allocation is ignored",
			snapshot: lowFundsWalletSnapshot{Owner: projectOwner, Category: storage, MaxUsable: 1, ActiveUsage: 9},
			eligible: true,
			state:    lowFundsHealthy,
		},
		{
			name:     "tiny compute allocation is ignored",
			snapshot: lowFundsWalletSnapshot{Owner: projectOwner, Category: compute, MaxUsable: 1, ActiveUsage: 9},
			eligible: true,
			state:    lowFundsHealthy,
		},
		{
			name:     "tiny inference allocation is ignored",
			snapshot: lowFundsWalletSnapshot{Owner: projectOwner, Category: inference, MaxUsable: 1, ActiveUsage: 9},
			eligible: true,
			state:    lowFundsHealthy,
		},
		{
			name:     "healthy wallet does not notify",
			snapshot: lowFundsWalletSnapshot{Owner: projectOwner, Category: storage, MaxUsable: 50, ActiveUsage: 50},
			eligible: true,
			state:    lowFundsHealthy,
		},
		{
			name:     "healthy to exhausted notifies",
			snapshot: lowFundsWalletSnapshot{Owner: projectOwner, Category: storage, MaxUsable: 0, ActiveUsage: 100},
			eligible: true,
			state:    lowFundsExhausted,
			notify:   true,
		},
		{
			name:     "low to low does not notify",
			snapshot: lowFundsWalletSnapshot{Owner: projectOwner, Category: storage, MaxUsable: 5, ActiveUsage: 95},
			previous: lowFundsStoredState{State: lowFundsLow},
			eligible: true,
			state:    lowFundsLow,
		},
		{
			name:     "exhausted to exhausted does not notify",
			snapshot: lowFundsWalletSnapshot{Owner: projectOwner, Category: storage, MaxUsable: 0, ActiveUsage: 100},
			previous: lowFundsStoredState{State: lowFundsExhausted},
			eligible: true,
			state:    lowFundsExhausted,
		},
		{
			name:     "low to exhausted notifies",
			snapshot: lowFundsWalletSnapshot{Owner: projectOwner, Category: storage, MaxUsable: 0, ActiveUsage: 100},
			previous: lowFundsStoredState{State: lowFundsLow},
			eligible: true,
			state:    lowFundsExhausted,
			notify:   true,
		},
		{
			name:     "low wallet recovers above hysteresis",
			snapshot: lowFundsWalletSnapshot{Owner: projectOwner, Category: storage, MaxUsable: 25, ActiveUsage: 75},
			previous: lowFundsStoredState{State: lowFundsLow},
			eligible: true,
			state:    lowFundsHealthy,
		},
		{
			name:     "wallet remains low until recovery threshold",
			snapshot: lowFundsWalletSnapshot{Owner: projectOwner, Category: storage, MaxUsable: 12, ActiveUsage: 88},
			previous: lowFundsStoredState{State: lowFundsLow},
			eligible: true,
			state:    lowFundsLow,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			decision := lowFundsEvaluate(now, tt.snapshot, tt.previous, cfg)
			if decision.Eligible != tt.eligible {
				t.Fatalf("eligible = %v, want %v", decision.Eligible, tt.eligible)
			}
			if decision.NewState != tt.state {
				t.Fatalf("state = %v, want %v", decision.NewState, tt.state)
			}
			if decision.Notify != tt.notify {
				t.Fatalf("notify = %v, want %v", decision.Notify, tt.notify)
			}
		})
	}
}

func TestPromiseExpiresSoon(t *testing.T) {
	now := time.Date(2024, time.January, 1, 0, 0, 0, 0, time.UTC)
	tests := []struct {
		name    string
		promise *Promise
		want    bool
	}{
		{
			name:    "expires within 30 days and duration at least 90 days",
			promise: &Promise{Start: now.Add(-70 * 24 * time.Hour), End: now.Add(20 * 24 * time.Hour)},
			want:    true,
		},
		{
			name:    "does not notify before 30 day window",
			promise: &Promise{Start: now.Add(-70 * 24 * time.Hour), End: now.Add(31 * 24 * time.Hour)},
		},
		{
			name:    "does not notify for short promises",
			promise: &Promise{Start: now.Add(-10 * 24 * time.Hour), End: now.Add(20 * 24 * time.Hour)},
		},
		{
			name:    "does not notify after expiration",
			promise: &Promise{Start: now.Add(-100 * 24 * time.Hour), End: now.Add(-1 * time.Hour)},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := promiseExpiresSoon(now, tt.promise); got != tt.want {
				t.Fatalf("promiseExpiresSoon = %v, want %v", got, tt.want)
			}
		})
	}
}
