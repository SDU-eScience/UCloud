import * as React from "react";
import {useCallback, useEffect, useMemo, useReducer, useRef, useState} from "react";
import {
    Button,
    Checkbox,
    Divider,
    Flex,
    Input,
    Label,
    MainContainer,
} from "@/ui-components";
import {
    NO_EXPIRATION_FALLBACK,
} from "@/Accounting";
import {useProjectId} from "@/Project/Api";
import {useDidUnmount} from "@/Utilities/ReactUtilities";
import AppRoutes from "@/Routes";
import {
    doNothing,
    timestampUnixMs
} from "@/UtilityFunctions";
import {useNavigate} from "react-router-dom";
import {Client} from "@/Authentication/HttpClientInstance";
import {useAvatars} from "@/AvataaarLib/hook";
import {TreeApi} from "@/ui-components/Tree";
import {dialogStore} from "@/Dialog/DialogStore";
import * as Heading from "@/ui-components/Heading";
import {checkCanConsumeResources} from "@/ui-components/ResourceBrowser";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {usePage} from "@/Navigation/Redux";
import {useProject} from "@/Project/cache";
import {OldProjectRole} from "@/Project";
import {VariableSizeList} from "react-window";
import {initialState, stateReducer, useEventReducer} from "./State"
import {GiftSection, RootAllocationSections} from "./ProviderOnlySections";
import {projectInfoPi, useProjectInfos} from "@/Project/InfoCache";
import {sendFailureNotification} from "@/Notifications";
import {callAPI} from "@/Authentication/DataHook";
import * as Grants from "@/Grants";
import {DefaultTemplateRevision} from "@/Grants/Editor";
import {AllocationPage} from "./AllocationPage";
import {allocationCurrentCalendarYear} from "./AllocationPeriodFilter";
import type {AllocationDateRange} from "@/Accounting/Allocations/AllocationGrid";

// User-interface
// =====================================================================================================================
const Allocations: React.FunctionComponent = () => {
    const didCancel = useDidUnmount();
    const projectId = useProjectId();
    const navigate = useNavigate();
    const [state, rawDispatch] = useReducer(stateReducer, initialState());
    const dispatchEvent = useEventReducer(didCancel, rawDispatch);
    const avatars = useAvatars();
    const suballocationTree = useRef<TreeApi>(null);
    const searchBox = useRef<HTMLInputElement>(null);
    const projectState = useProject();
    const projectRole = projectState.fetch().status.myRole ?? OldProjectRole.USER;
    const listRef = useRef<VariableSizeList<number[]>>(null);
    const didUnmount = useDidUnmount();

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

    const [settings, setSettings] = useState<Grants.RequestSettings>({
        enabled: false,
        description: "No description",
        allowRequestsFrom: [],
        excludeRequestsFrom: [],
        templates: {
            type: "structured",
            structured: {
                existingProject: [],
                newProject: [],
                personalProject: [],
                revisionNumber: DefaultTemplateRevision
            }
        }
    });

    useEffect(() => {
        if (!projectId) return;
        (async () => {
            try {
                const res = await callAPI<Grants.RequestSettings>(
                    {
                        ...Grants.retrieveRequestSettings(),
                        projectOverride: projectId,
                    }
                );

                if (!didUnmount.current) setSettings(res);
            } catch (e) {
                // Ignoring failure
            }
        })();
    }, [projectId]);

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
                    sendFailureNotification("Missing name");
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
                    <Divider />
                    <Label>
                        Project title
                        <Input onKeyDown={e => {
                            if (e.code !== "Escape") {
                                e.stopPropagation();
                            }
                        }} id={"subproject-name"} autoFocus />
                    </Label>
                    {(state.remoteData.managedProviders ?? []).length > 0 || !checkCanConsumeResources(Client.projectId ?? null, {api: {isCoreResource: false}}) ?
                        <Label>
                            <Checkbox id={"subproject-suballocator"} />
                            This sub-project is a sub-allocator
                        </Label> : null
                    }
                </div>
                <Flex mt="20px">
                    <Button type={"button"} onClick={dialogStore.failure.bind(dialogStore)} color={"errorMain"} mr="5px">Cancel</Button>
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

    return <AllocationPage
        dateRange={dateRange}
        onDateRangeChange={setDateRange}
        providerTools={(state.remoteData.managedProviders ?? []).length > 0 ? <>
            <RootAllocationSections state={state} dispatchEvent={dispatchEvent} />
            <GiftSection state={state} dispatchEvent={dispatchEvent} />
        </> : settings.enabled ? <GiftSection state={state} dispatchEvent={dispatchEvent} /> : null}
        subProjects={{
            projectId,
            onNewSubProject,
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
