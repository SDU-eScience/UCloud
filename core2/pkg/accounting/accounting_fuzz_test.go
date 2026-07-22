package accounting

import (
	"fmt"
	"testing"

	accapi "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/util"
)

// FuzzAccountingGraphOperations exercises valid DAGs while keeping a small model of
// local and scoped usage. Runtime accounting invariant checks validate the flow state
// after every operation; the explicit checks below also cover full-bucket topology.
func FuzzAccountingGraphOperations(f *testing.F) {
	for _, capacity := range []bool{false, true} {
		for _, scoped := range []bool{false, true} {
			for complexity := byte(0); complexity < 3; complexity++ {
				seed := []byte{complexity + 1, 7, 19, 31, 43, 59, 71, 83, 97, 109, 127, 149}
				f.Add(capacity, scoped, complexity, seed)
			}
		}
	}

	f.Fuzz(func(t *testing.T, capacity, scoped bool, complexity byte, input []byte) {
		data := fuzzAccountingBytes{data: input}
		category := timeCategory
		if capacity {
			category = capacityCategory
		}
		e := newEnv(t, category, false) // Reports bypass env helpers and the reference stream would be incomplete.
		_ = e.diagram.Close()

		level := complexity % 3
		walletCount := 1
		switch level {
		case 1:
			walletCount = 2 + int(data.next()%4)
		case 2:
			walletCount = 4 + int(data.next()%5)
		}

		owners := make([]string, walletCount)
		earlyAllocations := make([]accAllocId, 0, walletCount*2)
		laterAllocations := make([]accAllocId, 0, walletCount*2)
		for i := range owners {
			owners[i] = fmt.Sprintf("fuzz-owner-%d", i)
			parents := []int{-1}
			if i > 0 {
				if level == 1 {
					parents = []int{i - 1}
				} else {
					parents = []int{(i - 1) / 2}
					if level == 2 && i >= 3 && i-2 != parents[0] {
						parents = append(parents, i-2)
					}
				}
			}

			for _, parentIndex := range parents {
				parent := ""
				quota := int64(500)
				if parentIndex >= 0 {
					parent = owners[parentIndex]
					quota = 40 + int64(data.next()%61)
				}
				earlyAllocations = append(earlyAllocations, e.AllocateEx(0, 0, 10, quota, owners[i], parent))
				laterAllocations = append(laterAllocations, e.AllocateEx(0, 10, 100, quota, owners[i], parent))
			}
		}
		assertFuzzAccountingValid(t, e, 0)

		localUsage := make([]int64, walletCount)
		scopeUsage := make([][2]int64, walletCount)
		for i, owner := range owners {
			first := int64(1 + data.next()%5)
			second := int64(1 + data.next()%5)
			if scoped {
				reportFuzzAccounting(t, e, 1, owner, false, first, "scope-a")
				reportFuzzAccounting(t, e, 2, owner, false, first, "scope-a") // absolute reports are idempotent
				reportFuzzAccounting(t, e, 3, owner, true, second, "scope-b")
				scopeUsage[i] = [2]int64{first, second}
			} else {
				reportFuzzAccounting(t, e, 1, owner, false, first, "")
				reportFuzzAccounting(t, e, 2, owner, false, first, "") // absolute reports are idempotent
				reportFuzzAccounting(t, e, 3, owner, true, second, "")
			}
			localUsage[i] = first + second
		}
		assertFuzzAccountingUsage(t, e, owners, localUsage, scopeUsage, scoped)
		assertFuzzAccountingValid(t, e, 3)

		// The exact end/start boundary retires the first allocation in every
		// group and activates its replacement before more usage is routed.
		e.Scan(10)
		for _, id := range earlyAllocations {
			allocation := e.Bucket.AllocationsById[id]
			if !allocation.Retired {
				t.Fatalf("allocation %d was not retired at its exclusive end", id)
			}
			if !capacity && allocation.Quota != allocation.RetiredUsage {
				t.Fatalf("retired periodic allocation %d quota = %d, retired usage = %d", id, allocation.Quota, allocation.RetiredUsage)
			}
		}
		for _, id := range laterAllocations {
			allocation := e.Bucket.AllocationsById[id]
			if !allocation.Active || allocation.Retired {
				t.Fatalf("replacement allocation %d is not current at its start", id)
			}
		}
		assertFuzzAccountingUsage(t, e, owners, localUsage, scopeUsage, scoped)
		assertFuzzAccountingValid(t, e, 10)

		actionCount := 8 + int(data.next()%17)
		for action := 0; action < actionCount; action++ {
			ownerIndex := int(data.next()) % walletCount
			scopeIndex := int(data.next() % 2)
			isDelta := data.next()%2 == 0
			current := localUsage[ownerIndex]
			scope := ""
			if scoped {
				current = scopeUsage[ownerIndex][scopeIndex]
				scope = []string{"scope-a", "scope-b"}[scopeIndex]
			}

			var report int64
			var delta int64
			if isDelta {
				if capacity {
					delta = int64(data.next()%31) - 15
					if delta < -current {
						delta = -current
					}
				} else {
					delta = int64(data.next() % 21)
				}
				report = delta
			} else {
				if capacity {
					report = int64(data.next() % 41)
				} else {
					report = current + int64(data.next()%21)
				}
				delta = report - current
			}

			reportFuzzAccounting(t, e, 11+action, owners[ownerIndex], isDelta, report, scope)
			localUsage[ownerIndex] += delta
			if scoped {
				scopeUsage[ownerIndex][scopeIndex] += delta
			}
			assertFuzzAccountingUsage(t, e, owners, localUsage, scopeUsage, scoped)
			assertFuzzAccountingValid(t, e, 11+action)
		}

		// Finally remove all entitlement. Capacity gauges become excess while
		// periodic flow remains covered only by its committed retired usage.
		e.Scan(100)
		for _, id := range laterAllocations {
			if !e.Bucket.AllocationsById[id].Retired {
				t.Fatalf("allocation %d was not retired at its exclusive end", id)
			}
		}
		assertFuzzAccountingUsage(t, e, owners, localUsage, scopeUsage, scoped)
		assertFuzzAccountingValid(t, e, 100)
	})
}

type fuzzAccountingBytes struct {
	data   []byte
	offset int
}

func (b *fuzzAccountingBytes) next() byte {
	if len(b.data) == 0 {
		return 0
	}
	result := b.data[b.offset%len(b.data)]
	b.offset++
	return result
}

func reportFuzzAccounting(t *testing.T, e *env, at int, owner string, delta bool, usage int64, scope string) {
	t.Helper()
	request := accapi.ReportUsageRequest{
		IsDeltaCharge: delta,
		Owner:         e.Owner(owner).WalletOwner(),
		CategoryIdV2:  e.Bucket.Category.ToId(),
		Usage:         usage,
	}
	if scope != "" {
		request.Description.Scope = util.OptValue(scope)
	}
	if _, err := internalReportUsage(e.Tm(at), request); err != nil {
		t.Fatalf("report owner=%s delta=%t usage=%d scope=%q: %v", owner, delta, usage, scope, err)
	}
}

func assertFuzzAccountingUsage(t *testing.T, e *env, owners []string, local []int64, scopes [][2]int64, scoped bool) {
	t.Helper()
	for i, ownerReference := range owners {
		owner := e.Owner(ownerReference)
		wallet := e.Bucket.WalletsById[e.Wallet(owner, e.Tm(0))]
		if wallet.LocalUsage != local[i] {
			t.Fatalf("wallet %s local usage = %d, want %d", ownerReference, wallet.LocalUsage, local[i])
		}
		if !scoped {
			continue
		}
		for scopeIndex, scopeName := range []string{"scope-a", "scope-b"} {
			key := fmt.Sprintf("%d\n%s", owner.Id, scopeName)
			scope := accGlobals.Usage[key]
			if scope == nil || scope.Usage != scopes[i][scopeIndex] {
				got := int64(-1)
				if scope != nil {
					got = scope.Usage
				}
				t.Fatalf("wallet %s scope %s usage = %d, want %d", ownerReference, scopeName, got, scopes[i][scopeIndex])
			}
		}
	}
}

func assertFuzzAccountingValid(t *testing.T, e *env, at int) {
	t.Helper()
	if err := internalValidateAccountingTree(e.Bucket, e.Tm(at)); err != nil {
		t.Fatalf("invalid accounting state at %d: %v", at, err)
	}
}
