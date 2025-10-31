import {injectStyle} from "@/Unstyled";
import * as React from "react";
import {useCallback, useEffect, useMemo, useReducer, useRef} from "react";
import {
    Box,
    Button,
    Checkbox,
    Divider,
    Flex,
    Input,
    Label,
    MainContainer,
} from "@/ui-components";
import {ProjectSwitcher} from "@/Project/ProjectSwitcher";
import * as Accounting from "@/Accounting";
import {
    NO_EXPIRATION_FALLBACK,
    ProductType,
} from "@/Accounting";
import {useProjectId} from "@/Project/Api";
import {useDidUnmount} from "@/Utilities/ReactUtilities";
import {callAPIWithErrorHandler} from "@/Authentication/DataHook";
import AppRoutes from "@/Routes";
import {
    bulkRequestOf,
    doNothing,
    timestampUnixMs
} from "@/UtilityFunctions";
import {useNavigate} from "react-router";
import {Client} from "@/Authentication/HttpClientInstance";
import {useAvatars} from "@/AvataaarLib/hook";
import {TreeApi} from "@/ui-components/Tree";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {dialogStore} from "@/Dialog/DialogStore";
import * as Heading from "@/ui-components/Heading";
import {checkCanConsumeResources} from "@/ui-components/ResourceBrowser";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {usePage} from "@/Navigation/Redux";
import {useProject} from "@/Project/cache";
import {OldProjectRole} from "@/Project";
import {VariableSizeList} from "react-window";
import {State, initialState, stateReducer, useEventReducer} from "./State"
import {ProviderOnlySections} from "./ProviderOnlySections";
import {
    YourAllocations,
    SubProjectList,
    resetOpenNodes,
    SubProjectAllocations
} from "./CommonSections";

// Styling
// =====================================================================================================================
const AllocationsStyle = injectStyle("allocations", k => `
    ${k} > header {
        display: flex;
        flex-direction: row;
        align-items: center;
        gap: 8px;
        margin-bottom: 16px;
    }
    
    ${k} > header h2 {
        font-size: 20px;
        margin: 0;
    }
    
    ${k} h1,
    ${k} h2,
    ${k} h4 {
        margin: 15px 0;
    }
    
    ${k} .disabled-alloc .row-left, ${k} .disabled-alloc .low-opaqueness {
        filter: opacity(0.5);
    }
    
    ${k} .sub-alloc {
        display: flex;
        align-items: center;
        gap: 8px;
    }
`);

// User-interface
// =====================================================================================================================
const Allocations: React.FunctionComponent = () => {
    const didCancel = useDidUnmount();
    const projectId = useProjectId();
    const navigate = useNavigate();
    const [state, rawDispatch] = useReducer(stateReducer, initialState());
    const dispatchEvent = useEventReducer(didCancel, rawDispatch);
    const avatars = useAvatars();
    const allocationTree = useRef<TreeApi>(null);
    const suballocationTree = useRef<TreeApi>(null);
    const searchBox = useRef<HTMLInputElement>(null);
    const projectState = useProject();
    const projectRole = projectState.fetch().status.myRole ?? OldProjectRole.USER;

    usePage("Allocations", SidebarTabId.PROJECT);

    useEffect(() => {
        dispatchEvent({type: "Reset"});
        dispatchEvent({type: "Init"});
    }, [projectId]);

    useEffect(() => {
        const users = new Set<string>();
        state.subAllocations.recipients.forEach(it => users.add(it.owner.primaryUsername));
        avatars.updateCache(Array.from(users));
    }, [state.subAllocations.recipients]);

    // Event handlers
    // -----------------------------------------------------------------------------------------------------------------
    // These event handlers translate, primarily DOM, events to higher-level UIEvents which are sent to
    // dispatchEvent(). There is nothing complicated in these, but they do take up a bit of space. When you are writing
    // these, try to avoid having dependencies on more than just dispatchEvent itself.
    useEffect(() => {
        const handler = (ev: KeyboardEvent) => {
            switch (ev.code) {
                case "Tab": {
                    ev.preventDefault();
                    const allocTreeActive = allocationTree.current?.isActive() === true;
                    const subAllocTreeActive = suballocationTree.current?.isActive() === true;

                    if (allocTreeActive) {
                        suballocationTree.current?.activate();
                    } else if (subAllocTreeActive) {
                        allocationTree.current?.activate();
                    } else {
                        allocationTree.current?.activate();
                    }
                    break;
                }

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

    const currentPeriodEnd = useMemo(() => {
        let currentMinimum = NO_EXPIRATION_FALLBACK;
        for (const v of Object.values(state.yourAllocations)) {
            for (const wallet of v.wallets) {
                for (const alloc of wallet.allocations) {
                    if (alloc.end < currentMinimum) {
                        currentMinimum = alloc.end;
                    }
                }
            }
        }
        return currentMinimum;
    }, [state.yourAllocations]);

    const onNewSubProject = useCallback(async () => {
        dialogStore.addDialog(
            <form onSubmit={ev => {
                ev.preventDefault();

                const name = document.querySelector<HTMLInputElement>("#subproject-name");
                if (!name) return;
                if (!name.value) {
                    snackbarStore.addFailure("Missing name", false);
                    return;
                }

                const subAllocatorCheckbox = document.querySelector<HTMLInputElement>("#subproject-suballocator");
                const subAllocator = subAllocatorCheckbox?.checked === true;

                dialogStore.success();
                navigate(AppRoutes.grants.grantGiverInitiatedEditor({
                    title: name.value,
                    start: timestampUnixMs(),
                    end: currentPeriodEnd,
                    piUsernameHint: Client.username ?? "?",
                    subAllocator,
                }));
            }}>
                <div>
                    <Heading.h3>New sub-project</Heading.h3>
                    <Divider/>
                    <Label>
                        Project title
                        <Input onKeyDown={e => {
                            if (e.code !== "Escape") {
                                e.stopPropagation();
                            }
                        }} id={"subproject-name"} autoFocus/>
                    </Label>
                    {(state.remoteData.managedProviders ?? []).length > 0 || !checkCanConsumeResources(Client.projectId ?? null, {api: {isCoreResource: false}}) ?
                        <Label>
                            <Checkbox id={"subproject-suballocator"}/>
                            This sub-project is a sub-allocator
                        </Label> : null
                    }
                </div>
                <Flex mt="20px">
                    <Button type={"button"} onClick={dialogStore.failure.bind(dialogStore)} color={"errorMain"}
                            mr="5px">Cancel</Button>
                    <Button type={"submit"} color={"successMain"}>Create sub-project</Button>
                </Flex>
            </form>,
            doNothing
        );
    }, [currentPeriodEnd, (state.remoteData.managedProviders ?? []).length]);

    const onEdit = useCallback((elem: HTMLElement) => {
        const idx = parseInt(elem.getAttribute("data-idx") ?? "");
        const gidx = parseInt(elem.getAttribute("data-gidx") ?? "");
        const ridx = parseInt(elem.getAttribute("data-ridx") ?? "");
        if (isNaN(idx) || isNaN(ridx) || isNaN(gidx)) return;

        dispatchEvent({type: "SetEditing", allocationIdx: idx, groupIdx: gidx, recipientIdx: ridx, isEditing: true});
    }, []);

    const onEditKey = useCallback(async (ev: React.KeyboardEvent) => {
        ev.stopPropagation();
        const elem = ev.target as HTMLInputElement;
        const idx = parseInt(elem.getAttribute("data-idx") ?? "");
        const gidx = parseInt(elem.getAttribute("data-gidx") ?? "");
        const ridx = parseInt(elem.getAttribute("data-ridx") ?? "");
        switch (ev.code) {
            case "Enter": {
                const value = parseInt(elem.value);
                if (!isNaN(value)) {
                    const group = state.subAllocations.recipients[ridx].groups[gidx];
                    const alloc = group.allocations[idx]!;
                    const unit = Accounting.explainUnit(group.category);
                    const success = (await callAPIWithErrorHandler(
                        Accounting.updateAllocationV2(bulkRequestOf({
                            allocationId: alloc.allocationId,
                            newQuota: value * unit.invBalanceFactor,
                            reason: "Allocation updated with new quota",
                        }))
                    )) !== null;

                    if (success) {
                        dispatchEvent({
                            type: "UpdateAllocation",
                            allocationIdx: idx,
                            recipientIdx: ridx,
                            groupIdx: gidx,
                            newQuota: value * unit.invBalanceFactor,
                        });
                    }

                    dispatchEvent({
                        type: "SetEditing", allocationIdx: idx, groupIdx: gidx, recipientIdx: ridx,
                        isEditing: false
                    });
                }
                break;
            }

            case "Escape": {
                dispatchEvent({
                    type: "SetEditing", allocationIdx: idx, recipientIdx: ridx, groupIdx: gidx,
                    isEditing: false
                });
                break;
            }
        }
    }, [state.subAllocations.recipients]);

    const onEditBlur = useCallback((ev: React.SyntheticEvent) => {
        const elem = ev.target as HTMLInputElement;
        const idx = parseInt(elem.getAttribute("data-idx") ?? "");
        const ridx = parseInt(elem.getAttribute("data-ridx") ?? "");
        const gidx = parseInt(elem.getAttribute("data-gidx") ?? "");
        dispatchEvent({
            type: "SetEditing", allocationIdx: idx, recipientIdx: ridx, groupIdx: gidx,
            isEditing: false
        });
    }, [dispatchEvent]);

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
    // Short-hands used in the user-interface
    // -----------------------------------------------------------------------------------------------------------------
    const indent = 16;

    const sortedAllocations = Object.entries(state.yourAllocations).sort((a, b) => {
        const aPriority = Accounting.ProductTypesByPriority.indexOf(a[0] as ProductType);
        const bPriority = Accounting.ProductTypesByPriority.indexOf(b[0] as ProductType);

        if (aPriority === bPriority) return 0;
        if (aPriority === -1) return 1;
        if (bPriority === -1) return -1;
        if (aPriority < bPriority) return -1;
        if (aPriority > bPriority) return 1;
        return 0;
    });

    // Actual user-interface
    // -----------------------------------------------------------------------------------------------------------------
    if (projectState.fetch().status.personalProviderProjectFor != null) {
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

    React.useEffect(() => {
        resetOpenNodes();
    }, [projectId]);

    const listRef = useRef<VariableSizeList<number[]>>(null);

    return <MainContainer
        headerSize={0}
        main={<div className={AllocationsStyle}>
            <header>
                <h3 className="title">Resource allocations</h3>
                <Box flexGrow={1}/>
                <ProjectSwitcher/>
            </header>

            <ProviderOnlySections state={state} dispatchEvent={dispatchEvent}/>

            <YourAllocations state={state} allocations={sortedAllocations} allocationTree={allocationTree} indent={indent}/>

            {/*<ResourcesGranted state={state} allocationTree={allocationTree} sortedAllocations={sortedAllocations}*/}
            {/*                  indent={indent} avatars={avatars}/>*/}

            <SubProjectAllocations allocations={sortedAllocations} indent={indent} />

            <SubProjectList projectId={projectId} onNewSubProject={onNewSubProject} projectRole={projectRole}
                                   state={state} onSearchInput={onSearchInput} onSearchKey={onSearchKey}
                                   searchBox={searchBox} dispatchEvent={dispatchEvent}
                                   suballocationTree={suballocationTree} listRef={listRef}
                                   onSubAllocationShortcut={onSubAllocationShortcut} avatars={avatars} onEdit={onEdit}
                                   onEditKey={onEditKey} onEditBlur={onEditBlur} />
        </div>}
    />;
};

export default Allocations;