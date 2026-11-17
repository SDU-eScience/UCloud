package accounting

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
	Kind     string
	At       int
	AtMillis int
	Start    int
	End      int
	Quota    int64
	Parent   string
	Owner    string
	Package  string
	Label    string
	Expected *accountingScenarioExpected
}

type accountingScenarioDefinition struct {
	Name        string
	Description string
	Capacity    bool
	MinuteUnits bool
	RootQuota   int64
	Steps       []accountingScenarioStep
}

func accountingScenarioDefinitions() []accountingScenarioDefinition {
	return []accountingScenarioDefinition{
		{
			Name:        "single-grant",
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
			Name:        "expired-unused",
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
	}
}
