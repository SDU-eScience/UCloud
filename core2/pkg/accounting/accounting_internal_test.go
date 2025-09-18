package accounting

import (
	"fmt"
	"testing"
	accapi "ucloud.dk/shared/pkg/accounting"
)

func TestAllocations(t *testing.T) {
	e := newEnv(t, capacityCategory)

	e.AllocateEx(0, 0, 10, 1000, "user", "")
	e.AllocateEx(0, 1, 10, 10000, "user", "")
	e.ReportAbs(0, "user", 10)

	e.Scan(1)
	e.Scan(11)
	e.Snapshot("final-state", "user", false)
}

func TestExcessUsageHierarchyCapacity(t *testing.T) {
	e := newEnv(t, capacityCategory)

	e.AllocateEx(0, 0, 10, 1000, "P1", "")
	e.AllocateEx(0, 0, 100, 1000, "P2", "P1")

	e.ReportAbs(1, "P2", 100)
	e.Snapshot("initial", "P2", false)

	e.Scan(11)
	e.Snapshot("after-retire", "P2", false)

	e.AllocateEx(12, 12, 1000, 1000, "P1", "")
	e.Snapshot("after-new-alloc", "P2", false)
}

func TestRouting(t *testing.T) {
	e := newEnv(t, capacityCategory)

	e.Allocate(a{
		Now:       0,
		Start:     0,
		End:       10,
		Quota:     1000,
		Recipient: "provider",
		Parent:    "",
	})

	e.Allocate(a{
		Now:       0,
		Start:     0,
		End:       10,
		Quota:     1000,
		Recipient: "external",
		Parent:    "provider",
	})

	e.Allocate(a{
		Now:       0,
		Start:     0,
		End:       10,
		Quota:     1000,
		Recipient: "t1",
		Parent:    "provider",
	})

	e.Allocate(a{
		Now:       0,
		Start:     0,
		End:       10,
		Quota:     0,
		Recipient: "abc",
		Parent:    "external",
	})

	e.Allocate(a{
		Now:       0,
		Start:     0,
		End:       10,
		Quota:     0,
		Recipient: "abc",
		Parent:    "t1",
	})

	e.Allocate(a{
		Now:       0,
		Start:     0,
		End:       10,
		Quota:     1000,
		Recipient: "sub1",
		Parent:    "abc",
	})

	e.Allocate(a{
		Now:       0,
		Start:     0,
		End:       10,
		Quota:     1000,
		Recipient: "sub1",
		Parent:    "t1",
	})

	e.Allocate(a{
		Now:       0,
		Start:     0,
		End:       10,
		Quota:     1000,
		Recipient: "sub2",
		Parent:    "abc",
	})

	e.Allocate(a{
		Now:       0,
		Start:     0,
		End:       10,
		Quota:     1000,
		Recipient: "sub2",
		Parent:    "t1",
	})

	e.Allocate(a{
		Now:       0,
		Start:     0,
		End:       10,
		Quota:     0,
		Recipient: "dummy",
		Parent:    "sub2",
	})

	e.Allocate(a{
		Now:       0,
		Start:     0,
		End:       10,
		Quota:     0,
		Recipient: "dummy",
		Parent:    "sub1",
	})

	e.Snapshot("", "dummy", false)

	// This weird graph just needs to route around abc which is completely locked. This case has happened with the old
	// system before. The dummy node is just for the graphs and it won't be used.

	e.ReportDelta(0, "sub1", 10, "")
	e.Snapshot("", "dummy", false)

	e.ReportDelta(0, "sub2", 10, "")
	e.Snapshot("", "dummy", false)

	e.Expect("provider", 20, false)
}

func TestCapacityRetirementSwapInNode(t *testing.T) {
	e := newEnv(t, capacityCategory)

	e.AllocateEx(0, 0, 100, 1000, "sdu", "")
	e.AllocateEx(0, 0, 100, 1000, "sdu-nat", "sdu")
	e.AllocateEx(0, 0, 100, 1000, "sdu-tek", "sdu")

	e.AllocateEx(0, 0, 10, 1000, "p1", "sdu-nat")
	e.AllocateEx(0, 15, 20, 1000, "p1", "sdu-tek")

	// charge flows through NAT first
	// p1 will have to flow through TEK after its own allocation expires
	e.ReportAbs(1, "p1", 100)

	e.ExpectMany(map[string]want{
		"p1":      {PUsage: 100, Locked: false},
		"sdu-nat": {PUsage: 100, Locked: false},
		"sdu-tek": {PUsage: 0, Locked: false},
		"sdu":     {PUsage: 100, Locked: false},
	})

	e.Snapshot("initial", "p1", false)
	e.Scan(11)

	e.ExpectMany(map[string]want{
		"p1":      {PUsage: 100, Locked: true},
		"sdu-nat": {PUsage: 100, Locked: false},
		"sdu-tek": {PUsage: 0, Locked: false},
		"sdu":     {PUsage: 100, Locked: false},
	})

	e.Snapshot("after-retire", "p1", false)
	e.Scan(16)

	e.ExpectMany(map[string]want{
		"p1":      {PUsage: 100, Locked: false},
		"sdu-nat": {PUsage: 0, Locked: false},
		"sdu-tek": {PUsage: 100, Locked: false},
		"sdu":     {PUsage: 100, Locked: false},
	})

	e.Snapshot("after-activation", "p1", false)
}

func TestCapacityRetirementSwapInChild(t *testing.T) {
	e := newEnv(t, capacityCategory)

	e.AllocateEx(0, 0, 100, 1000, "sdu", "")
	e.AllocateEx(0, 0, 10, 1000, "sdu-nat", "sdu")
	e.AllocateEx(0, 0, 100, 1000, "sdu-tek", "sdu")

	// p1 prefers NAT because of larger quota
	// p1 will have to swap to TEK after retirement of the NAT allocation
	e.AllocateEx(0, 0, 2000, 10000, "p1", "sdu-nat")
	e.AllocateEx(0, 0, 2000, 1000, "p1", "sdu-tek")

	e.Snapshot("before-charge", "p1", true)
	e.ReportAbs(1, "p1", 100)

	e.ExpectMany(map[string]want{
		"p1":      {PUsage: 100, Locked: false},
		"sdu-nat": {PUsage: 100, Locked: false},
		"sdu-tek": {PUsage: 0, Locked: false},
		"sdu":     {PUsage: 100, Locked: false},
	})

	e.Snapshot("initial", "p1", false)
	e.Scan(11)
	e.Snapshot("after-retire", "p1", false)

	e.ExpectMany(map[string]want{
		"p1":      {PUsage: 100, Locked: false},
		"sdu-nat": {PUsage: 0, Locked: true},
		"sdu-tek": {PUsage: 100, Locked: false},
		"sdu":     {PUsage: 100, Locked: false},
	})
}

func TestExcessUsageHierarchyTime(t *testing.T) {
	e := newEnv(t, timeCategory)

	e.AllocateEx(0, 0, 10, 1000, "P1", "")
	e.AllocateEx(0, 0, 100, 1000, "P2", "P1")

	e.ReportAbs(1, "P2", 100)
	e.Snapshot("initial", "P2", false)

	e.Scan(11)
	e.Snapshot("after-retire", "P2", false)

	e.AllocateEx(12, 12, 1000, 1000, "P1", "")
	e.Snapshot("after-new-alloc", "P2", false)
}

func TestOverConsumptionFollowedByAllocation(t *testing.T) {
	runTable(t, []accapi.ProductCategory{timeCategory, capacityCategory}, func(e *env) {
		e.AllocateEx(0, 0, 10, 1000, "P1", "")
		e.AllocateEx(0, 0, 100, 100, "P2", "P1")

		e.ReportAbs(1, "P2", 150) // 50 over quota
		e.Snapshot("after-charge", "P2", false)

		e.ExpectMany(map[string]want{
			"P1": {PUsage: 100, Locked: false},
			"P2": {PUsage: 100, Locked: true},
		})

		e.AllocateEx(2, 0, 100, 100, "P2", "P1")
		e.Snapshot("after-extra-alloc", "P2", false)

		e.ExpectMany(map[string]want{
			"P1": {PUsage: 150, Locked: false},
			"P2": {PUsage: 150, Locked: false},
		})
	})
}

func TestCapacityParentRetireAfterChildOverspend(t *testing.T) {
	e := newEnv(t, capacityCategory)

	e.Allocate(a{
		Now:       0,
		Start:     0,
		End:       5,
		Quota:     1000,
		Recipient: "p1",
		Parent:    "",
	})

	e.Allocate(a{
		Now:       0,
		Start:     0,
		End:       10,
		Quota:     500,
		Recipient: "p2",
		Parent:    "p1",
	})

	// --------------------------------------------

	e.ReportAbs(0, "p2", 100)
	e.Snapshot("abs(p2, 100)", "p2", false)
	e.ExpectMany(map[string]want{
		"p1": {PUsage: 100, Locked: false},
		"p2": {PUsage: 100, Locked: false},
	})

	e.ReportAbs(0, "p2", 600)
	e.Snapshot("abs(p2, 600)", "p2", false)
	e.ExpectMany(map[string]want{
		"p1": {PUsage: 500, Locked: false},
		"p2": {PUsage: 500, Locked: true},
	})

	e.Scan(6)
	e.Snapshot("retire(p1)", "p2", false)
	e.ExpectMany(map[string]want{
		"p1": {PUsage: 500, Locked: true},
		"p2": {PUsage: 500, Locked: true},
	})

	e.ReportAbs(0, "p2", 200)
	e.Snapshot("abs(p2, 200)", "p2", false)
	e.ExpectMany(map[string]want{
		"p1": {PUsage: 200, Locked: true},
		"p2": {PUsage: 200, Locked: true},
	})

	e.Allocate(a{
		Now:       7,
		Start:     7,
		End:       10,
		Quota:     1000,
		Recipient: "p1",
		Parent:    "",
	})
	e.Snapshot("allocate(p1, 1000)", "p2", false)
	e.ExpectMany(map[string]want{
		"p1": {PUsage: 200, Locked: false},
		"p2": {PUsage: 200, Locked: false},
	})

	e.ReportAbs(0, "p2", 300)
	e.Snapshot("abs(p2, 300)", "p2", false)
	e.ExpectMany(map[string]want{
		"p1": {PUsage: 300, Locked: false},
		"p2": {PUsage: 300, Locked: false},
	})
}

func TestInjectResourcesAfterRetirement(t *testing.T) {
	for _, massive := range []bool{false, true} {
		name := "normal-charge"
		if massive {
			name = "massive-overcharge"
		}
		t.Run(name, func(t *testing.T) {
			e := newEnv(t, capacityCategory)

			// root quota directly on the provider
			e.AllocateEx(0, 0, 2_000_000, 1000, "provider", "")

			// give the project a tiny slice that will retire early
			e.AllocateEx(0, 0, 1_000, 50, "project", "provider")

			// project uses either 10 OK or 500 (way over quota)
			if massive {
				e.ReportDelta(0, "project", 500)
			} else {
				e.ReportDelta(0, "project", 10)
			}

			e.Snapshot("report", "project", false)

			// run the retirement scan (the original Kotlin test moved
			// StaticTimeProvider from 0 → 2000 before doing this)
			e.Scan(2_000)

			e.Snapshot("scan", "project", false)

			// provider injects more resources for the project
			e.AllocateEx(0, 0, 10_000, 50, "project", "provider")

			e.Snapshot("second alloc", "project", false)

			// invariants
			if massive {
				e.ExpectMany(map[string]want{
					"project":  {PUsage: 50, Locked: true},
					"provider": {PUsage: 50, Locked: false},
				})
				assertEqualMaxUsable(t, e, "project", 0)
			} else {
				e.ExpectMany(map[string]want{
					"project":  {PUsage: 10, Locked: false},
					"provider": {PUsage: 10, Locked: false},
				})
				assertEqualMaxUsable(t, e, "project", 40)
			}
		})
	}
}

func TestInjectResourcesAfterTotalUsage(t *testing.T) {
	type cfg struct {
		massiveOvercharge       bool
		secondAllocFromNewOwner bool
	}
	matrix := []cfg{
		{false, false},
		{false, true},
		{true, false},
		{true, true},
	}

	for _, c := range matrix {
		title := fmt.Sprintf("over=%v-second=%v", c.massiveOvercharge, c.secondAllocFromNewOwner)
		t.Run(title, func(t *testing.T) {
			e := newEnv(t, capacityCategory)

			e.AllocateEx(0, 0, 100_000, 1000000, "provider", "")

			// provider → allocator
			e.AllocateEx(0, 0, 1_000, 1000, "allocator", "provider")

			// provider may also seed a *second* allocator
			if c.secondAllocFromNewOwner {
				e.AllocateEx(0, 0, 10_000, 1000, "allocator-2", "provider")
			}

			// allocator → project
			e.AllocateEx(0, 0, 10_000, 10, "project", "allocator")

			// project gets charged
			if c.massiveOvercharge {
				e.ReportDelta(0, "project", 500)
			} else {
				e.ReportDelta(0, "project", 10)
			}

			// allocator (or second allocator) injects new resources
			parent := "allocator"
			if c.secondAllocFromNewOwner {
				parent = "allocator-2"
			}
			e.AllocateEx(0, 0, 10_000, 10, "project", parent)

			wantMax := int64(10)
			if c.massiveOvercharge {
				wantMax = 0
			}
			assertEqualMaxUsable(t, e, "project", wantMax)
		})
	}
}

func TestChildUsesAllCheckParent(t *testing.T) {
	e := newEnv(t, capacityCategory)

	// provider → project
	e.AllocateEx(0, 0, 1_000, 10, "project", "provider")

	// project → child
	e.AllocateEx(0, 0, 10_000, 10, "child", "project")

	// child spends everything
	e.ReportDelta(0, "child", 10)

	// parent should have zero head-room
	assertEqualMaxUsable(t, e, "project", 0)
}
