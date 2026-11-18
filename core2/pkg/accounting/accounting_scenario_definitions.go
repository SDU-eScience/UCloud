package accounting

import (
	"cmp"
	"fmt"
	"slices"
	"time"
)

const (
	accountingScenarioPortfolioName    = "portfolio-500"
	accountingScenarioPortfolioTimeout = 5 * time.Minute
)

type accountingScenarioExpected struct {
	Granted       int64 `json:"granted"`
	Used          int64 `json:"used"`
	ActiveQuota   int64 `json:"activeQuota"`
	ActiveUsed    int64 `json:"activeUsed"`
	ActiveBalance int64 `json:"activeBalance"`
	RetiredUsed   int64 `json:"retiredUsed"`
	ExpiredUnused int64 `json:"expiredUnused"`
	Future        int64 `json:"future"`
}

type accountingScenarioStep struct {
	Kind            string
	At              int
	AtMillis        int
	Start           int
	End             int
	Quota           int64
	Parent          string
	Owner           string
	Package         string
	Label           string
	Expected        *accountingScenarioExpected
	ExpectedByOwner map[string]accountingScenarioExpected
}

type accountingScenarioDefinition struct {
	Name        string
	Code        string
	Description string
	Capacity    bool
	MinuteUnits bool
	RootQuota   int64
	ExplicitRun bool
	Steps       []accountingScenarioStep
}

func accountingScenarioDefinitions() []accountingScenarioDefinition {
	return []accountingScenarioDefinition{
		{
			Name:        "single-grant",
			Code:        "SING",
			Description: "One grant with partial consumption.",
			Steps: []accountingScenarioStep{
				{Kind: "allocate", At: -90, Start: -90, End: 90, Quota: 10000},
				{Kind: "charge", At: -60, Quota: 3000},
				{
					Kind: "checkpoint", At: 0, Label: "Partial consumption",
					Expected: &accountingScenarioExpected{
						Granted:       10000,
						Used:          3000,
						ActiveQuota:   10000,
						ActiveUsed:    3000,
						ActiveBalance: 7000,
					},
				},
			},
		},
		{
			Name:        "overlapping-grants",
			Code:        "OVLP",
			Description: "An expiring slice absorbs charges reported before it started; total Used must not fall.",
			Steps: []accountingScenarioStep{
				{Kind: "allocate", At: -90, Start: -90, End: 90, Quota: 10000},
				{Kind: "charge", At: -60, Quota: 3000},
				{Kind: "allocate", At: -30, Start: -30, End: 0, Quota: 1000, Package: "monthly"},
				{
					Kind: "checkpoint", At: -1, Label: "Before slice expiry",
					Expected: &accountingScenarioExpected{
						Granted:       11000,
						Used:          3000,
						ActiveQuota:   11000,
						ActiveUsed:    3000,
						ActiveBalance: 8000,
					},
				},
				{
					Kind: "checkpoint", At: 0, Label: "At slice expiry",
					Expected: &accountingScenarioExpected{
						Granted:       11000,
						Used:          3000,
						ActiveQuota:   10000,
						ActiveUsed:    2000,
						ActiveBalance: 8000,
						RetiredUsed:   1000,
					},
				},
			},
		},
		{
			Name:        "slice-renewal",
			Code:        "RNWL",
			Description: "A release schedule with partial charges, renewals, and one future release.",
			Steps: []accountingScenarioStep{
				{Kind: "allocate", At: -90, Start: -90, End: -60, Quota: 1000, Package: "schedule"},
				{Kind: "allocate", At: -90, Start: -60, End: -30, Quota: 1000, Package: "schedule"},
				{Kind: "allocate", At: -90, Start: -30, End: 30, Quota: 1000, Package: "schedule"},
				{Kind: "allocate", At: -90, Start: 30, End: 60, Quota: 1000, Package: "schedule"},
				{Kind: "charge", At: -75, Quota: 500},
				{
					Kind: "checkpoint", At: -61, Label: "Before first renewal",
					Expected: &accountingScenarioExpected{
						Granted:       4000,
						Used:          500,
						ActiveQuota:   1000,
						ActiveUsed:    500,
						ActiveBalance: 500,
						Future:        3000,
					},
				},
				{
					Kind: "checkpoint", At: -60, Label: "First renewal",
					Expected: &accountingScenarioExpected{
						Granted:       4000,
						Used:          500,
						ActiveQuota:   1000,
						ActiveBalance: 1000,
						RetiredUsed:   500,
						ExpiredUnused: 500,
						Future:        2000,
					},
				},
				{Kind: "charge", At: -45, Quota: 500},
				{
					Kind: "checkpoint", At: -30, Label: "Second renewal",
					Expected: &accountingScenarioExpected{
						Granted:       4000,
						Used:          1000,
						ActiveQuota:   1000,
						ActiveBalance: 1000,
						RetiredUsed:   1000,
						ExpiredUnused: 1000,
						Future:        1000,
					},
				},
				{Kind: "charge", At: -15, Quota: 500},
				{
					Kind: "checkpoint", At: 0, Label: "Current release",
					Expected: &accountingScenarioExpected{
						Granted:       4000,
						Used:          1500,
						ActiveQuota:   1000,
						ActiveUsed:    500,
						ActiveBalance: 500,
						RetiredUsed:   1000,
						ExpiredUnused: 1000,
						Future:        1000,
					},
				},
			},
		},
		{
			Name:        "quarterly-overlap",
			Code:        "QOVR",
			Description: "Two relative years of quarterly releases, each with a separate grant covering quarters two through four.",
			Steps: []accountingScenarioStep{
				{Kind: "allocate", At: -500, Start: -500, End: -409, Quota: 1000, Package: "previous-year"},
				{Kind: "allocate", At: -500, Start: -409, End: -318, Quota: 1000, Package: "previous-year"},
				{Kind: "allocate", At: -500, Start: -318, End: -227, Quota: 1000, Package: "previous-year"},
				{Kind: "allocate", At: -500, Start: -227, End: -135, Quota: 1000, Package: "previous-year"},
				{Kind: "charge", At: -455, Quota: 400},
				{Kind: "allocate", At: -409, Start: -409, End: -135, Quota: 2200},
				{Kind: "charge", At: -365, Quota: 600},
				{Kind: "charge", At: -275, Quota: 400},
				{Kind: "charge", At: -180, Quota: 1300},
				{
					Kind: "checkpoint", At: -136, Label: "Previous year: usage exceeds separate grant but not quarterly schedule",
					Expected: &accountingScenarioExpected{
						Granted: 6200, Used: 2700, ActiveQuota: 3200, ActiveUsed: 1300, ActiveBalance: 1900,
						RetiredUsed: 1400, ExpiredUnused: 1600,
					},
				},
				{
					Kind: "checkpoint", At: -135, Label: "Previous year: both grants expired",
					Expected: &accountingScenarioExpected{
						Granted: 6200, Used: 2700, RetiredUsed: 2700, ExpiredUnused: 3500,
					},
				},
				{Kind: "allocate", At: -135, Start: -135, End: -44, Quota: 1000, Package: "current-year"},
				{Kind: "allocate", At: -135, Start: -44, End: 47, Quota: 1000, Package: "current-year"},
				{Kind: "allocate", At: -135, Start: 47, End: 138, Quota: 1000, Package: "current-year"},
				{Kind: "allocate", At: -135, Start: 138, End: 230, Quota: 1000, Package: "current-year"},
				{Kind: "charge", At: -90, Quota: 350},
				{Kind: "allocate", At: -44, Start: -44, End: 230, Quota: 2200},
				{
					Kind: "checkpoint", At: -44, Label: "Current year: first quarter expired and overlap begins",
					Expected: &accountingScenarioExpected{
						Granted: 12400, Used: 3050, ActiveQuota: 3200, ActiveBalance: 3200,
						RetiredUsed: 3050, ExpiredUnused: 4150, Future: 2000,
					},
				},
				{Kind: "charge", At: -15, Quota: 700},
				{
					Kind: "checkpoint", At: 0, Label: "Current year: usage below both grants with two future quarters",
					Expected: &accountingScenarioExpected{
						Granted: 12400, Used: 3750, ActiveQuota: 3200, ActiveUsed: 700, ActiveBalance: 2500,
						RetiredUsed: 3050, ExpiredUnused: 4150, Future: 2000,
					},
				},
			},
		},
		{
			Name:        "expired-unused",
			Code:        "EXUN",
			Description: "An unused grant expires; Used stays zero and expired-unused becomes the full amount.",
			Steps: []accountingScenarioStep{
				{Kind: "allocate", At: -30, Start: -30, End: -1, Quota: 1000},
				{
					Kind: "checkpoint", At: 0, Label: "Unused expired grant",
					Expected: &accountingScenarioExpected{Granted: 1000, ExpiredUnused: 1000},
				},
			},
		},
		{
			Name:        "expired-consumed",
			Code:        "EXCO",
			Description: "A fully consumed grant expires; Used must remain 1000.",
			Steps: []accountingScenarioStep{
				{Kind: "allocate", At: -30, Start: -30, End: -1, Quota: 1000},
				{Kind: "charge", At: -15, Quota: 1000},
				{
					Kind: "checkpoint", At: 0, Label: "Consumed expired grant",
					Expected: &accountingScenarioExpected{Granted: 1000, Used: 1000, RetiredUsed: 1000},
				},
			},
		},
		{
			Name:        "release-gap",
			Code:        "RGAP",
			Description: "An expired slice and a future slice, with no active funding.",
			Steps: []accountingScenarioStep{
				{Kind: "allocate", At: -60, Start: -60, End: -30, Quota: 1000, Package: "schedule"},
				{Kind: "allocate", At: -60, Start: 30, End: 60, Quota: 1000, Package: "schedule"},
				{Kind: "charge", At: -45, Quota: 400},
				{
					Kind: "checkpoint", At: 0, Label: "Between releases",
					Expected: &accountingScenarioExpected{
						Granted:       2000,
						Used:          400,
						RetiredUsed:   400,
						ExpiredUnused: 600,
						Future:        1000,
					},
				},
			},
		},
		{
			Name:        "simultaneous-expiry",
			Code:        "SIMX",
			Description: "Two grants expire together; retirement must not count the same charges twice.",
			Steps: []accountingScenarioStep{
				{Kind: "allocate", At: -30, Start: -30, End: 0, Quota: 1000},
				{Kind: "allocate", At: -30, Start: -30, End: 0, Quota: 1000},
				{Kind: "charge", At: -15, Quota: 1500},
				{
					Kind: "checkpoint", At: -1, Label: "Before joint expiry",
					Expected: &accountingScenarioExpected{
						Granted:       2000,
						Used:          1500,
						ActiveQuota:   2000,
						ActiveUsed:    1500,
						ActiveBalance: 500,
					},
				},
				{
					Kind: "checkpoint", At: 0, Label: "Joint expiry",
					Expected: &accountingScenarioExpected{
						Granted:       2000,
						Used:          1500,
						RetiredUsed:   1500,
						ExpiredUnused: 500,
					},
				},
			},
		},
		{
			Name:        "capacity-renewal",
			Code:        "CAPR",
			Description: "Storage remains charged across renewal, then decreases; historical usage is not added.",
			Capacity:    true,
			Steps: []accountingScenarioStep{
				{Kind: "allocate", At: -60, Start: -60, End: -30, Quota: 1000},
				{Kind: "allocate", At: -60, Start: -30, End: 30, Quota: 1000},
				{Kind: "charge", At: -45, Quota: 400},
				{
					Kind: "checkpoint", At: -31, Label: "Before capacity renewal",
					Expected: &accountingScenarioExpected{
						Granted:       2000,
						Used:          400,
						ActiveQuota:   1000,
						ActiveUsed:    400,
						ActiveBalance: 600,
						Future:        1000,
					},
				},
				{
					Kind: "checkpoint", At: -30, Label: "Capacity renewed",
					Expected: &accountingScenarioExpected{
						Granted:       2000,
						Used:          400,
						ActiveQuota:   1000,
						ActiveUsed:    400,
						ActiveBalance: 600,
					},
				},
				{Kind: "charge", At: -15, Quota: -100},
				{
					Kind: "checkpoint", At: 0, Label: "Capacity released",
					Expected: &accountingScenarioExpected{
						Granted:       2000,
						Used:          300,
						ActiveQuota:   1000,
						ActiveUsed:    300,
						ActiveBalance: 700,
					},
				},
			},
		},
		{
			Name:        "capacity-expired",
			Code:        "CAPE",
			Description: "All capacity funding expires while local storage usage remains; attributed usage is no longer local usage.",
			Capacity:    true,
			Steps: []accountingScenarioStep{
				{Kind: "allocate", At: -30, Start: -30, End: 0, Quota: 1000},
				{Kind: "charge", At: -15, Quota: 400},
				{
					Kind: "checkpoint", At: 0, Label: "Unfunded capacity",
					Expected: &accountingScenarioExpected{Granted: 1000},
				},
			},
		},
		{
			Name:        "multiple-parents",
			Code:        "MULT",
			Description: "Charges split across two funding parents; each relationship reports only its own charges.",
			Steps: []accountingScenarioStep{
				{Kind: "allocate", At: -90, Start: -90, End: 90, Quota: 10000, Owner: "other"},
				{Kind: "allocate", At: -90, Start: -90, End: 90, Quota: 1000},
				{Kind: "allocate", At: -90, Start: -90, End: 90, Quota: 2000, Parent: "other"},
				{Kind: "charge", At: -15, Quota: 3000},
				{
					Kind: "checkpoint", At: 0, Label: "Fully charged through both parents",
					Expected: &accountingScenarioExpected{Granted: 1000, Used: 1000, ActiveQuota: 1000, ActiveUsed: 1000},
				},
			},
		},
		{
			Name:        "downstream-usage",
			Code:        "DOWN",
			Description: "The direct funding relationship includes local charges and charges routed from a descendant.",
			Steps: []accountingScenarioStep{
				{Kind: "allocate", At: -90, Start: -90, End: 90, Quota: 1000},
				{
					Kind:   "allocate",
					At:     -90,
					Start:  -90,
					End:    90,
					Quota:  500,
					Parent: "workspace",
					Owner:  "downstream",
				},
				{Kind: "charge", At: -15, Quota: 200, Owner: "downstream"},
				{Kind: "charge", At: -10, Quota: 100},
				{
					Kind: "checkpoint", At: 0, Label: "Local and descendant charges",
					Expected: &accountingScenarioExpected{
						Granted:       1000,
						Used:          300,
						ActiveQuota:   1000,
						ActiveUsed:    300,
						ActiveBalance: 700,
					},
				},
			},
		},
		{
			Name:        "upstream-limited",
			Code:        "UPLM",
			Description: "The relationship balance exceeds actual usable headroom because the parent's funding is smaller.",
			RootQuota:   500,
			Steps: []accountingScenarioStep{
				{Kind: "allocate", At: -30, Start: -30, End: 30, Quota: 1000},
				{Kind: "charge", At: -15, Quota: 200},
				{
					Kind: "checkpoint", At: 0, Label: "800 balance, 300 headroom",
					Expected: &accountingScenarioExpected{
						Granted:       1000,
						Used:          200,
						ActiveQuota:   1000,
						ActiveUsed:    200,
						ActiveBalance: 800,
					},
				},
			},
		},
		{
			Name:        "future-only",
			Code:        "FUTR",
			Description: "An entirely future grant has no active balance or attributed charges.",
			Steps: []accountingScenarioStep{
				{Kind: "allocate", At: -30, Start: 30, End: 60, Quota: 1000},
				{
					Kind: "checkpoint", At: 0, Label: "Future grant",
					Expected: &accountingScenarioExpected{
						Granted: 1000,
						Future:  1000,
					},
				},
			},
		},
		{
			Name:        "minute-units",
			Code:        "MINS",
			Description: "600000 CPU-minutes granted and 180000 charged must display as 10000 and 3000 CPU-hours.",
			MinuteUnits: true,
			Steps: []accountingScenarioStep{
				{Kind: "allocate", At: -30, Start: -30, End: 30, Quota: 600000},
				{Kind: "charge", At: -15, Quota: 180000},
				{
					Kind: "checkpoint", At: 0, Label: "Minutes normalized to hours",
					Expected: &accountingScenarioExpected{
						Granted:       600000,
						Used:          180000,
						ActiveQuota:   600000,
						ActiveUsed:    180000,
						ActiveBalance: 420000,
					},
				},
			},
		},
		{
			Name:        "expiry-boundary",
			Code:        "EXBD",
			Description: "Snapshots one millisecond before expiry and exactly at the exclusive end.",
			Steps: []accountingScenarioStep{
				{Kind: "allocate", At: -30, Start: -30, End: 0, Quota: 1000},
				{Kind: "charge", At: -15, Quota: 400},
				{
					Kind: "checkpoint", At: 0, AtMillis: -1, Label: "One millisecond before expiry",
					Expected: &accountingScenarioExpected{
						Granted:       1000,
						Used:          400,
						ActiveQuota:   1000,
						ActiveUsed:    400,
						ActiveBalance: 600,
					},
				},
				{
					Kind: "checkpoint", At: 0, Label: "Exactly at expiry",
					Expected: &accountingScenarioExpected{
						Granted:       1000,
						Used:          400,
						RetiredUsed:   400,
						ExpiredUnused: 600,
					},
				},
			},
		},
		{
			Name:        "over-consumption",
			Code:        "OVCU",
			Description: "Reported local consumption exceeds routed charges; Used is attributed charges, not all local consumption.",
			Steps: []accountingScenarioStep{
				{Kind: "allocate", At: -30, Start: -30, End: 30, Quota: 1000},
				{Kind: "charge", At: -15, Quota: 1500},
				{
					Kind: "checkpoint", At: 0, Label: "Charges capped by funding",
					Expected: &accountingScenarioExpected{Granted: 1000, Used: 1000, ActiveQuota: 1000, ActiveUsed: 1000},
				},
			},
		},
		accountingScenarioPortfolioDefinition(),
	}
}

func accountingScenarioPortfolioDefinition() accountingScenarioDefinition {
	patterns := []struct {
		Code     string
		Steps    []accountingScenarioStep
		Expected accountingScenarioExpected
	}{
		{
			Code: "STG",
			Steps: []accountingScenarioStep{
				{Kind: "allocate", At: -90, Start: -90, End: -30, Quota: 1000, Package: "staged"},
				{Kind: "allocate", At: -90, Start: -30, End: 30, Quota: 1500, Package: "staged"},
				{Kind: "allocate", At: -90, Start: 30, End: 90, Quota: 2000, Package: "staged"},
				{Kind: "charge", At: -60, Quota: 400},
				{Kind: "charge", At: -15, Quota: 600},
			},
			Expected: accountingScenarioExpected{
				Granted: 4500, Used: 1000, ActiveQuota: 1500, ActiveUsed: 600, ActiveBalance: 900,
				RetiredUsed: 400, ExpiredUnused: 600, Future: 2000,
			},
		},
		{
			Code: "IDL",
			Steps: []accountingScenarioStep{
				{Kind: "allocate", At: -60, Start: -60, End: 90, Quota: 1000},
			},
			Expected: accountingScenarioExpected{Granted: 1000, ActiveQuota: 1000, ActiveBalance: 1000},
		},
		{
			Code: "LGT",
			Steps: []accountingScenarioStep{
				{Kind: "allocate", At: -60, Start: -60, End: 90, Quota: 2000},
				{Kind: "charge", At: -15, Quota: 200},
			},
			Expected: accountingScenarioExpected{Granted: 2000, Used: 200, ActiveQuota: 2000, ActiveUsed: 200, ActiveBalance: 1800},
		},
		{
			Code: "STD",
			Steps: []accountingScenarioStep{
				{Kind: "allocate", At: -60, Start: -60, End: 90, Quota: 3000},
				{Kind: "charge", At: -45, Quota: 500},
				{Kind: "charge", At: -30, Quota: 500},
				{Kind: "charge", At: -15, Quota: 500},
			},
			Expected: accountingScenarioExpected{Granted: 3000, Used: 1500, ActiveQuota: 3000, ActiveUsed: 1500, ActiveBalance: 1500},
		},
		{
			Code: "NLM",
			Steps: []accountingScenarioStep{
				{Kind: "allocate", At: -30, Start: -30, End: 14, Quota: 4000},
				{Kind: "charge", At: -2, Quota: 3900},
			},
			Expected: accountingScenarioExpected{Granted: 4000, Used: 3900, ActiveQuota: 4000, ActiveUsed: 3900, ActiveBalance: 100},
		},
		{
			Code: "OVQ",
			Steps: []accountingScenarioStep{
				{Kind: "allocate", At: -30, Start: -30, End: 60, Quota: 1000},
				{Kind: "charge", At: -7, Quota: 1500},
			},
			Expected: accountingScenarioExpected{Granted: 1000, Used: 1000, ActiveQuota: 1000, ActiveUsed: 1000},
		},
		{
			Code: "EXU",
			Steps: []accountingScenarioStep{
				{Kind: "allocate", At: -90, Start: -90, End: -14, Quota: 2500},
			},
			Expected: accountingScenarioExpected{Granted: 2500, ExpiredUnused: 2500},
		},
		{
			Code: "EXC",
			Steps: []accountingScenarioStep{
				{Kind: "allocate", At: -90, Start: -90, End: -14, Quota: 2500},
				{Kind: "charge", At: -30, Quota: 1600},
			},
			Expected: accountingScenarioExpected{Granted: 2500, Used: 1600, RetiredUsed: 1600, ExpiredUnused: 900},
		},
		{
			Code: "UPC",
			Steps: []accountingScenarioStep{
				{Kind: "allocate", At: -30, Start: 14, End: 90, Quota: 2000},
			},
			Expected: accountingScenarioExpected{Granted: 2000, Future: 2000},
		},
		{
			Code: "OVL",
			Steps: []accountingScenarioStep{
				{Kind: "allocate", At: -90, Start: -90, End: 90, Quota: 10000},
				{Kind: "charge", At: -60, Quota: 3000},
				{Kind: "allocate", At: -30, Start: -30, End: -1, Quota: 1000},
			},
			Expected: accountingScenarioExpected{
				Granted: 11000, Used: 3000, ActiveQuota: 10000, ActiveUsed: 2000, ActiveBalance: 8000, RetiredUsed: 1000,
			},
		},
	}
	definition := accountingScenarioDefinition{
		Name:        accountingScenarioPortfolioName,
		Code:        "PORT",
		Description: "One funding project and 499 recipients with varied quotas, usage, expiry, and release schedules. Run by name only.",
		ExplicitRun: true,
	}
	checkpoint := accountingScenarioStep{
		Kind:            "checkpoint",
		Label:           "Portfolio after expiry and before upcoming releases",
		ExpectedByOwner: make(map[string]accountingScenarioExpected, 499),
	}
	for i := range 499 {
		pattern := patterns[i%len(patterns)]
		owner := fmt.Sprintf("%s%03d", pattern.Code, i+1)
		scale := int64(1 + (i/len(patterns)*7)%25)
		if i >= 490 {
			scale = 100
		}
		for _, step := range pattern.Steps {
			step.Owner = owner
			step.Quota *= scale
			definition.Steps = append(definition.Steps, step)
			if step.Kind == "allocate" {
				definition.RootQuota += step.Quota
			}
		}
		expected := pattern.Expected
		expected.Granted *= scale
		expected.Used *= scale
		expected.ActiveQuota *= scale
		expected.ActiveUsed *= scale
		expected.ActiveBalance *= scale
		expected.RetiredUsed *= scale
		expected.ExpiredUnused *= scale
		expected.Future *= scale
		checkpoint.ExpectedByOwner[owner] = expected
		if i == 0 {
			checkpoint.Owner = owner
			checkpoint.Expected = &expected
		}
	}
	slices.SortStableFunc(definition.Steps, func(a, b accountingScenarioStep) int {
		return cmp.Compare(a.At, b.At)
	})
	definition.Steps = append(definition.Steps, checkpoint)
	return definition
}
