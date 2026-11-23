import * as React from "react";
import {useMemo, useRef, useState} from "react";
import {useLocation} from "react-router-dom";
import {Box, Link, MainContainer} from "@/ui-components";
import {ProjectSwitcher} from "@/Project/ProjectSwitcher";
import {TreeApi} from "@/ui-components/Tree";
import {ProductTypesByPriority} from "@/Accounting";
import type {ProductType} from "@/Accounting";
import {AllocationPeriodFilter} from "./AllocationPeriodFilter";
import {AllocationOverview} from "./AllocationOverview";
import {SubProjectList, YourAllocations} from "./CommonSections";
import {injectStyle} from "@/Unstyled";
import AppRoutes from "@/Routes";
import {allocationNormalize} from "./AllocationModel";
import {focusAllocationTable, useAllocationShortcuts} from "./AllocationShortcuts";
import {useShortcutReveal} from "@/Applications/KeyboardNavigation";

const AllocationPageStyle = injectStyle("allocation-page", k => `
    ${k}[data-mode="allocations"] {
        display: flex;
        flex-direction: column;
        height: calc(100vh - var(--termsize, 0px) - 32px);
        min-height: 0;
        min-width: 0;
    }
    ${k} > header {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: 12px;
        margin-bottom: 16px;
        flex: 0 0 auto;
    }
    ${k} > header h3 {
        margin: 0;
    }
    ${k} .demo-badge {
        padding: 3px 7px;
        border-radius: 3px;
        background: var(--primaryMain);
        color: var(--primaryContrast);
        font-size: 11px;
    }
    ${k} .disabled-alloc .row-left, ${k} .disabled-alloc .low-opaqueness {
        filter: opacity(0.5);
    }
    ${k} .sub-alloc {
        display: flex;
        align-items: center;
        gap: 8px;
    }
    ${k} [data-allocation-shortcut-hint] {
        display: none;
    }
    ${k}[data-shortcuts-visible="true"] [data-allocation-shortcut-hint] {
        display: inline-flex;
    }
`);

export function AllocationPage(props: {
    subProjects: Omit<React.ComponentProps<typeof SubProjectList>, "model">;
    dateRange: React.ComponentProps<typeof AllocationPeriodFilter>["value"];
    onDateRangeChange: React.ComponentProps<typeof AllocationPeriodFilter>["onChange"];
    demo?: boolean;
    providerTools?: React.ReactNode;
    snapshotTime?: number;
}): React.ReactNode {
    const location = useLocation();
    const received = location.pathname.endsWith("/received");
    const providerTools = location.pathname.endsWith("/provider-tools");
    const basePath = props.demo ? AppRoutes.accounting.allocationsDemo() : AppRoutes.accounting.allocations();
    const allocationTree = useRef<TreeApi>(null);
    const [selectedCategories, setSelectedCategories] = useState<string[]>([]);
    const shortcutsVisible = useShortcutReveal();
    useAllocationShortcuts();
    const state = props.subProjects.state;
    const model = useMemo(() => allocationNormalize(state.remoteData.wallets ?? [], props.snapshotTime ?? Date.now()),
        [state.remoteData.wallets, props.snapshotTime]);
    const sortedAllocations = Object.entries(state.yourAllocations).sort((a, b) => {
        const aPriority = ProductTypesByPriority.indexOf(a[0] as ProductType);
        const bPriority = ProductTypesByPriority.indexOf(b[0] as ProductType);
        return (aPriority === -1 ? ProductTypesByPriority.length : aPriority) -
            (bPriority === -1 ? ProductTypesByPriority.length : bPriority);
    });

    return <MainContainer headerSize={0} main={<div className={AllocationPageStyle}
        data-mode={providerTools ? "provider" : received ? "received" : "allocations"}
        data-shortcuts-visible={shortcutsVisible ? "true" : undefined}>
        <header>
            {providerTools ?
                <h3>Gifts and root allocations</h3> :
                <h3>{received ? "Your allocations" : "Resource allocations"}</h3>}
            {props.demo ? <span className="demo-badge">Read-only checkpoint</span> : null}
            {received ?
                <Link to={basePath}>Granted allocations</Link> : null}
            <Box flexGrow={1} />
            <ProjectSwitcher />
        </header>
        {providerTools ?
            props.providerTools :
            received ?
                <YourAllocations
                    state={state}
                    allocations={sortedAllocations}
                    allocationTree={allocationTree}
                    indent={16}
                /> :
                <>
                <AllocationOverview
                    model={model}
                    loading={state.remoteData.wallets === undefined}
                    personalWorkspaces={!state.viewOnlyProjects}
                    dateRange={props.dateRange}
                    selectedCategories={selectedCategories}
                    onSelectCategories={setSelectedCategories}
                />
                <SubProjectList
                    {...props.subProjects}
                    model={model}
                    demo={props.demo}
                    dateRange={props.dateRange}
                    selectedCategories={selectedCategories}
                    onCategoriesChange={setSelectedCategories}
                    periodControl={<AllocationPeriodFilter
                        now={model.now}
                        value={props.dateRange}
                        onChange={props.onDateRangeChange}
                        shortcut="K"
                        onReturnFocus={focusAllocationTable}
                    />}
                />
            </>}
    </div>} />;
}
