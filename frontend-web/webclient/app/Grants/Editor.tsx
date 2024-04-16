import * as React from "react";
import {useCallback, useEffect, useLayoutEffect, useMemo, useReducer, useRef} from "react";
import {injectStyle} from "@/Unstyled";
import MainContainer from "@/ui-components/MainContainer";
import {Box, Button, Checkbox, Icon, Input, Select, TextArea} from "@/ui-components";
import {IconName} from "@/ui-components/Icon";
import {ProjectLogo} from "@/Grants/ProjectLogo";
import {ProviderLogo} from "@/Providers/ProviderLogo";
import {ProviderTitle} from "@/Providers/ProviderTitle";
import {PageV2} from "@/UCloud";
import {callAPI, callAPIWithErrorHandler} from "@/Authentication/DataHook";
import {useDidUnmount} from "@/Utilities/ReactUtilities";
import * as Grants from ".";
import * as Accounting from "@/Accounting";
import * as Projects from "@/Project/Api";
import {useProjectId} from "@/Project/Api";
import {Client} from "@/Authentication/HttpClientInstance";
import HexSpin from "@/LoadingIcon/LoadingIcon";
import {fetchAll} from "@/Utilities/PageUtilities";
import {getQueryParam} from "@/Utilities/URIUtilities";
import {useLocation, useNavigate} from "react-router";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";
import {errorMessageOrDefault, timestampUnixMs} from "@/UtilityFunctions";
import {UserAvatar} from "@/AvataaarLib/UserAvatar";
import {dateToString} from "@/Utilities/DateUtilities";
import {useAvatars} from "@/AvataaarLib/hook";
import {addStandardInputDialog} from "@/UtilityComponents";
import {dialogStore} from "@/Dialog/DialogStore";
import {createRecordFromArray, deepCopy} from "@/Utilities/CollectionUtilities";
import {TooltipV2} from "@/ui-components/Tooltip";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {CSSVarCurrentSidebarStickyWidth, CSSVarCurrentSidebarWidth} from "@/ui-components/List";
import AppRoutes from "@/Routes";
import {Project, isAdminOrPI} from "@/Project";
import {BaseLinkClass} from "@/ui-components/BaseLink";
import {usePage} from "@/Navigation/Redux";
import {formatDistance} from "date-fns/formatDistance";
import {SidebarTabId} from "@/ui-components/SidebarComponents";

// State model
// =====================================================================================================================
interface EditorState {
    locked: boolean;
    loading: boolean;

    fullScreenLoading: boolean;
    fullScreenError?: string;

    allocationPeriod: {start: {month: number, year: number}, durationInMonths: number};
    principalInvestigator: string;
    loadedProjects: {id: string | null; title: string;}[];

    stateDuringCreate?: {
        creatingWorkspace: boolean;
        reference?: string;
    };

    stateDuringEdit?: {
        id: string;
        storedApplication?: Grants.Application;

        wallets: Accounting.WalletV2[];

        overallState: Grants.State;
        stateByGrantGiver: Record<string, Grants.State>;

        title: string;
        recipient: Grants.Recipient;
        recipientInfo: {
            piUsername: string;
            workspaceTitle: string;
        };

        allowWithdrawal: boolean;

        newestRevision: number;
        activeRevision: number;
        revisions: SimpleRevision[];

        document: Grants.Doc;
        referenceIds: string[];
        comments?: Grants.Comment[];
    };

    allocators: {
        id: string;
        title: string;
        description: string;
        template: string;
        checked: boolean;
    }[];

    application: ApplicationSection[];
    applicationDocument: Record<string, string>;

    resources: Record<string, ResourceCategory[]>;
}

interface SimpleRevision {
    revisionNumber: number;
    createdAt: number;
    updatedBy: string;
    changeLog?: string | null;
}

interface ResourceCategory {
    category: Accounting.ProductCategoryV2;
    allocators: Set<string>;
    totalBalanceRequested: Record<string, number>;
    error?: {
        allocator: string;
        message: string;
    };
}

const defaultState: EditorState = {
    locked: false,
    allocators: [],
    resources: {},
    allocationPeriod: {
        start: {
            month: new Date().getMonth(),
            year: new Date().getFullYear(),
        },
        durationInMonths: 12
    },
    application: [],
    applicationDocument: {},
    loading: false,
    principalInvestigator: Client.activeUsername ?? "",
    loadedProjects: [],
    fullScreenLoading: true,
};

// State reducer
// =====================================================================================================================
type EditorAction =
    {type: "GrantLoaded", grant: Grants.Application, wallets: Accounting.WalletV2[]}
    | {
        type: "GrantGiverInitiatedLoaded",
        wallets: Accounting.WalletV2[],
        start: number,
        end: number,
        title: string,
        projectId?: string,
        piUsernameHint: string
    }
    | {type: "AllocatorsLoaded", allocators: Grants.GrantGiver[]}
    | {type: "DurationUpdated", month?: number, year?: number, duration?: number}
    | {type: "AllocatorChecked", isChecked: boolean, allocatorId: string}
    | {
        type: "BalanceUpdated",
        provider: string,
        category: string,
        allocator: string,
        balance: number | null,
    }
    | {type: "SetIsCreating"}
    | {type: "RecipientUpdated", isCreatingNewProject: boolean, reference?: string}
    | {type: "ProjectsReloaded", projects: {id: string | null, title: string}[]}
    | {type: "ApplicationUpdated", section: string, contents: string}
    | {type: "LoadingStateChange", isLoading: boolean}
    | {type: "ReferenceIdUpdated", newReferenceId: string, idx: number}
    | {type: "CleanupReferenceIds"}
    | {type: "CommentPosted", comment: string, grantId: string}
    | {type: "CommentsReloaded", comments: Grants.Comment[]}
    | {type: "CommentDeleted", grantId: string, commentId: string}
    | {type: "Unlock"}
    | {type: "OpenRevision", revision: number}
    | {type: "GrantGiverStateChange", grantGiver: string, approved: boolean, grantId: string}
    | {type: "UpdateFullScreenLoading", isLoading: boolean}
    | {type: "UpdateFullScreenError", error: string}
    | {type: "SetResourceError", provider: string, category: string, allocator: string, message: string}
    ;

function stateReducer(state: EditorState, action: EditorAction): EditorState {
    switch (action.type) {
        // Loading and error state
        // -------------------------------------------------------------------------------------------------------------
        case "UpdateFullScreenError": {
            return {
                ...state,
                fullScreenLoading: false,
                fullScreenError: action.error,
            };
        }

        case "UpdateFullScreenLoading": {
            return {
                ...state,
                fullScreenLoading: action.isLoading
            };
        }

        case "LoadingStateChange": {
            return {
                ...state,
                loading: action.isLoading
            };
        }

        // Initialization events
        // -------------------------------------------------------------------------------------------------------------
        // These events are usually triggered by a network response and often near the beginning. These are sometimes
        // executed later to refresh the state of the UI.
        //
        // Notable events:
        // - AllocatorsLoaded: Fired when the affiliations (i.e. the potential grant givers) are loaded. This is
        //                     always loaded before other events.
        // - GrantLoaded     : Fired when an existing grant application has finished loading.
        // - GrantGiverInitiatedLoaded : Fired when we are creating a grant giver initiated grant application
        // - SetIsCreating   : Fired when we won't be loading an existing grant application (because we are creating
        //                     a new one).

        case "AllocatorsLoaded": {
            const newAllocators: EditorState["allocators"] = state.allocators
                .filter(it => action.allocators.some(other => it.id === other.id));
            const newResources: EditorState["resources"] = {...state.resources};

            let templateKey: keyof Grants.Templates = "newProject";
            if (state.stateDuringCreate) {
                if (state.stateDuringCreate.creatingWorkspace) {
                    templateKey = "newProject";
                } else if (state.stateDuringCreate.reference) {
                    templateKey = "existingProject";
                } else {
                    templateKey = "personalProject";
                }
            } else {
                const recipient = state.stateDuringEdit?.recipient;
                if (recipient) {
                    switch (recipient.type) {
                        case "personalWorkspace":
                            templateKey = "personalProject";
                            break;
                        case "newProject":
                            templateKey = "newProject";
                            break;
                        case "existingProject":
                            templateKey = "existingProject";
                            break;
                    }
                }
            }

            let i = 0;
            for (const allocator of action.allocators) {
                const existing = newAllocators.find(it => it.id === allocator.id);
                if (!existing) {
                    newAllocators.push({
                        id: allocator.id, title: allocator.title, description: allocator.description,
                        template: allocator.templates[templateKey], checked: false
                    });
                } else if (existing.template !== allocator.templates[templateKey]) {
                    newAllocators[i] = {...existing, template: allocator.templates[templateKey]};
                }

                for (const category of allocator.categories) {
                    let sectionForProvider = newResources[category.provider];
                    if (!sectionForProvider) {
                        newResources[category.provider] = [];
                        sectionForProvider = newResources[category.provider]!;
                    }

                    const existing = sectionForProvider.find(it => it.category.name === category.name);
                    if (existing) {
                        existing.allocators.add(allocator.id);
                    } else {
                        const allocators = new Set<string>();
                        allocators.add(allocator.id);
                        sectionForProvider.push({category, allocators, totalBalanceRequested: {}});
                    }
                }
                i++;
            }

            for (const arr of Object.values(newResources)) {
                arr.sort((a, b) => Accounting.categoryComparator(a.category, b.category));
            }

            const allSections = calculateNewApplication(
                action.allocators
                    .filter(it => newAllocators.find(existing => existing.id === it.id)?.checked === true)
                    .map(it => it.templates[templateKey])
            );

            return {
                ...state,
                allocators: newAllocators,
                resources: newResources,
                application: allSections,
            };
        }

        case "SetIsCreating": {
            return {
                ...state,
                stateDuringEdit: undefined,
                stateDuringCreate: state.stateDuringCreate ?? {
                    creatingWorkspace: true,
                }
            };
        }

        case "GrantLoaded": {
            const tempState: EditorState = {
                ...state,
                stateDuringCreate: undefined,
                stateDuringEdit: {
                    id: action.grant.id,
                    storedApplication: action.grant,
                    activeRevision: action.grant.currentRevision.revisionNumber,
                    wallets: action.wallets,
                    title: "",
                    referenceIds: [],
                    recipient: action.grant.currentRevision.document.recipient,
                    recipientInfo: {
                        piUsername: action.grant.status.projectPI,
                        workspaceTitle: action.grant.status.projectTitle ?? "Personal workspace",
                    },
                    comments: action.grant.status.comments,
                    revisions: action.grant.status.revisions.map(rev => ({
                        changeLog: rev.document.revisionComment,
                        createdAt: rev.createdAt,
                        revisionNumber: rev.revisionNumber,
                        updatedBy: rev.updatedBy
                    })),
                    newestRevision: action.grant.currentRevision.revisionNumber,
                    stateByGrantGiver: createRecordFromArray(
                        action.grant.status.stateBreakdown,
                        value => [value.projectId, value.state]
                    ),
                    overallState: action.grant.status.overallState,
                    document: action.grant.currentRevision.document,
                    allowWithdrawal: action.grant.createdBy === Client.username,
                },
                locked: true,
            };

            return loadRevision(tempState);
        }

        case "GrantGiverInitiatedLoaded": {
            const recipient: Grants.Recipient = {
                type: action.projectId ? "existingProject" : "newProject",
                title: action.title,
                id: action.projectId ?? action.title,
                username: action.piUsernameHint,
            };

            const start = new Date(Math.max(timestampUnixMs(), action.start));
            const end = new Date(action.end + 1000);

            const newState: EditorState = {
                ...state,
                allocationPeriod: {
                    start: {
                        month: start.getUTCMonth(),
                        year: start.getUTCFullYear()
                    },
                    durationInMonths: Math.max(
                        3,
                        ((end.getUTCFullYear() - start.getUTCFullYear()) * 12) +
                        (end.getUTCMonth() - start.getUTCMonth()),
                    )
                },
                stateDuringEdit: {
                    id: GRANT_GIVER_INITIATED_ID,
                    activeRevision: 0,
                    wallets: action.wallets,
                    title: "",
                    referenceIds: [],
                    recipient,
                    recipientInfo: {
                        piUsername: action.piUsernameHint,
                        workspaceTitle: action.title,
                    },
                    overallState: Grants.State.IN_PROGRESS,
                    stateByGrantGiver: {},
                    allowWithdrawal: false,
                    newestRevision: 0,
                    revisions: [{revisionNumber: 0, updatedBy: Client.username ?? "?", createdAt: timestampUnixMs()}],
                    document: {
                        recipient,
                        allocationRequests: [],
                        parentProjectId: Client.projectId,
                        referenceIds: [],
                        revisionComment: null,
                        form: {
                            type: "plain_text",
                            text: "",
                        }
                    }
                }
            };

            newState.allocators = newState.allocators.map(it => ({...it, checked: true}));

            return loadRevision(newState);
        }

        // Form events
        // -------------------------------------------------------------------------------------------------------------
        // These events handle anything related to the application form itself. This covers actions such as changing
        // an input field or checking off a checkbox.

        case "DurationUpdated": {
            return {
                ...state,
                allocationPeriod: {
                    start: {
                        month: action.month ?? state.allocationPeriod.start.month,
                        year: action.year ?? state.allocationPeriod.start.year,
                    },
                    durationInMonths: action.duration ?? state.allocationPeriod.durationInMonths,
                }
            };
        }

        case "ApplicationUpdated": {
            const newContents = {...state.applicationDocument};
            newContents[action.section] = action.contents;

            return {
                ...state,
                applicationDocument: newContents,
            };
        }

        case "RecipientUpdated": {
            if (state.stateDuringCreate === undefined) return state;

            return {
                ...state,
                stateDuringCreate: {
                    ...state.stateDuringCreate,
                    creatingWorkspace: action.isCreatingNewProject,
                    reference: action.reference,
                }
            };
        }

        case "AllocatorChecked": {
            const newAllocators = state.allocators.map(it => {
                if (it.id === action.allocatorId) {
                    return {...it, checked: action.isChecked};
                } else {
                    return it;
                }
            });

            const allSections = calculateNewApplication(
                newAllocators
                    .filter(it => it.checked)
                    .map(it => it.template)
            );

            return {
                ...state,
                allocators: newAllocators,
                application: allSections,
            }
        }

        case "BalanceUpdated": {
            const categoryIdx = state.resources[action.provider]?.findIndex(it => it.category.name === action.category);
            if (categoryIdx === -1) return state;

            const newResources = {...state.resources};
            const newSection = [...newResources[action.provider]];
            newResources[action.provider] = newSection;
            const category = newSection[categoryIdx];

            if (action.balance === null) {
                delete category.totalBalanceRequested[action.allocator];
            } else {
                category.totalBalanceRequested[action.allocator] = action.balance;
            }

            return {
                ...state,
                resources: newResources
            };
        }

        case "ReferenceIdUpdated": {
            if (state.stateDuringEdit === undefined) return state;
            const newIds = [...state.stateDuringEdit.referenceIds];
            const missing = (action.idx + 1) - newIds.length;
            for (let i = 0; i < missing; i++) newIds.push("");
            newIds[action.idx] = action.newReferenceId;

            return {
                ...state,
                stateDuringEdit: {
                    ...state.stateDuringEdit,
                    referenceIds: newIds,
                }
            };
        }

        case "CleanupReferenceIds": {
            if (state.stateDuringEdit === undefined) return state;
            const newIds = [...state.stateDuringEdit.referenceIds].filter(it => it.length > 0);

            return {
                ...state,
                stateDuringEdit: {
                    ...state.stateDuringEdit,
                    referenceIds: newIds,
                }
            };
        }

        case "SetResourceError": {
            const resources = deepCopy(state.resources);
            for (const categories of Object.values(resources)) {
                for (const category of categories) {
                    if (category.category.name === action.category) {
                        category.error = {
                            allocator: action.allocator,
                            message: action.message
                        };
                    } else {
                        category.error = undefined;
                    }
                }
            }

            return {
                ...state,
                resources
            };
        }

        // Partial reloads
        // -------------------------------------------------------------------------------------------------------------
        // These events return a partially reloaded application. We typically do this to ensure that our state is
        // up-to-date with the current version.
        case "ProjectsReloaded": {
            return {
                ...state,
                loadedProjects: action.projects
            };
        }

        case "CommentsReloaded": {
            if (state.stateDuringEdit === undefined) return state;
            return {
                ...state,
                stateDuringEdit: {
                    ...state.stateDuringEdit,
                    comments: action.comments,
                }
            };
        }

        // Control
        // -------------------------------------------------------------------------------------------------------------
        // These are various forms of "control" events. These all share that they are triggered because of some command
        // from the user. For example, the user might command that the application be approved. These will typically
        // be followed by a network request, but not always. If they are, then we will predicatively update the state
        // until a partial/full reload occurs later.
        case "OpenRevision": {
            if (state.stateDuringEdit === undefined) return state;
            if (!state.locked) return state;

            const app = state.stateDuringEdit.storedApplication;
            if (!app) return state;

            return loadRevision({
                ...state,
                stateDuringEdit: {
                    ...state.stateDuringEdit,
                    activeRevision: action.revision,
                    document: app.status.revisions.find(it => it.revisionNumber === action.revision)!.document,
                }
            });
        }

        case "GrantGiverStateChange": {
            if (state.stateDuringEdit === undefined) return state;
            const newState: EditorState = {
                ...state,
                stateDuringEdit: {
                    ...state.stateDuringEdit,
                    stateByGrantGiver: {
                        ...state.stateDuringEdit.stateByGrantGiver,
                    }
                }
            };

            newState.stateDuringEdit!.stateByGrantGiver[action.grantGiver] =
                action.approved ? Grants.State.APPROVED : Grants.State.REJECTED;

            return newState;
        }

        case "Unlock": {
            return {
                ...state,
                locked: false
            };
        }

        case "CommentPosted": {
            if (state.stateDuringEdit === undefined) return state;
            return {
                ...state,
                stateDuringEdit: {
                    ...state.stateDuringEdit,
                    comments: [
                        ...(state.stateDuringEdit.comments ?? []),
                        {
                            comment: action.comment,
                            username: Client.username!,
                            id: "",
                            createdAt: timestampUnixMs()
                        }
                    ]
                }
            };
        }

        case "CommentDeleted": {
            if (state.stateDuringEdit === undefined) return state;
            return {
                ...state,
                stateDuringEdit: {
                    ...state.stateDuringEdit,
                    comments: state.stateDuringEdit.comments?.filter(it => it.id !== action.commentId)
                }
            };
        }
    }

    // Scoped utility functions
    // -----------------------------------------------------------------------------------------------------------------
    function calculateNewApplication(templates: string[]): ApplicationSection[] {
        const allSections: ApplicationSection[] = [];
        for (const template of templates) {
            const theseSections = parseIntoSections(template)
            for (const section of theseSections) {
                if (allSections.some(it => it.title === section.title)) continue
                allSections.push(section);
            }
        }
        return allSections;
    }

    function loadRevision(state: EditorState): EditorState {
        if (!state.stateDuringEdit) return state;
        const newEditState = {...state.stateDuringEdit};

        const doc = state.stateDuringEdit.document;
        const docText = doc.form.text;

        const newAllocators = [...state.allocators]
            .filter(allocator => {
                return state.stateDuringEdit?.id === GRANT_GIVER_INITIATED_ID ||
                    doc.allocationRequests.some(it => it.grantGiver === allocator.id);
            });

        const app = state.stateDuringEdit.storedApplication;
        if (app) {
            for (const breakdown of app.status.stateBreakdown) {
                const existingAllocator = newAllocators.find(it => it.id === breakdown.projectId);
                if (!existingAllocator) {
                    newAllocators.push({
                        title: breakdown.projectTitle,
                        id: breakdown.projectId,
                        template: "",
                        description: "",
                        checked: true
                    });
                }
            }
        }

        newAllocators.forEach(it => it.checked = true);

        const newResources: EditorState["resources"] = {};
        for (const [provider, categories] of Object.entries(state.resources)) {
            const relevantCategories = categories.filter(it => {
                return newAllocators.some(a => it.allocators.has(a.id))
            });

            if (relevantCategories.length <= 0) continue;

            newResources[provider] = categories.map(it => ({
                ...it,
                totalBalanceRequested: {}
            }));
        }

        for (const request of doc.allocationRequests) {
            const section = newResources[request.provider];
            if (!section) continue;

            for (const category of section) {
                const {priceFactor} = Accounting.explainUnit(category.category);
                if (request.category !== category.category.name) continue;

                category.totalBalanceRequested[request.grantGiver] = request.balanceRequested * priceFactor;
            }
        }

        const isGrantGiverInitiated = app && app.status.overallState == "APPROVED" && app.status.revisions.length === 1 && docText.startsWith(grantGiverInitiatedPrefix);

        const docSections = parseIntoSections(docText);
        const templates = isGrantGiverInitiated ? [grantGiverInitiatedTemplate] : newAllocators.map(it => it.template);
        const newApplication = calculateNewApplication(templates);

        const newApplicationDocument: EditorState["applicationDocument"] = {};
        let otherSection = "";
        for (const section of docSections) {
            const hasSection = newApplication.some(it => it.title === section.title);
            if (hasSection) {
                newApplicationDocument[section.title] = section.description;
            } else {
                otherSection += section.title;
                otherSection += ":\n\n";
                otherSection += section.description
                otherSection += "\n\n";
            }
        }

        if (otherSection) {
            newApplication.push({title: "Other", rows: 6, mandatory: false, description: ""});
            newApplicationDocument["Other"] = otherSection;
        }

        const startDate = new Date(
            doc.allocationRequests
                .map(it => new Date(it.period.start ?? Date.now()).getTime())
                .reduce((a, b) => Math.min(a, b), Number.MAX_SAFE_INTEGER)
        );

        const endDate = new Date(
            doc.allocationRequests
                .map(it => new Date(it.period.end ?? Date.now()).getTime())
                .reduce((a, b) => Math.max(a, b), 0) + 1000
        ); // Off by one second in some cases, let's just adjust slightly here.

        const startYear = Math.max(2019, startDate.getUTCFullYear());
        const startMonth = startDate.getUTCMonth();
        const normalizedStart = (startDate.getUTCFullYear() * 12) + (startDate.getUTCMonth() + 1);
        const normalizedEnd = (endDate.getUTCFullYear() * 12) + (endDate.getUTCMonth() + 1);

        const projectPi = state.stateDuringEdit.recipientInfo.piUsername;
        let recipientName: string;
        switch (doc.recipient.type) {
            case "existingProject": {
                recipientName = newEditState.recipientInfo.workspaceTitle ?? "";
                break;
            }
            case "newProject": {
                recipientName = doc.recipient.title;
                break;
            }
            case "personalWorkspace": {
                recipientName = "Personal workspace of " + doc.recipient.username;
                break;
            }
        }

        newEditState.title = recipientName;
        newEditState.referenceIds = doc.referenceIds ?? [];

        return {
            ...state,
            allocators: newAllocators,
            resources: newResources,
            application: newApplication,
            applicationDocument: newApplicationDocument,
            allocationPeriod: state.stateDuringEdit.id === GRANT_GIVER_INITIATED_ID ? state.allocationPeriod : {
                start: {
                    month: startMonth,
                    year: startYear,
                },
                durationInMonths: normalizedEnd - normalizedStart,
            },
            principalInvestigator: projectPi,
            stateDuringEdit: newEditState,
        };
    }
}

// State reducer middleware
// =====================================================================================================================
type EditorEvent =
    EditorAction
    | {type: "Init", grantId?: string, scrollToTop?: boolean, affiliationRequest?: Grants.RetrieveGrantGiversRequest}
    | {type: "InitGrantGiverInitiated", title: string, projectId?: string, start: number, end: number, piUsernameHint: string}
    | {type: "Withdraw", id: string}
    ;

function useStateReducerMiddleware(
    doDispatch: (e: EditorAction) => void,
    scrollToTopRef: React.MutableRefObject<boolean>,
): {dispatchEvent: (e: EditorEvent) => unknown} {
    const didCancel = useDidUnmount();
    const dispatchEvent = useCallback(async (event: EditorEvent) => {
        function dispatch(ev: EditorAction) {
            if (didCancel.current === true) return;
            doDispatch(ev);
        }

        switch (event.type) {
            case "Init": {
                let affiliationRequest: Grants.RetrieveGrantGiversRequest;
                if (event.grantId) {
                    affiliationRequest = {type: "ExistingApplication", id: event.grantId};
                } else {
                    affiliationRequest = {type: "PersonalWorkspace"};
                }
                if (event.affiliationRequest) affiliationRequest = event.affiliationRequest;

                const pAffiliations = callAPI(Grants.retrieveGrantGivers(affiliationRequest));

                const projectPromise = callAPI(
                    Projects.api.browse({
                        itemsPerPage: 250,
                    })
                );

                if (event.grantId) {
                    try {
                        const pApp = callAPI(Grants.retrieve({id: event.grantId}));

                        const affiliations = await pAffiliations;
                        dispatch({type: "AllocatorsLoaded", allocators: affiliations.grantGivers});

                        const application = await pApp;

                        const projectPage = await projectPromise;
                        const allRelevantAllocators = new Set(application.status.revisions.flatMap(rev =>
                            rev.document.allocationRequests.map(it => it.grantGiver)));

                        const projectsToFetchAllocationsFor = projectPage.items
                            .filter(it =>
                                isAdminOrPI(it.status.myRole!) && allRelevantAllocators.has(it.id))
                            .map(it => it.id);

                        const pWallets = projectsToFetchAllocationsFor.map(projectOverride =>
                            fetchAll(next => callAPI<PageV2<Accounting.WalletV2>>({
                                ...(Accounting.browseWalletsV2({itemsPerPage: 250, next})),
                                projectOverride,
                            }))
                        );

                        const wallets = (await Promise.all(pWallets)).flatMap(it => it);
                        scrollToTopRef.current = true;
                        dispatch({type: "GrantLoaded", grant: application, wallets});
                    } catch (e) {
                        dispatch({
                            type: "UpdateFullScreenError",
                            error: errorMessageOrDefault(e, "An error occured, try reloading the page.")
                        });
                    }
                } else {
                    dispatch({type: "SetIsCreating"});
                    const affiliations = await pAffiliations;
                    dispatch({type: "AllocatorsLoaded", allocators: affiliations.grantGivers});
                }

                const projectPage = await projectPromise;
                dispatch({
                    type: "ProjectsReloaded",
                    projects: [{id: null as (string | null), title: "My workspace"}].concat(
                        projectPage.items
                            .filter(it => isAdminOrPI(it.status.myRole!))
                            .map(it => ({id: it.id, title: it.specification.title}))
                    )
                });
                break;
            }

            case "InitGrantGiverInitiated": {
                const pProject = callAPI<Project>(Projects.api.retrieve({id: Client.projectId ?? ""}));
                const pWallets = fetchAll(next => callAPI(Accounting.browseWalletsV2({itemsPerPage: 250, next})));
                const project = await pProject;
                const wallets = (await pWallets).filter(it => !it.paysFor.freeToUse);

                try {


                    dispatch({
                        type: "AllocatorsLoaded",
                        allocators: [{
                            id: project.id,
                            title: project.specification.title,
                            description: "Your project",
                            categories: wallets.map(w => w.paysFor),
                            templates: {
                                type: "plain_text",
                                newProject: grantGiverInitiatedTemplate,
                                existingProject: grantGiverInitiatedTemplate,
                                personalProject: grantGiverInitiatedTemplate,
                            }
                        }]
                    });

                    dispatch({
                        type: "GrantGiverInitiatedLoaded",
                        wallets,
                        projectId: event.projectId,
                        title: event.title,
                        start: event.start,
                        end: event.end,
                        piUsernameHint: event.piUsernameHint,
                    });

                    dispatchEvent({type: "UpdateFullScreenLoading", isLoading: false});
                } catch (e) {
                    dispatch({
                        type: "UpdateFullScreenError",
                        error: errorMessageOrDefault(e, "An error occured, try reloading the page.")
                    });
                }
                break;
            }

            case "CommentPosted": {
                dispatch(event);
                await callAPI(Grants.postComment({
                    applicationId: event.grantId,
                    comment: event.comment
                }));
                const app = await callAPI(Grants.retrieve({id: event.grantId}));
                dispatch({type: "CommentsReloaded", comments: app.status.comments});
                break;
            }

            case "CommentDeleted": {
                dispatch(event);
                await callAPI(Grants.deleteComment({
                    applicationId: event.grantId,
                    commentId: event.commentId
                }));
                break;
            }

            case "Withdraw": {
                await callAPIWithErrorHandler(
                    Grants.updateState({
                        applicationId: event.id,
                        newState: Grants.State.CLOSED,
                    }),
                );
                await dispatchEvent({type: "Init", grantId: event.id});
                break;
            }

            case "GrantGiverStateChange": {
                dispatch(event);
                await callAPIWithErrorHandler(
                    {
                        ...Grants.updateState({
                            applicationId: event.grantId,
                            newState: event.approved ? Grants.State.APPROVED : Grants.State.REJECTED,
                        }),
                        projectOverride: event.grantGiver
                    }
                );
                await dispatchEvent({type: "Init", grantId: event.grantId});
                break;
            }

            default: {
                dispatch(event);
                break;
            }
        }
    }, [doDispatch]);
    return {dispatchEvent};
}

// Styling
// =====================================================================================================================
const style = injectStyle("grant-editor", k => `
    ${k} {
        width: 1000px;
    }
    
    ${k} .grow {
        flex-grow: 1;
    }
    
    /* the header element is a sticky box containing the controls for the page */
    /* -------------------------------------------------------------------------------------------------------------- */
    ${k} header {
        position: fixed;
        top: 0;
        left: var(${CSSVarCurrentSidebarStickyWidth});
        
        background: var(--backgroundDefault);
        color: var(--textPrimary);
        
        display: flex;
        flex-direction: row;
        align-items: center;
        gap: 8px;
        
        height: 50px;
        width: calc(100vw - var(${CSSVarCurrentSidebarStickyWidth}));
        
        padding: 0 16px;
        z-index: 9;
        
        box-shadow: var(--defaultShadow);
    }
    
    ${k} header button {
        height: 40px;
        font-size: 14px !important;
    }
    
    ${k} header [data-tag=confirm-button] {
        min-width: unset;
        width: 200px;
    }
    
    ${k} header [data-tag=confirm-button] ul {
        left: 10px;
    }
    
    ${k} header.at-top {
        box-shadow: unset;
    }
    
    ${k} header h3 {
        margin: 0;
    }
    
    ${k} [data-tag=loading-spinner] {
        /* tweaks the spinner displayed in the header buttons such that it is placed correctly */
        margin: 0;
        margin-top: -5px;
    }
    
    ${k} .application-wrapper {
        /* ensures that the sticky header doesn't feel cramped (application is the last section of the page) */
        min-height: calc(100vh - 200px);
    }
    
    /* typography tweaks */
    /* -------------------------------------------------------------------------------------------------------------- */
    ${k} h1, ${k} h2, ${k} h3, ${k} h4 {
        margin: 19px 0;
    }
    
    ${k} h4 {
        display: flex;
        align-items: center;
        gap: 8px;
    }
    
    ${k}.is-editing h4 {
        margin-top: 50px;
        margin-bottom: 0;
    }
    
    ${k}.is-editing h3 + h4 {
        margin-top: 0;
    }
    
    ${k} h3:first-child {
        margin-top: 0;
    }
    
    ${k} h3 {
        margin-top: 50px;
    }
    
    ${k} label code {
        font-weight: normal;
    }
    
    ${k} label {
        font-weight: bold;
        user-select: none;
    }
    
    ${k} label.section {
        font-size: 120%;
    }
    
    /* section and form styling */
    /* -------------------------------------------------------------------------------------------------------------- */
    ${k} .project-info, ${k} .select-resources, ${k} .application {
        display: grid;
        grid-template-columns: 450px 550px;
        row-gap: 30px;
    }
    
    ${k}.is-editing .project-info, ${k} .select-resources, ${k} .application {
        row-gap: 30px;
        margin-bottom: 30px;
    }
    
    ${k}.is-editing .select-resources .section.optional {
        margin-top: 29px;
        display: block;
    }
   
    ${k}.is-editing .description.optional {
        display: none;
    }
    
    ${k} .description {
        color: var(--textSecondary);
        margin-right: 20px;
    }
    
    ${k} .description p:first-child {
        margin-top: 0;
    }
    
    ${k} .form-body {
        display: flex;
        flex-direction: column;
        gap: 15px;
    }
    
    ${k} textarea {
        resize: vertical;
    }
    
    ${k} .application textarea {
        margin-top: 23px;
    }
    
    ${k} .mandatory::after {
        content: '*';
        color: red;
        margin-left: 8px;
    }
    
    /* grant givers */
    ${k} .grant-giver {
        display: flex;
        flex-direction: row;
        align-items: start;
        margin-bottom: 20px;
    }
    
    ${k}.is-editing .grant-giver {
        align-items: center;
    }
    
    ${k} .grant-giver label {
        display: flex;
        align-items: center;
        gap: 8px;
    }
    
    ${k} .grant-giver .checkbox > div {
        margin: 0;
    }
    
    /* requested resources */
    /* -------------------------------------------------------------------------------------------------------------- */
    ${k} .allocation-row {
        display: flex;
        flex-direction: column;
    }
    
    ${k} .allocation-row label {
        display: block;
    }
    
    ${k} .allocation-row td:first-child {
        width: 32px;
    }
    
    ${k} .allocation-row table {
        border-collapse: separate;
        border-spacing: 4px;
    }
    
    ${k} .allocation-row th {
        text-align: left;
    }
    
    /* comments */
    ${k} .comments {
        margin-top: 15px;
    }
    
    ${k} .comment-scrolling {
        height: 300px;
        overflow-y: auto;
        
        box-shadow: inset 0 11px 8px -10px #ccc;
        border: 1px solid #ccc;
        
        display: flex;
        flex-direction: column;
    }
    
    ${k} .comment-scrolling > :first-child {
        margin-top: auto;
    }
    
    ${k} .comment-scrolling.at-top {
        box-shadow: unset;
    }
    
    ${k} .comment {
        display: flex;
        padding: 15px 0;
    }
    
    ${k} .comment .body {
        flex-grow: 1;
        margin: 0 6px;
    }
    
    ${k} .comment time {
        color: var(--textSecondary);
    }
    
    ${k} .comment p {
        margin: 0;
    }
    
    ${k} .create-comment {
        margin-top: 16px;
    }
    
    ${k} .create-comment .wrapper {
        display: flex;
    }
    
    ${k} .create-comment textarea {
        flex-grow: 1;
        margin-left: 6px;
    }
    
    ${k} .create-comment .buttons {
        display: flex;
        margin-top: 6px;
        justify-content: flex-end;
    }
    
    ${k} .create-comment .buttons button {
        position: relative;
        top: -45px;
        left: -10px;
        height: 30px;
        width: 30px;
    }
`);

// Main user-interface
// =====================================================================================================================
export function Editor(): React.JSX.Element {
    const scrollToTopRef = useRef(false);
    const [state, doDispatch] = useReducer(stateReducer, defaultState);
    const {dispatchEvent} = useStateReducerMiddleware(doDispatch, scrollToTopRef);
    usePage("Grant application", SidebarTabId.WORKSPACE);
    const location = useLocation();
    const navigate = useNavigate();
    const isForSubAllocator = getQueryParam(location.search, "subAllocator") == "true";
    useProjectId();

    useEffect(() => {
        (async () => {
            switch (getQueryParam(location.search, "type")) {
                case "grantGiverInitiated": {
                    const startP = getQueryParam(location.search, "start");
                    const endP = getQueryParam(location.search, "end");
                    const title = getQueryParam(location.search, "title");
                    const piUsernameHint = getQueryParam(location.search, "piUsernameHint");
                    const projectId = getQueryParam(location.search, "projectId");

                    if (!startP || !endP || !title || !piUsernameHint || !Client.projectId) {
                        navigate("/");
                        return;
                    }

                    const start = parseInt(startP);
                    const end = parseInt(endP);

                    if (isNaN(start) || isNaN(end)) {
                        navigate("/");
                        return;
                    }

                    dispatchEvent({
                        type: "InitGrantGiverInitiated",
                        title,
                        projectId: projectId != null ? projectId : undefined,
                        start,
                        end,
                        piUsernameHint
                    });
                    break;
                }
                case "applicantInitiated": {
                    const projectId = getQueryParam(location.search, "projectId");

                    await dispatchEvent({type: "Init"});
                    dispatchEvent({type: "RecipientUpdated", isCreatingNewProject: false, reference: projectId ?? undefined})
                    dispatchEvent({type: "UpdateFullScreenLoading", isLoading: false});
                    break;
                }
                default: {
                    const grantId = getQueryParam(location.search, "id") ?? undefined;
                    if (!grantId) {
                        dispatchEvent({type: "UpdateFullScreenLoading", isLoading: false});
                    }

                    try {
                        await dispatchEvent({type: "Init", grantId});
                    } finally {
                        if (grantId) dispatchEvent({type: "UpdateFullScreenLoading", isLoading: false});
                    }
                    break;
                }
            }
        })();
    }, []);

    // Event handlers
    // -----------------------------------------------------------------------------------------------------------------
    // These event handlers translate, primarily DOM, events to higher-level EditorEvents which are sent to
    // dispatchEvent(). There is nothing complicated in these, but they do take up a bit of space. When you are writing
    // these, try to avoid having dependencies on more than just dispatchEvent itself.
    const switchToNewWorkspace = useCallback(
        () => dispatchEvent({type: "RecipientUpdated", isCreatingNewProject: true}),
        [dispatchEvent]
    );

    const switchToExistingWorkspace = useCallback(
        () => dispatchEvent({type: "RecipientUpdated", isCreatingNewProject: false}),
        [dispatchEvent]
    );

    const onAllocatorChecked = useCallback((projectId: string, checked: boolean) => {
        dispatchEvent({type: "AllocatorChecked", allocatorId: projectId, isChecked: checked});
    }, [dispatchEvent]);

    const onResourceInput = useCallback<React.FormEventHandler>(ev => {
        const inputElem = ev.target as HTMLInputElement;
        const [provider, category, allocator] = inputElem.id.split("/");
        let balance: number | null = parseInt(inputElem.value);
        if (isNaN(balance)) balance = null;

        dispatchEvent({
            type: "BalanceUpdated",
            provider, allocator, category, balance,
        });
    }, [dispatchEvent]);

    const onProjectSelected = useCallback<React.FormEventHandler>(ev => {
        const select = ev.target as HTMLSelectElement;
        const selectedItem = select.value === "null" ? undefined : select.value;
        dispatchEvent({type: "RecipientUpdated", isCreatingNewProject: false, reference: selectedItem});
    }, [dispatchEvent]);

    const onNewProjectInput = useCallback<React.FormEventHandler>(ev => {
        const input = ev.target as HTMLInputElement;
        dispatchEvent({type: "RecipientUpdated", isCreatingNewProject: true, reference: input.value});
    }, [dispatchEvent]);

    const onReferenceIdInput = useCallback<React.FormEventHandler>(ev => {
        const input = ev.target as HTMLInputElement;
        const idx = parseInt(input.id.split("-")[1]);
        dispatchEvent({type: "ReferenceIdUpdated", newReferenceId: input.value, idx});
    }, [dispatchEvent]);

    const onReferenceBlur = useCallback(() => {
        dispatchEvent({type: "CleanupReferenceIds"});
    }, [dispatchEvent]);

    const onApplicationChange = useCallback<React.FormEventHandler>(ev => {
        if (!(ev.target instanceof HTMLTextAreaElement)) return;
        const id = ev.target.id;
        const newValue = ev.target.value;

        dispatchEvent({type: "ApplicationUpdated", section: id, contents: newValue});
    }, [dispatchEvent]);

    const onStartUpdated = useCallback<React.FormEventHandler>(ev => {
        const select = ev.target as HTMLSelectElement;
        const valSplit = select.value.split("/");
        const [month, year] = valSplit.map(it => parseInt(it));
        if (isNaN(month) || isNaN(year)) return;
        dispatchEvent({type: "DurationUpdated", year, month});
    }, [dispatchEvent]);

    const onDurationUpdated = useCallback<React.FormEventHandler>(ev => {
        const select = ev.target as HTMLSelectElement;
        const duration = parseInt(select.value);
        if (isNaN(duration)) return;
        dispatchEvent({type: "DurationUpdated", duration});
    }, [dispatchEvent]);

    const onUnlock = useCallback(() => {
        dispatchEvent({type: "Unlock"});
    }, [dispatchEvent]);

    const onWithdraw = useCallback(() => {
        if (!state.stateDuringEdit) return;
        dispatchEvent({type: "Withdraw", id: state.stateDuringEdit.id});
    }, [dispatchEvent, state.stateDuringEdit?.id]);

    const formRef = useRef<HTMLFormElement>(null);
    const triggerFormSubmit = useCallback(() => {
        formRef.current?.requestSubmit();
    }, []);

    const onSubmit = useCallback<React.FormEventHandler>(async ev => {
        ev.preventDefault();
        if (!state.stateDuringCreate) return;
        if (state.loading) return;
        const checked = state.allocators.filter(it => it.checked);
        if (checked.length === 0) return;

        const doc: Grants.Doc = {
            recipient: stateToCreationRecipient(state)!,
            referenceIds: null,
            revisionComment: null,
            form: stateToApplication(state),
            parentProjectId: checked[0].id,
            allocationRequests: stateToRequests(state),
        };

        dispatchEvent({type: "LoadingStateChange", isLoading: true});
        try {
            const response = await callAPIWithErrorHandler(
                Grants.submitRevision({
                    revision: doc,
                    comment: "Submitted the application",
                })
            );

            if (!response) return;

            const grantId = response.id;
            navigate(`${window.location.pathname.replace("/app", "")}?id=${grantId}`);
            dispatchEvent({type: "Init", grantId: grantId.toString(), scrollToTop: true});
        } finally {
            dispatchEvent({type: "LoadingStateChange", isLoading: false});
        }
    }, [state, dispatchEvent]);

    const onDiscard = useCallback(() => {
        const id = state.stateDuringEdit?.id;
        if (!id) return;
        dispatchEvent({type: "Init", grantId: id});
    }, [dispatchEvent, state.stateDuringEdit?.id]);

    const onUpdate = useCallback(async (dry: boolean = false) => {
        if (!state.stateDuringEdit) return;
        if (state.loading) return;

        const editState = state.stateDuringEdit;
        const currentDoc = editState.document;
        const isGrantGiverInitiated = state.stateDuringEdit.id === GRANT_GIVER_INITIATED_ID;

        let revisionCommentResult: string;
        if (isGrantGiverInitiated) {
            revisionCommentResult = "Submitted the application";
        } else {
            if (dry) {
                revisionCommentResult = "Dry run";
            } else {
                revisionCommentResult = (await addStandardInputDialog({
                    type: "textarea",
                    title: "Revision comment",
                    placeholder: "Please provider a comment describing the change and the reason for the change.",
                    width: "100%",
                    rows: 9,
                    validationFailureMessage: "Comment cannot be empty",
                    validator: text => text.length > 0
                })).result
            }
        }

        const doc: Grants.Doc = {
            recipient: currentDoc.recipient,
            referenceIds: editState.referenceIds ? editState.referenceIds : null,
            allocationRequests: stateToRequests(state),
            form: stateToApplication(state),
            parentProjectId: currentDoc.parentProjectId
        };

        if (isGrantGiverInitiated) {
            doc.form.type = "grant_giver_initiated";
            doc.form["subAllocator"] = isForSubAllocator
        }

        if (isGrantGiverInitiated && Object.values(state.applicationDocument).length === 0) {
            snackbarStore.addFailure(
                "Missing description (see application section)",
                false,
            );
            return false;
        }

        if (!dry) {
            dispatchEvent({type: "LoadingStateChange", isLoading: true});
            try {
                const result = await callAPIWithErrorHandler(Grants.submitRevision({
                    applicationId: isGrantGiverInitiated ? undefined : editState.id,
                    revision: doc,
                    comment: revisionCommentResult,
                }));

                if (result) {
                    if (isGrantGiverInitiated) {
                        navigate(AppRoutes.accounting.allocations());
                        return true;
                    }
                    dispatchEvent({type: "Init", grantId: result.id});
                }
            } finally {
                dispatchEvent({type: "LoadingStateChange", isLoading: false});
            }
        }

        return true;
    }, [state, dispatchEvent]);

    const validateThenUpdate = useCallback(async () => {
        if (await onUpdate(true)) {
            await onUpdate(false);
        } else {
            // TODO(Dan) FIXME
            snackbarStore.addFailure("Validation error, see the form for details", false);
        }
    }, [onUpdate]);

    const onTransfer = useCallback(async (source: string, destination: string, comment: string) => {
        if (!state.stateDuringEdit) return;

        await callAPIWithErrorHandler(
            {
                ...Grants.transfer({
                    applicationId: state.stateDuringEdit?.id,
                    target: destination,
                    comment: "Transferring to a different grant giver: " + comment
                }),
                projectOverride: source,
            }
        );

        navigate("/grants/ingoing");
    }, [navigate, state.stateDuringEdit?.id]);

    const onCommentPosted = useCallback((comment: string) => {
        if (!state.stateDuringEdit) return;
        dispatchEvent({type: "CommentPosted", comment, grantId: state.stateDuringEdit.id});
    }, [dispatchEvent, state.stateDuringEdit?.id]);

    const onCommentDeleted = useCallback((commentId: string) => {
        if (!state.stateDuringEdit) return;
        dispatchEvent({type: "CommentDeleted", commentId, grantId: state.stateDuringEdit.id});
    }, [dispatchEvent, state.stateDuringEdit?.id]);

    const onRevisionSelected = useCallback((revision: number) => {
        dispatchEvent({type: "OpenRevision", revision});
    }, [dispatchEvent]);

    const onStateChange = useCallback((grantGiver: string, approved: boolean) => {
        if (!state.stateDuringEdit) return;
        dispatchEvent({type: "GrantGiverStateChange", grantGiver, approved, grantId: state.stateDuringEdit.id});
    }, [dispatchEvent, state.stateDuringEdit?.id]);

    // Top-level event listeners
    // -----------------------------------------------------------------------------------------------------------------

    useEffect(() => {
        // Chrome (and others?) have this annoying feature that if you scroll on an input field you scroll both the page
        // and the value. This has lead to a lot of people accidentally changing the resources requested. We now
        // intercept these events and simply blur the element before the value is changed.
        const stupidChromeListener = () => {
            if (document.activeElement?.["type"] === "number") {
                (document.activeElement as HTMLInputElement).blur();
            }
        };

        document.addEventListener("wheel", stupidChromeListener);
        return () => {
            document.removeEventListener("wheel", stupidChromeListener);
        };
    }, []);

    useEffect(() => {
        // Implements the sticky header and updates the title based on the most recent h3 we have seen on the page.

        const scrollingElement = document.querySelector("[data-component=main]")!.parentElement!; // TODO fragile
        const scrollListener = () => {
            const headings = Array.from(document.querySelectorAll<HTMLHeadingElement>(`.${style} h3`));
            let scrollOffsetY = scrollingElement.scrollTop;
            const header = document.querySelector<HTMLElement>(`.${style} header`);
            if (!header) return;

            if (scrollOffsetY === 0) {
                header.classList.add("at-top");
            } else {
                header.classList.remove("at-top");
            }

            const stickyHeader = document.querySelector<HTMLHeadingElement>(`.${style} header h3`)!;
            scrollOffsetY += 30; // NOTE(Dan): Fragile magic number which makes it feel correct

            let headingToUse = "";
            for (let i = 1; i < headings.length; i++) {
                if (i === 1 || scrollOffsetY >= headings[i].offsetTop) headingToUse = headings[i].innerText;
            }
            stickyHeader.innerText = headingToUse;
        };

        scrollingElement.addEventListener("scroll", scrollListener);
        return () => {
            scrollingElement.removeEventListener("scroll", scrollListener);
        }
    }, []);

    useLayoutEffect(() => {
        // Scrolls to the top of the page if we have been instructed to do so.
        if (scrollToTopRef.current) {
            scrollToTopRef.current = false;
            const scrollingElement = document.querySelector("[data-component=main]")!.parentElement!; // TODO fragile
            if (!scrollingElement) return;
            scrollingElement.scrollTop = 0;
        }
    });

    const recipientDependency = stateToCreationRecipient(state);
    useEffect(() => {
        // Listen to recipient change
        const recipient = stateToCreationRecipient(state);
        if (!recipient || !state.stateDuringCreate) return;

        let req: Grants.RetrieveGrantGiversRequest | undefined = undefined;

        switch (recipient?.type) {
            case "existingProject":
                req = {type: "ExistingProject", id: recipient.id};
                break;
            case "personalWorkspace":
                req = {type: "PersonalWorkspace"}
                break;
            case "newProject":
                req = {type: "NewProject", title: recipient.title}
                break;
        }

        dispatchEvent({type: "Init", affiliationRequest: req});
    }, [recipientDependency?.type, recipientDependency?.["id"]]);

    // Short-hands used in the user-interface
    // -----------------------------------------------------------------------------------------------------------------
    const isGrantGiverInitiated = state.stateDuringEdit?.id === GRANT_GIVER_INITIATED_ID;
    const monthOptions = stateToMonthOptions(state);
    const anyChecked = state.allocators.some(it => it.checked);
    const newestRevision = state.stateDuringEdit?.newestRevision;
    const activeRevision = state.stateDuringEdit?.activeRevision;
    const isViewingHistoricEntry = newestRevision !== activeRevision;
    const historicEntry = activeRevision == null ? null :
        state.stateDuringEdit!.revisions.find(it => it.revisionNumber === activeRevision);
    const isReadyToApprove: boolean = !!state.stateDuringEdit;

    const overallState = state.stateDuringEdit?.overallState;
    const isClosed =
        state.stateDuringEdit &&
        overallState !== Grants.State.IN_PROGRESS;

    const referenceIdsToShow = state.stateDuringEdit?.referenceIds ?? [];
    const isGrantGiverAndNeedsNewIdField = (
        !state.locked && state.stateDuringEdit?.wallets &&
        referenceIdsToShow &&
        referenceIdsToShow[referenceIdsToShow.length - 1] !== ""
    );

    if (isGrantGiverAndNeedsNewIdField || !referenceIdsToShow.length) {
        referenceIdsToShow.push("");
    }

    const classes = [style];
    if (state.stateDuringEdit !== undefined) classes.push("is-editing");
    if (state.stateDuringCreate !== undefined) classes.push("is-creating");

    // The actual user-interface
    // -----------------------------------------------------------------------------------------------------------------
    return <MainContainer
        headerSize={0}
        main={
            state.fullScreenLoading ? <>
                <HexSpin size={64} />
            </> : state.fullScreenError ? <>
                {state.fullScreenError}
            </> :
                <Box mx="auto" className={classes.join(" ")}>
                    <header className={"at-top"}>
                        <h3>Information about your project</h3>

                        <div style={{flexGrow: 1}} />

                        {isViewingHistoricEntry && historicEntry ?
                            <>
                                <b>Viewing old entry from {dateToString(historicEntry.createdAt)}</b>
                            </> :
                            <>
                                {state.stateDuringCreate &&
                                    <Button onClick={triggerFormSubmit} disabled={state.loading}>
                                        {!state.loading && "Submit application"}
                                        {state.loading && <HexSpin size={26} />}
                                    </Button>
                                }

                                {state.stateDuringEdit && (overallState === Grants.State.IN_PROGRESS || isClosed) && <>
                                    {state.locked && <Button onClick={onUnlock}>Edit this request</Button>}
                                    {!state.locked && <>
                                        {!isGrantGiverInitiated &&
                                            <ConfirmationButton actionText={"Discard changes"} icon={"heroTrash"}
                                                color={"errorMain"}
                                                onAction={onDiscard} />
                                        }

                                        <Button onClick={validateThenUpdate} type={"button"} color={"successMain"}>
                                            <Icon name={"heroCheck"} mr={"20px"} />
                                            <Box mr={"20px"}>
                                                {isGrantGiverInitiated ? "Grant resources" : "Save changes"}
                                            </Box>
                                        </Button>
                                    </>}

                                    {!isClosed && state.stateDuringEdit.allowWithdrawal && state.locked && <>
                                        <ConfirmationButton actionText={"Withdraw application"} icon={"heroTrash"}
                                            color={"errorMain"}
                                            onAction={onWithdraw} />
                                    </>}
                                </>}

                                {state.stateDuringEdit && overallState === Grants.State.CLOSED && <>
                                    <b>This application was withdrawn.</b>
                                </>}

                                {state.stateDuringEdit && overallState === Grants.State.APPROVED && <>
                                    <b>This application was approved.</b>
                                </>}

                                {state.stateDuringEdit && overallState === Grants.State.REJECTED && <>
                                    <b>This application was rejected.</b>
                                </>}
                            </>
                        }
                    </header>

                    <form onSubmit={onSubmit} ref={formRef}>
                        <h3>Information about your project</h3>
                        <div className={"project-info"}>
                            <FormField
                                id={FormIds.title}
                                title={"Project information"}
                                showDescriptionInEditMode={true}
                                description={<>
                                    <p>
                                        The principal investigator is the person responsible for the project.
                                        After the approval, the PI may choose to transfer this role.
                                    </p>

                                    {state.stateDuringCreate && <>
                                        <p>
                                            Please keep the project title specified here <i>short</i> and memorable.
                                            You can justify your project in the "Application" section.
                                        </p>
                                    </>}
                                </>}
                            >
                                <label>
                                    Principal investigator (PI)
                                    <Input id={FormIds.pi} height="42px" disabled value={state.principalInvestigator} />
                                </label>

                                {state.stateDuringCreate && <>
                                    <label>
                                        {state.stateDuringCreate.creatingWorkspace && <>
                                            New project (<a className={BaseLinkClass} href="#" onClick={switchToExistingWorkspace}>
                                                select an existing workspace instead
                                            </a>)
                                            <Input id={FormIds.title}
                                                placeholder={"Please enter the title of your project"}
                                                height="42px"
                                                value={state.stateDuringCreate.reference ?? ""}
                                                onInput={onNewProjectInput} required />
                                        </>}
                                        {!state.stateDuringCreate.creatingWorkspace && <>
                                            Existing workspace (<a href="#" className={BaseLinkClass} onClick={switchToNewWorkspace}>
                                                create a new project instead
                                            </a>)
                                            <Select value={state.stateDuringCreate.reference || "null"}
                                                onChange={onProjectSelected}>
                                                {state.loadedProjects.map(workspace =>
                                                    <React.Fragment key={workspace.id ?? "null"}>
                                                        <option value={workspace.id ?? "null"}>
                                                            {workspace.title}
                                                        </option>
                                                        {workspace.id === null &&
                                                            <option
                                                                disabled>---------------------------------------------------------</option>
                                                        }
                                                    </React.Fragment>
                                                )}
                                            </Select>
                                        </>}
                                    </label>
                                </>}

                                {state.stateDuringEdit && <>
                                    <label>
                                        Project title
                                        <Input value={state.stateDuringEdit.title} disabled />
                                    </label>
                                </>}
                            </FormField>

                            <FormField
                                id={FormIds.startDate}
                                title={"Allocation duration"}
                                showDescriptionInEditMode={true}
                                description={<>
                                </>}
                            >
                                <label>
                                    When should the allocation start?
                                    <Select
                                        value={state.allocationPeriod.start.month + "/" + state.allocationPeriod.start.year}
                                        onChange={onStartUpdated}
                                        disabled={state.locked || isClosed}
                                    >
                                        {monthOptions.map(it => <option value={it.key} key={it.key}>{it.text}</option>)}
                                    </Select>
                                </label>

                                <label>
                                    For how many months should the allocation last?
                                    <Select
                                        value={state.allocationPeriod.durationInMonths}
                                        onChange={onDurationUpdated}
                                        disabled={state.locked || isClosed}
                                    >
                                        <option value="1">1 months</option>
                                        <option value="3">3 months</option>
                                        <option value="6">6 months</option>
                                        <option value="12">12 months</option>
                                        <option value="24">24 months</option>
                                    </Select>
                                </label>
                            </FormField>

                            {state.stateDuringEdit && <>
                                <FormField
                                    id={FormIds.deicId + "-0"}
                                    title={"Reference ID"}
                                    description={"The reference ID allows you to attach additional information to this request."}
                                >
                                    {referenceIdsToShow.map((id, idx) => <label key={idx}>
                                        Reference ID #{idx + 1}
                                        <Input id={FormIds.deicId + "-" + idx}
                                            disabled={state.locked || !state?.stateDuringEdit?.wallets?.length}
                                            placeholder={state.locked ? "None specified" : "DeiC-SDU-L1-0000"}
                                            value={id} onInput={onReferenceIdInput} onBlur={onReferenceBlur} />
                                    </label>)}
                                </FormField>
                            </>}
                        </div>

                        {state.stateDuringEdit && state.stateDuringEdit.id === GRANT_GIVER_INITIATED_ID ?
                            null :
                            <>
                                <h3>
                                    {!state.stateDuringEdit && <>Select grant giver(s)</>}
                                    {state.stateDuringEdit && <>Grant givers</>}
                                </h3>
                                <div className={"select-grant-givers"}>
                                    {state.allocators.map(it =>
                                        <GrantGiver
                                            projectId={it.id}
                                            title={it.title}
                                            description={it.description}
                                            key={it.id}
                                            checked={it.checked}
                                            onChange={onAllocatorChecked}
                                            adminOfProjects={state.loadedProjects}
                                            isEditing={state.stateDuringEdit !== undefined}
                                            onStateChange={onStateChange}
                                            replaceApproval={!isReadyToApprove || isViewingHistoricEntry || !state.locked || isClosed ? <>
                                                {!isViewingHistoricEntry && state.locked && !isClosed && <>
                                                    <Button onClick={onUnlock} mr={8}>Set allocations (required to
                                                        approve)</Button>
                                                </>}
                                            </> : undefined}
                                            replaceReject={isViewingHistoricEntry || !state.locked || isClosed ? <>
                                                {isClosed ?
                                                    <>This application has been closed.</> :
                                                    <>
                                                        {isViewingHistoricEntry &&
                                                            <>You cannot approve/reject a request while viewing an old
                                                                version.</>}
                                                        {!state.locked &&
                                                            <>You cannot approve/reject while you are editing an
                                                                application.</>}
                                                    </>
                                                }

                                            </> : undefined
                                            }
                                            state={state.stateDuringEdit?.stateByGrantGiver[it.id]}
                                            allAllocators={state.allocators}
                                            onTransfer={onTransfer}
                                        />
                                    )}
                                </div>

                                {state.stateDuringEdit && <>
                                    <h3>Revisions and comments</h3>
                                    <CommentSection
                                        comments={state.stateDuringEdit.comments}
                                        revisions={state.stateDuringEdit.revisions}
                                        disabled={false}
                                        onCommentPosted={onCommentPosted}
                                        onCommentDeleted={onCommentDeleted}
                                        onRevisionSelected={onRevisionSelected}
                                    />
                                </>}
                            </>
                        }

                        {anyChecked && <>
                            <h3>
                                {!state.stateDuringEdit || isGrantGiverInitiated && <>Select resources</>}
                                {state.stateDuringEdit && !isGrantGiverInitiated && <>Resources requested</>}
                            </h3>
                            {Object.entries(state.resources).map(([providerId, categories]) => {
                                if (categories.length === 0) return null;
                                const relevantCategories = categories.filter(category => {
                                    const checkedAllocators = Array.from(category.allocators).filter(needle =>
                                        state.allocators.find(hay => hay.id === needle)?.checked === true
                                    );

                                    return checkedAllocators.length !== 0;
                                });

                                if (relevantCategories.length === 0) return null;

                                return <React.Fragment key={providerId}>
                                    <h4><ProviderLogo providerId={providerId} size={30} /> <ProviderTitle
                                        providerId={providerId} /></h4>

                                    <div className={"select-resources"}>
                                        {relevantCategories.map(category => {
                                            const checkedAllocators: string[] = Array.from(category.allocators).filter(needle =>
                                                state.allocators.find(hay => hay.id === needle)?.checked === true
                                            );

                                            return <FormField
                                                title={<code>{category.category.name}</code>}
                                                id={`${providerId}/${category.category.name}/${checkedAllocators[0]}`}
                                                key={`${providerId}/${category.category.name}`}
                                                description={Accounting.guestimateProductCategoryDescription(category.category.name, providerId)}
                                                icon={Accounting.productTypeToIcon(category.category.productType)}
                                                showDescriptionInEditMode={false}
                                            >
                                                {checkedAllocators.map(allocatorId => {
                                                    const allocatorName = state.allocators.find(all => all.id === allocatorId)!.title;
                                                    const unit = Accounting.explainUnit(category.category);

                                                    const freq = category.category.accountingFrequency;

                                                    const errorMessage = category.error?.allocator === allocatorId ?
                                                        category.error?.message : undefined;

                                                    return <React.Fragment key={allocatorId}>
                                                        <div className={"allocation-row"}>
                                                            <label>
                                                                {unit.name} requested
                                                                {checkedAllocators.length > 1 && <> from {allocatorName}</>}

                                                                <Input
                                                                    id={`${providerId}/${category.category.name}/${allocatorId}`}
                                                                    type={"number"} placeholder={"0"}
                                                                    onInput={onResourceInput}
                                                                    min={0}
                                                                    value={category.totalBalanceRequested[allocatorId] ?? ""}
                                                                    disabled={state.locked || isClosed} />
                                                            </label>

                                                            {errorMessage && <div style={{color: "var(--errorMain)"}}>{errorMessage}</div>}
                                                        </div>
                                                    </React.Fragment>;
                                                })}
                                            </FormField>;
                                        })}
                                    </div>
                                </React.Fragment>;
                            })}

                            <h3>Application</h3>
                            <div className="application-wrapper">
                                <div className="application">
                                    {state.application.map((val, idx) => {
                                        // NOTE(Dan): Empty placeholder is a quick work-around for fields having error
                                        // immediately on load.
                                        return <FormField title={val.title} key={idx} id={`${val.title}`}
                                            description={val.description} mandatory={val.mandatory}>
                                            <TextArea id={`${val.title}`} rows={val.rows} maxLength={val.limit}
                                                required={val.mandatory} disabled={state.locked || isClosed}
                                                value={state.applicationDocument[val.title] ?? ""}
                                                onChange={onApplicationChange} placeholder={" "} />
                                        </FormField>
                                    })}
                                </div>
                            </div>
                        </>}
                    </form>
                </Box>
        }
    />;
};

// Project transfer
// =====================================================================================================================
const TransferPrompt: React.FunctionComponent<{
    allocators: EditorState["allocators"];
    onTransfer: (targetId: string, comment: string) => void;
}> = ({allocators, onTransfer}) => {
    const selectRef = useRef<HTMLSelectElement>(null);
    const commentRef = useRef<HTMLInputElement>(null);

    const onSubmit = useCallback((ev: React.SyntheticEvent) => {
        ev.preventDefault();
        const select = selectRef.current;
        const comment = commentRef.current;
        if (!select || !comment) return;
        if (!select.value || select.value === "null") return;

        onTransfer(select.value, comment.value);
    }, [onTransfer]);

    return <form onSubmit={onSubmit} style={{display: "flex", flexDirection: "column", gap: "8px"}}>
        <label htmlFor={"project-transfer-target"}>
            Transfer target
        </label>
        <Select selectRef={selectRef} id={"project-transfer-target"}>
            {!allocators.length && <option value={"null"}>No suitable targets found</option>}
            {allocators.map(it => <option key={it.id} value={it.id}>{it.title}</option>)}
        </Select>

        <label htmlFor={"project-transfer-comment"}>Reason for transferring</label>

        <TextArea
            id={"project-transfer-comment"}
            placeholder={"Please describe the reason for transferring this application."}
            rows={9}
            inputRef={commentRef}
        />

        <Button fullWidth type={"submit"}>Transfer application</Button>
    </form>;
}

function transferProject(allocators: EditorState["allocators"]): Promise<{targetId: string, comment: string} | null> {
    return new Promise((resolve) => {
        const callback = (targetId: string, comment: string) => {
            resolve({targetId, comment});
            dialogStore.success();
        };

        const onCancel = () => {
            resolve(null);
        };

        dialogStore.addDialog(
            <TransferPrompt allocators={allocators} onTransfer={callback} />,
            onCancel,
            true
        );
    });
}

// Utility components
// =====================================================================================================================
// Various helper components used by the main user-interface.

// Comments
// ---------------------------------------------------------------------------------------------------------------------
const CommentSection: React.FunctionComponent<{
    comments?: Grants.Comment[];
    revisions: SimpleRevision[];
    disabled: boolean;
    onCommentPosted: (newComment: string) => void;
    onCommentDeleted: (commentId: string) => void;
    onRevisionSelected: (revisionNumber: number) => void;
}> = props => {
    const avatars = useAvatars();
    const textAreaRef = useRef<HTMLInputElement>(null);
    const scrollingRef = useRef<HTMLDivElement>(null);

    const onFormSubmit = useCallback(() => {
        const commentForm = textAreaRef.current!;
        if (commentForm.value) {
            props.onCommentPosted(commentForm.value);
            commentForm.value = "";
        }
    }, [props.onCommentPosted]);

    const onDelete = useCallback<React.ReactEventHandler>(ev => {
        let elem = ev.target as Element;
        while (true) {
            const commentId = elem.getAttribute("data-comment-id");
            if (commentId) {
                props.onCommentDeleted(commentId);
                break;
            } else {
                if (!elem.parentElement) break;
                elem = elem.parentElement;
            }
        }
    }, [props.onCommentDeleted]);

    const entries: (Grants.Comment | SimpleRevision)[] = useMemo(() => {
        const result: (Grants.Comment | SimpleRevision)[] = [...(props.comments ?? []), ...props.revisions];
        result.sort((a, b) => {
            if (a.createdAt < b.createdAt) return -1;
            if (a.createdAt > b.createdAt) return 1;
            return 0;
        });
        return result;
    }, [props.comments, props.revisions]);

    const onScroll = useCallback(() => {
        const current = scrollingRef.current;
        if (!current) return;

        if (current.scrollTop === 0) {
            current.classList.add("at-top");
        } else {
            current.classList.remove("at-top");
        }
    }, []);

    useEffect(() => {
        avatars.updateCache([...entries.map(it => it["username"] ?? it["updatedBy"]), Client.username!]);
    }, [entries]);

    useLayoutEffect(() => {
        const scrollingBox = scrollingRef.current;
        if (!scrollingBox) return;
        scrollingBox.scrollTop = Number.MAX_SAFE_INTEGER;
    }, [entries]);

    const onKeyDown = useCallback<React.KeyboardEventHandler>(ev => {
        const isCtrlOrMeta = ev.ctrlKey || ev.metaKey;
        if (isCtrlOrMeta && ev.code === "Enter") {
            onFormSubmit();
        }
    }, [onFormSubmit]);

    const onRevSelected = useCallback((ev: React.SyntheticEvent) => {
        ev.preventDefault();
        const revision = (ev.target as HTMLElement).getAttribute("data-rev");
        if (revision) props.onRevisionSelected(parseInt(revision));
    }, [props.onRevisionSelected]);

    return <div className={"comments"}>
        <div className={"comment-scrolling at-top"} ref={scrollingRef} onScroll={onScroll}>
            {!entries.length && <p className={"no-comments"}>No comments have been posted yet.</p>}

            {entries.map(entry => {
                let username: string;
                let comment: React.ReactNode;
                let commentId: string | null = null;
                let action: React.ReactNode;

                if ("comment" in entry) {
                    username = entry.username;
                    comment = entry.comment;
                    commentId = entry.id;
                    action = "says";
                } else {
                    username = entry.updatedBy;
                    action = <>submitted a new version
                        (<a href={"#"} onClick={onRevSelected} data-rev={entry.revisionNumber}>view</a>)</>
                    comment = <>
                        <p><i>{entry.changeLog ?? "Submitted the application"}</i></p>
                    </>;
                }

                return <div className={"comment"} key={commentId || entry.createdAt}>
                    <div className="avatar">
                        <UserAvatar avatar={avatars.avatar(username)} width={"48px"} />
                    </div>

                    <div className="body">
                        <div style={{display: "flex", gap: "8px"}}>
                            <p style={{flexGrow: "1"}}><strong>{username}</strong> {action}:</p>
                            {commentId && username === Client.username ? (
                                <div style={{cursor: "pointer"}} onClick={onDelete} data-comment-id={commentId}>
                                    <Icon name={"trash"} color={"errorMain"} />
                                </div>
                            ) : null}
                            <TooltipV2 tooltip={dateToString(entry.createdAt)}>
                                <time>{formatDistance(entry.createdAt, Date.now(), {addSuffix: true})}</time>
                            </TooltipV2>
                        </div>
                        <div>{comment}</div>
                    </div>
                </div>;
            })}
        </div>

        <div className={"create-comment"}>
            <div className="wrapper">
                <UserAvatar avatar={avatars.avatar(Client.username!)} width={"48px"} />
                <TextArea inputRef={textAreaRef} rows={3} disabled={props.disabled}
                    placeholder={"Your comment"} onKeyDown={onKeyDown} />
            </div>

            <div className="buttons">
                <Button type={"submit"} onClick={onFormSubmit} disabled={props.disabled}>
                    <Icon name={"heroPaperAirplane"} size={20} />
                </Button>
            </div>
        </div>
    </div>;
};

// Form fields
// ---------------------------------------------------------------------------------------------------------------------
const FormField: React.FunctionComponent<{
    icon?: IconName;
    title: React.ReactNode;
    id: string;
    showDescriptionInEditMode?: boolean;
    mandatory?: boolean;
    description?: React.ReactNode;
    children?: React.ReactNode;
}> = props => {
    return <>
        <div>
            <label htmlFor={props.id}
                className={`section ${props.showDescriptionInEditMode === false ? "optional" : ""}`}>
                {props.icon && <Icon name={props.icon} mr={"8px"} size={30} />}
                {props.title}
                {props.mandatory && <span className={"mandatory"} />}
            </label>

            {props.description &&
                <div
                    className={`description ${props.showDescriptionInEditMode === false ? "optional" : ""}`}
                    style={props.icon && {marginLeft: "38px"}}
                >
                    {props.description}
                </div>
            }
        </div>

        <div className="form-body">{props.children}</div>
    </>;
};

// Grant givers
// ---------------------------------------------------------------------------------------------------------------------
const GrantGiver: React.FunctionComponent<{
    projectId: string;
    title: string;
    description: string;
    checked: boolean;
    onChange: (projectId: string, checked: boolean) => void;
    isEditing: boolean;
    adminOfProjects: {id: string | null, title: string}[];
    state?: Grants.State;
    onStateChange: (projectId: string, approved: boolean) => void;
    replaceApproval?: React.ReactNode;
    replaceReject?: React.ReactNode;
    allAllocators: EditorState["allocators"];
    onTransfer?: (source: string, jdestination: string, comment: string) => void;
}> = props => {
    const checkboxId = `check-${props.projectId}`;
    const size = 30;
    const invisiblePaddingFromCheckbox = 5;
    const onChange = useCallback(() => {
        props.onChange(props.projectId, !props.checked);
    }, [props.projectId, props.onChange, props.checked]);

    const isAdmin = props.adminOfProjects.some(it => it.id === props.projectId)

    const onApprove = useCallback(() => {
        props.onStateChange(props.projectId, true);
    }, [props.onStateChange, props.projectId]);

    const onReject = useCallback(() => {
        props.onStateChange(props.projectId, false);
    }, [props.onStateChange, props.projectId]);

    const startTransfer = useCallback(async () => {
        if (!props.allAllocators) return;
        if (!props.onTransfer) return;

        const result = await transferProject(
            props.allAllocators.filter(it => !it.checked && it.id !== props.projectId)
        );
        if (result) {
            props.onTransfer(props.projectId, result.targetId, result.comment);
        }
    }, [props.projectId, props.allAllocators, props.onTransfer]);

    const stateIconAndColor = Grants.stateToIconAndColor(props.state ?? Grants.State.IN_PROGRESS);

    return <div className={"grant-giver"}>
        <div className={"grow"}>
            <label htmlFor={checkboxId}>
                <ProjectLogo projectId={props.projectId} size={`${size}px`} />
                {props.title}
            </label>
            {!props.isEditing &&
                <div className={"description"} style={{marginLeft: (size + 8) + "px"}}>{props.description}</div>
            }
        </div>
        {!props.isEditing &&
            <div
                className={"checkbox"}
                style={{
                    marginTop: (-invisiblePaddingFromCheckbox) + "px",
                    position: "relative",
                    left: invisiblePaddingFromCheckbox + "px",
                }}
            >
                <Checkbox
                    id={checkboxId}
                    size={size + invisiblePaddingFromCheckbox * 2}
                    checked={props.checked}
                    onChange={onChange}
                    handleWrapperClick={onChange}
                />
            </div>
        }
        {props.isEditing && isAdmin && props.state === Grants.State.IN_PROGRESS && <>
            {!props.replaceApproval && !props.replaceReject && props.onTransfer && <>
                <TooltipV2 tooltip={"Transfer to new grant giver"}>
                    <Button onClick={startTransfer} mr={8} width={"50px"} height={"40px"}>
                        <Icon name={"heroArrowUpTray"} />
                    </Button>
                </TooltipV2>
            </>}
            {props.replaceApproval}
            {!props.replaceApproval && <>
                <TooltipV2 tooltip={"Approve (hold to confirm)"}>
                    <ConfirmationButton color={"successMain"} icon={"heroCheck"} onAction={onApprove} height={40} mr={8} />
                </TooltipV2>
            </>}

            {props.replaceReject}
            {!props.replaceReject && <>
                <TooltipV2 tooltip={"Reject (hold to confirm)"}>
                    <ConfirmationButton color={"errorMain"} icon={"heroXMark"} onAction={onReject} height={40} />
                </TooltipV2>
            </>}
        </>}
        {props.isEditing && (!isAdmin || props.state !== Grants.State.IN_PROGRESS) && props.state && <>
            <Icon name={stateIconAndColor.icon} color={stateIconAndColor.color} />
        </>}
    </div>
}

const FormIds = {
    pi: "pi",
    title: "title",
    startDate: "start-date",
    endDate: "end-date",
    deicId: "deicref",
    revisions: "revisions",
};

// Application template parsing
// =====================================================================================================================
interface ApplicationSection {
    title: string;
    description: string;
    rows: number;
    mandatory: boolean;
    limit?: number;
}

function parseIntoSections(text: string): ApplicationSection[] {
    function normalizeTitle(title: string): string {
        const words = title.split(" ");
        let builder = "";
        if (words.length > 0) builder = words[0];
        for (let i = 1; i < words.length; i++) {
            builder += " ";
            const word = words[i];
            if (word.toUpperCase() === word || word.toLowerCase() === word) {
                builder += word;
            } else {
                builder += word.toLowerCase();
            }
        }
        return builder;
    }

    const result: ApplicationSection[] = [];
    const lines = text.split("\n");
    const sectionSeparators: number[] = [];
    for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        if (line.startsWith("---") && /-+$/.test(line)) sectionSeparators.push(i);
    }

    let titles: string[] = [];
    for (const lineIdx of sectionSeparators) {
        if (lineIdx > 0) titles.push(lines[lineIdx - 1]);
    }

    let foundDescriptionBeforeFirstTitle = false;
    const descriptions: string[] = [];
    let currentStartLine = 0;
    for (let i = 0; i <= sectionSeparators.length; i++) {
        const end = i < sectionSeparators.length ? sectionSeparators[i] - 1 : lines.length;
        let builder = "";
        for (let row = currentStartLine; row < end; row++) {
            builder += lines[row];
            builder += "\n";

        }
        builder = builder.trim();
        if (builder.length > 0) {
            if (i === 0) foundDescriptionBeforeFirstTitle = true;
            descriptions.push(builder);
        } else {
            if (i !== 0) descriptions.push("");
        }
        currentStartLine = end + 2;
    }

    if (foundDescriptionBeforeFirstTitle) {
        if (titles.length > 0) titles = ["Introduction", ...titles];
        else titles = ["Application"];
    }

    const prefixesWhichSoundMandatory = [
        "Add a ",
        "Describe the ",
        "Provide a ",
        "Please describe the reason for applying"
    ];

    for (let i = 0; i < titles.length; i++) {
        const description = descriptions[i] ?? "";
        const section: ApplicationSection = {
            title: normalizeTitle(titles[i]),
            description: description,
            rows: 3,
            mandatory: prefixesWhichSoundMandatory.some(it => description.startsWith(it))
        };

        const limitMatches = section.description.matchAll(/max (\d+) ch/g);
        while (true) {
            const match = limitMatches.next();
            if (match.done) break;
            section.limit = parseInt(match.value[1]);
        }

        if (section.title.toLowerCase() === "application") {
            section.limit = 4000;
        }

        section.rows = Math.min(15, Math.max(2, Math.floor((section.limit ?? 250) / 50)));

        if (section.title.toLowerCase().indexOf("project title") !== -1) {
            section.rows = 2;
        }

        result.push(section);
    }

    // Move large sections to the end
    for (let i = 0; i < result.length; i++) {
        const section = result[i];
        if ((section.limit ?? 0) > 1000) {
            result.splice(i, 1);
            result.push(section);
        }
    }

    return result;
}

// Utility functions
// =====================================================================================================================
function stateToApplication(state: EditorState): Grants.Doc["form"] {
    let builder = "";
    for (const section of state.application) {
        builder += section.title;
        builder += "\n-----------------------------------------\n";

        const contents = state.applicationDocument[section.title] ?? "";
        const contentLines = contents.split("\n");
        builder += contentLines.map(line => {
            if (line.startsWith("---") && /-+$/.test(line)) return `!${line}!`;
            return line;
        }).join("\n");
        builder += "\n\n";
    }
    return {type: "plain_text", text: builder};
}

function stateToRequests(state: EditorState): Grants.Doc["allocationRequests"] {
    const result: Grants.Doc["allocationRequests"] = [];
    const [start, end] = stateToAllocationPeriod(state);

    const period: Grants.Period = {start, end};

    const checkedAllocators = new Set(state.allocators.filter(it => it.checked).map(it => it.id));

    for (const providerSection of Object.values(state.resources)) {
        for (const category of providerSection) {
            const pc = category.category;
            const explanation = Accounting.explainUnit(pc);

            for (const [allocator, amount] of Object.entries(category.totalBalanceRequested)) {
                if (!checkedAllocators.has(allocator)) continue;

                result.push({
                    category: pc.name,
                    provider: pc.provider,
                    balanceRequested: Math.ceil(amount * explanation.invPriceFactor),
                    grantGiver: allocator,
                    period,
                });
            }
        }
    }

    return result;
}

function stateToCreationRecipient(state: EditorState): Grants.Doc["recipient"] | null {
    const creationState = state.stateDuringCreate;
    if (!creationState) return null;

    let recipient: Grants.Doc["recipient"];
    if (creationState.creatingWorkspace) {
        recipient = {
            type: "newProject",
            title: creationState.reference ?? "",
        };
    } else {
        if (!creationState.reference) {
            recipient = {
                type: "personalWorkspace",
                username: Client.username!,
            };
        } else {
            recipient = {
                type: "existingProject",
                id: creationState.reference,
            };
        }
    }

    return recipient;
}

function stateToAllocationPeriod(state: EditorState): [number, number] {
    const start = new Date();
    start.setUTCFullYear(state.allocationPeriod.start.year, state.allocationPeriod.start.month, 1);
    start.setUTCHours(0, 0, 0, 0);

    const end = new Date(start.getTime());
    end.setUTCMonth(end.getUTCMonth() + state.allocationPeriod.durationInMonths);
    end.setUTCHours(0, 0, 0, 0);
    end.setUTCSeconds(0, -1);

    return [start.getTime(), end.getTime()];
}

const monthNames = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October",
    "November", "December"];

function stateToMonthOptions(state: EditorState): {key: string, text: string}[] {
    const result: {key: string, text: string, time: number}[] = [];
    function insertIfUnique(date: Date) {
        const today = new Date();
        const month = monthNames[date.getMonth()];
        const key = `${date.getMonth()}/${date.getFullYear()}`;
        if (result.some(it => it.key === key)) return;

        if (today.getUTCFullYear() === date.getUTCFullYear() && today.getUTCMonth() === date.getUTCMonth()) {
            result.push({key, text: `Immediately`, time: date.getTime()});
        } else {
            result.push({key, text: `${month} ${date.getFullYear()}`, time: date.getTime()});
        }
    }

    const date = new Date();
    date.setDate(1);

    for (let i = 0; i < 12; i++) {
        insertIfUnique(date);
        let currentMonth = date.getMonth();
        date.setMonth((currentMonth + 1) % 12);
        if (currentMonth === 11) date.setFullYear(date.getFullYear() + 1);
    }

    date.setUTCFullYear(state.allocationPeriod.start.year, state.allocationPeriod.start.month);
    date.setUTCMonth(date.getUTCMonth() - 6);
    for (let i = 0; i < 12; i++) {
        insertIfUnique(date);
        let currentMonth = date.getMonth();
        date.setMonth((currentMonth + 1) % 12);
        if (currentMonth === 11) date.setFullYear(date.getFullYear() + 1);
    }

    result.sort((a, b) => {
        if (a.time < b.time) return -1;
        if (a.time > b.time) return 1;
        return 0;
    });

    return result;
}

const GRANT_GIVER_INITIATED_ID = "_GRANT_GIVER_INITIATED_FAKE_ID_";

const grantGiverInitiatedPrefix = "Sub-allocation description";
const grantGiverInitiatedTemplate = `${grantGiverInitiatedPrefix}
--------------------------------------------------
                    
Describe the reason for creating this sub-allocation (max 4000 ch).`;

export default Editor;
