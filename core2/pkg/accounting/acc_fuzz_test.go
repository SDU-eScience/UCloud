package accounting

import (
	"fmt"
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

func fuzzCreateAllocation(t *testing.T, e *lowTestEnv, reader *fuzzBytes, at int, name string, wallets []string, allocations []string) (bool, string) {
	t.Helper()
	start := reader.intn(36)
	end := start + 1 + reader.intn(8)
	quota := reader.int64n(260)
	parent := util.OptNone[AllocationId]()
	if len(allocations) > 0 && reader.intn(2) == 1 {
		parent = util.OptValue(e.allocs[allocations[reader.intn(len(allocations))]])
	}

	wallet := wallets[reader.intn(len(wallets))]
	id, _ := AllocationCreate(e.tm(at), e.categoryId, e.tm(start), e.tm(end), quota, e.wallet(wallet), parent, util.OptNone[GrantId]())
	e.assertValid()
	if id == 0 {
		return false, ""
	}
	e.allocs[name] = id
	return true, name
}

func fuzzCreatePromise(t *testing.T, e *lowTestEnv, reader *fuzzBytes, at int, owners []string) (bool, PromiseId) {
	t.Helper()
	parent := owners[reader.intn(len(owners))]
	child := owners[reader.intn(len(owners))]
	start := reader.intn(24)
	end := start + reader.intn(24)
	quota := reader.int64n(180)
	id, _ := PromiseCreate(e.tm(at), e.categoryId, e.wallet(parent), e.wallet(child), e.tm(start), e.tm(end), quota, util.OptNone[GrantId]())
	e.assertValid()
	return id != 0, id
}

func fuzzUpdatePromiseMaterialization(t *testing.T, e *lowTestEnv, reader *fuzzBytes, at int, promises []PromiseId) {
	t.Helper()
	if len(promises) == 0 {
		return
	}
	promise := e.tree().PromiseTree.PromisesById[promises[reader.intn(len(promises))]]
	if promise == nil {
		e.assertValid()
		return
	}
	materializations := promiseAllocationsFor(e.tm(at), e.tree(), promise, true)
	if len(materializations) == 0 {
		e.assertValid()
		return
	}

	quota := util.OptNone[int64]()
	start := util.OptNone[time.Time]()
	end := util.OptNone[time.Time]()
	switch reader.intn(3) {
	case 0:
		quota.Set(reader.int64n(180))
	case 1:
		start.Set(e.tm(reader.intn(24)))
	case 2:
		end.Set(e.tm(reader.intn(30)))
	}
	_, _, _ = AllocationUpdate(e.tm(at), e.categoryId, materializations[0].Id, quota, start, end)
	e.assertValid()
}

func FuzzLowLevelAPI(f *testing.F) {
	f.Add([]byte{0, 1, 2, 3, 4, 5, 6, 7})
	f.Add([]byte{9, 8, 7, 6, 5, 4, 3, 2, 1})
	f.Add([]byte{1, 20, 2, 90, 3, 40, 4, 10, 5, 200})
	f.Add([]byte{0, 0, 0, 80, 0, 4, 10, 0, 20, 1, 8, 30, 2, 12, 40, 4, 2, 5, 6})

	f.Fuzz(func(t *testing.T, data []byte) {
		if len(data) == 0 {
			t.Skip()
		}

		frequency := accapi.AccountingFrequencyOnce
		if data[0]%2 == 1 {
			frequency = accapi.AccountingFrequencyPeriodicHour
		}

		e := newLowTestEnv(t, frequency)
		e.add(lowAllocSpec{Name: "root", Wallet: "root", Start: 0, End: 48, Quota: 300, Self: 80, Children: 220})
		e.add(lowAllocSpec{Name: "short-a", Wallet: "a", Parent: "root", Start: 0, End: 4, Quota: 60})
		e.add(lowAllocSpec{Name: "short-b", Wallet: "b", Parent: "root", Start: 3, End: 8, Quota: 60})
		e.add(lowAllocSpec{Name: "long", Wallet: "long", Parent: "root", Start: 0, End: 36, Quota: 60, Self: 30, Children: 30})
		e.add(lowAllocSpec{Name: "leaf", Wallet: "leaf", Parent: "long", Start: 0, End: 12, Quota: 20})

		reader := fuzzBytes{data: data}
		wallets := []string{"a", "b", "long", "leaf", "extra", "missing"}
		allocations := []string{"short-a", "short-b", "long", "leaf"}
		parents := []string{"root", "short-a", "short-b", "long", "leaf"}

		steps := 1 + reader.intn(48)
		for i := 0; i < steps; i++ {
			at := reader.intn(56)
			switch reader.intn(7) {
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
			case 6:
				ok, name := fuzzCreateAllocation(t, e, &reader, at, fmt.Sprintf("dyn-%d", i), wallets, parents)
				if ok {
					allocations = append(allocations, name)
					parents = append(parents, name)
				}
			}
		}
	})
}

func FuzzPromiseSystem(f *testing.F) {
	f.Add([]byte{0, 40, 1, 80, 2, 20, 3, 100})
	f.Add([]byte{5, 100, 4, 90, 3, 80, 2, 70})
	f.Add([]byte{7, 20, 8, 120, 9, 10, 10, 50})
	f.Add([]byte{0, 0, 5, 2, 0, 0, 20, 100, 1, 80, 3, 0, 2, 2, 60, 3, 1})
	f.Add([]byte{0, 0, 0, 4, 80, 2, 5, 10, 4, 8, 3, 6, 60, 0, 13, 20, 4, 14})
	f.Add([]byte{1, 1, 0, 5, 100})

	f.Fuzz(func(t *testing.T, data []byte) {
		if len(data) == 0 {
			t.Skip()
		}

		frequency := accapi.AccountingFrequencyOnce
		if len(data) > 1 && data[1]%2 == 1 {
			frequency = accapi.AccountingFrequencyPeriodicHour
		}

		e := newLowTestEnv(t, frequency)

		e.add(lowAllocSpec{Name: "root0-a", Wallet: "root", Start: 0, End: 12, Quota: 50, Self: 0, Children: 50})
		e.add(lowAllocSpec{Name: "root0-b", Wallet: "root", Start: 0, End: 12, Quota: 50, Self: 0, Children: 50})
		e.add(lowAllocSpec{Name: "root1", Wallet: "root", Start: 12, End: 24, Quota: 100, Self: 0, Children: 100})
		e.add(lowAllocSpec{Name: "root2", Wallet: "root", Start: 24, End: 48, Quota: 100, Self: 0, Children: 100})
		promises := []PromiseId{
			e.addPromise("root", "a", 0, 24, 120),
			e.addPromise("root", "b", 0, 24, 120),
			e.addPromise("root", "split", 0, 12, 100),
			e.addPromise("a", "leaf", 0, 24, 100),
			e.addPromise("b", "leaf", 0, 24, 100),
			e.addPromise("root", "a", 6, 18, 80),
			e.addPromise("root", "short", 0, 4, 100),
			e.addPromise("a", "short", 2, 7, 80),
		}

		reader := fuzzBytes{data: data}
		wallets := []string{"a", "b", "leaf", "short", "root", "split", "missing"}
		owners := []string{"a", "b", "leaf", "short", "root", "split"}

		steps := 1 + reader.intn(64)
		for i := 0; i < steps; i++ {
			at := reader.intn(56)
			usage := reader.int64n(220)
			switch reader.intn(7) {
			case 0, 1, 2:
				fuzzReport(t, e, at, wallets[reader.intn(len(wallets))], usage)
			case 3:
				e.reconcile(at, owners[reader.intn(len(owners))], usage)
			case 4:
				e.reconcile(at, owners[reader.intn(len(owners))], 0)
			case 5:
				if ok, id := fuzzCreatePromise(t, e, &reader, at, owners); ok {
					promises = append(promises, id)
				}
			case 6:
				fuzzUpdatePromiseMaterialization(t, e, &reader, at, promises)
			}
		}
	})
}
