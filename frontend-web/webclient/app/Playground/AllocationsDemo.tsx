import * as React from "react";
import {useCallback, useReducer, useRef, useState} from "react";
import {Link} from "@/ui-components";
import AppRoutes from "@/Routes";
import {AllocationPage} from "@/Accounting/Allocations/AllocationPage";
import {initialState, stateReducer} from "@/Accounting/Allocations/State";
import type {AllocationDateRange, AllocationEditRequest} from "@/Accounting/Allocations/AllocationGrid";
import type {UIEvent} from "@/Accounting/Allocations/State";
import {useAvatars} from "@/AvataaarLib/hook";
import {OldProjectRole} from "@/Project";
import {sendInformationNotification} from "@/Notifications";
import {TreeApi} from "@/ui-components/Tree";
import {VariableSizeList} from "react-window";
import {allocationScenarioFailures, allocationScenarioRead} from "./AllocationScenarioData";
import type {AllocationScenarioCheckpoint, AllocationScenarioData} from "./AllocationScenarioData";

function AllocationScenarioView(props: {
    data: AllocationScenarioData;
    checkpoint: AllocationScenarioCheckpoint;
    projectId: string;
}): React.ReactNode {
    const avatars = useAvatars();
    const suballocationTree = useRef<TreeApi>(null);
    const listRef = useRef<VariableSizeList<number[]>>(null);
    const searchBox = useRef<HTMLInputElement>(null);
    const [dateRange, setDateRange] = useState<AllocationDateRange>(() => {
        const date = new Date(props.checkpoint.now);
        return {start: new Date(date.getFullYear(), 0, 1), end: new Date(date.getFullYear(), 11, 1)};
    });
    const [state, dispatch] = useReducer(
        stateReducer,
        undefined,
        () => stateReducer(initialState(), {
            type: "WalletsLoaded",
            wallets: (props.checkpoint.walletsByProject[props.projectId] ?? []).map(wallet => ({
                ...wallet,
                children: wallet.children?.map(child => {
                    const childWallet = props.checkpoint.walletsByProject[child.child.projectId ?? ""]?.find(candidate =>
                        candidate.paysFor.name === wallet.paysFor.name && candidate.paysFor.provider === wallet.paysFor.provider
                    );
                    return {...child, maxUsable: child.maxUsable ?? childWallet?.maxUsable};
                }),
            })),
        }),
    );
    const dispatchEvent = useCallback((event: UIEvent) => {
        if (event.type !== "Init") dispatch(event);
    }, []);
    const readOnly = useCallback(() => {
        sendInformationNotification("Scenario checkpoints are read-only. Open the generated project to edit live data.");
    }, []);
    const onEditAllocation = useCallback((_request: AllocationEditRequest) => readOnly(), [readOnly]);
    const onOpenGrant = useCallback((_grantId: number) => readOnly(), [readOnly]);
    return <AllocationPage
        demo
        snapshotTime={props.checkpoint.now}
        dateRange={dateRange}
        onDateRangeChange={setDateRange}
        subProjects={{
            projectId: props.projectId,
            canCreateSubAllocator: false,
            projectRole: OldProjectRole.USER,
            state,
            onSearchInput: event => dispatch({type: "UpdateSearchQuery", newQuery: (event.target as HTMLInputElement).value}),
            onSearchKey: event => event.stopPropagation(),
            searchBox,
            dispatchEvent,
            suballocationTree,
            listRef,
            onSubAllocationShortcut: () => {},
            avatars,
            projectInfo: props.data.projectInfo,
            onEditAllocation,
            onOpenGrant,
            renderGrantLink: grantId => <Link to={AppRoutes.grants.editor(grantId)} onClick={event => {
                event.preventDefault();
                readOnly();
            }}>#{grantId}</Link>,
        }}
    />;
}

export default function AllocationsDemo(): React.ReactNode {
    const [data, setData] = useState<AllocationScenarioData>();
    const [index, setIndex] = useState(0);
    const [projectId, setProjectId] = useState("");
    const [error, setError] = useState("");
    const checkpoint = data?.checkpoints[index];
    const failures = checkpoint ? allocationScenarioFailures(checkpoint) : [];
    const checkedRelationships = checkpoint?.checksByProject ? Object.keys(checkpoint.checksByProject).length : 1;
    return <>
        <section style={{padding: "20px 32px", display: "flex", flexDirection: "column", gap: 12}}>
            <h3>Accounting scenarios</h3>
            <p>Generate real development data with <code>ucloud accounting-scenarios run overlapping-grants</code>, then load its <code>checkpoints.json</code> here. This view uses the normal allocation model and table at the recorded time.</p>
            <input type="file" accept=".json,application/json" aria-label="Load accounting scenario" onChange={async event => {
                const file = event.target.files?.[0];
                if (!file) return;
                try {
                    const next = allocationScenarioRead(await file.text());
                    setData(next);
                    setIndex(0);
                    setProjectId(next.projectId);
                    setError("");
                } catch (error) {
                    setError(error instanceof Error ? error.message : "Could not load scenario.");
                }
                event.target.value = "";
            }} />
            {error ? <p role="alert">{error}</p> : null}
            {data && checkpoint ? <>
                <p><strong>{data.scenario}</strong>: {data.description}</p>
                {data.failure ? <p role="alert">Backend validation failed: {data.failure}</p> : null}
                <label>Checkpoint <select value={index} onChange={event => setIndex(Number(event.target.value))}>
                    {data.checkpoints.map((checkpoint, index) => <option key={index} value={index}>{checkpoint.label} — {new Date(checkpoint.now).toISOString()}</option>)}
                </select></label>
                <label>Funding parent <select value={projectId} onChange={event => setProjectId(event.target.value)}>
                    {Object.entries(data.projects).map(([role, id]) => <option key={id} value={id}>{role}</option>)}
                </select></label>
                <p role="status">{failures.length === 0
                    ? `Checkpoint amounts match for ${checkedRelationships} funding ${checkedRelationships === 1 ? "relationship" : "relationships"}.`
                    : `${failures.length} mismatches: ${failures.slice(0, 5).join("; ")}`}</p>
                {!checkpoint.checksByProject && <p>Expected raw accounting units: {Object.entries(checkpoint.expected).map(([key, value]) => `${key}=${value}`).join(", ")}</p>}
            </> : null}
        </section>
        {data && checkpoint ? <AllocationScenarioView
            key={`${data.projectId}/${index}/${projectId}`}
            data={data}
            checkpoint={checkpoint}
            projectId={projectId}
        /> : null}
    </>;
}
