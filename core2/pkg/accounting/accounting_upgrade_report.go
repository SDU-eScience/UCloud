package accounting

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"html/template"
	"math/big"
	"path/filepath"
	"slices"
	"strconv"

	"ucloud.dk/shared/pkg/log"
)

type accountingUpgradeImpactWallet struct {
	Provider          string
	Category          string
	Unit              string
	Frequency         string
	WalletBefore      string
	WalletAfter       string
	Before            map[string]string
	After             map[string]string
	Changes           []string
	Reasons           []string
	Unknown           bool
	Capacity          bool
	DisplayUnit       string
	Numerator         string
	Denominator       string
	AllocationsBefore []map[string]string
	AllocationsAfter  []map[string]string
	FundingBefore     []map[string]string
	FundingAfter      []map[string]string
}

type accountingUpgradeDetails struct {
	Allocations []map[string]string
	Funding     []map[string]string
}

func accountingUpgradeWalletKey(wallet AccountingAuditWallet) string {
	return wallet.OwnerKind + "/" + wallet.OwnerReference + "\x00" + wallet.Provider + "\x00" + wallet.Category
}

func accountingUpgradeWalletDetails(capture AccountingAuditCapture) map[string]accountingUpgradeDetails {
	result := map[string]accountingUpgradeDetails{}
	wallets := map[AccWalletId]AccountingAuditWallet{}
	for _, wallet := range capture.Wallets {
		wallets[wallet.Id] = wallet
	}
	for _, allocation := range capture.Allocations {
		wallet := wallets[allocation.AssociatedWallet]
		key := accountingUpgradeWalletKey(wallet)
		detail := result[key]
		detail.Allocations = append(detail.Allocations, map[string]string{
			"ID": strconv.Itoa(int(allocation.Id)), "Start": allocation.Start.UTC().Format("2006-01-02T15:04:05Z"),
			"End":   allocation.End.UTC().Format("2006-01-02T15:04:05Z"),
			"Quota": strconv.FormatInt(allocation.Quota, 10), "Retired": strconv.FormatBool(allocation.Retired),
			"Retired usage": strconv.FormatInt(allocation.RetiredUsage, 10), "Retired quota": strconv.FormatInt(allocation.RetiredQuota, 10),
		})
		result[key] = detail
	}
	for _, group := range capture.Groups {
		wallet := wallets[group.AssociatedWallet]
		key := accountingUpgradeWalletKey(wallet)
		detail := result[key]
		parent := "root"
		if group.ParentWallet != 0 {
			owner := wallets[group.ParentWallet]
			parent = owner.OwnerKind + "/" + owner.OwnerReference
		}
		detail.Funding = append(detail.Funding, map[string]string{
			"Group": strconv.Itoa(int(group.Id)), "Parent": parent,
			"Charged usage":      strconv.FormatInt(group.TreeUsage, 10),
			"Contributing quota": strconv.FormatInt(group.ContributingQuota, 10),
		})
		result[key] = detail
	}
	return result
}

func accountingUpgradeDisplayUnit(wallet AccountingAuditWallet) (string, string, string) {
	unit := wallet.UnitNamePlural
	factor := new(big.Rat).SetInt64(1)
	if wallet.UnitFrequencySuffix && wallet.AccountingFrequency != "ONCE" {
		periods := map[string]int64{"PERIODIC_MINUTE": 60, "PERIODIC_HOUR": 3600, "PERIODIC_DAY": 86400}
		desired := int64(86400)
		suffix := "-days"
		if wallet.ProductType == "COMPUTE" {
			desired = 3600
			suffix = "-hours"
		}
		if period := periods[wallet.AccountingFrequency]; period != 0 {
			factor.SetFrac64(period, desired)
		}
		unit = wallet.UnitName + suffix
	}
	if wallet.UnitFloatingPoint {
		factor.Quo(factor, new(big.Rat).SetInt64(1000000))
	}
	return unit, factor.Num().String(), factor.Denom().String()
}

type accountingUpgradeImpactProject struct {
	Id        string
	Kind      string
	Reference string
	Title     string
	Parent    string
	Outcome   string
	Wallets   []accountingUpgradeImpactWallet
}

type accountingUpgradeAggregate struct {
	Provider      string
	Category      string
	Unit          string
	Frequency     string
	LocalBefore   string
	LocalAfter    string
	Unknown       int
	Locked        int
	Unlocked      int
	BalanceLosses int
	BalanceGains  int
}

type accountingUpgradeImpactReport struct {
	Version      string
	RunId        string
	Status       string
	CapturedAt   string
	Baseline     string
	Warnings     []string
	Blockers     []string
	Projects     []accountingUpgradeImpactProject
	Aggregates   []accountingUpgradeAggregate
	RepairCounts map[string]int
}

func accountingUpgradeMetrics(wallet AccountingAuditWallet) map[string]string {
	if wallet.Id == 0 {
		return nil
	}
	result := map[string]string{
		"Local usage":                      strconv.FormatInt(wallet.LocalUsage, 10),
		"Usage including charged children": strconv.FormatInt(wallet.TotalUsage, 10),
		"Contributing quota":               strconv.FormatInt(wallet.IncomingContributingQuota, 10),
		"Granted to children":              strconv.FormatInt(wallet.OutgoingContributingQuota, 10),
		"Additional usable balance":        strconv.FormatInt(wallet.MaxUsable, 10),
		"Uncovered usage":                  strconv.FormatInt(wallet.RecomputedExcessUsage, 10),
		"Charged to parents":               strconv.FormatInt(wallet.PropagatedUsage, 10),
		"Charged from children":            strconv.FormatInt(wallet.ChildUsage, 10),
		"Locked":                           strconv.FormatBool(wallet.WasLocked),
		"Scope baselines":                  strconv.Itoa(wallet.ScopeCount),
		"Allocations":                      strconv.Itoa(wallet.AllocationCount),
		"Funding parents":                  strconv.Itoa(wallet.ParentCount),
	}
	if wallet.DerivedError != "" || !wallet.Loaded {
		for _, field := range []string{"Additional usable balance", "Uncovered usage", "Charged to parents", "Charged from children"} {
			result[field] = "unknown"
		}
	}
	return result
}

func accountingUpgradeImpacts(bundle accountingUpgradeBundle) accountingUpgradeImpactReport {
	report := accountingUpgradeImpactReport{
		Version: bundle.Version, RunId: bundle.RunId, Status: bundle.Status,
		CapturedAt: bundle.CapturedAt.UTC().Format("2006-01-02T15:04:05.000000000Z"),
		Baseline: "Before values reconstruct persisted legacy accounting at cutover. Headroom uses legacy residual " +
			"semantics, not an observed staging API. Scope totals use mapped baselines. Duplicate-wallet projections " +
			"are unknown. Capacity is retained reported data, not a fresh measurement.",
		Warnings: bundle.Warnings, Blockers: bundle.Blockers,
		RepairCounts: map[string]int{
			"Merged wallets":                        len(bundle.Duplicates.Wallets),
			"Merged groups":                         len(bundle.Duplicates.Groups),
			"Cycle allocations removed":             len(bundle.Cycles.AllocationDeletions),
			"Historical rows archived then removed": bundle.Duplicates.DeletedHistoricalRows,
			"Clamps":                                len(bundle.Repairs.Clamps),
			"Lifecycle changes":                     len(bundle.Repairs.LifecycleChanges),
		},
	}
	projects := map[string]*accountingUpgradeImpactProject{}
	for _, project := range bundle.Projects {
		projects["project/"+project.Id] = &accountingUpgradeImpactProject{
			Id: "project/" + project.Id, Kind: "project", Reference: project.Id,
			Title: project.Title, Parent: project.Parent, Outcome: "unchanged",
		}
	}
	keyFor := accountingUpgradeWalletKey
	beforeDetails, afterDetails := accountingUpgradeWalletDetails(bundle.Before), accountingUpgradeWalletDetails(bundle.After)
	removedHistory := map[int64]int{}
	for _, name := range []string{"samples", "snapshots", "reports", "intermediate"} {
		for _, raw := range bundle.Raw[name] {
			var row struct {
				Id       int64 `json:"id"`
				WalletId int64 `json:"wallet_id"`
			}
			if err := json.Unmarshal(raw, &row); err != nil {
				panic(err)
			}
			id := row.WalletId
			if name == "snapshots" {
				id = row.Id
			}
			removedHistory[id]++
		}
	}
	before, after := map[string]AccountingAuditWallet{}, map[string]AccountingAuditWallet{}
	counts := map[string]int{}
	for _, wallet := range bundle.Before.Wallets {
		key := keyFor(wallet)
		counts[key]++
		if _, exists := before[key]; !exists {
			before[key] = wallet
		}
	}
	for _, wallet := range bundle.After.Wallets {
		after[keyFor(wallet)] = wallet
	}
	keys := []string{}
	for key := range before {
		keys = append(keys, key)
	}
	for key := range after {
		if _, exists := before[key]; !exists {
			keys = append(keys, key)
		}
	}
	slices.Sort(keys)
	aggregates := map[string]*accountingUpgradeAggregate{}
	localBefore, localAfter := map[string]*big.Int{}, map[string]*big.Int{}
	for _, key := range keys {
		old, current := before[key], after[key]
		identity := current
		if identity.Id == 0 {
			identity = old
		}
		projectKey := identity.OwnerKind + "/" + identity.OwnerReference
		project := projects[projectKey]
		if project == nil {
			title := identity.ProjectTitle
			if title == "" {
				title = identity.OwnerReference
			}
			project = &accountingUpgradeImpactProject{
				Id:        projectKey,
				Kind:      identity.OwnerKind,
				Reference: identity.OwnerReference,
				Title:     title,
				Outcome:   "unchanged",
			}
			projects[projectKey] = project
		}
		impact := accountingUpgradeImpactWallet{
			Provider: identity.Provider, Category: identity.Category, Unit: identity.UnitNamePlural,
			Frequency:    identity.AccountingFrequency,
			WalletBefore: strconv.Itoa(int(old.Id)), WalletAfter: strconv.Itoa(int(current.Id)),
			Before: accountingUpgradeMetrics(old), After: accountingUpgradeMetrics(current),
			Unknown: old.Id == 0 || current.Id == 0 || !old.Loaded || !current.Loaded || old.DerivedError != "" || current.DerivedError != "" ||
				counts[key] > 1,
			Capacity: identity.AccountingFrequency == "ONCE" && identity.ProductType != "INFERENCE",
			Reasons: []string{
				"Derived flow is rebuilt without replaying historical consumption. See bundle.json.gz for raw values, mappings, and repair rules.",
			},
		}
		impact.DisplayUnit, impact.Numerator, impact.Denominator = accountingUpgradeDisplayUnit(identity)
		impact.AllocationsBefore, impact.AllocationsAfter = beforeDetails[key].Allocations, afterDetails[key].Allocations
		impact.FundingBefore, impact.FundingAfter = beforeDetails[key].Funding, afterDetails[key].Funding
		if counts[key] > 1 {
			impact.Reasons = append(
				impact.Reasons,
				"Approved duplicate rule retains the maximum local usage. Legacy duplicate wallets have no unique visible projection.",
			)
			for field := range impact.Before {
				impact.Before[field] = "unknown"
			}
			for _, change := range bundle.Duplicates.Wallets {
				if change.KeepId == int64(current.Id) {
					impact.Reasons = append(
						impact.Reasons,
						fmt.Sprintf(
							"Wallet %d merged into %d; %d historical rows archived in source evidence and removed from user-facing history.",
							change.RemoveId,
							change.KeepId,
							removedHistory[change.RemoveId],
						),
					)
				}
			}
		}
		for _, clamp := range bundle.Repairs.Clamps {
			if clamp.Kind == "wallet" && clamp.Id == int64(current.Id) {
				impact.Reasons = append(impact.Reasons, fmt.Sprintf("Approved clamp: %s %d -> %d", clamp.Field, clamp.From, clamp.To))
			}
		}
		for _, capacity := range bundle.Capacity {
			if capacity.WalletId == current.Id {
				impact.Reasons = append(
					impact.Reasons,
					fmt.Sprintf(
						"Retained capacity: scopes=%d, preserved unscoped residual=%d, local=%d -> %d. No provider contact.",
						capacity.Scoped,
						capacity.Unscoped,
						capacity.LocalBefore,
						capacity.LocalAfter,
					),
				)
			}
		}
		fields := map[string]bool{}
		for field := range impact.Before {
			fields[field] = true
		}
		for field := range impact.After {
			fields[field] = true
		}
		visible := false
		oldAllocations, _ := json.Marshal(impact.AllocationsBefore)
		newAllocations, _ := json.Marshal(impact.AllocationsAfter)
		if !bytes.Equal(oldAllocations, newAllocations) {
			impact.Changes = append(impact.Changes, "Allocation details")
			impact.Reasons = append(
				impact.Reasons,
				"Allocation details changed. Compare the allocation records and approved repair decisions in bundle.json.gz.",
			)
			visible = true
		}
		oldFunding, _ := json.Marshal(impact.FundingBefore)
		newFunding, _ := json.Marshal(impact.FundingAfter)
		if !bytes.Equal(oldFunding, newFunding) {
			impact.Changes = append(impact.Changes, "Funding details")
			impact.Reasons = append(impact.Reasons, "Funding details changed, even if wallet totals are equal. Compare charges by parent below.")
			visible = true
		}
		for field := range fields {
			if impact.Before[field] != impact.After[field] {
				impact.Changes = append(impact.Changes, field)
				if field != "Scope baselines" && field != "Charged to parents" {
					visible = true
				}
			}
		}
		slices.Sort(impact.Changes)
		if impact.Unknown {
			project.Outcome = "unknown"
		} else if project.Outcome != "unknown" && visible {
			project.Outcome = "visible"
		} else if project.Outcome == "unchanged" && len(impact.Changes) > 0 {
			project.Outcome = "internal"
		}
		project.Wallets = append(project.Wallets, impact)
		group := identity.Provider + "\x00" + identity.Category + "\x00" + identity.UnitNamePlural + "\x00" + identity.AccountingFrequency
		aggregate := aggregates[group]
		if aggregate == nil {
			aggregate = &accountingUpgradeAggregate{
				Provider:  identity.Provider,
				Category:  identity.Category,
				Unit:      identity.UnitNamePlural,
				Frequency: identity.AccountingFrequency,
			}
			aggregates[group] = aggregate
			localBefore[group], localAfter[group] = new(big.Int), new(big.Int)
		}
		if impact.Unknown {
			aggregate.Unknown++
		} else {
			localBefore[group].Add(localBefore[group], big.NewInt(old.LocalUsage))
			localAfter[group].Add(localAfter[group], big.NewInt(current.LocalUsage))
			if !old.WasLocked && current.WasLocked {
				aggregate.Locked++
			}
			if old.WasLocked && !current.WasLocked {
				aggregate.Unlocked++
			}
			if current.MaxUsable < old.MaxUsable {
				aggregate.BalanceLosses++
			}
			if current.MaxUsable > old.MaxUsable {
				aggregate.BalanceGains++
			}
		}
	}
	projectKeys := []string{}
	for key := range projects {
		projectKeys = append(projectKeys, key)
	}
	slices.Sort(projectKeys)
	for _, key := range projectKeys {
		project := projects[key]
		if bundle.Status != "committed" {
			project.Outcome = "unknown"
		}
		report.Projects = append(report.Projects, *project)
	}
	groups := []string{}
	for key := range aggregates {
		groups = append(groups, key)
	}
	slices.Sort(groups)
	for _, key := range groups {
		aggregate := aggregates[key]
		aggregate.LocalBefore, aggregate.LocalAfter = localBefore[key].String(), localAfter[key].String()
		report.Aggregates = append(report.Aggregates, *aggregate)
	}
	return report
}

func accountingUpgradeExport(bundle accountingUpgradeBundle) {
	directory := filepath.Join(accountingUpgradeDirectory(), bundle.RunId)
	impact := accountingUpgradeImpacts(bundle)
	files := map[string][]byte{
		"bundle.json.gz": accountingUpgradeEncode(bundle),
		"source.json.gz": accountingUpgradeEncode(bundle.Raw),
		"before.json.gz": accountingUpgradeEncode(bundle.Before),
		"after.json.gz":  accountingUpgradeEncode(bundle.After),
		"impact.json.gz": accountingUpgradeEncode(impact),
	}
	validation, err := json.MarshalIndent(struct {
		RunId           string
		Status          string
		Checks          map[string]bool
		Blockers        []string
		Warnings        []string
		DurationSeconds float64
	}{bundle.RunId, bundle.Status, bundle.Validation, bundle.Blockers, bundle.Warnings, bundle.DurationSeconds}, "", "  ")
	if err != nil {
		panic(err)
	}
	files["validation.json"] = validation
	encoded, err := json.Marshal(impact)
	if err != nil {
		panic(err)
	}
	page := template.Must(template.New("accounting-upgrade").Parse(accountingUpgradeHTML))
	var html bytes.Buffer
	if err = page.Execute(&html, template.JS(encoded)); err != nil {
		panic(err)
	}
	files["summary.html"] = html.Bytes()
	checksums := map[string]string{}
	names := []string{}
	for name := range files {
		names = append(names, name)
	}
	slices.Sort(names)
	for _, name := range names {
		data := files[name]
		accountingUpgradeWrite(directory, name, data)
		hash := sha256.Sum256(data)
		checksums[name] = hex.EncodeToString(hash[:])
	}
	manifest := struct {
		Version         string
		ArtifactVersion int
		Build           map[string]string
		RunId           string
		InputHash       string
		Status          string
		Checksums       map[string]string
	}{bundle.Version, bundle.ArtifactVersion, bundle.Build, bundle.RunId, bundle.InputHash, bundle.Status, checksums}
	data, err := json.MarshalIndent(manifest, "", "  ")
	if err != nil {
		panic(err)
	}
	accountingUpgradeWrite(directory, "manifest.json", data)
	log.Info(
		"Accounting upgrade %s: %s (projects=%d warnings=%d)",
		bundle.Status,
		filepath.Join(directory, "summary.html"),
		len(impact.Projects),
		len(bundle.Warnings),
	)
}

const accountingUpgradeHTML = `<!DOCTYPE html>
<html lang="en">
  <meta charset="utf-8" /><meta name="viewport" content="width=device-width,initial-scale=1" /><title>
    Accounting upgrade impact
  </title>
  <style>
    :root {
      font-family: Inter, system-ui, sans-serif;
      color: #212529;
      background: #f7f8f9;
    }
    * {
      box-sizing: border-box;
      min-width: 0;
    }
    body {
      margin: 0;
    }
    main {
      max-width: 1250px;
      margin: auto;
      padding: 28px;
    }
    h1 {
      font-size: 32px;
    }
    h2 {
      margin-top: 32px;
    }
    a {
      color: #096de3;
      overflow-wrap: anywhere;
    }
    p {
      max-width: 95ch;
    }
    button,
    input,
    select {
      font: inherit;
      padding: 8px;
      border: 1px solid #d0d5dc;
      border-radius: 5px;
      background: white;
    }
    button {
      cursor: pointer;
    }
    .cards {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
      gap: 12px;
    }
    .card,
    article {
      padding: 16px;
      border: 1px solid #d0d5dc;
      border-radius: 8px;
      background: #fff;
    }
    .card b {
      display: block;
      font-size: 28px;
    }
    .muted {
      color: #646669;
    }
    .controls {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
      margin: 16px 0;
    }
    .scroll {
      overflow: auto;
    }
    table {
      border-collapse: collapse;
      width: 100%;
      background: #fff;
      font-size: 14px;
    }
    td,
    th {
      padding: 10px;
      text-align: left;
      border-bottom: 1px solid #d0d5dc;
      vertical-align: top;
      overflow-wrap: anywhere;
    }
    th {
      background: #e4effc;
    }
    .changed {
      background: #fff6e5;
    }
    .unknown {
      color: #994117;
    }
    .warning {
      border-left: 4px solid #ae6000;
      padding: 12px;
      background: #fff6e5;
    }
    code {
      overflow-wrap: anywhere;
    }
    article {
      margin-top: 12px;
    }
    details {
      margin: 12px 0;
    }
    summary {
      cursor: pointer;
    }
    .selected {
      border: 2px solid #096de3;
    }
    pre {
      white-space: pre-wrap;
      overflow-wrap: anywhere;
    }
    label {
      display: flex;
      gap: 8px;
      align-items: center;
    }
    @media (max-width: 650px) {
      main {
        padding: 14px;
      }
      td,
      th {
        font-size: 12px;
        padding: 7px;
      }
      .cards {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }
    }
  </style>
  <main>
    <h1>Accounting upgrade: user impact</h1>
    <p id="run"></p>
    <p id="baseline" class="muted"></p>
    <div id="failure"></div>
    <section>
      <h2>Aggregate impact</h2>
      <div id="counts" class="cards"></div>
      <p class="muted">
        Project outcomes are exclusive: unknown takes priority over visible, internal-only, and unchanged. Personal
        workspaces are separate. Category counts below count wallets; they are not distinct project counts.
      </p>
      <div class="scroll" id="aggregate"></div>
      <p class="muted">
        Local usage totals include comparable logical wallets only. Unknown wallets are excluded and counted. Tree usage
        is never summed as global consumption. Units and frequencies remain separate.
      </p>
      <details>
        <summary>Repair counts and source warnings</summary>
        <div id="repairs"></div>
        <ul id="warnings"></ul>
      </details>
    </section>
    <section>
      <h2>Find a project or workspace</h2>
      <div class="controls">
        <input id="search" aria-label="Project title or ID" placeholder="Project title or ID" /><select
          id="outcome"
          aria-label="Outcome"
        >
          <option value="">All outcomes</option>
          <option value="visible">Visible changes</option>
          <option value="internal">Internal only</option>
          <option value="unchanged">Unchanged</option>
          <option value="unknown">Unknown</option></select
        ><select id="effect" aria-label="Wallet effect">
          <option value="">All effects</option>
          <option value="loss">Balance loss</option>
          <option value="gain">Balance gain</option>
          <option value="locked">Newly locked</option>
          <option value="unlocked">Newly unlocked</option></select
        ><input id="category" aria-label="Provider or category" placeholder="Provider or category" /><label
          ><input type="checkbox" id="pinned" />Pinned only</label
        >
      </div>
      <p id="matches"></p>
      <div id="projects"></div>
      <div class="controls"><button id="previous">Previous</button><button id="next">Next</button></div>
    </section>
    <section id="detail">
      <h2>Project comparison</h2>
      <p>Select a project. Stable links use project IDs, not wallet IDs.</p>
    </section>
    <footer>
      <p>
        <a href="manifest.json">Manifest and checksums</a> · <a href="impact.json.gz">Machine-readable impact</a> ·
        <a href="bundle.json.gz">Full before/after evidence and repair decisions</a> ·
        <a href="source.json.gz">Original source rows</a>
      </p>
      <p class="muted">
        This report contains private accounting data. Keep it restricted. No production connection is used.
      </p>
    </footer>
  </main>
  <script type="application/json" id="data">
    {{.}}
  </script>
  <script>
    const report = JSON.parse(document.getElementById("data").textContent),
      projects = report.Projects || [];
    const el = (id) => document.getElementById(id),
      text = (v) => (v == null ? "" : String(v)),
      node = (tag, value) => {
        const n = document.createElement(tag);
        if (value != null) n.textContent = text(value);
        return n;
      };
    el("run").textContent = report.RunId + " · " + report.Status + " · " + report.CapturedAt;
    el("baseline").textContent = report.Baseline;
    if (report.Status !== "committed") {
      const p = node("p", "No committed after-state is established. Results are candidate or failure evidence.");
      p.className = "warning";
      el("failure").append(p);
    }
    for (const blocker of report.Blockers || []) el("failure").append(node("p", "BLOCKED: " + blocker));
    const outcomeLabels = {
      visible: "Visible changes",
      internal: "Internal only",
      unchanged: "Unchanged",
      unknown: "Unknown",
    };
    function clearFilters() {
      for (const id of ["search", "outcome", "effect", "category"]) el(id).value = "";
      el("pinned").checked = false;
      page = 0;
    }
    for (const [key, label] of Object.entries(outcomeLabels)) {
      const card = node("button");
      card.className = "card";
      card.append(
        node("b", projects.filter((p) => p.Kind === "project" && p.Outcome === key).length),
        node("span", label + " projects")
      );
      card.onclick = () => {
        clearFilters();
        el("workspace-kind").value = "project";
        el("outcome").value = key;
        render();
      };
      el("counts").append(card);
    }
    const personal = node("button");
    personal.className = "card";
    personal.append(
      node("b", projects.filter((p) => p.Kind !== "project").length),
      node("span", "Personal workspaces")
    );
    personal.onclick = () => {
      clearFilters();
      el("workspace-kind").value = "user";
      render();
    };
    el("counts").append(personal);
    function table(headers, rows) {
      const wrap = node("div");
      wrap.className = "scroll";
      const t = node("table"),
        head = node("tr");
      for (const h of headers) head.append(node("th", h));
      t.append(head);
      for (const cells of rows) {
        const r = node("tr");
        for (const c of cells) {
          const d = node("td");
          if (c instanceof Node) d.append(c);
          else d.textContent = text(c);
          r.append(d);
        }
        t.append(r);
      }
      wrap.append(t);
      return wrap;
    }
    el("aggregate").append(
      table(
        [
          "Provider / category",
          "Unit / frequency",
          "Raw local before → after",
          "Unknown wallets",
          "New locks / unlocks",
          "Balance losses / gains",
        ],
        (report.Aggregates || []).map((g) => {
          function action(value, effect) {
            const b = node("button", value);
            b.onclick = () => {
              clearFilters();
              el("workspace-kind").value = "";
              el("category").value = g.Provider + " / " + g.Category;
              el("effect").value = effect;
              render();
            };
            return b;
          }
          const locks = node("span");
          locks.append(action(g.Locked, "locked"), node("span", " / "), action(g.Unlocked, "unlocked"));
          const balances = node("span");
          balances.append(action(g.BalanceLosses, "loss"), node("span", " / "), action(g.BalanceGains, "gain"));
          return [
            action(g.Provider + " / " + g.Category, ""),
            g.Unit + " / " + g.Frequency,
            g.LocalBefore + " → " + g.LocalAfter,
            action(g.Unknown, "unknown"),
            locks,
            balances,
          ];
        })
      )
    );
    for (const [k, v] of Object.entries(report.RepairCounts || {})) el("repairs").append(node("p", k + ": " + v));
    for (const w of report.Warnings || []) el("warnings").append(node("li", w));
    let pins = new Set();
    try {
      pins = new Set(JSON.parse(localStorage.getItem("accounting-upgrade-projects") || "[]"));
    } catch {}
    let page = 0;
    const pageSize = 50;
    const workspaceKind = node("select");
    workspaceKind.id = "workspace-kind";
    workspaceKind.setAttribute("aria-label", "Workspace type");
    for (const [value, label] of [
      ["", "All workspaces"],
      ["project", "Projects"],
      ["user", "Personal workspaces"],
    ]) {
      const option = node("option", label);
      option.value = value;
      workspaceKind.append(option);
    }
    el("outcome").parentElement.append(workspaceKind);
    const unknownEffect = node("option", "Unknown wallet");
    unknownEffect.value = "unknown";
    el("effect").append(unknownEffect);
    function effect(w, kind) {
      if (kind === "unknown") return w.Unknown;
      if (w.Unknown || !w.Before || !w.After) return false;
      const b = w.Before,
        a = w.After;
      if (kind === "locked") return b.Locked === "false" && a.Locked === "true";
      if (kind === "unlocked") return b.Locked === "true" && a.Locked === "false";
      try {
        return kind === "loss"
          ? BigInt(a["Additional usable balance"]) < BigInt(b["Additional usable balance"])
          : BigInt(a["Additional usable balance"]) > BigInt(b["Additional usable balance"]);
      } catch {
        return false;
      }
    }
    function render() {
      const q = el("search").value.trim().toLowerCase(),
        category = el("category").value.trim().toLowerCase(),
        level = el("outcome").value,
        kind = el("effect").value,
        ownerKind = el("workspace-kind").value;
      const found = projects.filter(
        (p) =>
          (!q || [p.Title, p.Reference].some((v) => text(v).toLowerCase().includes(q))) &&
          (!ownerKind || p.Kind === ownerKind) &&
          (!level || p.Outcome === level) &&
          (!el("pinned").checked || pins.has(p.Id)) &&
          ((!category && !kind) ||
            (p.Wallets || []).some(
              (w) =>
                (!category || (w.Provider + " / " + w.Category).toLowerCase().includes(category)) &&
                (!kind || effect(w, kind))
            ))
      );
      found.sort(
        (a, b) =>
          Number(pins.has(b.Id)) - Number(pins.has(a.Id)) || a.Title.localeCompare(b.Title) || a.Id.localeCompare(b.Id)
      );
      const start = page * pageSize;
      el("matches").textContent =
        found.length +
        " of " +
        projects.length +
        " workspaces · " +
        (found.length ? start + 1 : 0) +
        "–" +
        Math.min(start + pageSize, found.length);
      el("projects").replaceChildren(
        table(
          ["Project / workspace", "ID", "Impact", "Wallets", "Pin"],
          found.slice(start, start + pageSize).map((p) => {
            const a = node("a", p.Title || p.Reference);
            a.href = "#project=" + encodeURIComponent(p.Id);
            const pin = node("button", pins.has(p.Id) ? "Unpin" : "Pin");
            pin.onclick = () => {
              if (pins.has(p.Id)) pins.delete(p.Id);
              else pins.add(p.Id);
              try {
                localStorage.setItem("accounting-upgrade-projects", JSON.stringify([...pins]));
              } catch {}
              render();
            };
            return [a, p.Reference, outcomeLabels[p.Outcome], (p.Wallets || []).length, pin];
          })
        )
      );
      el("previous").disabled = page === 0;
      el("next").disabled = start + pageSize >= found.length;
    }
    function metric(field, value, w) {
      if (["Locked", "Scope baselines", "Allocations", "Funding parents"].includes(field)) return value;
      try {
        let n = BigInt(value) * BigInt(w.Numerator),
          d = BigInt(w.Denominator),
          sign = n < 0n ? "-" : "";
        if (n < 0n) n = -n;
        const whole = n / d,
          remainder = n % d,
          decimals = ((remainder * 1000000n) / d).toString().padStart(6, "0").replace(/0+$/, "");
        return (
          sign + whole.toString() + (decimals ? "." + decimals : "") + " " + w.DisplayUnit + " (raw " + value + ")"
        );
      } catch {
        return value;
      }
    }
    function evidence(title, rows) {
      const d = node("details");
      d.append(node("summary", title));
      const fields = [...new Set((rows || []).flatMap((r) => Object.keys(r)))];
      d.append(
        table(
          fields,
          (rows || []).map((r) => fields.map((f) => r[f]))
        )
      );
      return d;
    }
    function detail() {
      if (!location.hash.startsWith("#project=")) return;
      let id;
      try {
        id = decodeURIComponent(location.hash.slice(9));
      } catch {
        return;
      }
      const p = projects.find((p) => p.Id === id);
      el("detail").replaceChildren(node("h2", p ? p.Title : "Project not found"));
      if (!p) {
        el("detail").append(node("p", "This project is not in the recorded input. That does not establish no change."));
        return;
      }
      el("detail").append(
        node("p", p.Reference + " · " + outcomeLabels[p.Outcome]),
        node("p", "Organizational parent: " + (p.Parent || "none recorded"))
      );
      if (!p.Wallets?.length) el("detail").append(node("p", "No accounting wallets were captured for this project."));
      for (const w of p.Wallets || []) {
        const article = node("article");
        article.append(
          node("h3", w.Provider + " / " + w.Category),
          node("p", w.DisplayUnit + " · " + w.Frequency + " · wallet " + w.WalletBefore + " → " + w.WalletAfter)
        );
        if (w.Capacity)
          article.append(
            node(
              "p",
              "Capacity source is retained accounting data. Fresh usage may differ when the provider reports normally."
            )
          );
        if (w.Unknown)
          article.append(node("p", "Some before/after values are unknown. Do not treat this as unchanged."));
        const fields = [...new Set([...Object.keys(w.Before || {}), ...Object.keys(w.After || {})])];
        article.append(
          table(
            ["Metric", "Before", "After", "Change (raw)"],
            fields.map((f) => {
              const before = w.Before?.[f] ?? "not present",
                after = w.After?.[f] ?? "not present";
              let delta = before === after ? "unchanged" : "changed";
              try {
                delta = (BigInt(after) - BigInt(before)).toString();
              } catch {}
              return [f, metric(f, before, w), metric(f, after, w), delta];
            })
          )
        );
        for (const reason of w.Reasons || []) article.append(node("p", reason));
        article.append(
          evidence("Allocations before: dates and raw quota", w.AllocationsBefore),
          evidence("Allocations after: dates and raw quota", w.AllocationsAfter),
          evidence("Funding before: raw charges by parent", w.FundingBefore),
          evidence("Funding after: raw charges by parent", w.FundingAfter)
        );
        el("detail").append(article);
      }
      el("detail").scrollIntoView({ block: "start" });
    }
    for (const id of ["search", "outcome", "effect", "category", "pinned", "workspace-kind"])
      el(id).addEventListener("input", () => {
        page = 0;
        render();
      });
    el("previous").onclick = () => {
      page--;
      render();
    };
    el("next").onclick = () => {
      page++;
      render();
    };
    window.addEventListener("hashchange", detail);
    render();
    detail();
  </script>
</html>
`
