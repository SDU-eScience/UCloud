import * as React from "react";
import {useEffect, useMemo, useState} from "react";
import {useLocation} from "react-router-dom";
import AppRoutes from "@/Routes";
import {Box, MainContainer} from "@/ui-components";
import {ProjectSwitcher} from "@/Project/ProjectSwitcher";
import {isAdminOrPI} from "@/Project";
import {AllocationPeriodFilter} from "./AllocationPeriodFilter";
import {AllocationOverview} from "./AllocationOverview";
import {SubProjectList} from "./CommonSections";
import {ReceivedAllocations} from "./ReceivedAllocations";
import {injectStyle} from "@/Unstyled";
import {allocationNormalize} from "./AllocationModel";
import {focusAllocationTable, useAllocationShortcuts} from "./AllocationShortcuts";
import {ShortcutHint, useShortcutReveal} from "@/Applications/KeyboardNavigation";

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
    const subAllocated = location.pathname.endsWith("/sub-allocated") ||
        location.pathname === AppRoutes.accounting.allocationsDemo();
    const providerTools = location.pathname.endsWith("/provider-tools");
    const received = !providerTools && !subAllocated;
    const [selectedCategories, setSelectedCategories] = useState<string[]>([]);
    const shortcutsVisible = useShortcutReveal();
    useAllocationShortcuts();
    const state = props.subProjects.state;
    const model = useMemo(() => allocationNormalize(state.remoteData.wallets ?? [], props.snapshotTime ?? Date.now()),
        [state.remoteData.wallets, props.snapshotTime]);
    const canApply = !props.subProjects.projectId || isAdminOrPI(props.subProjects.projectRole);

    useEffect(() => {
        setSelectedCategories([]);
    }, [received, providerTools, props.subProjects.projectId]);

    return <MainContainer headerSize={0} main={<div className={AllocationPageStyle}
        data-mode={providerTools ? "provider" : "allocations"}
        data-received={received ? "true" : undefined}
        data-shortcuts-visible={shortcutsVisible ? "true" : undefined}>
        <header>
            {providerTools ?
                <h3>Gifts and root allocations</h3> :
                <h3>{received ? "Received allocations" : "Resource allocations"}</h3>}
            {received ? <ShortcutHint shortcut="S" hideUntilReveal /> : null}
            {props.demo ? <span className="demo-badge">Read-only checkpoint</span> : null}
            <Box flexGrow={1} />
            <ProjectSwitcher />
        </header>
        {providerTools ?
            props.providerTools :
            received ?
                <ReceivedAllocations
                    model={model}
                    state={state}
                    dateRange={props.dateRange}
                    onDateRangeChange={props.onDateRangeChange}
                    projectId={props.subProjects.projectId}
                    canApply={canApply}
                    projectInfo={props.subProjects.projectInfo}
                    searchBox={props.subProjects.searchBox}
                    demo={props.demo}
                    onOpenGrant={props.subProjects.onOpenGrant}
                    renderGrantLink={props.subProjects.renderGrantLink}
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
