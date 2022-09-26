import * as React from "react";
import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {MainContainer} from "@/MainContainer/MainContainer";
import {ProjectBreadcrumbs} from "@/Project/Breadcrumbs";
import * as Heading from "@/ui-components/Heading";
import Box from "@/ui-components/Box";
import Button from "@/ui-components/Button";
import Card from "@/ui-components/Card";
import ExternalLink from "@/ui-components/ExternalLink";
import Flex from "@/ui-components/Flex";
import Icon, {IconName} from "@/ui-components/Icon";
import Input, {HiddenInputField} from "@/ui-components/Input";
import Label from "@/ui-components/Label";
import List from "@/ui-components/List";
import Checkbox from "@/ui-components/Checkbox";
import Text from "@/ui-components/Text";
import TextArea from "@/ui-components/TextArea";
import {ThemeColor} from "@/ui-components/theme";
import Tooltip from "@/ui-components/Tooltip";
import {apiBrowse, apiCreate, apiRetrieve, apiUpdate, callAPI, useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {
    browseWallets,
    explainAllocation,
    normalizeBalanceForBackend,
    normalizeBalanceForFrontend,
    Product,
    productCategoryEquals,
    ProductCategoryId,
    ProductMetadata,
    ProductType,
    productTypes,
    productTypeToIcon,
    productTypeToTitle,
    usageExplainer,
    Wallet,
    WalletAllocation
} from "@/Accounting";
import styled from "styled-components";
import HighlightedCard from "@/ui-components/HighlightedCard";
import {
    GrantsRetrieveAffiliationsResponse,
    retrieveDescription,
    RetrieveDescriptionResponse,
} from "@/Project/Grant/index";
import {useHistory, useParams} from "react-router";
import {dateToString, getStartOfDay} from "@/Utilities/DateUtilities";
import {UserAvatar} from "@/AvataaarLib/UserAvatar";
import {AvatarType, defaultAvatar} from "@/UserSettings/Avataaar";
import Table, {TableCell, TableHeader, TableHeaderCell, TableRow} from "@/ui-components/Table";
import {addStandardDialog, addStandardInputDialog} from "@/UtilityComponents";
import {setLoading, useTitle} from "@/Navigation/Redux/StatusActions";
import {useDispatch} from "react-redux";
import * as UCloud from "@/UCloud";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {default as ReactModal} from "react-modal";
import {defaultModalStyle} from "@/Utilities/ModalUtilities";
import {bulkRequestOf, emptyPage, emptyPageV2} from "@/DefaultObjects";
import {Spacer} from "@/ui-components/Spacer";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";
import {Truncate} from "@/ui-components";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {Logo} from "./ProjectBrowser";
import {format} from "date-fns";
import {DatePicker} from "@/ui-components/DatePicker";
import {
    AllocationRequest,
    browseAffiliations,
    browseAffiliationsByResource,
    closeApplication,
    Comment,
    commentOnGrantApplication,
    deleteGrantApplicationComment,
    Document,
    editReferenceId,
    FetchGrantApplicationRequest,
    FetchGrantApplicationResponse,
    GrantApplication,
    GrantGiverApprovalState,
    GrantProductCategory,
    Recipient,
    State,
    Templates,
    transferApplication,
} from "./GrantApplicationTypes";
import {useAvatars} from "@/AvataaarLib/hook";
import {useProjectId, useProjectManagementStatus} from "..";
import {displayErrorMessageOrDefault, errorMessageOrDefault, stopPropagationAndPreventDefault} from "@/UtilityFunctions";
import {Client} from "@/Authentication/HttpClientInstance";
import ProjectWithTitle = UCloud.grant.ProjectWithTitle;
import {Accordion} from "@/ui-components/Accordion";
import {isAdminOrPI} from "@/Utilities/ProjectUtilities";

export enum RequestTarget {
    EXISTING_PROJECT = "existing_project",
    NEW_PROJECT = "new_project",
    PERSONAL_PROJECT = "personal_workspace",
    VIEW_APPLICATION = "view"
}

/* 
    TODO List:
        - Find new In Progress Icon (General)
        - Remember to update documentation
        - Ensure Grant Giver description works.
        - Hold to confirm when commenting or skipping comments on transferring.
        - Find a way to show revision comments.
        - 'Enter' doesn't work when updating referenceID

        Backend:
            - Fix Transfer Application
                - Only allow transferring to another grant giver provided they have all the same requested products.
                - Confirm transfer with comment
*/

const THIRTY_DAYS_AGO = new Date().getTime() - 30 * 24 * 60 * 60 * 1000;

export const RequestForSingleResourceWrapper = styled.div`
    ${Icon} {
        float: right;
        margin-left: 10px;
    }

    ${Card} {
        height: 100%;

        .dashboard-card-inner {
            padding: 16px;
        }
    }

    table {
        margin: 16px;
    }

    th {
        width: 100%;
        text-align: left;
        padding-right: 30px
    }

    td {
        margin-left: 10px;
        padding-bottom: 16px;
        min-width: 350px;
    }

    tr {
        vertical-align: top;
        height: 40px;
    }

    .unit {
        flex-shrink: 0;
        margin-left: 10px;
        width: 55px;
    }
`;

const ResourceContainer = styled.div`
    display: grid;
    grid-gap: 32px;
    grid-template-columns: repeat(auto-fit, minmax(500px, auto));
    margin: 32px 0;
`;

const RequestFormContainer = styled.div`
    width: 100%;

    ${TextArea} {
        width: 100%;
        height: calc(100% - 40px);
        margin: 10px 0;
    }
`;

function productCategoryId(pid: ProductCategoryId, projectId: string): string {
    return `${pid.name}/${pid.provider}/${projectId}`;
}

function productCategoryStartDate(pid: ProductCategoryId, projectId: string): string {
    return `${productCategoryId(pid, projectId)}/start_date`;
}

function productCategoryEndDate(pid: ProductCategoryId, projectId: string): string {
    return `${productCategoryId(pid, projectId)}/end_date`;
}

function productCategoryAllocation(pid: ProductCategoryId, projectId: string): string {
    return `${productCategoryId(pid, projectId)}/allocation_id`;
}

function anyAutoAssigned(grantProductCategories: Record<string, GrantProductCategory[]>): boolean {
    var anyAssigned = false;
    Object.keys(grantProductCategories).forEach(grantGiver => {
        grantProductCategories[grantGiver].forEach(wb => {
            const element = document.querySelector<HTMLInputElement>(
                `input[data-target="${productCategoryAllocation(wb.metadata.category, grantGiver)}"]`
            );
            if (element) {
                if (element.getAttribute("data-auto-assigned") === "true") {
                    anyAssigned = true;
                    return;
                }
            }
        });
    });
    return anyAssigned;
}

function parseIntegerFromInput(input?: HTMLInputElement | null): number | undefined {
    if (!input) return undefined;
    const rawValue = input.value;
    const parsed = parseInt(rawValue, 10);
    if (isNaN(parsed)) return undefined;
    return parsed;
}

const milleniumAndCenturyPrefix = "20";

function parseDateFromInput(input?: HTMLInputElement | null): number | undefined {
    if (!input) return undefined;
    const rawValue = input.value;
    if (!rawValue) return undefined;
    const [day, month, year] = rawValue.split("/");
    const d = getStartOfDay(new Date());
    d.setDate(parseInt(day, 10));
    d.setMonth(parseInt(month, 10) - 1);
    d.setFullYear(parseInt(`${milleniumAndCenturyPrefix}${year}`, 10));
    const time = d.getTime();
    if (isNaN(time)) return undefined;
    return time;
}

const Wrapper = styled.div`
    & > table {
        margin-left: -9px;
    }

    & > table > tbody > ${TableRow}:hover {
        cursor: pointer;
        background-color: var(--lightGray, #f00);
        color: var(--black, #f00);
    }
`;

function checkIsGrantRecipient(target: RequestTarget, grantApplication: GrantApplication, adminOrPi: boolean): boolean {
    if (target !== RequestTarget.VIEW_APPLICATION) return true;
    const {recipient} = getDocument(grantApplication);
    switch (recipient.type) {
        case "existingProject":
            return recipient.id === Client.projectId && adminOrPi;
        case "personalWorkspace":
            return !Client.hasActiveProject && grantApplication.createdBy === Client.username;
        case "newProject":
            return grantApplication.createdBy === Client.username;
    }
}

const GenericRequestCard: React.FunctionComponent<{
    wb: GrantProductCategory;
    isApprover: boolean;
    projectId: string;
    grantFinalized: boolean;
    isLocked: boolean;
    isRecipient: boolean;
    wallets: Wallet[];
    allocationRequest?: AllocationRequest;
}> = ({wb, grantFinalized, isLocked, wallets, isApprover, allocationRequest, projectId, ...props}) => {
    const [startDate, setStartDate] = useState<Date>(getStartOfDay(new Date()));
    const [endDate, setEndDate] = useState<Date | null>(null);

    const canEdit = isApprover || props.isRecipient;

    React.useEffect(() => {
        if (allocationRequest && allocationRequest.period.start) {
            setStartDate(getStartOfDay(new Date(allocationRequest.period.start)));
        }
    }, []);

    React.useEffect(() => {
        if (endDate == null) return;
        if (endDate < startDate) {
            setEndDate(null);
        }
    }, [startDate, endDate]);

    if (wb.metadata.freeToUse) {
        return <RequestForSingleResourceWrapper>
            <HighlightedCard color="blue" isLoading={false}>
                <Flex flexDirection="row" alignItems="center">
                    <Box flexGrow={1}>
                        <Label>
                            <Checkbox
                                size={32}
                                defaultChecked={wb.requestedBalance !== undefined && wb.requestedBalance > 0}
                                disabled={grantFinalized || isLocked || !canEdit}
                                data-target={"checkbox-" + productCategoryId(wb.metadata.category, projectId)}
                                onChange={e => {
                                    const checkbox = e.target as HTMLInputElement;
                                    const input = document.querySelector(
                                        `input[data-target="${productCategoryId(wb.metadata.category, projectId)}"]`
                                    ) as HTMLInputElement;

                                    if (input) {
                                        const wasChecked = input.value === "1";
                                        input.value = wasChecked ? "0" : "1";
                                        checkbox.checked = !wasChecked;
                                    }
                                }}
                            />
                            {wb.metadata.category.provider} / {wb.metadata.category.name}
                        </Label>
                    </Box>
                    <Icon name={productTypeToIcon(wb.metadata.productType)} size={40} />
                </Flex>

                <Input
                    disabled={grantFinalized || isLocked || !isApprover}
                    data-target={productCategoryId(wb.metadata.category, projectId)}
                    autoComplete="off"
                    type="hidden"
                    min={0}
                />
            </HighlightedCard>
        </RequestForSingleResourceWrapper>;
    } else {
        const defaultValue = allocationRequest?.balanceRequested;
        var normalizedValue = defaultValue != null ?
            normalizeBalanceForFrontend(defaultValue, wb.metadata.productType, wb.metadata.chargeType, wb.metadata.unitOfPrice, 2, true) :
            undefined;
        return <RequestForSingleResourceWrapper>
            <HighlightedCard color="blue" isLoading={false}>
                <table>
                    <tbody>
                        <tr>
                            <th>Product</th>
                            <td>
                                {wb.metadata.category.provider} / {wb.metadata.category.name}
                                <Icon name={productTypeToIcon(wb.metadata.productType)} size={40} />
                            </td>
                        </tr>
                        {wb.currentBalance === undefined ? null : (
                            <tr>
                                <th>Current balance</th>
                                <td>
                                    {usageExplainer(wb.currentBalance, wb.metadata.productType, wb.metadata.chargeType,
                                        wb.metadata.unitOfPrice)}
                                </td>
                            </tr>
                        )}
                        <tr>
                            <th>
                                {wb.metadata.chargeType === "ABSOLUTE" ?
                                    <>Balance requested</> :
                                    <>Quota requested</>
                                }
                            </th>
                            <td>
                                <Flex alignItems={"center"}>
                                    <Input
                                        placeholder={"0"}
                                        disabled={grantFinalized || isLocked || !canEdit}
                                        data-target={productCategoryId(wb.metadata.category, projectId)}
                                        autoComplete="off"
                                        defaultValue={normalizedValue}
                                        type="number"
                                        min={0}
                                    />
                                    <div className={"unit"}>
                                        {explainAllocation(wb.metadata.productType, wb.metadata.chargeType,
                                            wb.metadata.unitOfPrice)}
                                    </div>
                                </Flex>
                                <Flex mt="6px" width="280px">
                                    <DatePicker
                                        selectsStart
                                        startDate={startDate}
                                        endDate={endDate}
                                        borderWidth="2px"
                                        py="8px"
                                        mr="3px"
                                        backgroundColor={isLocked ? "var(--lightGray)" : undefined}
                                        placeholderText="Start date..."
                                        required={isApprover}
                                        value={format(startDate, "dd/MM/yy")}
                                        disabled={grantFinalized || isLocked || !canEdit}
                                        onChange={(date: Date) => setStartDate(date)}
                                        className={productCategoryStartDate(wb.metadata.category, projectId)}
                                    />
                                    <DatePicker
                                        selectsEnd
                                        startDate={startDate}
                                        endDate={endDate}
                                        isClearable={endDate != null}
                                        borderWidth="2px"
                                        py="8px"
                                        ml="3px"
                                        backgroundColor={isLocked ? "var(--lightGray)" : undefined}
                                        placeholderText={isLocked ? undefined : "End date..."}
                                        value={endDate ? format(endDate, "dd/MM/yy") : undefined}
                                        disabled={grantFinalized || isLocked || !canEdit}
                                        onChange={(date: Date | null) => setEndDate(date)}
                                        className={productCategoryEndDate(wb.metadata.category, projectId)}
                                    />
                                </Flex>
                            </td>
                        </tr>
                    </tbody>
                </table>
                <AllocationSelection
                    projectId={projectId}
                    showAllocationSelection={isApprover}
                    allocationRequest={allocationRequest}
                    wallets={wallets}
                    wb={wb}
                    isLocked={isLocked}
                />
            </HighlightedCard>
        </RequestForSingleResourceWrapper>;
    }
};

export async function fetchGrantApplication(request: FetchGrantApplicationRequest): Promise<FetchGrantApplicationResponse> {
    return callAPI<FetchGrantApplicationResponse>(apiRetrieve(request, "api/grant"));
}

const AllocationBox = styled.div`
    padding: 8px 8px 8px 8px;
    border-radius: 6px;
    border: 2px solid var(--borderGray); 
`;

function AllocationSelection({wallets, wb, isLocked, allocationRequest, showAllocationSelection, projectId}: {
    wallets: Wallet[];
    showAllocationSelection: boolean;
    wb: GrantProductCategory;
    isLocked: boolean;
    projectId: string;
    allocationRequest?: AllocationRequest;
}): JSX.Element {
    const [allocation, setAllocation] = useState<{wallet: Wallet; allocation: WalletAllocation; autoAssigned: boolean} | undefined>(undefined);
    // Note(Jonas): Allocation ID can be provided by an allocation request, where wallets can be empty,
    // so we can always look it up. That's why it's divided into two 'useState's, but any friction-less solution
    // that removes one is very welcome.
    const [allocationId, setAllocationId] = useState<string | undefined>(allocationRequest?.sourceAllocation ?? "");

    const allocationCount = React.useMemo(() => {
        return wallets.reduce((acc, w) => w.allocations.length + acc, 0);
    }, [wallets]);

    React.useEffect(() => {
        if (allocationRequest != null && allocationRequest.sourceAllocation != null) {
            if (allocation?.autoAssigned) {
                setAllocation({...allocation, autoAssigned: false});
            }
        }
    }, [allocation, allocationRequest])

    const allocationText = allocation ?
        `${allocation.wallet.paysFor.provider} @ ${allocation.wallet.paysFor.name} [${allocation.allocation.id}]` : "";

    React.useEffect(() => {
        if (!allocationRequest || allocation != null || !showAllocationSelection) return;
        if (
            allocationRequest.sourceAllocation == null &&
            allocationCount === 1
        ) {
            const [wallet] = wallets;
            setAllocation({wallet: wallet, allocation: wallet.allocations[0], autoAssigned: true});
            setAllocationId(wallet.allocations[0].id);
        } else for (const w of wallets) {
            for (const a of w.allocations) {
                if (allocationRequest.sourceAllocation == a.id) {
                    setAllocation({wallet: w, allocation: a, autoAssigned: false});
                    return;
                }
            }
        }
    }, [wallets, allocationRequest, allocation]);

    return <Box mb="8px" ml="6px">
        <HiddenInputField value={allocationId} data-auto-assigned={allocation?.autoAssigned ?? "false"} onChange={() => undefined} data-target={productCategoryAllocation(wb.metadata.category, projectId)} />
        {!showAllocationSelection ? null : !isLocked ?
            <ClickableDropdown colorOnHover={false} width="677px" useMousePositioning trigger={
                <AllocationBox>
                    {!allocation ?
                        <>No allocation selected <Icon name="chevronDownLight" size="1em" mt="4px" /></> :
                        <>{allocationText}<Icon name="chevronDownLight" size="1em" mt="4px" /></>}
                </AllocationBox>
            }>
                <Table>
                    <TableHeader>
                        <TableRow>
                            <TableHeaderCell width="200px">Provider</TableHeaderCell>
                            <TableHeaderCell width="200px">Name</TableHeaderCell>
                            <TableHeaderCell width="200px">Balance</TableHeaderCell>
                            <TableHeaderCell width="45px">ID</TableHeaderCell>
                        </TableRow>
                    </TableHeader>
                    <tbody>
                        {wallets.map(w =>
                            <AllocationRows
                                key={w.paysFor.provider + w.paysFor.name + w.paysFor.title}
                                wallet={w}
                                onClick={(wallet, allocation) => {
                                    setAllocationId(allocation.id);
                                    setAllocation({wallet, allocation, autoAssigned: false});
                                }}
                            />
                        )}
                    </tbody>
                </Table>
            </ClickableDropdown> : allocation ?
                <Flex>{allocationText} {allocation.autoAssigned ?
                    <Tooltip trigger={<Text ml="4px" color="gray"> (Auto-assigned)</Text>}>
                        As only one allocation was available, it was auto-assigned.
                    </Tooltip>
                    : null}
                </Flex> :
                "No allocation selected"
        }
    </Box>
}

function AllocationRows({wallet, onClick}: {onClick(wallet: Wallet, allocation: WalletAllocation): void; wallet: Wallet;}) {
    return <>
        {wallet.allocations.map(a =>
            <TableRow key={a.id} onClick={() => onClick(wallet, a)} cursor="pointer">
                <TableCell width="200px">{wallet.paysFor.provider}</TableCell>
                <TableCell width="200px">{wallet.paysFor.name}</TableCell>
                <TableCell width="200px">{a.localBalance}</TableCell>
                <TableCell width="45px">{a.id}</TableCell>
            </TableRow>
        )}
    </>;
}

function titleFromTarget(target: RequestTarget): string {
    switch (target) {
        case RequestTarget.EXISTING_PROJECT:
            return "Viewing project";
        case RequestTarget.NEW_PROJECT:
            return "Create project";
        case RequestTarget.PERSONAL_PROJECT:
            return "My workspace";
        case RequestTarget.VIEW_APPLICATION:
            return "Viewing application";
    }
}

const defaultGrantApplication: GrantApplication = {
    createdAt: 0,
    updatedAt: 0,
    createdBy: "unknown",
    currentRevision: {
        createdAt: 0,
        updatedBy: "",
        revisionNumber: 0,
        document: {
            allocationRequests: [],
            form: {
                type: "plain_text",
                text: ""
            },
            recipient: {
                username: "",
                type: "personalWorkspace"
            },
            referenceId: "",
            revisionComment: "",
            parentProjectId: null,
        }
    },
    id: "0",
    status: {
        overallState: State.IN_PROGRESS,
        comments: [],
        revisions: [],
        stateBreakdown: []
    }
};


const FETCHED_GRANT_APPLICATION = "FETCHED_GRANT_APPLICATION";
type FetchedGrantApplication = PayloadAction<typeof FETCHED_GRANT_APPLICATION, GrantApplication>;
const UPDATED_REFERENCE_ID = "UPDATE_REFERENCE_ID";
type UpdatedReferenceID = PayloadAction<typeof UPDATED_REFERENCE_ID, {referenceId: string;}>;
const UPDATE_PARENT_PROJECT_ID = "UPDATE_PARENT_PROJECT_ID";
type UpdatedParentProjectID = PayloadAction<typeof UPDATE_PARENT_PROJECT_ID, {parentProjectId: string;}>;
const POSTED_COMMENT = "POSTED_COMMENT";
type PostedComment = PayloadAction<typeof POSTED_COMMENT, Comment>;
const UPDATE_DOCUMENT = "UPDATE_DOCUMENT";
type UpdateDocument = PayloadAction<typeof UPDATE_DOCUMENT, Document>;
const UPDATE_GRANT_STATE = "UPDATE_GRANT_STATE";
type UpdateGrantState = PayloadAction<typeof UPDATE_GRANT_STATE, {projectId: string, state: State}>;
const SET_STATE_WITHDRAWN = "SET_STATE_WITHDRAWN";
interface SetStateWithdrawn {type: typeof SET_STATE_WITHDRAWN};


type GrantApplicationReducerAction = FetchedGrantApplication | UpdatedReferenceID | UpdatedParentProjectID | PostedComment | UpdateDocument | UpdateGrantState | SetStateWithdrawn;
function grantApplicationReducer(state: GrantApplication, action: GrantApplicationReducerAction): GrantApplication {
    switch (action.type) {
        case FETCHED_GRANT_APPLICATION: {
            return action.payload;
        }
        case UPDATED_REFERENCE_ID: {
            // TODO: Is this enough? Technically, we now have a new revision.
            state.currentRevision.document.referenceId = action.payload.referenceId;
            return {...state};
        }
        case UPDATE_PARENT_PROJECT_ID: {
            // TODO: Is this enough? Technically, we now have a new revision.
            state.currentRevision.document.parentProjectId = action.payload.parentProjectId;
            return {...state};
        }
        case UPDATE_DOCUMENT: {
            state.currentRevision.document = action.payload;
            return {...state};
        }
        case POSTED_COMMENT: {
            state.status.comments.push(action.payload);
            return {...state};
        }
        case UPDATE_GRANT_STATE: {
            state.status.stateBreakdown.forEach(it => {
                if (it.projectId === action.payload.projectId) {
                    it.state = action.payload.state;
                }
            });
            state.status.overallState = findNewOverallState(state.status.stateBreakdown);
            return {...state};
        }
        case SET_STATE_WITHDRAWN: {
            state.status.overallState = State.CLOSED;
            return {...state};
        }
    }
}

function findNewOverallState(approvalStates: GrantGiverApprovalState[]): State {
    if (approvalStates.some(it => it.state === State.CLOSED)) return State.CLOSED;
    // Note(Jonas): This is how it's currently handled in the backend.
    if (approvalStates.some(it => it.state === State.REJECTED)) return State.REJECTED;
    if (approvalStates.some(it => it.state === State.IN_PROGRESS)) return State.IN_PROGRESS;
    return State.APPROVED;
}


// Note(Dan): target is baked into the component to make sure we follow the rules of hooks.
//
// We need to take wildly different paths depending on the target which causes us to use very different hooks. Baking
// the target property ensures a remount of the component.

// Note(Jonas): Maybe this can be solved using keys when upgrading to the new React-Router version.
export const GrantApplicationEditor: (target: RequestTarget) =>
    React.FunctionComponent = target => function MemoizedEditor() {
        const [loading, runWork] = useCloudCommand();
        const projectTitleRef = useRef<HTMLInputElement>(null);
        const projectReferenceIdRef = useRef<HTMLInputElement>(null);
        useTitle(titleFromTarget(target));
        const history = useHistory();

        const [grantGivers, fetchGrantGivers] = useCloudAPI<GrantsRetrieveAffiliationsResponse>(
            browseAffiliations({itemsPerPage: 250}), emptyPage
        );

        const {appId} = useParams<{appId?: string;}>();
        const projectId = useProjectId();
        const documentRef = useRef<HTMLTextAreaElement>(null);

        const [isLocked, setIsLocked] = useState<boolean>(target === RequestTarget.VIEW_APPLICATION);
        const [isEditingProjectReferenceId, setIsEditingProjectReference] = useState(false);
        const [grantProductCategories, setGrantProductCategories] = useState<Record<string, GrantProductCategory[]>>({});
        const [submitLoading, setSubmissionsLoading] = React.useState(false);
        const [grantGiversInUse, setGrantGiversInUse] = React.useState<string[]>([]);
        const [transferringApplication, setTransferringApplication] = useState(false);

        const [wallets, fetchWallets] = useCloudAPI<UCloud.PageV2<Wallet>>({noop: true}, emptyPageV2)

        const [grantApplication, dispatch] = React.useReducer(grantApplicationReducer, defaultGrantApplication, () => defaultGrantApplication);
        const activeStateBreakDown = React.useMemo(() => grantApplication.status.stateBreakdown.find(it => it.projectId === Client.projectId), [grantApplication, projectId]);
        const isApprover = activeStateBreakDown != null;

        const [templates, fetchTemplates] = useCloudAPI<Templates | undefined>({noop: true}, undefined);
        React.useEffect(() => {
            const {parentProjectId} = getDocument(grantApplication);
            if (parentProjectId && target !== RequestTarget.VIEW_APPLICATION) {
                fetchTemplates(apiRetrieve({projectId: grantApplication.currentRevision.document.parentProjectId}, "/api/grant/templates"))
            }
        }, [grantApplication.currentRevision.document.parentProjectId]);
        React.useEffect(() => {
            if (target === RequestTarget.VIEW_APPLICATION) return;
            if (!documentRef.current) return;
            if (templates.data != null && templates.loading === false) {
                switch (target) {
                    case RequestTarget.EXISTING_PROJECT:
                        documentRef.current.value = templates.data.existingProject;
                        return;
                    case RequestTarget.NEW_PROJECT:
                        documentRef.current.value = templates.data.newProject;
                        return;
                    case RequestTarget.PERSONAL_PROJECT:
                        documentRef.current.value = templates.data.personalProject;
                }
            }
        }, [templates.data]);

        React.useEffect(() => {
            if (documentRef.current) {
                documentRef.current.value = formTextFromGrantApplication(grantApplication);
            }
        }, [grantApplication, documentRef.current]);

        React.useEffect(() => {
            if (appId) {
                fetchGrantApplication({id: appId}).then(g =>
                    dispatch({
                        type: FETCHED_GRANT_APPLICATION, payload: g
                    })
                );
            };
        }, [appId]);


        const projectManagement = useProjectManagementStatus({isRootComponent: true, allowPersonalProject: true});
        const isRecipient = checkIsGrantRecipient(target, grantApplication, isAdminOrPI(projectManagement.projectDetails.data.whoami.role));

        const editApplication = useCallback(async (autoMessage: boolean = false) => {
            setSubmissionsLoading(true);

            const requestedResourcesByAffiliate = findRequestedResources(grantProductCategories);

            // Note(Jonas): Remove source allocations from modified requestedResources, 
            // as long as user is not also approver for said resource.
            if (isRecipient) {
                const approverFor = Client.projectId;
                for (const requestedResource of requestedResourcesByAffiliate) {
                    if (approverFor === requestedResource.grantGiver) {
                        continue;
                    }
                    const oldAllocation = grantApplication.currentRevision.document.allocationRequests.find(it =>
                        it.category === requestedResource.category &&
                        it.provider === requestedResource.provider &&
                        it.grantGiver === requestedResource.grantGiver
                    );
                    if (!oldAllocation) continue;
                    if (
                        oldAllocation.balanceRequested !== requestedResource.balanceRequested ||
                        oldAllocation.period.start !== requestedResource.period.start ||
                        oldAllocation.period.end !== requestedResource.period.end
                    ) {
                        requestedResource.sourceAllocation = null;
                    }
                }
            }

            if (requestedResourcesByAffiliate.length === 0) {
                snackbarStore.addFailure("At least one resource field must be non-zero.", false);
                setSubmissionsLoading(false);
                return;
            }

            try {
                const formText = documentRef.current?.value;
                if (!formText) {
                    snackbarStore.addFailure("Please explain why this application is being submitted.", false);
                    setSubmissionsLoading(false);
                    return;
                }

                const revisionComment = await (autoMessage ? "Auto inserted allocations." : promptRevisionComment());
                if (revisionComment == null) {
                    setSubmissionsLoading(false);
                    return;
                }

                const document: Document = {
                    referenceId: grantApplication.currentRevision.document.referenceId,
                    revisionComment,
                    recipient: grantApplication.currentRevision.document.recipient,
                    allocationRequests: requestedResourcesByAffiliate,
                    form: toForm(target, grantApplication, formText),
                    parentProjectId: grantApplication.currentRevision.document.parentProjectId,
                };

                const toSubmit = {
                    applicationId: appId,
                    document
                };

                await runWork<[id: string]>(apiUpdate(bulkRequestOf(toSubmit), "/api/grant", "edit"));
                dispatch({type: UPDATE_DOCUMENT, payload: document});
                setIsLocked(true);
            } catch (error) {
                displayErrorMessageOrDefault(error, "Failed to submit application.");
            }
            setSubmissionsLoading(false);
        }, [grantGiversInUse, grantProductCategories, grantApplication, isRecipient]);

        const submitRequest = useCallback(async () => {
            setSubmissionsLoading(true);

            if (grantGiversInUse.length === 0) {
                snackbarStore.addFailure("No grant giver selected. Please select at least one to submit.", false);
                setSubmissionsLoading(false);
                return;
            }

            const requestedResourcesByAffiliate = findRequestedResources(grantProductCategories);

            if (requestedResourcesByAffiliate.length === 0) {
                snackbarStore.addFailure("At least one resource field must be non-zero.", false);
                setSubmissionsLoading(false);
                return;
            }

            let recipient: Recipient;
            switch (target) {
                case RequestTarget.EXISTING_PROJECT: {
                    recipient = {
                        type: "existingProject",
                        id: Client.projectId ?? ""
                    };
                } break;
                case RequestTarget.NEW_PROJECT: {
                    const newProjectTitle = projectTitleRef.current?.value;
                    if (!newProjectTitle) {
                        snackbarStore.addFailure("Project title can't be empty.", false);
                        setSubmissionsLoading(false);
                        return;
                    }
                    recipient = {
                        type: "newProject",
                        title: newProjectTitle
                    };
                } break;
                case RequestTarget.PERSONAL_PROJECT: {
                    recipient = {
                        type: "personalWorkspace",
                        username: Client.username ?? "",
                    };
                } break;
                case RequestTarget.VIEW_APPLICATION: {
                    recipient = grantApplication.currentRevision.document.recipient;
                } break;
            }

            const formText = documentRef.current?.value;
            if (!formText) {
                snackbarStore.addFailure("Please explain why this application is being submitted.", false);
                setSubmissionsLoading(false);
                return;
            }

            const documentToSubmit: Document = {
                referenceId: null,
                revisionComment: null,
                recipient: recipient,
                allocationRequests: requestedResourcesByAffiliate,
                form: toForm(target, grantApplication, formText),
                parentProjectId: grantApplication.currentRevision.document.parentProjectId,
            };

            const payload = bulkRequestOf({document: documentToSubmit});

            try {
                const [{id}] = await runWork(apiCreate(payload, "/api/grant", "submit-application"), {defaultErrorHandler: false});
                history.push(`/project/grants/view/${id}`);
            } catch (error) {
                displayErrorMessageOrDefault(error, "Failed to submit application.");
            }
            setSubmissionsLoading(false);
        }, [grantGiversInUse, grantProductCategories, grantApplication]);

        const updateReferenceID = useCallback(async () => {
            const value = projectReferenceIdRef.current?.value;
            if (!value) {
                snackbarStore.addFailure("Project Reference ID can't be empty", false);
                return;
            }

            const {document} = grantApplication.currentRevision;
            document.referenceId = value;

            const toSubmit = {
                applicationId: appId,
                document
            };

            await runWork<[id: string]>(apiUpdate(bulkRequestOf(toSubmit), "/api/grant", "edit"));
            dispatch({type: UPDATE_DOCUMENT, payload: document});
            setIsEditingProjectReference(false);
            dispatch({type: "UPDATE_REFERENCE_ID", payload: {referenceId: value}});
        }, [grantApplication]);

        const cancelEditOfRefId = useCallback(async () => {
            setIsEditingProjectReference(false);
        }, []);

        const approveRequest = useCallback(async () => {
            try {
                if (!activeStateBreakDown) return;
                if (!canApproveApplication(grantProductCategories)) {
                    snackbarStore.addFailure("Not all requested resources have a source allocation assigned. " +
                        "You must select one for each resource.", false);
                    return;
                }

                if (anyAutoAssigned(grantProductCategories)) {
                    await editApplication(true);
                }
                await runWork(updateState(bulkRequestOf({applicationId: grantApplication.id, newState: State.APPROVED, notify: true})), {defaultErrorHandler: false});
                dispatch({type: UPDATE_GRANT_STATE, payload: {projectId: activeStateBreakDown.projectId, state: State.APPROVED}});
            } catch (e) {
                displayErrorMessageOrDefault(e, "Failed to reject application.")
                reload();
            }
        }, [grantApplication.id, activeStateBreakDown, grantProductCategories]);

        const rejectRequest = useCallback(async () => {
            try {
                if (!activeStateBreakDown) return;
                await runWork(updateState(bulkRequestOf({applicationId: grantApplication.id, newState: State.REJECTED, notify: false})));
                dispatch({type: UPDATE_GRANT_STATE, payload: {projectId: activeStateBreakDown.projectId, state: State.REJECTED}});
            } catch (e) {
                displayErrorMessageOrDefault(e, "Failed to reject application.");
                reload();
            }
        }, [grantApplication.id, activeStateBreakDown]);



        const closeRequest = useCallback(async () => {
            if (!appId) return;
            try {
                await runWork(closeApplication(bulkRequestOf({applicationId: appId})), {defaultErrorHandler: false});
                dispatch({type: SET_STATE_WITHDRAWN});
            } catch (e) {
                displayErrorMessageOrDefault(e, "Failed to withdraw application.");
                reload();
            }
        }, [appId]);

        const setRequestPending = useCallback(async () => {
            if (!appId || !activeStateBreakDown) return;
            try {
                await runWork(updateState(bulkRequestOf({applicationId: appId, newState: State.IN_PROGRESS, notify: false})), {defaultErrorHandler: false});
                dispatch({type: UPDATE_GRANT_STATE, payload: {projectId: activeStateBreakDown.projectId, state: State.IN_PROGRESS}});
            } catch (e) {
                displayErrorMessageOrDefault(e, "Failed to undo.")
            }
        }, [appId, activeStateBreakDown]);

        const trySetRequestPending = React.useCallback(() => {
            if (grantApplication.updatedAt < THIRTY_DAYS_AGO) {
                addStandardDialog({
                    title: "Set as pending",
                    message: "The application has not been updated for over 30 days. Do you wish to update it?",
                    onConfirm: setRequestPending
                })
            } else {
                setRequestPending();
            }
        }, [grantApplication, setRequestPending]);

        const reload = useCallback(() => {
            fetchGrantGivers(browseAffiliations({itemsPerPage: 250}));
            if (appId) {fetchGrantApplication({id: appId}).then(g => dispatch({type: FETCHED_GRANT_APPLICATION, payload: g}));};
        }, [appId]);


        const discardChanges = useCallback(() => {
            setIsLocked(true);
            reload();
        }, [reload]);

        const transferRequest = useCallback(async (toProjectId: string) => {
            if (!appId) return;
            let comment = "";
            try {
                const {result} = await addStandardInputDialog({
                    title: "Post comment before transferring application?",
                    help: <Box mb="16px">
                        Note: After transferring the application, the application and comments will  <br />
                        no longer be available to members of your current active project.
                    </Box>,
                    type: "textarea",
                    rows: 3,
                    width: "100%",
                    resize: "none",
                    cancelText: "Skip",
                    confirmText: "Post",
                    cancelButtonColor: "blue",
                    validator: () => true
                });
                comment = result;
                if (comment) {
                    await runWork<{id: string}[]>(commentOnGrantApplication({
                        grantId: appId,
                        comment
                    }), {defaultErrorHandler: false});
                }
            } catch (e) {
                if ("cancelled" in e) {/* expected */}
                else errorMessageOrDefault(e, "Failed to post comment. Cancelling transfer");
            }

            runWork(transferApplication(bulkRequestOf({
                applicationId: parseInt(appId, 10),
                transferToProjectId: toProjectId,
                revisionComment:
                    `${comment}

                    -- Auto-inserted: transferred from project ${projectId}.
                `
            })));

        }, [appId]);

        React.useEffect(() => {
            if (
                grantApplication.currentRevision.document.parentProjectId &&
                grantGiversInUse.includes(grantApplication.currentRevision.document.parentProjectId)
            ) return;
            const [firstGrantGiver] = grantGiversInUse;
            if (firstGrantGiver != null) dispatch({type: "UPDATE_PARENT_PROJECT_ID", payload: {parentProjectId: firstGrantGiver}});
        }, [grantGiversInUse, grantApplication]);

        const grantFinalized = isGrantFinalized(grantApplication.status.overallState);

        React.useEffect(() => {
            if (target === RequestTarget.PERSONAL_PROJECT && projectId != null) {
                history.push("/");
                return;
            }
            setGrantGiversInUse([]);
            fetchWallets(browseWallets({itemsPerPage: 250}));
            reload();
        }, [projectId, appId]);

        const grantGiverDropdown = React.useMemo(() => {
            const filteredGrantGivers = grantGivers.data.items.filter(grantGiver =>
                (target === RequestTarget.NEW_PROJECT || grantGiver.projectId !== projectId) &&
                !grantGiversInUse.includes(grantGiver.projectId)
            );
            return target === RequestTarget.VIEW_APPLICATION || filteredGrantGivers.length === 0 ? null :
                <ClickableDropdown
                    fullWidth
                    colorOnHover={false}
                    trigger={<Flex><Heading.h2>Select grant giver</Heading.h2> <Icon name="chevronDownLight" size="1em" mt="18px" ml=".7em" color={"darkGray"} /></Flex>}
                >
                    <Wrapper>
                        <Table>
                            <TableHeader>
                                <TableRow>
                                    <TableHeaderCell pl="6px">Name</TableHeaderCell>
                                    <TableHeaderCell pl="6px">Description</TableHeaderCell>
                                </TableRow>
                            </TableHeader>
                            <tbody>
                                {filteredGrantGivers.map(grantGiver =>
                                    <TableRow cursor="pointer" key={grantGiver.projectId} onClick={() => setGrantGiversInUse(inUse => [...inUse, grantGiver.projectId])}>
                                        <TableCell pl="6px"><Flex><Logo projectId={grantGiver.projectId} size="32px" /><Text mt="3px" ml="8px">{grantGiver.title}</Text></Flex></TableCell>
                                        <TableCell pl="6px"><GrantGiverDescription key={grantGiver.projectId} projectId={grantGiver.projectId} /></TableCell>
                                    </TableRow>
                                )}
                            </tbody>
                        </Table>
                    </Wrapper>
                </ClickableDropdown>
        }, [grantGivers, grantGiversInUse]);

        const grantGiverEntries = React.useMemo(() =>
            grantGivers.data.items.filter(it => grantGiversInUse.includes(it.projectId)).map(it =>
                <GrantGiver
                    key={it.projectId}
                    target={target}
                    isApprover={Client.projectId === it.projectId}
                    grantApplication={grantApplication}
                    remove={() => setGrantGiversInUse(inUse => [...inUse.filter(entry => entry !== it.projectId)])}
                    setGrantProductCategories={setGrantProductCategories}
                    wallets={wallets.data.items}
                    project={it}
                    isParentProject={getDocument(grantApplication).parentProjectId === it.projectId}
                    setParentProject={parentProjectId => dispatch({type: "UPDATE_PARENT_PROJECT_ID", payload: {parentProjectId}})}
                    isLocked={isLocked}
                    isRecipient={isRecipient}
                />
            ), [grantGivers, isRecipient, grantGiversInUse, isLocked, wallets, grantApplication, isRecipient]);

        const recipient = getDocument(grantApplication).recipient;
        const recipientName = getRecipientName(grantApplication);
        const isProject = recipient.type !== "personalWorkspace";

        return (
            <MainContainer
                header={target === RequestTarget.EXISTING_PROJECT ?
                    <ProjectBreadcrumbs crumbs={[{title: "Request for Resources"}]} /> : null
                }
                sidebar={<>
                    {target !== RequestTarget.VIEW_APPLICATION ? (
                        <Button fullWidth disabled={grantFinalized || submitLoading} onClick={submitRequest}>
                            Submit Application
                        </Button>
                    ) : null}
                    {target !== RequestTarget.VIEW_APPLICATION || grantFinalized ? null : (
                        isLocked ? (
                            <Button fullWidth onClick={() => setIsLocked(false)} disabled={loading}>
                                Edit this request
                            </Button>
                        ) : (
                            <>
                                <Button
                                    color={"green"}
                                    fullWidth
                                    disabled={loading}
                                    onClick={() => editApplication()}
                                >
                                    Save Changes
                                </Button>
                                <Button my="4px" fullWidth color={"red"} onClick={discardChanges}>Discard changes</Button>
                            </>
                        )
                    )}
                    {isApprover && ![State.APPROVED, State.CLOSED].includes(grantApplication.status.overallState) ?
                        <Button
                            mt="4px"
                            color="blue"
                            fullWidth
                            onClick={() => setTransferringApplication(true)}
                            disabled={!isLocked}
                        >
                            Transfer application
                        </Button> : null
                    }
                </>}
                main={<>
                    <Flex justifyContent="center">
                        <Box maxWidth={1400} width="100%">
                            {target !== RequestTarget.NEW_PROJECT ? null : (
                                <>
                                    <Label mb={16} mt={16}>
                                        Principal Investigator (PI)
                                        <Input
                                            value={
                                                `${Client.userInfo?.firstNames} ${Client.userInfo?.lastName} ` +
                                                `(${Client.username})`
                                            }
                                            disabled
                                        />
                                    </Label>
                                    <Label mb={16} mt={16}>
                                        Project title
                                        <Input ref={projectTitleRef} />
                                    </Label>
                                </>
                            )}

                            {target !== RequestTarget.VIEW_APPLICATION ? null : (
                                <>
                                    <HighlightedCard color="blue" isLoading={false}>
                                        <Heading.h4 mb={16}>Metadata</Heading.h4>

                                        <Text mb={16}>
                                            <i>Application must be resubmitted to change the metadata.</i>
                                        </Text>
                                        <Table>
                                            <tbody>
                                                <TableRow>
                                                    <TableCell>Application Approver(s)</TableCell>
                                                    <TableCell>
                                                        <Box>
                                                            {grantApplication.status.stateBreakdown.map(state => <Flex key={state.projectId}>
                                                                <StateIcon state={state.state} /><Text>{state.projectTitle}</Text>
                                                            </Flex>)}
                                                        </Box>
                                                    </TableCell>
                                                </TableRow>
                                                <TableRow>
                                                    <TableCell>{
                                                        !isProject ? "Recipient Username" : "Project Title"}
                                                    </TableCell>
                                                    <TableCell>{recipientName}</TableCell>
                                                </TableRow>
                                                {isProject ? <TableRow>
                                                    <TableCell>Principal Investigator (PI)</TableCell>
                                                    {/* TODO(Jonas): This is wrong. This could be created by an admin. */}
                                                    <TableCell>{grantApplication.createdBy}</TableCell>
                                                </TableRow> : null}

                                                <TableRow>
                                                    <TableCell verticalAlign="top">
                                                        Recipient Type
                                                    </TableCell>
                                                    <TableCell>
                                                        {recipientTypeToText(recipient)}
                                                    </TableCell>
                                                </TableRow>
                                                <TableRow>
                                                    <TableCell verticalAlign="top">
                                                        Reference ID
                                                    </TableCell>
                                                    {/* TODO(Jonas): When should this be shown? Approver? */}
                                                    <TableCell>
                                                        <table>
                                                            <tbody>
                                                                <tr>
                                                                    {isEditingProjectReferenceId ? (
                                                                        <>
                                                                            <td>
                                                                                <form onSubmit={e => {
                                                                                    stopPropagationAndPreventDefault(e);
                                                                                    updateReferenceID();
                                                                                }}>
                                                                                    <Input
                                                                                        placeholder={"e.g. DeiC-SDU-L1-000001"}
                                                                                        ref={projectReferenceIdRef}
                                                                                    />
                                                                                </form>
                                                                            </td>
                                                                            <td>
                                                                                <Button
                                                                                    color="blue"
                                                                                    onClick={updateReferenceID}
                                                                                    mx="6px"
                                                                                >
                                                                                    Update Reference ID
                                                                                </Button>
                                                                                <Button
                                                                                    color="red"
                                                                                    onClick={cancelEditOfRefId}
                                                                                >
                                                                                    Cancel
                                                                                </Button>
                                                                            </td>
                                                                        </>) : (
                                                                        <>
                                                                            <td>
                                                                                {getReferenceId(grantApplication) ?? "No ID given"}
                                                                            </td>
                                                                            <td>
                                                                                <Button ml={"4px"} onClick={() => setIsEditingProjectReference(true)}>Edit</Button>
                                                                            </td>
                                                                        </>)
                                                                    }
                                                                </tr>
                                                            </tbody>
                                                        </table>
                                                    </TableCell>
                                                </TableRow>
                                                <TableRow>
                                                    <TableCell verticalAlign="top" mt={32}>Current Status</TableCell>
                                                    <TableCell>
                                                        {overallStateText(grantApplication)}
                                                        {target !== RequestTarget.VIEW_APPLICATION ? null : (
                                                            <Box mt="18px">
                                                                {isApprover && ![State.APPROVED, State.CLOSED].includes(grantApplication.status.overallState) ?
                                                                    <>
                                                                        {activeStateBreakDown.state === State.IN_PROGRESS ? <>
                                                                            <ConfirmationButton
                                                                                mb="4px"
                                                                                icon="check"
                                                                                color="green"
                                                                                onAction={approveRequest}
                                                                                disabled={!isLocked}
                                                                                fullWidth
                                                                                actionText={`Approve for ${activeStateBreakDown?.projectTitle}`}
                                                                            />
                                                                            <ConfirmationButton
                                                                                color="red"
                                                                                disabled={!isLocked}
                                                                                fullWidth
                                                                                icon="close"
                                                                                onAction={rejectRequest}
                                                                                actionText={`Reject for ${activeStateBreakDown?.projectTitle}`}
                                                                            />
                                                                        </> : [State.APPROVED, State.REJECTED].includes(activeStateBreakDown.state) ? <>
                                                                            <ConfirmationButton
                                                                                fullWidth
                                                                                icon="ellipsis"
                                                                                onAction={trySetRequestPending}
                                                                                actionText={`Undo ${activeStateBreakDown.state === State.APPROVED ? "approval" : "rejection"}`}
                                                                            />
                                                                        </> : null}
                                                                    </> : null
                                                                }
                                                                {isRecipient && !grantFinalized ?
                                                                    <ConfirmationButton
                                                                        mt="16px"
                                                                        icon="close"
                                                                        color="red"
                                                                        fullWidth
                                                                        onAction={closeRequest}
                                                                        disabled={!isLocked}
                                                                        actionText={"Withdraw"}
                                                                    /> : null
                                                                }
                                                            </Box>
                                                        )}
                                                    </TableCell>
                                                </TableRow>
                                            </tbody>
                                        </Table>
                                    </HighlightedCard>
                                </>
                            )}

                            <Box my="32px" />

                            {target === RequestTarget.VIEW_APPLICATION ? null : grantGiverDropdown}
                            {target === RequestTarget.VIEW_APPLICATION ? null : grantGiverEntries}
                            {/* Note(Jonas): This is for the grant givers that are part of an existing grant application */}
                            {target !== RequestTarget.VIEW_APPLICATION ? null : (
                                grantGivers.data.items.sort(grantGiverSortFn)
                                    .filter(it => grantApplication.status.stateBreakdown
                                        .map(it => it.projectId).includes(it.projectId)).map(grantGiver =>
                                            <GrantGiver
                                                key={grantGiver.projectId}
                                                target={target}
                                                isLocked={isLocked}
                                                isApprover={Client.projectId === grantGiver.projectId}
                                                project={grantGiver}
                                                setParentProject={parentProjectId =>
                                                    dispatch({type: "UPDATE_PARENT_PROJECT_ID", payload: {parentProjectId}})
                                                }
                                                isParentProject={grantGiver.projectId === getDocument(grantApplication).parentProjectId}
                                                grantApplication={grantApplication}
                                                setGrantProductCategories={setGrantProductCategories}
                                                wallets={wallets.data.items}
                                                isRecipient={isRecipient}
                                            />))}

                            <CommentApplicationWrapper>
                                <RequestFormContainer>
                                    <Heading.h4>Application</Heading.h4>
                                    <TextArea
                                        disabled={grantFinalized || isLocked}
                                        rows={25}
                                        ref={documentRef}
                                    />
                                </RequestFormContainer>

                                <Comments
                                    target={target}
                                    appId={appId}
                                    dispatch={dispatch}
                                    grantApplication={grantApplication}
                                    reload={reload}
                                />
                            </CommentApplicationWrapper>

                        </Box>
                    </Flex>
                </>}
                additional={
                    <TransferApplicationPrompt
                        isActive={transferringApplication}
                        close={() => setTransferringApplication(false)}
                        transfer={transferRequest}
                        grantId={appId}
                    />}
            />
        );
    };

function StateIcon({state}: {state: State;}) {
    let icon: IconName;
    let color: ThemeColor;
    switch (state) {
        case State.APPROVED:
            icon = "check";
            color = "green";
            break;
        case State.CLOSED:
        case State.REJECTED:
            icon = "close";
            color = "red";
            break;
        case State.IN_PROGRESS:
            icon = "ellipsis";
            color = "blue";
            break;
    }
    return <Icon mr="8px" name={icon} color={color} />;
}

interface CommentsProps {
    grantApplication: GrantApplication;
    dispatch: any;
    target: RequestTarget;
    appId?: string;
    reload(): void;
}

function Comments(props: CommentsProps) {
    const avatars = useAvatars();

    return (
        <Box width="100%">
            <Heading.h4>Comments</Heading.h4>
            {props.grantApplication.status.comments.length > 0 ? null : (
                <Box mt={16} mb={16}>
                    No comments have been posted yet.
                </Box>
            )}

            {props.grantApplication.status.comments.map(it => (
                <CommentBox
                    key={it.id}
                    grantId={props.appId}
                    comment={it}
                    avatar={avatars.cache[it.username] ?? defaultAvatar}
                    reload={props.reload}
                />
            ))}
            {!props.appId ? null :
                <PostCommentWidget
                    grantId={props.appId}
                    avatar={avatars.cache[Client.username!] ?? defaultAvatar}
                    onPostedComment={comment => props.dispatch({type: "POSTED_COMMENT", payload: comment})}
                    disabled={props.target !== RequestTarget.VIEW_APPLICATION}
                />
            }
        </Box>
    )
}


function grantGiverSortFn(grantGiverA: ProjectWithTitle, grantGiverB: ProjectWithTitle): number {
    if (grantGiverA.projectId === Client.projectId) return -1;
    if (grantGiverB.projectId === Client.projectId) return 1;
    return grantGiverA.title.localeCompare(grantGiverB.title);
}

const projectDescriptionCache: {[projectId: string]: string | undefined;} = {};

function getReferenceId(grantApplication: GrantApplication): string | null {
    return grantApplication.currentRevision.document.referenceId;
}

function GrantGiverDescription(props: {projectId: string;}): JSX.Element {
    const [description, fetchDescription] = useCloudAPI<RetrieveDescriptionResponse>(
        {noop: true}, {description: projectDescriptionCache[props.projectId] ?? ""}
    );

    useEffect(() => {
        if (projectDescriptionCache[props.projectId] == null) {
            fetchDescription(retrieveDescription({
                projectId: props.projectId,
            }));
        }
    }, [props.projectId]);

    useEffect(() => {
        if (description.data.description != null) {
            projectDescriptionCache[props.projectId] = description.data.description;
        }
    }, [description.data.description, projectDescriptionCache]);

    return <Truncate>{description.data.description}</Truncate>;
}

function recipientTypeToText(recipient: Recipient): string {
    switch (recipient.type) {
        case "existingProject":
            return "Existing Project";
        case "newProject":
            return "New Project";
        case "personalWorkspace":
            return "Personal Workspace";
    }
}

function getRecipientName(grantApplication: GrantApplication): string {
    switch (grantApplication.currentRevision.document.recipient.type) {
        case "existingProject": {
            return grantApplication.status.projectTitle ?? "";
        }
        case "newProject": {
            return grantApplication.currentRevision.document.recipient.title;
        }
        case "personalWorkspace": {
            return grantApplication.currentRevision.document.recipient.username;
        }
    }
}

function getRecipientId(recipient: Recipient): string {
    return recipient.type === "existingProject" ? recipient.id :
        recipient.type === "newProject" ? recipient.title :
            recipient.type === "personalWorkspace" ? recipient.username : "";
}

function getAllocationRequests(grantApplication: GrantApplication): AllocationRequest[] {
    return getDocument(grantApplication).allocationRequests;
}

function overallStateText(grantApplication: GrantApplication): string {
    const {updatedBy} = grantApplication.currentRevision;
    switch (grantApplication.status.overallState) {
        case State.APPROVED:
            return updatedBy === null ? "Approved" : "Approved by " + updatedBy;
        case State.REJECTED:
            return updatedBy === null ? "Rejected" : "Rejected by " + updatedBy;
        case State.IN_PROGRESS:
            return "In progress";
        case State.CLOSED:
            return updatedBy === null ? "Closed" : "Withdrawn by " + updatedBy;
    }
}

function GrantGiver(props: {
    grantApplication: GrantApplication;
    project: ProjectWithTitle;
    target: RequestTarget;
    isLocked: boolean;
    isApprover: boolean;
    isRecipient: boolean;
    remove?: () => void;
    wallets: Wallet[];
    isParentProject: boolean;
    setParentProject(project: string): void;
    setGrantProductCategories: React.Dispatch<React.SetStateAction<{[key: string]: GrantProductCategory[];}>>;
}): JSX.Element {
    const {recipient} = props.grantApplication.currentRevision.document;
    const recipientId = getRecipientId(recipient);

    const [products] = useCloudAPI<{availableProducts: Product[]}>(apiBrowse({
        projectId: props.project.projectId,
        recipientType: recipient.type,
        recipientId,
        showHidden: false
    }, "/api/grant", "products"), {availableProducts: []});

    const productCategories: GrantProductCategory[] = useMemo(() => {
        const result: GrantProductCategory[] = [];
        for (const product of products.data.availableProducts) {
            const metadata: ProductMetadata = {
                category: product.category,
                freeToUse: product.freeToUse,
                productType: product.productType,
                chargeType: product.chargeType,
                hiddenInGrantApplications: product.hiddenInGrantApplications,
                unitOfPrice: product.unitOfPrice
            };

            if (result.find(it => productCategoryEquals(it.metadata.category, metadata.category)) === undefined) {
                result.push({metadata});
            }
        }

        for (const request of getAllocationRequests(props.grantApplication)) {
            const existing = result.find(it =>
                productCategoryEquals(
                    it.metadata.category,
                    {name: request.category, provider: request.provider})
            );

            if (existing) {
                existing.requestedBalance = request.balanceRequested;
            }
        }
        return result;
    }, [products]);

    React.useEffect(() => {
        props.setGrantProductCategories(gpc => {
            gpc[props.project.projectId] = productCategories;
            return gpc;
        });
        return () => {
            props.setGrantProductCategories(gpc => {
                delete gpc[props.project.projectId];
                return gpc;
            });
        };
    }, [productCategories]);

    return React.useMemo(() => {
        const products = productTypes.map(type => <AsProductType
            key={type}
            target={props.target}
            type={type}
            projectId={props.project.projectId}
            isApprover={props.isApprover}
            grantApplication={props.grantApplication}
            wallets={props.wallets}
            productCategories={productCategories}
            isLocked={props.isLocked}
            isRecipient={props.isRecipient}
        />);

        const canEditAffiliation = canChangePrimaryAffiliation(
            props.target,
            props.grantApplication.currentRevision.document.recipient
        );

        const left = <Flex>
            <Heading.h2>{props.project.title}</Heading.h2>
            {!props.isParentProject && props.isLocked || !canEditAffiliation ? null :
                <Tooltip trigger={
                    <ParentProjectIcon
                        onClick={() => props.isLocked ? null : props.setParentProject(props.project.projectId)}
                        cursor={props.isLocked ? undefined : "pointer"}
                        isSelected={props.isParentProject} name="check"
                    />
                }>
                    {props.isParentProject ? "Selected as primary affiliation." : (
                        !props.isLocked ? "Click to select as primary affiliation" :
                            "Not selected as primary affiliation.")}
                </Tooltip>
            }
        </Flex>;

        const right = props.remove ? <Flex cursor="pointer" onClick={props.remove}>
            <Icon name="close" color="red" mr="8px" />Remove
        </Flex> : null

        const creatingOrApproverOrRecipient = props.target !== RequestTarget.VIEW_APPLICATION || props.isApprover || props.isRecipient;

        return <Box mt="12px">
            {creatingOrApproverOrRecipient ? <Spacer
                left={left}
                right={right}
            /> : null}
            {creatingOrApproverOrRecipient ? products :
                <Accordion noBorder title={<Flex mt="-12px">{left}</Flex>} titleContent={right} panelProps={{mx: "-8px", pl: "8px"}}>
                    {products}
                </Accordion>
            }
        </Box>
    }, [productCategories, props.wallets, props.isRecipient, props.grantApplication, props.isParentProject, props.isLocked]);
}

function canChangePrimaryAffiliation(target: RequestTarget, recipient: Recipient): boolean {
    switch (target) {
        case RequestTarget.NEW_PROJECT:
            return true;
        case RequestTarget.EXISTING_PROJECT:
        case RequestTarget.PERSONAL_PROJECT:
            return false;
        case RequestTarget.VIEW_APPLICATION: {
            switch (recipient.type) {
                case "existingProject":
                case "personalWorkspace":
                    return false;
                case "newProject":
                    return true;
            }
        }
    }
}

function AsProductType(props: {
    target: RequestTarget,
    type: ProductType;
    productCategories: GrantProductCategory[];
    wallets: Wallet[];
    isRecipient: boolean;
    projectId: string;
    isLocked: boolean;
    isApprover: boolean;
    grantApplication: GrantApplication;
}): JSX.Element | null {
    const grantFinalized = isGrantFinalized(props.grantApplication.status.overallState);
    const filteredProductCategories = props.productCategories.filter(pc => pc.metadata.productType === props.type);
    const noEntries = filteredProductCategories.length === 0;
    const {allocationRequests} = props.grantApplication.currentRevision.document;
    const filteredWallets = props.wallets.filter(it => it.productType === props.type);
    if (props.target === RequestTarget.VIEW_APPLICATION && noEntries) return null;
    return <>
        <Heading.h4 mt={32}><Flex>{productTypeToTitle(props.type)} <ProductLink /></Flex></Heading.h4>
        {noEntries ? <Heading.h4 mt="12px">No products for type available.</Heading.h4> : <ResourceContainer>
            {filteredProductCategories.map((pc, idx) =>
                <GenericRequestCard
                    key={idx}
                    wb={pc}
                    isApprover={props.isApprover}
                    grantFinalized={grantFinalized}
                    isLocked={props.isLocked}
                    allocationRequest={allocationRequests.find(it =>
                        it.category === pc.metadata.category.name &&
                        it.provider === pc.metadata.category.provider &&
                        it.grantGiver === props.projectId
                    )}
                    projectId={props.projectId}
                    isRecipient={props.isRecipient}
                    wallets={filteredWallets}
                />
            )}
        </ResourceContainer>}
    </>;
}

const ParentProjectIcon = styled(Icon) <{isSelected: boolean;}>`
    color: var(--${p => p.isSelected ? "blue" : "gray"});

    transition: color 0.4s;

    margin-top: 12px;
    margin-left: 8px;

    &:hover {
        color: var(--blue);
    }
`;

interface TransferApplicationPromptProps {
    isActive: boolean;
    grantId?: string;

    close(): void;

    transfer(toProjectId: string): Promise<void>;
}

function TransferApplicationPrompt({isActive, close, transfer, grantId}: TransferApplicationPromptProps) {
    const [projects, fetchProjects] = useCloudAPI<GrantsRetrieveAffiliationsResponse>({noop: true}, emptyPageV2);

    const history = useHistory();

    React.useEffect(() => {
        if (grantId) {
            fetchProjects(browseAffiliationsByResource({
                itemsPerPage: 100,
                applicationId: grantId
            }));
        }
    }, [grantId]);

    const dispatch = useDispatch();

    if (!grantId) return null;

    return (
        <ReactModal
            style={defaultModalStyle}
            isOpen={isActive}
            onRequestClose={close}
            shouldCloseOnEsc
            ariaHideApp={false}
            shouldCloseOnOverlayClick
        >
            <List>
                {!projects.loading && projects.data.items.length === 0 ? <Heading.h3>No projects found.</Heading.h3> : null}
                {projects.data.items.map(it =>
                    <Spacer
                        key={it.projectId}
                        left={<Box my="auto" mr="24px" key={it.projectId}>{it.title}</Box>}
                        right={<>
                            <ConfirmationButton
                                my="3px"
                                width="115px"
                                height="40px"
                                onAction={async () => {
                                    close();
                                    dispatch(setLoading(true));
                                    await transfer(it.projectId);
                                    dispatch(setLoading(false));
                                    history.push("/project/grants/ingoing");
                                }}
                                icon="move"
                                actionText="Transfer"
                            />
                        </>}
                    />
                )}
            </List>
        </ReactModal>
    );
}

const CommentApplicationWrapper = styled.div`
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(600px, 1fr));
    grid-gap: 32px;
    max-width: 1400px;
    margin-top: 32px;
`;

const CommentBoxWrapper = styled.div`
    display: flex;
    margin: 10px 0;

    .body {
        flex-grow: 1;
        margin: 0 6px;
    }

    time {
        color: var(--gray, #ff0);
    }

    p {
        margin: 0;
    }
`;

const CommentBox: React.FunctionComponent<{
    comment: Comment,
    avatar: AvatarType,
    reload: () => void;
    grantId?: string;
}> = ({comment, avatar, reload, grantId}) => {
    const [, runCommand] = useCloudCommand();
    const onDelete = useCallback(() => {
        if (!grantId) return;
        addStandardDialog({
            title: "Confirm comment deletion",
            message: "Are you sure you wish to delete your comment?",
            confirmText: "Delete",
            addToFront: true,
            onConfirm: async () => {
                try {
                    await runCommand(deleteGrantApplicationComment({grantId, commentId: comment.id}));
                    reload();
                } catch (err) {
                    displayErrorMessageOrDefault(err, "Failed to delete comment.")
                }
            }
        });
    }, [comment.id]);

    if (!grantId) return null;

    return <CommentBoxWrapper>
        <div className="avatar">
            <UserAvatar avatar={avatar} width={"48px"} />
        </div>

        <div className="body">
            <p><strong>{comment.username}</strong> says:</p>
            <p>{comment.comment}</p>
            <time>{dateToString(comment.createdAt)}</time>
        </div>

        {comment.username === Client.username ? (
            <div>
                <Icon cursor={"pointer"} name={"trash"} color={"red"} onClick={onDelete} />
            </div>
        ) : null}
    </CommentBoxWrapper>;
};

const PostCommentWrapper = styled.form`
    .wrapper {
        display: flex;
    }

    ${TextArea} {
        flex-grow: 1;
        margin-left: 6px;
    }

    .buttons {
        display: flex;
        margin-top: 6px;
        justify-content: flex-end;
    }
`;

interface UpdateStateRequest {
    applicationId: string;
    newState: State;
    notify: boolean;
}

function updateState(request: UCloud.BulkRequest<UpdateStateRequest>): APICallParameters<UCloud.BulkRequest<UpdateStateRequest>> {
    return apiUpdate(request, "/api/grant", "update-state");
}

const PostCommentWidget: React.FunctionComponent<{
    grantId: string,
    avatar: AvatarType,
    onPostedComment(comment: Comment): void;
    disabled: boolean;
}> = ({grantId, avatar, onPostedComment, disabled}) => {
    const commentBoxRef = useRef<HTMLTextAreaElement>(null);
    const [loading, runWork] = useCloudCommand();
    const submitComment = useCallback(async (e) => {
        e.preventDefault();
        if (disabled) return;
        try {
            const result = await runWork<{id: string}[]>(commentOnGrantApplication({
                grantId,
                comment: commentBoxRef.current!.value
            }), {defaultErrorHandler: false});
            if (result != null) {
                const [{id}] = result;
                onPostedComment({
                    comment: commentBoxRef.current!.value,
                    createdAt: new Date().getTime(),
                    id,
                    username: Client.activeUsername!
                });

                if (commentBoxRef.current) commentBoxRef.current!.value = "";
            }
        } catch (error) {
            displayErrorMessageOrDefault(error, "Failed to post comment.");
        }
    }, [runWork, grantId, commentBoxRef.current]);

    return <PostCommentWrapper onSubmit={submitComment}>
        <div className="wrapper">
            <UserAvatar avatar={avatar} width={"48px"} />
            <TextArea rows={3} ref={commentBoxRef} disabled={disabled} placeholder={"Your comment"} />
        </div>
        <div className="buttons">
            {disabled ? <Tooltip trigger={<Button disabled>Send</Button>}>Submit application to allow comments</Tooltip> :
                <Button disabled={loading}>Send</Button>}
        </div>
    </PostCommentWrapper>;
};

function getDocument(grantApplication: GrantApplication): Document {
    return grantApplication.currentRevision.document;
}

function ProductLink(): JSX.Element {
    return <Tooltip trigger={<ExternalLink href="/app/skus"><ProductLinkBox> ?</ProductLinkBox></ExternalLink>}>
        <Box width="100px">Click to view details for resources</Box>
    </Tooltip>;
}

const ProductLinkBox = styled.div`
    cursor: pointer;
    border: 2px var(--black) solid;
    border-radius: 9999px;
    width: 35px;
    height: 35px;
    margin-left: 9px;
    padding-left: 10px;
    margin-top: -2px;
`;

export function isGrantFinalized(status: State): boolean {
    return State.IN_PROGRESS !== status;
}

async function promptRevisionComment(): Promise<string | null> {
    try {
        return (await addStandardInputDialog({
            type: "textarea",
            title: "Revision Reason",
            validator: text => text.length !== 0,
            placeholder: "Explain the reasoning behind the revision.",
            width: "100%",
            rows: 9,
            validationFailureMessage: "Reason can't be empty."
        })).result;
    } catch {
        return null;
    }
}

function toForm(request: RequestTarget, grantApplication: GrantApplication, formText: string): {type: "plain_text", text: string;} {
    switch (request) {
        case RequestTarget.EXISTING_PROJECT:
        case RequestTarget.NEW_PROJECT:
        case RequestTarget.PERSONAL_PROJECT: {
            return {
                type: "plain_text",
                text: formText,
            };
        }
        case RequestTarget.VIEW_APPLICATION: {
            switch (grantApplication.currentRevision.document.recipient.type) {
                case "existingProject":
                case "newProject":
                case "personalWorkspace":
                default:
                    return {
                        type: "plain_text",
                        text: formText
                    };
            }
        }
    }
}

function formTextFromGrantApplication(grantApplication: GrantApplication): string {
    return grantApplication.currentRevision.document.form.text;
}

function findRequestedResources(grantProductCategories: Record<string, GrantProductCategory[]>): AllocationRequest[] {
    return Object.keys(grantProductCategories).flatMap(grantGiver =>
        grantProductCategories[grantGiver].map(wb => {
            let creditsRequested = parseIntegerFromInput(
                document.querySelector<HTMLInputElement>(
                    `input[data-target="${productCategoryId(wb.metadata.category, grantGiver)}"]`
                )
            );

            const first = document.getElementsByClassName(
                productCategoryStartDate(wb.metadata.category, grantGiver)
            )?.[0] as HTMLInputElement;
            const start = parseDateFromInput(first);

            const second = document.getElementsByClassName(
                productCategoryEndDate(wb.metadata.category, grantGiver)
            )?.[0] as HTMLInputElement;
            const end = parseDateFromInput(second);

            const allocation = document.querySelector<HTMLInputElement>(
                `input[data-target="${productCategoryAllocation(wb.metadata.category, grantGiver)}"]`
            )?.value ?? null;
            const sourceAllocation = allocation === "" ? null : allocation;

            if (creditsRequested) {
                creditsRequested = normalizeBalanceForBackend(
                    creditsRequested,
                    wb.metadata.productType, wb.metadata.chargeType, wb.metadata.unitOfPrice
                );
            }

            if (creditsRequested === undefined || creditsRequested <= 0) {
                return null;
            }

            return {
                category: wb.metadata.category.name,
                provider: wb.metadata.category.provider,
                balanceRequested: creditsRequested,
                grantGiver,
                sourceAllocation,
                period: {
                    start,
                    end: end ?? null
                }
            } as AllocationRequest;
        }).filter(it => it != null)
    ) as AllocationRequest[];
}

function canApproveApplication(grantProductCategories: Record<string, GrantProductCategory[]>): boolean {
    const projectId = Client.projectId;
    if (!projectId) return false;
    const resources = findRequestedResources(grantProductCategories);
    return resources.filter(it => it.grantGiver === projectId).every(it => it.sourceAllocation != null);
}

export default GrantApplicationEditor;
