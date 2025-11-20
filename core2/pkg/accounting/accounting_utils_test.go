package accounting

import (
	"fmt"
	"os"
	"strings"
	"testing"
	"time"

	accapi "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/assert"
	"ucloud.dk/shared/pkg/util"
)

type env struct {
	t             *testing.T
	Bucket        *internalBucket
	diagram       *os.File
	TimeInMinutes bool
}

var capacityCategory = accapi.ProductCategory{
	Name:        "capacity",
	Provider:    "provider",
	ProductType: accapi.ProductTypeStorage,
	AccountingUnit: accapi.AccountingUnit{
		Name:                   "GB",
		NamePlural:             "GB",
		FloatingPoint:          false,
		DisplayFrequencySuffix: false,
	},
	AccountingFrequency: accapi.AccountingFrequencyOnce,
	FreeToUse:           false,
	AllowSubAllocations: true,
}

var timeCategory = accapi.ProductCategory{
	Name:        "compute",
	Provider:    "provider",
	ProductType: accapi.ProductTypeStorage,
	AccountingUnit: accapi.AccountingUnit{
		Name:                   "Core",
		NamePlural:             "Core",
		FloatingPoint:          false,
		DisplayFrequencySuffix: true,
	},
	AccountingFrequency: accapi.AccountingFrequencyPeriodicHour,
	FreeToUse:           false,
	AllowSubAllocations: true,
}

// New returns a fresh env for the given category.
func newEnv(t *testing.T, cat accapi.ProductCategory) *env {
	accGlobals.TestingEnabled = true
	close(providerWalletNotifications)
	providerWalletNotifications = make(chan AccWalletId, 128)
	go func() {
		for {
			_, ok := <-providerWalletNotifications
			if !ok {
				break
			}
		}
	}()
	t.Helper()

	accGlobals.OwnersByReference = map[string]*internalOwner{}
	accGlobals.OwnersById = map[accOwnerId]*internalOwner{}
	accGlobals.Usage = map[string]*scopedUsage{}
	accGlobals.BucketsByCategory = map[accapi.ProductCategoryIdV2]*internalBucket{}

	internalBucketOrInit(capacityCategory)
	internalBucketOrInit(timeCategory)

	b := internalBucketOrInit(cat)

	// Create a markdown scratch-file once per test and clean it up automatically.
	fileName := fmt.Sprintf("/tmp/diagram-%s.md", strings.ReplaceAll(t.Name(), "/", "-"))
	_ = os.Truncate(fileName, 0)

	f, _ := os.OpenFile(fileName, os.O_WRONLY|os.O_CREATE|os.O_APPEND, 0660)

	return &env{t: t, Bucket: b, diagram: f}
}

func (e *env) Tm(t int) time.Time {
	baseTime := time.Date(2000, time.January, 1, 0, 0, 0, 0, time.UTC)
	if e.TimeInMinutes {
		return baseTime.Add(time.Duration(t) * time.Minute)
	} else {
		return baseTime.AddDate(t, 0, 0)
	}
}

func (e *env) Owner(ref string) *internalOwner {
	return internalOwnerByReference(ref)
}

func (e *env) Wallet(owner *internalOwner, at time.Time) AccWalletId {
	return internalWalletByOwner(e.Bucket, at, owner.Id)
}

type a struct {
	Now       int
	Start     int
	End       int
	Quota     int64
	Recipient string
	Parent    string
}

func (e *env) Allocate(req a) accAllocId {
	return e.AllocateEx(req.Now, req.Start, req.End, req.Quota, req.Recipient, req.Parent)
}

func (e *env) AllocateEx(now, start, end int, quota int64, recipientRef, parentRef string) accAllocId {
	rcp := e.Wallet(e.Owner(recipientRef), e.Tm(now))
	var parent AccWalletId
	if parentRef != "" {
		parent = e.Wallet(e.Owner(parentRef), e.Tm(now))
	}
	id, err := internalAllocateNoCommit(e.Tm(now), e.Bucket, e.Tm(start), e.Tm(end), quota, rcp, parent, util.OptNone[accGrantId]())
	if err != nil {
		e.t.Fatalf("allocate: %v", err)
	}
	return id
}

// Report usage helpers.
func (e *env) ReportAbs(at int, ownerRef string, usage int64, scope ...string) {
	e.report(at, ownerRef, false, usage, scope...)
}
func (e *env) ReportDelta(at int, ownerRef string, usage int64, scope ...string) {
	e.report(at, ownerRef, true, usage, scope...)
}
func (e *env) report(at int, ownerRef string, delta bool, usage int64, scope ...string) {
	req := accapi.ReportUsageRequest{
		IsDeltaCharge: delta,
		Owner:         e.Owner(ownerRef).WalletOwner(),
		CategoryIdV2:  e.Bucket.Category.ToId(),
		Usage:         usage,
		Description:   accapi.ChargeDescription{},
	}
	if len(scope) > 0 && scope[0] != "" {
		req.Description.Scope = util.OptStringIfNotEmpty(scope[0])
	}
	if _, err := internalReportUsage(e.Tm(at), req); err != nil {
		e.t.Fatalf("report usage: %v", err)
	}
}

func (e *env) Scan(at int) {
	internalScanAllocations(e.Bucket, e.Tm(at))
}

func (e *env) Expect(ownerRef string, wantUsage int64, wantLocked bool) {
	e.t.Helper()
	wallet := e.Bucket.WalletsById[e.Wallet(e.Owner(ownerRef), e.Tm(0))]
	assert.Equal(e.t, wantUsage, lInternalWalletTotalPropagatedUsage(e.Bucket, wallet))
	assert.Equal(e.t, wantLocked, wallet.WasLocked)
}

func (e *env) Snapshot(step, walletRef string, charge bool) {
	walletId := e.Wallet(e.Owner(walletRef), e.Tm(0))

	var section string
	if charge {
		g := lInternalBuildGraph(e.Bucket, e.Tm(0), e.Bucket.WalletsById[walletId], 0)
		section = fmt.Sprintf("# Charge %s\n\n```mermaid\n%s\n```\n\n", step, g.ToMermaid())
	} else {
		section = fmt.Sprintf("# Step %s\n\n```mermaid\n%s\n```\n\n",
			step,
			lInternalMermaidGraph(e.Bucket, e.Tm(0), walletId),
		)
	}
	if _, err := e.diagram.WriteString(section); err != nil {
		e.t.Logf("diagram write failed: %v", err)
	}
}

type want struct {
	PUsage int64
	Locked bool
}

func (e *env) ExpectMany(wants map[string]want) {
	e.t.Helper()
	for ref, w := range wants {
		e.Expect(ref, w.PUsage, w.Locked)
	}
}

func runTable(t *testing.T, cats []accapi.ProductCategory, fn func(*env)) {
	for _, cat := range cats {
		t.Run(cat.Name, func(t *testing.T) {
			fn(newEnv(t, cat))
		})
	}
}

func (e *env) MaxUsable(ownerRef string) int64 {
	wallet := e.Bucket.WalletsById[e.Wallet(e.Owner(ownerRef), e.Tm(0))]
	return lInternalMaxUsable(e.Bucket, e.Tm(0), wallet)
}

func assertEqualMaxUsable(t *testing.T, e *env, owner string, want int64) {
	if got := e.MaxUsable(owner); got != want {
		t.Fatalf("MaxUsable(%s) = %d, want %d", owner, got, want)
	}
}
