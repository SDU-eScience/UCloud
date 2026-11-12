import * as React from "react";
import {useCallback, useReducer, useRef, useState} from "react";
import {AllocationPage} from "@/Accounting/Allocations/AllocationPage";
import {allocationCurrentCalendarYear} from "@/Accounting/Allocations/AllocationPeriodFilter";
import {initialState, stateReducer} from "@/Accounting/Allocations/State";
import type {AllocationDateRange, AllocationEditRequest} from "@/Accounting/Allocations/AllocationGrid";
import type {UIEvent} from "@/Accounting/Allocations/State";
import {useAvatars} from "@/AvataaarLib/hook";
import {useProjectId} from "@/Project/Api";
import {OldProjectRole} from "@/Project";
import {sendInformationNotification} from "@/Notifications";
import {TreeApi} from "@/ui-components/Tree";
import {VariableSizeList} from "react-window";
import {buildDemoWallets, demoProjectInfo} from "@/Playground/AllocationsDemoData";

const AllocationsDemo: React.FunctionComponent = () => {
    const projectId = useProjectId();
    const avatars = useAvatars();
    const suballocationTree = useRef<TreeApi>(null);
    const listRef = useRef<VariableSizeList<number[]>>(null);
    const searchBox = useRef<HTMLInputElement>(null);
    const [dateRange, setDateRange] = useState<AllocationDateRange>(allocationCurrentCalendarYear);

    const [state, dispatch] = useReducer(
        stateReducer,
        undefined,
        () => stateReducer(initialState(), {type: "WalletsLoaded", wallets: buildDemoWallets()}),
    );

    const dispatchEvent = useCallback((event: UIEvent) => {
        if (event.type === "Init") return;
        dispatch(event);
    }, []);

    const onSearchInput = useCallback((event: React.SyntheticEvent) => {
        const input = event.target as HTMLInputElement;
        dispatch({type: "UpdateSearchQuery", newQuery: input.value});
    }, []);

    const onSearchKey = useCallback<React.KeyboardEventHandler>(event => {
        event.stopPropagation();
        if (event.code === "Escape") {
            (event.target as HTMLInputElement).blur();
        }
    }, []);

    const onNewSubProject = useCallback(async () => {
        sendInformationNotification("Demo data: creating sub-projects is disabled.");
    }, []);

    const onEditAllocation = useCallback((_request: AllocationEditRequest) => {
        sendInformationNotification("Demo data: allocation edits are disabled.");
    }, []);

    const onOpenGrant = useCallback((_grantId: number) => {
        sendInformationNotification("Demo data: grant applications are not connected.");
    }, []);

    const onSubAllocationShortcut = useCallback(() => {}, []);

    const renderGrantLink = useCallback((grantId: number) => (
        <a
            href="#"
            onClick={event => {
                event.preventDefault();
                event.stopPropagation();
                sendInformationNotification("Demo data: grant applications are not connected.");
            }}
        >
            #{grantId}
        </a>
    ), []);

    return <AllocationPage
        demo
        dateRange={dateRange}
        onDateRangeChange={setDateRange}
        subProjects={{
            projectId: projectId ?? "demo-project",
            onNewSubProject,
            projectRole: OldProjectRole.ADMIN,
            state,
            onSearchInput,
            onSearchKey,
            searchBox,
            dispatchEvent,
            suballocationTree,
            listRef,
            onSubAllocationShortcut,
            avatars,
            projectInfo: demoProjectInfo,
            onEditAllocation,
            onOpenGrant,
            renderGrantLink,
        }}
    />;
};

export default AllocationsDemo;
