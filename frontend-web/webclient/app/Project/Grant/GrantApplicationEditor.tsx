import * as React from "react";
import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {MainContainer} from "@/MainContainer/MainContainer";
import {ProjectBreadcrumbs} from "@/Project/Breadcrumbs";
import * as Heading from "@/ui-components/Heading";
import Box from "@/ui-components/Box";
import Button from "@/ui-components/Button";
import ButtonGroup from "@/ui-components/ButtonGroup";
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
import {apiCreate, apiRetrieve, apiUpdate, callAPI, InvokeCommand, useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {
    ProductCategoryId,
    ProductMetadata,
    Product,
    productCategoryEquals,
    usageExplainer,
    productTypeToIcon,
    productTypeToTitle,
    productTypes,
    explainAllocation,
    normalizeBalanceForBackend,
    browseWallets,
    Wallet,
    WalletAllocation,
    normalizeBalanceForFrontend,
    ProductType
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
import ProjectWithTitle = UCloud.grant.ProjectWithTitle;
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {TextSpan} from "@/ui-components/Text";
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
    GrantApplication,
    AllocationRequest,
    Comment,
    State,
    deleteGrantApplicationComment,
    Recipient,
    browseAffiliations,
    commentOnGrantApplication,
    Document,
    transferApplication,
    GrantProductCategory,
    FetchGrantApplicationRequest,
    FetchGrantApplicationResponse,
    editReferenceId,
    fetchProducts,
} from "./GrantApplicationTypes";
import {useAvatars} from "@/AvataaarLib/hook";
import {ProjectRole, UserInProject, viewProject} from "..";
import {displayErrorMessageOrDefault} from "@/UtilityFunctions";
import {Client} from "@/Authentication/HttpClientInstance";

export enum RequestTarget {
    EXISTING_PROJECT = "existing_project",
    NEW_PROJECT = "new_project",
    PERSONAL_PROJECT = "personal_workspace",
    VIEW_APPLICATION = "view"
}

/* 
    TODO List:
        - Improve allocation selection UI.
        - Find out of an approval can be rescinded. (Same for rejection.)
*/

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

function productCategoryId(pid: ProductCategoryId): string {
    return `${pid.name}/${pid.provider}`;
}

function productCategoryStartDate(pid: ProductCategoryId): string {
    return `${productCategoryId(pid)}/start_date`;
}

function productCategoryEndDate(pid: ProductCategoryId): string {
    return `${productCategoryId(pid)}/end_date`;
}

function productCategoryAllocation(pid: ProductCategoryId): string {
    return `${productCategoryId(pid)}/allocation_id`;
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

/* FIXME(Jonas): Copy + pasted from elsewhere (MachineType dropdown) */
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

// TODO(Jonas): Find better name for function. 
function checkIsGrantRecipient(target: RequestTarget, grantApplication: GrantApplication): boolean {
    if (target !== RequestTarget.VIEW_APPLICATION) return true;
    // TODO(Jonas): So checking if active user is "createdBy" could work, but shouldn't admins and PIs also work for a grantApplication?
    return grantApplication.createdBy === Client.username;
}

const GenericRequestCard: React.FunctionComponent<{
    wb: GrantProductCategory;
    grantFinalized: boolean;
    isLocked: boolean;
    isApprover: boolean;
    isRecipient: boolean;
    showAllocationSelection: boolean;
    wallets: Wallet[];
    allocationRequests: AllocationRequest[];
}> = ({wb, grantFinalized, isLocked, wallets, ...props}) => {
    const [startDate, setStartDate] = useState<Date>(getStartOfDay(new Date()));
    const [endDate, setEndDate] = useState<Date | null>(null);

    const canEdit = props.isApprover || props.isRecipient;

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
                                data-target={"checkbox-" + productCategoryId(wb.metadata.category)}
                                onChange={e => {
                                    const checkbox = e.target as HTMLInputElement;
                                    const input = document.querySelector(
                                        `input[data-target="${productCategoryId(wb.metadata.category)}"]`
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
                    disabled={grantFinalized || isLocked || !props.isApprover}
                    data-target={productCategoryId(wb.metadata.category)}
                    autoComplete="off"
                    type="hidden"
                    min={0}
                />
            </HighlightedCard>
        </RequestForSingleResourceWrapper>;
    } else {
        const defaultValue = props.allocationRequests.find(it => it.provider === wb.metadata.category.provider && it.category === wb.metadata.category.name)?.balanceRequested;
        const normalizedValue = defaultValue != null ? normalizeBalanceForFrontend(defaultValue, wb.metadata.productType, wb.metadata.chargeType, wb.metadata.unitOfPrice, false) : undefined;
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
                                        data-target={productCategoryId(wb.metadata.category)}
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
                                        required={props.isApprover}
                                        value={format(startDate, "dd/MM/yy")}
                                        disabled={grantFinalized || isLocked || !canEdit}
                                        onChange={(date: Date) => setStartDate(date)}
                                        className={productCategoryStartDate(wb.metadata.category)}
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
                                        className={productCategoryEndDate(wb.metadata.category)}
                                    />
                                </Flex>
                            </td>
                        </tr>
                    </tbody>
                </table>
                {props.showAllocationSelection ?
                    <AllocationSelection wallets={wallets} wb={wb} isLocked={isLocked} /> : null
                }
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

function AllocationSelection({wallets, wb, isLocked}: {
    wallets: Wallet[];
    wb: GrantProductCategory;
    isLocked: boolean;
}): JSX.Element {
    const [allocation, setAllocation] = useState<{wallet: Wallet; allocation: WalletAllocation;} | undefined>(undefined);
    const allocationText = allocation ? `${allocation.wallet.paysFor.provider} @ ${allocation.wallet.paysFor.name} [${allocation.allocation.id}]` : "";
    return <Box mb="8px" ml="6px">
        <HiddenInputField value={allocation ? allocation.allocation.id : ""} onChange={() => undefined} data-target={productCategoryAllocation(wb.metadata.category)} />
        {!isLocked ?
            <ClickableDropdown colorOnHover={false} width="677px" useMousePositioning trigger={<AllocationBox>
                {!allocation ?
                    <>No allocation selected <Icon name="chevronDownLight" size="1em" mt="4px" /></> :
                    <>{allocationText}<Icon name="chevronDownLight" size="1em" mt="4px" /></>}
            </AllocationBox>
            }>
                <Table>
                    <TableHeader>
                        <TableHeaderCell width="200px">Provider</TableHeaderCell>
                        <TableHeaderCell width="200px">Name</TableHeaderCell>
                        <TableHeaderCell width="200px">Balance</TableHeaderCell>
                        <TableHeaderCell width="45px">ID</TableHeaderCell>
                    </TableHeader>
                    <tbody>
                        {wallets.map(w =>
                            <AllocationRows
                                key={w.paysFor.provider + w.paysFor.name + w.paysFor.title}
                                wallet={w}
                                onClick={(wallet, allocation) => setAllocation({wallet, allocation})}
                            />
                        )}
                    </tbody>
                </Table>
            </ClickableDropdown> : allocation ? allocationText : "No allocation selected"
        }
    </Box >;
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

type ApproverMap = Record<string, boolean>;
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


type GrantApplicationReducerAction = FetchedGrantApplication | UpdatedReferenceID | UpdatedParentProjectID | PostedComment;
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
        case POSTED_COMMENT: {
            state.status.comments.push(action.payload);
            return {...state};
        }
    }
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
        const avatars = useAvatars();

        const [grantGivers, fetchGrantGivers] = useCloudAPI<GrantsRetrieveAffiliationsResponse>(browseAffiliations({itemsPerPage: 250}), emptyPage);

        const {appId} = useParams<{appId?: string;}>();
        const documentRef = useRef<HTMLTextAreaElement>(null);

        const [isLocked, setIsLocked] = useState<boolean>(target === RequestTarget.VIEW_APPLICATION);

        const [isEditingProjectReferenceId, setIsEditingProjectReference] = useState(false);

        const [grantProductCategories, setGrantProductCategories] = useState<{[key: string]: GrantProductCategory[];}>({});

        const [submitLoading, setSubmissionsLoading] = React.useState(false);
        const [grantGiversInUse, setGrantGiversInUse] = React.useState<string[]>([]);
        const [transferringApplication, setTransferringApplication] = useState(false);
        const [approverMap, setApprovers] = useState<ApproverMap>({});

        const [walletsByOwner, setWallets] = useState<Record<string, UCloud.PageV2<Wallet>>>({});
        React.useEffect(() => {
            const requests = grantGivers.data.items.map(it => ({...browseWallets({itemsPerPage: 250}), projectOverride: it.projectId}));
            Promise.allSettled(requests.map(r => runWork<UCloud.PageV2<Wallet>>(r))).then(resolvedPromises => {
                const result: Record<string, UCloud.PageV2<Wallet>> = {};
                for (const [index, promise] of resolvedPromises.entries()) {
                    if (promise.status === "fulfilled" && promise.value != null) {
                        result[requests[index].projectOverride] = promise.value;
                    }
                };
                setWallets(result);
            });
        }, [grantGivers.data]);

        const [grantApplication, dispatch] = React.useReducer(grantApplicationReducer, defaultGrantApplication, () => defaultGrantApplication);

        React.useEffect(() => {
            if (documentRef.current) {
                documentRef.current.value = formTextFromGrantApplication(grantApplication);
            }
        }, [grantApplication, documentRef.current]);

        React.useEffect(() => {
            if (appId) {
                fetchGrantApplication({id: appId}).then(g =>
                    dispatch({
                        type: "FETCHED_GRANT_APPLICATION", payload: g
                    })
                );
            };
        }, [appId]);

        React.useEffect(() => {
            if (grantApplication.status.stateBreakdown.length > 0) {
                const approvers: ApproverMap = {};
                const promises = Promise.allSettled(grantApplication.status.stateBreakdown.map(p =>
                    runWork<UserInProject>(viewProject({
                        id: p.projectId,
                    }))
                ));
                promises.then(resolvedPromises => {
                    for (const [index, promise] of resolvedPromises.entries()) {
                        if (promise.status === "fulfilled") {
                            approvers[grantApplication.status.stateBreakdown[index].projectId] = (
                                promise.value != null && [ProjectRole.ADMIN, ProjectRole.PI].includes(promise.value.whoami.role)
                            );
                        }
                    }
                    setApprovers(approvers);
                });
            }
        }, [grantApplication.status.stateBreakdown]);

        const isRecipient = checkIsGrantRecipient(target, grantApplication);

        // Note(Jonas): Almost entirely a duplicate of the submitApplication-function. Improve it.
        const editApplication = useCallback(async () => {
            setSubmissionsLoading(true);

            const requestedResourcesByAffiliate = Object.keys(grantProductCategories).flatMap(entry =>
                grantProductCategories[entry].map(wb => {
                    let creditsRequested = parseIntegerFromInput(
                        document.querySelector<HTMLInputElement>(
                            `input[data-target="${productCategoryId(wb.metadata.category)}"]`
                        )
                    );

                    const first = document.getElementsByClassName(
                        productCategoryStartDate(wb.metadata.category)
                    )?.[0] as HTMLInputElement;
                    const start = parseDateFromInput(first);

                    const second = document.getElementsByClassName(
                        productCategoryEndDate(wb.metadata.category)
                    )?.[0] as HTMLInputElement;
                    const end = parseDateFromInput(second);

                    const allocation = document.querySelector<HTMLInputElement>(
                        `input[data-target="${productCategoryAllocation(wb.metadata.category)}"]`
                    );

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
                        grantGiver: entry,
                        sourceAllocation: allocation?.value ?? null, // TODO(Jonas): null on initial request, required on the following.
                        period: {
                            start,
                            end: end ?? null
                        }
                    } as AllocationRequest;
                }).filter(it => it != null)
            ) as AllocationRequest[];

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

                const revisionComment = await promptRevisionComment();
                if (revisionComment == null) {
                    setSubmissionsLoading(false);
                    return;
                }

                const documentToSubmit: Document = {
                    referenceId: grantApplication.currentRevision.document.referenceId,
                    revisionComment,
                    recipient: grantApplication.currentRevision.document.recipient,
                    allocationRequests: requestedResourcesByAffiliate,
                    form: toForm(target, grantApplication, formText),
                    parentProjectId: grantApplication.currentRevision.document.parentProjectId,
                };

                const result = await runWork<[id: string]>(apiUpdate(bulkRequestOf({document: documentToSubmit}), "grant", "submit-application"));
                if (result != null) history.push(`/project/grants/view/${result[0]}`);
            } catch (error) {
                displayErrorMessageOrDefault(error, "Failed to submit application.");
            }
            setSubmissionsLoading(false);
        }, [grantGiversInUse, grantProductCategories, grantApplication]); // TODO(Jonas): Find out which are actually needed.

        const submitRequest = useCallback(async () => {
            setSubmissionsLoading(true);

            if (grantGiversInUse.length === 0) {
                snackbarStore.addFailure("No grant giver selected. Please select at least one to submit.", false);
                setSubmissionsLoading(false);
                return;
            }

            const requestedResourcesByAffiliate = Object.keys(grantProductCategories).flatMap(entry =>
                grantProductCategories[entry].map(wb => {
                    let creditsRequested = parseIntegerFromInput(
                        document.querySelector<HTMLInputElement>(
                            `input[data-target="${productCategoryId(wb.metadata.category)}"]`
                        )
                    );

                    const first = document.getElementsByClassName(
                        productCategoryStartDate(wb.metadata.category)
                    )?.[0] as HTMLInputElement;
                    const start = parseDateFromInput(first);

                    const second = document.getElementsByClassName(
                        productCategoryEndDate(wb.metadata.category)
                    )?.[0] as HTMLInputElement;
                    const end = parseDateFromInput(second);

                    const allocation = document.querySelector<HTMLInputElement>(
                        `input[data-target="${productCategoryAllocation(wb.metadata.category)}"]`
                    );

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
                        grantGiver: entry,
                        sourceAllocation: allocation?.value ?? null, // TODO(Jonas): null on initial request, required on the following.
                        period: {
                            start,
                            end: end ?? null
                        }
                    } as AllocationRequest;
                }).filter(it => it != null)
            ) as AllocationRequest[];

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
                        id: Client.projectId ?? "", // TODO(Jonas): Is this the correct one?
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
                        // TODO(Jonas): Ensure that this is the correct username.
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
                const [{id}] = await runWork(apiCreate(payload, "/api/grant", "submit-application"));
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
            // TODO(Jonas): This is call seems to be "dead". I think submitting the grantApplication with the new ID is the way to go.
            await runWork(editReferenceId({id: grantApplication.id, newReferenceId: value}));
            setIsEditingProjectReference(false);
            dispatch({type: "UPDATE_REFERENCE_ID", payload: {referenceId: value}});
        }, []);

        const cancelEditOfRefId = useCallback(async () => {
            setIsEditingProjectReference(false);
        }, []);

        const approveRequest = useCallback(async () => {
            await runWork(updateState({applicationId: grantApplication.id, newState: State.APPROVED, notify: true}));
        }, [grantApplication.id]);

        const rejectRequest = useCallback(async (notify: boolean) => {
            await runWork(updateState({applicationId: grantApplication.id, newState: State.REJECTED, notify: notify}));
        }, [grantApplication.id]);

        const transferRequest = useCallback(async (toProjectId: string) => {
            await runWork(transferApplication({
                applicationId: grantApplication.id,
                transferToProjectId: toProjectId
            }));
        }, [grantApplication]);

        const closeRequest = useCallback(async () => {
            // TODO(Jonas): Still exists in the backend. Request wants 
            //await runWork(UCloud.grant.grant.closeApplication({requestId: appId}));
        }, [appId]);

        const reload = useCallback(() => {
            fetchGrantGivers(browseAffiliations({itemsPerPage: 250}));
            if (appId) {fetchGrantApplication({id: appId}).then(g => dispatch({type: FETCHED_GRANT_APPLICATION, payload: g}));};
            // fetchWallets(browseWallets({itemsPerPage: 250}));
        }, [appId]);

        const discardChanges = useCallback(() => {
            setIsLocked(true);
        }, [reload]);

        React.useEffect(() => {
            if (
                grantApplication.currentRevision.document.parentProjectId &&
                grantGiversInUse.includes(grantApplication.currentRevision.document.parentProjectId)
            ) return;
            const [firstGrantGiver] = grantGiversInUse;
            if (firstGrantGiver != null) dispatch({type: "UPDATE_PARENT_PROJECT_ID", payload: {parentProjectId: firstGrantGiver}});
        }, [grantGiversInUse, grantApplication]);

        const grantFinalized = isGrantFinalized(grantApplication.status.overallState);

        const isAnyApprover = Object.keys(approverMap).length > 0;

        const grantGiverDropdown = React.useMemo(() =>
            target === RequestTarget.VIEW_APPLICATION || (grantGivers.data.items.length - grantGiversInUse.length) === 0 ? null :
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
                                {grantGivers.data.items.filter(grantGiver => !grantGiversInUse.includes(grantGiver.projectId)).map(grantGiver =>
                                    <TableRow cursor="pointer" key={grantGiver.projectId} onClick={() => setGrantGiversInUse(inUse => [...inUse, grantGiver.projectId])}>
                                        <TableCell pl="6px"><Flex><Logo projectId={grantGiver.projectId} size="32px" /><Text mt="3px" ml="8px">{grantGiver.title}</Text></Flex></TableCell>
                                        <TableCell pl="6px"><GrantGiverDescription key={grantGiver.projectId} projectId={grantGiver.projectId} /></TableCell>
                                    </TableRow>
                                )}
                            </tbody>
                        </Table>
                    </Wrapper>
                </ClickableDropdown>,
            [grantGivers, grantGiversInUse]);

        const grantGiverEntries = React.useMemo(() =>
            grantGivers.data.items.filter(it => grantGiversInUse.includes(it.projectId)).map(it =>
                <GrantGiver
                    key={it.projectId}
                    grantApplication={grantApplication}
                    remove={() => setGrantGiversInUse(inUse => [...inUse.filter(entry => entry !== it.projectId)])}
                    setGrantProductCategories={setGrantProductCategories}
                    wallets={walletsByOwner[it.projectId]?.items ?? []}
                    project={it}
                    isParentProject={getDocument(grantApplication).parentProjectId === it.projectId}
                    setParentProject={parentProjectId => dispatch({type: "UPDATE_PARENT_PROJECT_ID", payload: {parentProjectId}})}
                    isLocked={isLocked}
                    isRecipient={isRecipient}
                    isApprover={approverMap[it.projectId]}
                />
            ), [grantGivers, isRecipient, grantGiversInUse, isLocked, walletsByOwner, grantApplication, isRecipient]);

        const recipient = getDocument(grantApplication).recipient;
        const recipientName = useRecipientName(getDocument(grantApplication).recipient);

        return (
            <MainContainer
                header={target === RequestTarget.EXISTING_PROJECT ?
                    <ProjectBreadcrumbs crumbs={[{title: "Request for Resources"}]} /> : null
                }
                sidebar={null}
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
                                                                <Text>{state.title}</Text><StateIcon state={state.state} />
                                                            </Flex>)}
                                                        </Box>
                                                    </TableCell>
                                                </TableRow>
                                                <TableRow>
                                                    <TableCell>Project Title</TableCell>
                                                    <TableCell>{recipientName}</TableCell>
                                                </TableRow>
                                                <TableRow>
                                                    <TableCell>Principal Investigator (PI)</TableCell>
                                                    <TableCell>{"TODO: grantRecipient PI"}</TableCell>
                                                </TableRow>
                                                <TableRow>
                                                    <TableCell verticalAlign="top">
                                                        Project Type
                                                    </TableCell>
                                                    <TableCell>
                                                        <td>{recipientTypeToText(recipient)}</td>
                                                    </TableCell>
                                                </TableRow>
                                                <TableRow>
                                                    <TableCell verticalAlign="top">
                                                        Reference ID
                                                    </TableCell>
                                                    {/* TODO(Jonas): When should this be shown? Aprrover? */}
                                                    <TableCell>
                                                        <table>
                                                            <tbody>
                                                                <tr>
                                                                    {isEditingProjectReferenceId ? (
                                                                        <>
                                                                            <td>
                                                                                <Input
                                                                                    placeholder={"e.g. DeiC-SDU-L1-000001"}
                                                                                    ref={projectReferenceIdRef}
                                                                                />
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
                                                        {   /* TODO(Jonas): Maybe not correct enum? */
                                                            overallStateText(grantApplication)
                                                        }
                                                        <ButtonGroup>
                                                            {target !== RequestTarget.VIEW_APPLICATION && Object.keys(approverMap).length > 0 != null ? null : (
                                                                <>

                                                                    {/* We have the following buttons that we need:
                                                                            - Approve (Should be per grant giver, not for every one.)
                                                                            - Reject (Does notify still make sense? Again, for each).
                                                                            - Transfer application
                                                                            - Close Request (from the view of the recipient, so only if creator of it.)
                                                                    */}
                                                                    {isAnyApprover && !grantFinalized ?
                                                                        <>
                                                                            <Button
                                                                                color="green"
                                                                                onClick={approveRequest}
                                                                                disabled={!isLocked}
                                                                            >
                                                                                Approve
                                                                            </Button>
                                                                            <ClickableDropdown
                                                                                top="-73px"
                                                                                fullWidth={true}
                                                                                trigger={(
                                                                                    <Button
                                                                                        color="red"
                                                                                        disabled={!isLocked}
                                                                                        onClick={() => undefined}
                                                                                    >
                                                                                        Reject
                                                                                    </Button>
                                                                                )}
                                                                            >
                                                                                <OptionItem
                                                                                    onClick={() => rejectRequest(true)}
                                                                                    text={"Reject"}
                                                                                />
                                                                                <OptionItem
                                                                                    onClick={() => rejectRequest(false)}
                                                                                    text={"Reject without notify"}
                                                                                />
                                                                            </ClickableDropdown>
                                                                            {recipient.type !== "existingProject" && localStorage.getItem("enableprojecttransfer") != null ?
                                                                                <Button
                                                                                    color="blue"
                                                                                    onClick={() => setTransferringApplication(true)}
                                                                                    disabled={!isLocked}
                                                                                >
                                                                                    Transfer to other project
                                                                                </Button> : null
                                                                            }
                                                                        </> : null
                                                                    }
                                                                    {isRecipient && !grantFinalized ?
                                                                        <>
                                                                            <Button
                                                                                color="red"
                                                                                onClick={closeRequest}
                                                                                disabled={!isLocked}
                                                                            >
                                                                                Withdraw
                                                                            </Button>
                                                                        </> : null
                                                                    }
                                                                </>
                                                            )}
                                                        </ButtonGroup>
                                                        {target !== RequestTarget.VIEW_APPLICATION || isLocked ||
                                                            grantFinalized ? null :
                                                            <Text>
                                                                You must finish making changes before you can
                                                                change the status of this application
                                                            </Text>
                                                        }
                                                    </TableCell>
                                                </TableRow>
                                            </tbody>
                                        </Table>
                                    </HighlightedCard>
                                </>
                            )}

                            <Heading.h1 mt={32} mb={12}>
                                {target === RequestTarget.VIEW_APPLICATION ? "Requested Resources" : "Resources"}
                            </Heading.h1>

                            {target === RequestTarget.VIEW_APPLICATION ? null : grantGiverDropdown}
                            {target === RequestTarget.VIEW_APPLICATION ? null : grantGiverEntries}
                            {/* Note(Jonas): This is for the grant givers that are part of an existing grant application */}
                            {target !== RequestTarget.VIEW_APPLICATION ? null : (
                                grantGivers.data.items.filter(it => grantApplication.status.stateBreakdown.map(it => it.projectId).includes(it.projectId)).map(grantGiver => <>
                                    <GrantGiver
                                        key={grantGiver.projectId}
                                        isApprover={approverMap[grantGiver.projectId]}
                                        isLocked={isLocked}
                                        project={grantGiver}
                                        setParentProject={parentProjectId => dispatch({type: "UPDATE_PARENT_PROJECT_ID", payload: {parentProjectId}})}
                                        isParentProject={grantGiver.projectId === getDocument(grantApplication).parentProjectId}
                                        grantApplication={grantApplication}
                                        setGrantProductCategories={setGrantProductCategories}
                                        wallets={walletsByOwner[grantGiver.projectId]?.items ?? []}
                                        isRecipient={isRecipient}
                                    />
                                </>))}

                            <CommentApplicationWrapper>
                                <RequestFormContainer>
                                    <Heading.h4>Application</Heading.h4>
                                    <TextArea
                                        disabled={grantFinalized || isLocked}
                                        rows={25}
                                        ref={documentRef}
                                    />
                                </RequestFormContainer>

                                <Box width="100%">
                                    <Heading.h4>Comments</Heading.h4>
                                    {grantApplication.status.comments.length > 0 ? null : (
                                        <Box mt={16} mb={16}>
                                            No comments have been posted yet.
                                        </Box>
                                    )}

                                    {grantApplication.status.comments.map(it => (
                                        <CommentBox
                                            key={it.id}
                                            comment={it}
                                            avatar={avatars.cache[it.username] ?? defaultAvatar}
                                            reload={reload}
                                        />
                                    ))}

                                    <PostCommentWidget
                                        applicationId={grantApplication.currentRevision.document.referenceId ?? "" /* TODO(Jonas): Is this the samme as .id from before? */}
                                        avatar={avatars.cache[Client.username!] ?? defaultAvatar}
                                        onPostedComment={comment => dispatch({type: "POSTED_COMMENT", payload: comment})}
                                        disabled={target !== RequestTarget.VIEW_APPLICATION}
                                    />
                                </Box>
                            </CommentApplicationWrapper>
                            <Box p={32} pb={16}>
                                {target !== RequestTarget.VIEW_APPLICATION ? (
                                    <Button disabled={grantFinalized || submitLoading} fullWidth onClick={submitRequest}>
                                        Submit Application
                                    </Button>
                                ) : null}
                                {target !== RequestTarget.VIEW_APPLICATION || grantFinalized ? null : (
                                    isLocked ? (
                                        <Button fullWidth onClick={() => setIsLocked(false)} disabled={loading}>
                                            Edit this request
                                        </Button>
                                    ) : (
                                        <ButtonGroup>
                                            <Button
                                                color={"green"}
                                                fullWidth
                                                disabled={loading}
                                                onClick={editApplication}
                                            >
                                                Save Changes
                                            </Button>
                                            <Button color={"red"} onClick={discardChanges}>Discard changes</Button>
                                        </ButtonGroup>
                                    )
                                )}
                            </Box>
                        </Box>
                    </Flex>
                </>}
                additional={
                    <TransferApplicationPrompt
                        isActive={transferringApplication}
                        close={() => setTransferringApplication(false)}
                        transfer={transferRequest}
                        grantId={getReferenceId(grantApplication) ?? ""}
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
    return <Icon ml="8px" name={icon} color={color} />;
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

function useRecipientName(recipient: Recipient): string {
    const [recipientName, setName] = useState(
        recipient.type === "existingProject" ? recipient.id :
            recipient.type === "newProject" ? recipient.title :
                recipient.type === "personalWorkspace" ? recipient.username : ""
    );
    const [, invokeCommand] = useCloudCommand();
    useEffect(() => {
        if (recipient.type === "existingProject") {
            invokeCommand(viewProject({id: recipient.id})).then(r => setName(r.title)).catch(e => setName(""));
        }
    }, [recipient]);
    return recipientName;
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
    switch (grantApplication.status.overallState) {
        case State.IN_PROGRESS:
            return "In progress";
        case State.CLOSED:
            return "Closed";
        case State.APPROVED:
            return grantApplication.currentRevision.updatedBy === null ? "Approved" : "Approved by " + grantApplication.currentRevision.updatedBy;
        case State.REJECTED:
            return grantApplication.currentRevision.updatedBy === null ? "Rejected" : "Rejected  by " + grantApplication.currentRevision.updatedBy;
    }
}

function GrantGiver(props: {
    grantApplication: GrantApplication;
    project: ProjectWithTitle;
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

    /* TODO(Jonas): Why is this done over two parts instead of one?  */
    const [products, setProducts] = useState<{availableProducts: Product[];}>({availableProducts: []});
    React.useEffect(() => {
        fetchProducts({
            projectId: props.project.projectId,
            recipientType: recipient.type,
            recipientId,
            showHidden: false
        }).then(it => setProducts(it));
    }, []);

    const productCategories: GrantProductCategory[] = useMemo(() => {
        const result: GrantProductCategory[] = [];
        for (const product of products.availableProducts) {
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

    return React.useMemo(() =>
        <Box mt="12px">
            <Spacer
                left={
                    <>
                        <Heading.h2>{props.project.title}</Heading.h2>
                        {!props.isParentProject && props.isLocked ? null :
                            <Tooltip trigger={
                                <ParentProjectIcon
                                    onClick={() => props.isLocked ? null : props.setParentProject(props.project.projectId)}
                                    cursor={props.isLocked ? undefined : "pointer"} isSelected={props.isParentProject} name="check"
                                />
                            }>
                                {props.isParentProject ? "Selected as parent project." : (
                                    !props.isLocked ? "Click to select as parent project" :
                                        "Not selected as parent project.")}
                            </Tooltip>
                        }
                    </>
                }
                right={props.remove ? <Flex cursor="pointer" onClick={props.remove}>
                    <Icon name="close" color="red" mr="8px" />Remove
                </Flex> : null}
            />
            {productTypes.map(type => <AsProductType
                key={type}
                isApprover={props.isApprover}
                type={type}
                grantApplication={props.grantApplication}
                wallets={props.wallets}
                productCategories={productCategories}
                isLocked={props.isLocked}
                isRecipient={props.isRecipient}
            />)}
        </Box>, [productCategories, props.wallets, props.isRecipient, props.grantApplication, props.isParentProject, props.isApprover, props.isLocked]);
}

function AsProductType(props: {
    type: ProductType;
    productCategories: GrantProductCategory[];
    wallets: Wallet[];
    isRecipient: boolean;
    isApprover: boolean;
    isLocked: boolean;
    grantApplication: GrantApplication;
}): JSX.Element {
    const grantFinalized = isGrantFinalized(props.grantApplication.status.overallState);
    const filteredProductCategories = props.productCategories.filter(pc => pc.metadata.productType === props.type);
    const noEntries = filteredProductCategories.length === 0;
    const {allocationRequests} = props.grantApplication.currentRevision.document;
    const filteredWallets = props.wallets.filter(it => it.productType === props.type);
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
                    allocationRequests={allocationRequests.filter(it =>
                        it.category === pc.metadata.category.name &&
                        it.provider === pc.metadata.category.provider
                    )}
                    isRecipient={props.isRecipient}
                    wallets={filteredWallets}
                    showAllocationSelection={props.isApprover}
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
    grantId: string;

    close(): void;

    transfer(toProjectId: string): Promise<void>;
}

function TransferApplicationPrompt({isActive, close, transfer, grantId}: TransferApplicationPromptProps) {
    const [projects, fetchProjects] = useCloudAPI<GrantsRetrieveAffiliationsResponse>({noop: true}, emptyPage);

    const history = useHistory();

    React.useEffect(() => {
        if (grantId) {
            fetchProjects(browseAffiliations({itemsPerPage: 100}));
        }
    }, [grantId]);

    const dispatch = useDispatch();

    return (
        <ReactModal
            style={defaultModalStyle}
            isOpen={isActive}
            onRequestClose={close}
            shouldCloseOnEsc
            shouldCloseOnOverlayClick
        >
            <List>
                {!projects.loading && projects.data.items.length === 0 ? <Heading.h3>No projects found.</Heading.h3> : null}
                {projects.data.items.map(it =>
                    <Spacer
                        key={it.projectId}
                        left={<Box key={it.projectId}>{it.title}</Box>}
                        right={<>
                            <ConfirmationButton
                                my="3px"
                                width="115px"
                                height="40px"
                                onAction={async () => {
                                    close();
                                    // Show that we are transferring
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

const OptionItem: React.FunctionComponent<{onClick: () => void; text: string; color?: string;}> = props => (
    <Box cursor="pointer" width="auto" onClick={props.onClick}>
        <TextSpan color={props.color}>{props.text}</TextSpan>
    </Box>
);

const CommentApplicationWrapper = styled.div`
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(600px, 1fr));
    grid-gap: 32px;
    max-width: 1400px;
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
}> = ({comment, avatar, reload}) => {
    const [, runCommand] = useCloudCommand();
    const onDelete = useCallback(() => {
        addStandardDialog({
            title: "Confirm comment deletion",
            message: "Are you sure you wish to delete your comment?",
            confirmText: "Delete",
            addToFront: true,
            onConfirm: async () => {
                await runCommand(deleteGrantApplicationComment({commentId: comment.id}));
                reload();
            }
        });
    }, [comment.id]);

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

function isAnyAllocationsOfParentProject(allocations: AllocationRequest[], parentProject: string) {
    return allocations.find(it => it.grantGiver === parentProject) != null;
}

function updateState(request: UpdateStateRequest): APICallParameters<UpdateStateRequest> {
    return apiUpdate(request, "grant", "update-state");
}

const PostCommentWidget: React.FunctionComponent<{
    applicationId: string,
    avatar: AvatarType,
    onPostedComment(comment: Comment): void;
    disabled: boolean;
}> = ({applicationId, avatar, onPostedComment, disabled}) => {
    const commentBoxRef = useRef<HTMLTextAreaElement>(null);
    const [loading, runWork] = useCloudCommand();
    const submitComment = useCallback(async (e) => {
        e.preventDefault();
        if (disabled) return;
        try {
            const id = await runWork<string>(commentOnGrantApplication({
                requestId: applicationId,
                comment: commentBoxRef.current!.value
            }), {defaultErrorHandler: false});

            onPostedComment({
                comment: commentBoxRef.current!.value,
                createdAt: new Date().getTime(),
                id: id!,
                username: Client.activeUsername!
            });

            if (commentBoxRef.current) commentBoxRef.current!.value = "";
        } catch (error) {
            displayErrorMessageOrDefault(error, "Failed to post comment.");
        }
    }, [runWork, applicationId, commentBoxRef.current]);

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
    return State.IN_PROGRESS != status;
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

export default GrantApplicationEditor;
