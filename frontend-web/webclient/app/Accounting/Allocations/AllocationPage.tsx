import * as React from "react";
import {useRef, useState} from "react";
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

const AllocationPageStyle = injectStyle("allocation-page", k => `
    ${k} > header { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin-bottom: 16px; }
    ${k} > header h3 { margin: 0; }
    ${k} .demo-badge { padding: 3px 7px; border-radius: 3px; background: var(--primaryMain); color: var(--primaryContrast); font-size: 11px; }
    ${k} .disabled-alloc .row-left, ${k} .disabled-alloc .low-opaqueness { filter: opacity(0.5); }
    ${k} .sub-alloc { display: flex; align-items: center; gap: 8px; }
`);

export function AllocationPage(props: {
    subProjects: React.ComponentProps<typeof SubProjectList>;
    dateRange: React.ComponentProps<typeof AllocationPeriodFilter>["value"];
    onDateRangeChange: React.ComponentProps<typeof AllocationPeriodFilter>["onChange"];
    demo?: boolean;
    providerTools?: React.ReactNode;
}): React.ReactNode {
    const location = useLocation();
    const received = location.pathname.endsWith("/received");
    const basePath = props.demo ? "/playground/allocations" : AppRoutes.accounting.allocations();
    const allocationTree = useRef<TreeApi>(null);
    const [selectedCategories, setSelectedCategories] = useState<string[]>([]);
    const state = props.subProjects.state;
    const sortedAllocations = Object.entries(state.yourAllocations).sort((a, b) => {
        const aPriority = ProductTypesByPriority.indexOf(a[0] as ProductType);
        const bPriority = ProductTypesByPriority.indexOf(b[0] as ProductType);
        return (aPriority === -1 ? ProductTypesByPriority.length : aPriority) -
            (bPriority === -1 ? ProductTypesByPriority.length : bPriority);
    });

    return <MainContainer headerSize={0} main={<div className={AllocationPageStyle}>
        <header>
            <h3>{received ? "Your allocations" : "Resource allocations"}</h3>
            {props.demo ? <span className="demo-badge">Demo data</span> : null}
            {received ? <Link to={basePath}>Granted allocations</Link> : null}
            <Box flexGrow={1} />
            <ProjectSwitcher />
        </header>
        {received ? <YourAllocations state={state} allocations={sortedAllocations} allocationTree={allocationTree} indent={16} /> : <>
            <AllocationOverview state={state} dateRange={props.dateRange} selectedCategories={selectedCategories} onSelectCategories={setSelectedCategories} />
            {props.providerTools}
            <SubProjectList
                {...props.subProjects}
                dateRange={props.dateRange}
                selectedCategories={selectedCategories}
                onCategoriesChange={setSelectedCategories}
                periodControl={<AllocationPeriodFilter value={props.dateRange} onChange={props.onDateRangeChange} />}
            />
        </>}
    </div>} />;
}
