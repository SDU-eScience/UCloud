package accounting

import (
	"testing"
	"time"

	accapi "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/util"
)

type fuzzBytes struct {
	data []byte
	pos  int
}

func (f *fuzzBytes) next() byte {
	if len(f.data) == 0 {
		return 0
	}
	value := f.data[f.pos%len(f.data)]
	f.pos++
	return value
}

func (f *fuzzBytes) intn(n int) int {
	if n <= 0 {
		return 0
	}
	return int(f.next()) % n
}

func (f *fuzzBytes) int64n(n int64) int64 {
	if n <= 0 {
		return 0
	}
	return int64(f.next()) % n
}

func fuzzReport(t *testing.T, e *lowTestEnv, at int, wallet string, usage int64) {
	t.Helper()
	_, _ = UsageReport(e.tm(at), accapi.ReportUsageRequest{
		Owner:        e.owner(wallet),
		CategoryIdV2: e.categoryId,
		Usage:        usage,
	})
	e.assertValid()
}

func fuzzUpdate(t *testing.T, e *lowTestEnv, at int, allocation string, quota util.Option[int64], start util.Option[int], end util.Option[int]) {
	t.Helper()
	startTime := util.OptNone[time.Time]()
	if start.Present {
		startTime.Set(e.tm(start.Value))
	}
	endTime := util.OptNone[time.Time]()
	if end.Present {
		endTime.Set(e.tm(end.Value))
	}
	_, _, _ = AllocationUpdate(e.tm(at), e.categoryId, e.allocs[allocation], quota, startTime, endTime)
	e.assertValid()
}

func FuzzLowLevelAPI(f *testing.F) {
	f.Add([]byte{0, 1, 2, 3, 4, 5, 6, 7})
	f.Add([]byte{9, 8, 7, 6, 5, 4, 3, 2, 1})
	f.Add([]byte{1, 20, 2, 90, 3, 40, 4, 10, 5, 200})

	f.Fuzz(func(t *testing.T, data []byte) {
		if len(data) == 0 {
			t.Skip()
		}

		frequency := accapi.AccountingFrequencyOnce
		if data[0]%2 == 1 {
			frequency = accapi.AccountingFrequencyPeriodicHour
		}

		e := newLowTestEnv(t, frequency)
		e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 24, Quota: 240, Self: 80, Children: 160})
		e.add(lowAllocSpec{Name: "a", Wallet: "a", Parent: "root", Start: 0, End: 12, Quota: 60, Self: 30, Children: 30})
		e.add(lowAllocSpec{Name: "b", Wallet: "b", Parent: "root", Start: 6, End: 24, Quota: 60})
		e.add(lowAllocSpec{Name: "leaf", Wallet: "leaf", Parent: "a", Start: 0, End: 12, Quota: 20})

		reader := fuzzBytes{data: data}
		wallets := []string{"root", "a", "b", "leaf", "missing"}
		allocations := []string{"root", "a", "b", "leaf"}

		steps := 1 + reader.intn(48)
		for i := 0; i < steps; i++ {
			at := reader.intn(30)
			switch reader.intn(6) {
			case 0, 1, 2:
				fuzzReport(t, e, at, wallets[reader.intn(len(wallets))], reader.int64n(260))
			case 3:
				fuzzUpdate(t, e, at, allocations[reader.intn(len(allocations))], util.OptValue(reader.int64n(260)), util.OptNone[int](), util.OptNone[int]())
			case 4:
				start := reader.intn(24)
				end := start + reader.intn(12)
				fuzzUpdate(t, e, at, allocations[reader.intn(len(allocations))], util.OptNone[int64](), util.OptValue(start), util.OptValue(end))
			case 5:
				e.assertValid()
			}
		}
	})
}

func FuzzPromiseSystem(f *testing.F) {
	f.Add([]byte{0, 40, 1, 80, 2, 20, 3, 100})
	f.Add([]byte{5, 100, 4, 90, 3, 80, 2, 70})
	f.Add([]byte{7, 20, 8, 120, 9, 10, 10, 50})

	f.Fuzz(func(t *testing.T, data []byte) {
		if len(data) == 0 {
			t.Skip()
		}

		e := newLowTestEnv(t, accapi.AccountingFrequencyOnce)
		e.setPolicy(PromisePolicy{
			MinSlack:              int64(data[0] % 16),
			GrowthStep:            int64(data[0] % 8),
			TrendAlphaBasisPoints: 2500 + int64(data[0]%4)*2500,
		})

		e.add(lowAllocSpec{Name: "root0", Wallet: "root", Start: 0, End: 12, Quota: 100, Self: 0, Children: 100})
		e.add(lowAllocSpec{Name: "root1", Wallet: "root", Start: 12, End: 24, Quota: 100, Self: 0, Children: 100})
		e.addPromise("root", "a", 0, 24, 120)
		e.addPromise("root", "b", 0, 24, 120)
		e.addPromise("a", "leaf", 0, 24, 100)
		e.addPromise("b", "leaf", 0, 24, 100)
		e.addPromise("root", "a", 6, 18, 80)

		reader := fuzzBytes{data: data}
		wallets := []string{"a", "b", "leaf", "root", "missing"}
		owners := []string{"a", "b", "leaf", "root"}

		steps := 1 + reader.intn(64)
		for i := 0; i < steps; i++ {
			at := reader.intn(30)
			usage := reader.int64n(220)
			switch reader.intn(5) {
			case 0, 1, 2:
				fuzzReport(t, e, at, wallets[reader.intn(len(wallets))], usage)
			case 3:
				e.reconcile(at, owners[reader.intn(len(owners))], util.OptValue(usage))
			case 4:
				e.reconcile(at, owners[reader.intn(len(owners))], util.OptNone[int64]())
			}
		}
	})
}
