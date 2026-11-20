import * as React from "react";
import {useCallback, useEffect, useMemo, useReducer, useRef, useState} from "react";
import {MainContainer} from "@/ui-components";
import {useProjectId} from "@/Project/Api";
import {useDidUnmount} from "@/Utilities/ReactUtilities";
import {useAvatars} from "@/AvataaarLib/hook";
import {TreeApi} from "@/ui-components/Tree";
import * as Heading from "@/ui-components/Heading";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {usePage} from "@/Navigation/Redux";
import {useProject} from "@/Project/cache";
import {OldProjectRole} from "@/Project";
import {VariableSizeList} from "react-window";
import {initialState, stateReducer, useEventReducer} from "./State"
import {GiftSection, RootAllocationSections} from "./ProviderOnlySections";
import {projectInfoPi, useProjectInfos} from "@/Project/InfoCache";
import {AllocationPage} from "./AllocationPage";
import {useProjectHasProviderTools} from "./ProviderTools";
import {allocationCurrentCalendarYear} from "./AllocationPeriodFilter";
import type {AllocationDateRange} from "@/Accounting/Allocations/AllocationGrid";

// User-interface
// =====================================================================================================================
const Allocations: React.FunctionComponent = () => {
    const didCancel = useDidUnmount();
    const projectId = useProjectId();
    const [state, rawDispatch] = useReducer(stateReducer, initialState());
    const dispatchEvent = useEventReducer(didCancel, rawDispatch);
    const avatars = useAvatars();
    const suballocationTree = useRef<TreeApi>(null);
    const searchBox = useRef<HTMLInputElement>(null);
    const projectState = useProject();
    const project = projectState.fetch();
    const projectRole = project.status.myRole ?? OldProjectRole.USER;
    const listRef = useRef<VariableSizeList<number[]>>(null);

    usePage("Allocations", SidebarTabId.PROJECT);

    useEffect(() => {
        dispatchEvent({type: "Reset"});
        dispatchEvent({type: "Init"});
    }, [projectId]);

    const subProjectIds = useMemo(() => {
        return state.subAllocations.recipients
            .filter(it => it.owner.reference.type === "project")
            .map(it => it.owner.reference["projectId"] as string)
    }, [state.subAllocations]);

    const projectInfo = useProjectInfos(subProjectIds);
    useEffect(() => {
        dispatchEvent({type: "SubProjectData", projects: projectInfo.data});
    }, [projectInfo]);

    const [dateRange, setDateRange] = useState<AllocationDateRange>(allocationCurrentCalendarYear);

    React.useEffect(() => {
        const usernames = new Set<string>();
        for (const recipient of state.subAllocations.recipients) {
            usernames.add(recipient.owner.reference.type === "user" ?
                recipient.owner.reference.username :
                projectInfoPi(projectInfo.data[recipient.owner.reference.projectId], recipient.owner.primaryUsername) ?? "-");
        }
        avatars.updateCache([...usernames]);
    }, [projectInfo, state.subAllocations.recipients])

    // Event handlers
    // -----------------------------------------------------------------------------------------------------------------
    // These event handlers translate, primarily DOM, events to higher-level UIEvents which are sent to
    // dispatchEvent(). There is nothing complicated in these, but they do take up a bit of space. When you are writing
    // these, try to avoid having dependencies on more than just dispatchEvent itself.
    useEffect(() => {
        const handler = (ev: KeyboardEvent) => {
            switch (ev.code) {
                case "KeyK": {
                    if (ev.ctrlKey || ev.metaKey) {
                        ev.preventDefault();
                        const box = searchBox.current;
                        if (box) {
                            box.scrollIntoView({block: "nearest"});
                            box.focus();
                        }
                    }
                    break;
                }
            }
        };

        document.body.addEventListener("keydown", handler);
        return () => document.body.removeEventListener("keydown", handler);
    }, []);

    const onEdit = useCallback((elem: HTMLElement) => {
        const idx = parseInt(elem.getAttribute("data-idx") ?? "");
        const gidx = parseInt(elem.getAttribute("data-gidx") ?? "");
        const ridx = parseInt(elem.getAttribute("data-ridx") ?? "");
        if (isNaN(idx) || isNaN(ridx) || isNaN(gidx)) return;

        dispatchEvent({type: "SetEditing", allocationIdx: idx, groupIdx: gidx, recipientIdx: ridx, isEditing: true});
    }, []);

    const onSearchInput = useCallback((ev: React.SyntheticEvent) => {
        const input = ev.target as HTMLInputElement;
        const newQuery = input.value;
        dispatchEvent({type: "UpdateSearchQuery", newQuery});
    }, [dispatchEvent]);

    const onSearchKey = useCallback<React.KeyboardEventHandler>(ev => {
        ev.stopPropagation();
        const input = ev.target as HTMLInputElement;
        if (ev.code === "Escape") {
            input.blur();
            suballocationTree?.current?.activate();
        }
    }, []);

    const onSubAllocationShortcut = useCallback((target: HTMLElement, ev: KeyboardEvent) => {
        if (ev.code === "KeyE") {
            ev.preventDefault();
            onEdit(target);
        }
    }, [onEdit]);
    // Actual user-interface
    // -----------------------------------------------------------------------------------------------------------------
    if (project.status.personalProviderProjectFor != null) {
        return <MainContainer
            main={
                <>
                    <Heading.h2>Unavailable for this project</Heading.h2>
                    <p>
                        This project belongs to a provider which does not support the accounting and project management
                        features of UCloud. Try again with a different project.
                    </p>
                </>
            }
        />
    }

    const hasManagedProviders = (state.remoteData.managedProviders ?? []).length > 0;
    const hasProviderTools = useProjectHasProviderTools();
    const canCreateSubAllocator = hasManagedProviders || project.specification.canConsumeResources === false;

    return <AllocationPage
        dateRange={dateRange}
        onDateRangeChange={setDateRange}
        providerTools={hasProviderTools ? <>
            {hasManagedProviders ? <RootAllocationSections state={state} dispatchEvent={dispatchEvent} /> : null}
            <GiftSection state={state} dispatchEvent={dispatchEvent} />
        </> : undefined}
        subProjects={{
            projectId,
            canCreateSubAllocator,
            projectRole,
            state,
            onSearchInput,
            onSearchKey,
            searchBox,
            dispatchEvent,
            suballocationTree,
            listRef,
            onSubAllocationShortcut,
            avatars,
            projectInfo: projectInfo.data,
        }}
    />;
};

export default Allocations;
