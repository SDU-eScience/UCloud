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
import Icon from "@/ui-components/Icon";
import Input, {HiddenInputField} from "@/ui-components/Input";
import Label from "@/ui-components/Label";
import List from "@/ui-components/List";
import Checkbox from "@/ui-components/Checkbox";
import Text from "@/ui-components/Text";
import TextArea from "@/ui-components/TextArea";
import theme from "@/ui-components/theme";
import Tooltip from "@/ui-components/Tooltip";
import {useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {
    ProductCategoryId,
    ProductMetadata,
    Product,
    productCategoryEquals,
    usageExplainer,
    productTypeToIcon,
    ProductType,
    productTypeToTitle,
    productTypes,
    explainAllocation,
    normalizeBalanceForBackend,
    normalizeBalanceForFrontendOpts,
    browseWallets,
    Wallet
} from "@/Accounting";
import styled from "styled-components";
import HighlightedCard from "@/ui-components/HighlightedCard";
import {
    GrantsRetrieveAffiliationsResponse,
    isGrantFinalized,
    retrieveDescription,
    RetrieveDescriptionResponse,
} from "@/Project/Grant/index";
import {useHistory, useParams} from "react-router";
import {Client} from "@/Authentication/HttpClientInstance";
import {dateToString, getStartOfDay} from "@/Utilities/DateUtilities";
import {UserAvatar} from "@/AvataaarLib/UserAvatar";
import {AvatarType, defaultAvatar} from "@/UserSettings/Avataaar";
import Table, {TableCell, TableHeader, TableHeaderCell, TableRow} from "@/ui-components/Table";
import {addStandardDialog} from "@/UtilityComponents";
import {setLoading, useTitle} from "@/Navigation/Redux/StatusActions";
import {useDispatch} from "react-redux";
import * as UCloud from "@/UCloud";
import grantApi = UCloud.grant.grant;
import ProjectWithTitle = UCloud.grant.ProjectWithTitle;
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {TextSpan} from "@/ui-components/Text";
import {default as ReactModal} from "react-modal";
import {defaultModalStyle} from "@/Utilities/ModalUtilities";
import {emptyPage, emptyPageV2} from "@/DefaultObjects";
import {Spacer} from "@/ui-components/Spacer";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";
import {buildQueryString} from "@/Utilities/URIUtilities";
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
    findAffiliations,
    commentOnGrantApplication
} from "./GrantApplicationTypes";
import {useAvatars} from "@/AvataaarLib/hook";

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

function browseProjects(request: UCloud.PaginationRequestV2): APICallParameters {
    return {
        method: "GET",
        context: "",
        path: buildQueryString("/api/grant/browse-projects", request),
        parameters: request
    };
}

export enum RequestTarget {
    EXISTING_PROJECT = "existing",
    NEW_PROJECT = "new",
    PERSONAL_PROJECT = "personal",
    VIEW_APPLICATION = "view"
}

interface GrantProductCategory {
    metadata: ProductMetadata;
    currentBalance?: number;
    requestedBalance?: number;
}

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

function parseDateFromInput(input?: HTMLInputElement | null): number | undefined {
    if (!input) return undefined;
    const rawValue = input.value;
    const [day, month, year] = rawValue.split("/");
    const d = getStartOfDay(new Date());
    d.setDate(parseInt(day, 10));
    d.setMonth(parseInt(month, 10) - 1);
    // TODO(Jonas): Instead, just add '20' to the string here, so it isn't 20xx but just xx in the 
    d.setFullYear(parseInt(`20${year}`, 10));
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


const GenericRequestCard: React.FunctionComponent<{
    wb: GrantProductCategory;
    grantFinalized: boolean;
    isLocked: boolean;
    showAllocationSelection: boolean;
    wallets: Wallet[];
}> = ({wb, grantFinalized, isLocked, wallets}) => {
    const [startDate, setStartDate] = useState<Date>(getStartOfDay(new Date()));
    const [endDate, setEndDate] = useState<Date | null>(null);

    if (wb.metadata.freeToUse) {
        return <RequestForSingleResourceWrapper>
            <HighlightedCard color="blue" isLoading={false}>
                <Flex flexDirection="row" alignItems="center">
                    <Box flexGrow={1}>
                        <Label>
                            <Checkbox
                                size={32}
                                defaultChecked={wb.requestedBalance !== undefined && wb.requestedBalance > 0}
                                disabled={grantFinalized || isLocked}
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
                    disabled={grantFinalized || isLocked}
                    data-target={productCategoryId(wb.metadata.category)}
                    autoComplete="off"
                    type="hidden"
                    min={0}
                />
            </HighlightedCard>
        </RequestForSingleResourceWrapper>
    } else {
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
                                        disabled={grantFinalized || isLocked}
                                        data-target={productCategoryId(wb.metadata.category)}
                                        autoComplete="off"
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
                                        py="8px"
                                        borderWidth="2px"
                                        required={false /* TODO state.approver */}
                                        disabled={grantFinalized || isLocked}
                                        mr="3px"
                                        backgroundColor={isLocked ? "var(--lightGray)" : undefined}
                                        placeholderText="Start date..."
                                        value={format(startDate, "dd/MM/yy")}
                                        onChange={(date: Date | null) => setStartDate(date!)}
                                        className={productCategoryStartDate(wb.metadata.category)}
                                    />
                                    <DatePicker
                                        selectsEnd
                                        isClearable={endDate != null}
                                        borderWidth="2px"
                                        py="8px"
                                        ml="3px"
                                        backgroundColor={isLocked ? "var(--lightGray)" : undefined}
                                        placeholderText={isLocked ? undefined : "End date..."}
                                        value={endDate ? format(endDate, "dd/MM/yy") : undefined}
                                        startDate={startDate}
                                        disabled={grantFinalized || isLocked}
                                        endDate={endDate}
                                        onChange={(date: Date | null) => setEndDate(date)}
                                        className={productCategoryEndDate(wb.metadata.category)}
                                    />
                                </Flex>
                            </td>
                        </tr>
                    </tbody>
                </table>
                <AllocationSelection wallets={wallets} wb={wb} />
            </HighlightedCard>
        </RequestForSingleResourceWrapper>;
    }
};

function AllocationSelection({wallets, wb}: {
    wallets: Wallet[]; wb: GrantProductCategory;
}): JSX.Element {
    const [allocationId, setAllocationId] = useState("");
    return <div>
        <HiddenInputField value={allocationId} data-target={productCategoryAllocation(wb.metadata.category)} />
    </div>;
}

function titleFromTarget(target: RequestTarget): string {
    switch (target) {
        case RequestTarget.EXISTING_PROJECT:
            return "Viewing Project";
        case RequestTarget.NEW_PROJECT:
            return "Create Project";
        case RequestTarget.PERSONAL_PROJECT:
            return "My Workspace";
        case RequestTarget.VIEW_APPLICATION:
            return "Viewing Application";
    }
}

// Note: target is baked into the component to make sure we follow the rules of hooks.
//
// We need to take wildly different paths depending on the target which causes us to use very different hooks. Baking
// the target property ensures a remount of the component.
export const GrantApplicationEditor: (target: RequestTarget) =>
    React.FunctionComponent = target => function MemoizedEditor() {
        const [loading, runWork] = useCloudCommand();
        const projectTitleRef = useRef<HTMLInputElement>(null);
        const projectReferenceIdRef = useRef<HTMLInputElement>(null);
        const history = useHistory();
        useTitle(titleFromTarget(target));
        const avatars = useAvatars();

        const dontFetchProjects = target === RequestTarget.VIEW_APPLICATION;
        const [grantGivers] = useCloudAPI<UCloud.PageV2<ProjectWithTitle>>(
            dontFetchProjects ?
                {noop: true} :
                browseProjects({itemsPerPage: 250}),
            emptyPageV2);

        const {appId} = useParams<{appId?: string}>();

        const documentRef = useRef<HTMLTextAreaElement>(null);

        const [grantApplication, fetchGrantApplication] = useCloudAPI<GrantApplication>(
            {noop: true},
            {
                createdAt: 0,
                updatedAt: 0,
                createdBy: "unknown",
                currentRevision: {
                    createdAt: 0,
                    updatedBy: "",
                    revisionNumber: 0,
                    document: {
                        allocationRequests: [],
                        form: "",
                        recipient: {
                            username: "",
                            type: "personal_workspace"
                        },
                        referenceId: "",
                        revisionComment: "",
                    }
                },
                id: "0",
                status: {
                    overallState: State.PENDING,
                    comments: [],
                    revisions: [],
                    stateBreakdown: []
                }
            }
        );

        React.useEffect(() => {
            console.log("Fetch application");
        }, [appId]);

        const grantFinalized = isGrantFinalized();

        const [isLocked, setIsLocked] = useState<boolean>(target === RequestTarget.VIEW_APPLICATION);

        const [isEditingProjectReferenceId, setIsEditingProjectReference] = useState(false);

        const [grantProductCategories, setGrantProductCategories] = useState<{[key: string]: GrantProductCategory[]}>({});

        const [submitLoading, setSubmissionsLoading] = React.useState(false);

        const submitRequest = useCallback(async () => {
            setSubmissionsLoading(true);
            if (grantGiversInUse.length === 0) {
                snackbarStore.addFailure("No grant giver selected. Please select one to submit.", false);
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
                        grantGiver: entry,
                        sourceAllocation: "TODO", // TODO(Jonas): null on initial request, required on the following.
                        period: {
                            start,
                            end
                        }
                    } as AllocationRequest;
                }).filter(it => it !== null)
            ) as AllocationRequest[];
            setSubmissionsLoading(false);
        }, []);

        const updateReferenceID = useCallback(async () => {
        }, []);

        const cancelEditOfRefId = useCallback(async () => {
        }, []);

        const approveRequest = useCallback(async () => {
        }, []);

        const rejectRequest = useCallback(async (notify: boolean) => {
        }, []);

        const transferRequest = useCallback(async (toProjectId: string) => {
        }, []);

        const closeRequest = useCallback(async () => {
        }, []);

        const reload = useCallback(() => {
            console.log("TODO");
        }, []);

        const discardChanges = useCallback(() => {

        }, []);

        const [grantGiversInUse, setGrantGiversInUse] = React.useState<string[]>([]);

        useEffect(() => {
            let timeout = 0;
            for (const resource of grantApplication.data.currentRevision.document.allocationRequests) {
                const credits = resource.balanceRequested;

                // TODO(Dan): The following code is a terrible idea.
                // This code is in here only because we did not notice the error until it was already in production
                // and was causing a lot of crashes. A proper solution is tracked in #1928.
                //
                // The code tends to crash because React has not yet rendered the inputs and we attempt to write to
                // the inputs before they are actually ready. We solve it in the code below by simply retrying
                // (via setTimeout) until React has rendered our input elements. This is obviously a bad idea and the
                // code should be refactored to avoid this. The error only manifests itself if the loading of network
                // resources occur in a specific order, an order which happens to occur often in production but for
                // some reason not in dev.
                let attempts = 0;
                const work = (): void => {
                    let success = true;
                    const creditsInput = document.querySelector<HTMLInputElement>(
                        `input[data-target="${productCategoryId({
                            provider: resource.provider,
                            name: resource.category
                        })}"]`
                    );

                    const freeButRequireBalanceCheckbox = document.querySelector<HTMLInputElement>(
                        `input[data-target="checkbox-${productCategoryId({
                            provider: resource.provider,
                            name: resource.category
                        })}"]`
                    );

                    if (credits !== undefined) {
                        if (creditsInput) {
                            const category = ([] as GrantProductCategory[]).find(it =>
                                productCategoryEquals(
                                    it.metadata.category,
                                    {name: resource.category, provider: resource.provider}
                                )
                            );

                            if (category == null) {
                                success = false;
                            } else {
                                const meta = category.metadata;
                                creditsInput.value = normalizeBalanceForFrontendOpts(
                                    credits,
                                    meta.productType, meta.chargeType, meta.unitOfPrice,
                                    {
                                        isPrice: false,
                                        forceInteger: true
                                    }
                                );
                            }
                        } else {
                            success = false;
                        }
                    }

                    if (credits !== undefined) {
                        if (freeButRequireBalanceCheckbox) {
                            freeButRequireBalanceCheckbox.checked = credits > 0;
                        }
                    }

                    if (!success) {
                        if (attempts > 10) {
                            return;
                        } else {
                            attempts++;
                            timeout = window.setTimeout(work, 500);
                        }
                    }
                };

                timeout = window.setTimeout(work, 0);
            }
            return () => {
                clearTimeout(timeout);
            }
        }, []);

        const [transferringApplication, setTransferringApplication] = useState(false);

        const shouldShowWallets = "foo";/* grantApplication.data.currentRevision.document. */;
        const [wallets, fetchWallets] = useCloudAPI<UCloud.PageV2<Wallet>>(
            shouldShowWallets ? browseWallets({itemsPerPage: 250}) : {noop: true}, emptyPage
        );
        React.useEffect(() => {
            if (shouldShowWallets && !wallets.loading) {
                /* TODO(Jonas): Find a different way of checking this. */
                if (wallets.data.itemsPerPage !== 250) {
                    fetchWallets(browseWallets({itemsPerPage: 250}));
                }
            }
        }, [shouldShowWallets, wallets.loading]);

        const grantGiverDropdown = React.useMemo(() =>
            target === RequestTarget.VIEW_APPLICATION || (grantGivers.data.items.length - grantGiversInUse.length) === 0 ? null :
                <ClickableDropdown
                    fullWidth
                    colorOnHover={false}
                    trigger="Select Grant Giver"
                    chevron
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
                                {grantGivers.data.items.map(grantGiver =>
                                    <TableRow key={grantGiver.projectId} onClick={() => setGrantGiversInUse(inUse => [...inUse, grantGiver.projectId])}>
                                        <TableCell pl="6px"><Flex><Logo projectId={grantGiver.projectId} size="32px" /><Text mt="3px" ml="8px">{grantGiver.title}</Text></Flex></TableCell>
                                        <TableCell pl="6px"><GrantGiverDescription key={grantGiver.projectId} projectId={grantGiver.projectId} /></TableCell>
                                    </TableRow>
                                )}
                            </tbody>
                        </Table>
                    </Wrapper>
                </ClickableDropdown>,
            [grantGivers, grantGiversInUse])

        const grantGiverEntries = React.useMemo(() =>
            grantGivers.data.items.filter(it => grantGiversInUse.includes(it.projectId)).map(it =>
                <GrantGiver
                    key={it.projectId}
                    grantApplication={grantApplication.data}
                    remove={() => setGrantGiversInUse(inUse => [...inUse.filter(entry => entry !== it.projectId)])}
                    setGrantProductCategories={setGrantProductCategories}
                    wallets={wallets.data.items}
                    project={it}
                    isLocked={isLocked}
                />
            ), [grantGivers, grantGiversInUse, isLocked, wallets]);

        const recipient = grantApplication.data.currentRevision.document.recipient;

        return (
            <MainContainer
                header={target === RequestTarget.EXISTING_PROJECT ?
                    <ProjectBreadcrumbs crumbs={[{title: "Request for Resources"}]} /> : null
                }
                sidebar={null}
                main={
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
                                                    <TableCell>Application Approver</TableCell>
                                                    <TableCell>{"state.editingApplication!.resourcesOwnedByTitle"}</TableCell>
                                                </TableRow>
                                                <TableRow>
                                                    <TableCell>Project Title</TableCell>
                                                    <TableCell>{"state.editingApplication!.grantRecipientTitle"}</TableCell>
                                                </TableRow>
                                                <TableRow>
                                                    <TableCell>Principal Investigator (PI)</TableCell>
                                                    <TableCell>{"state.editingApplication!.grantRecipientPi"}</TableCell>
                                                </TableRow>
                                                <TableRow>
                                                    <TableCell verticalAlign="top">
                                                        Project Type
                                                    </TableCell>
                                                    <TableCell>
                                                        <table>
                                                            <tbody>
                                                                <tr>
                                                                    <td>Personal</td>
                                                                    <td width="100%">
                                                                        {recipient.type === "personal_workspace" ?
                                                                            <Icon name="check" color="green" /> :
                                                                            <Icon name="close" color="red" />}
                                                                    </td>
                                                                </tr>
                                                                <tr>
                                                                    <td width="100%">New Project</td>
                                                                    <td>
                                                                        {recipient.type === "new_project" ?
                                                                            <Icon name="check" color="green" /> :
                                                                            <Icon name="close" color="red" />}
                                                                    </td>
                                                                </tr>
                                                                <tr>
                                                                    <td width="100%">Existing Project</td>
                                                                    <td>
                                                                        {recipient.type === "existing_project" ?
                                                                            <Icon name="check" color="green" /> :
                                                                            <Icon name="close" color="red" />}
                                                                    </td>
                                                                </tr>
                                                            </tbody>
                                                        </table>
                                                    </TableCell>
                                                </TableRow>
                                                <TableRow>
                                                    <TableCell verticalAlign="top">
                                                        Reference ID
                                                    </TableCell>
                                                    {"state.approver" ?
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
                                                                                    {"state.editingApplication?.referenceId" ?? "No ID given"}
                                                                                </td>
                                                                                <td>
                                                                                    <Button ml={"4px"} onClick={() => setIsEditingProjectReference(true)}>Edit</Button>
                                                                                </td>
                                                                            </>)
                                                                        }
                                                                    </tr>
                                                                </tbody>
                                                            </table>
                                                        </TableCell> :
                                                        <TableCell>
                                                            {grantApplication.data.currentRevision.document.referenceId ?? "No ID given"}
                                                        </TableCell>
                                                    }
                                                </TableRow>
                                                <TableRow>
                                                    <TableCell verticalAlign={"top"} mt={32}>Current Status</TableCell>
                                                    <TableCell>
                                                        {
                                                            /* TODO(Jonas): Maybe not correct enum? */
                                                            grantApplication.data.status.overallState === State.PENDING ? "In progress" :
                                                                grantApplication.data.status.overallState === State.APPROVED ? (grantApplication.data.currentRevision.updatedBy === null ? "Approved" : "Approved by " + grantApplication.data.currentRevision.updatedBy) :
                                                                    grantApplication.data.status.overallState === State.REJECTED ? (grantApplication.data.currentRevision.updatedBy === null ? "Rejected" : "Rejected  by " + grantApplication.data.currentRevision.updatedBy) :
                                                                        (grantApplication.data.currentRevision.updatedBy === null ? "Closed" : "Closed by " + grantApplication.data.currentRevision.updatedBy)
                                                        }
                                                        <ButtonGroup>
                                                            {target !== RequestTarget.VIEW_APPLICATION ? null : (
                                                                <>
                                                                    {"state.approver" && !grantFinalized ?
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
                                                                            {recipient.type !== "existing_project" && localStorage.getItem("enableprojecttransfer") != null ?
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
                                                                    {!"state.approver" && !grantFinalized ?
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

                            <Heading.h3 mt={32}>
                                {target === RequestTarget.VIEW_APPLICATION ? "Requested Resources" : "Resources"}
                            </Heading.h3>

                            {grantGiverDropdown}

                            {grantGiverEntries}

                            {/* TODO(Jonas): This doesn't group by provider  */}
                            {target !== RequestTarget.VIEW_APPLICATION ? null : (
                                [...new Set(([] as GrantProductCategory[]).map(it => it.metadata.category.provider))].map((grantGiver: string) => <>
                                    <Heading.h2 mt="12px">{grantGiver}</Heading.h2>
                                    {productTypes.map(type => (
                                        <ProductCategorySection
                                            wallets={wallets.data.items}
                                            grantGiver={grantGiver}
                                            type={type}
                                            isLocked={isLocked}
                                            key={type}
                                        />
                                    ))}
                                </>))}

                            <CommentApplicationWrapper>
                                <RequestFormContainer>
                                    <Heading.h4>Application</Heading.h4>
                                    <TextArea
                                        disabled={grantFinalized || isLocked /* TODO || state.approver */}
                                        rows={25}
                                        ref={documentRef}
                                    />
                                </RequestFormContainer>

                                <Box width="100%">
                                    <Heading.h4>Comments</Heading.h4>
                                    {grantApplication.data.status.comments.length > 0 ? null : (
                                        <Box mt={16} mb={16}>
                                            No comments have been posted yet.
                                        </Box>
                                    )}

                                    {grantApplication.data.status.comments.map(it => (
                                        <CommentBox
                                            key={it.id}
                                            comment={it}
                                            avatar={avatars.cache[it.username] ?? defaultAvatar}
                                            reload={reload}
                                        />
                                    ))}

                                    <PostCommentWidget
                                        applicationId={grantApplication.data.currentRevision.document.referenceId ?? "" /* TODO(Jonas): Is this the samme as .id from before? */}
                                        avatar={avatars.cache[Client.username!] ?? defaultAvatar}
                                        reload={reload}
                                    />
                                </Box>
                            </CommentApplicationWrapper>
                            <Box p={32} pb={16}>
                                {target !== RequestTarget.VIEW_APPLICATION ? (
                                    <Button disabled={grantFinalized || submitLoading} fullWidth onClick={submitRequest}>
                                        Submit Application
                                    </Button>
                                ) : null
                                }
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
                                                onClick={submitRequest}
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
                }
                additional={
                    /* TODO: state.editingApplication != null && state.editingApplication.grantRecipientPi ? */
                    <TransferApplicationPrompt
                        isActive={transferringApplication}
                        close={() => setTransferringApplication(false)}
                        transfer={transferRequest}
                        grantId={grantApplication.data.currentRevision.document.referenceId ?? ""}
                    />}
            />
        );
    };

const projectDescriptionCache: {[projectId: string]: string | undefined} = {};

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

function getRecipientId(recipient: Recipient): string {
    return recipient.type === "existing_project" ? recipient.id :
        recipient.type === "new_project" ? recipient.title :
            recipient.type === "personal_workspace" ? recipient.username : ""
}

function GrantGiver(props: {
    grantApplication: GrantApplication;
    project: ProjectWithTitle;
    isLocked: boolean;
    remove(): void;
    wallets: Wallet[];
    setGrantProductCategories: React.Dispatch<React.SetStateAction<{[key: string]: GrantProductCategory[]}>>;
}): JSX.Element {
    const {recipient} = props.grantApplication.currentRevision.document;
    const grantFinalized = isGrantFinalized();

    const recipientId = getRecipientId(recipient);

    /* TODO(Jonas): Why is this done over two parts instead of one?  */
    const [products, fetchProducts] = useCloudAPI<{availableProducts: Product[]}>({noop: true}, {availableProducts: []});
    React.useEffect(() => {
        fetchProducts(grantApi.retrieveProducts({
            projectId: props.project.projectId,
            recipientType: recipient.type,
            recipientId,
            showHidden: false
        }));
    }, []);

    /* FIXME: (Jonas) - Not DRY, copied from other point in file. */
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

        /* if (props.state.editingApplication !== undefined) { */
        for (const request of props.grantApplication.currentRevision.document.allocationRequests) {
            const existing = result.find(it =>
                productCategoryEquals(
                    it.metadata.category,
                    {name: request.category, provider: request.provider})
            );

            if (existing) {
                existing.requestedBalance = request.balanceRequested;
            }
        }
        /* } */
        return result;
    }, []);

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
                left={<Heading.h3>{props.project.title}</Heading.h3>}
                right={<Flex cursor="pointer" onClick={props.remove}><Icon name="close" color="red" mr="8px" />Remove</Flex>}
            />
            {productTypes.flatMap(type => {
                const filteredProductCategories = productCategories.filter(pc => pc.metadata.productType === type);
                const noEntries = filteredProductCategories.length === 0;
                const filteredWallets = props.wallets.filter(it => it.productType === type);
                return (<React.Fragment key={type}>
                    <Heading.h4 mt={32}><Flex>{productTypeToTitle(type)} <ProductLink /></Flex></Heading.h4>
                    {noEntries ? <Heading.h3 mt="12px">No products for type available.</Heading.h3> : <ResourceContainer>
                        {filteredProductCategories.map((pc, idx) => <>
                            <GenericRequestCard
                                key={idx}
                                wb={pc}
                                grantFinalized={grantFinalized}
                                isLocked={props.isLocked}
                                wallets={filteredWallets.filter(it => it.paysFor.provider === pc.metadata.category.provider && it.paysFor.name === pc.metadata.category.name)}
                                /* TODO(Jonas): This should require that it is being viewed by the grant approver, I think. Should a grant approver creating a  */
                                showAllocationSelection={false /* TODO props.state.approver */}
                            />
                            <GenericRequestCard
                                key={`${idx}-n`}
                                wb={pc}
                                grantFinalized={grantFinalized}
                                isLocked={props.isLocked}
                                wallets={filteredWallets.filter(it => it.paysFor.provider === pc.metadata.category.provider && it.paysFor.name === pc.metadata.category.name)}
                                /* This should require that it is being viewed by the grant approver, I think. Should a grant approver creating a  */
                                showAllocationSelection={false /* TODO props.state.approver */}
                            />
                        </>)}
                    </ResourceContainer>}
                </React.Fragment>);
            })}
        </Box>
        , [productCategories, props.wallets]);
}

const ProductCategorySection: React.FunctionComponent<{
    type: ProductType;
    isLocked: boolean;
    grantGiver: string;
    wallets: Wallet[];
}> = ({type, isLocked, grantGiver, wallets}) => {

    const grantFinalized = isGrantFinalized();
    const filtered = useMemo(
        /* TODO */
        () => ([] as GrantProductCategory[]).filter(pc => pc.metadata.productType == type && pc.metadata.category.provider === grantGiver),
        []
    );

    const filteredWallets = useMemo(
        () => wallets.filter(w => w.productType == type && w.paysFor.provider === grantGiver),
        [wallets, type, grantGiver]
    );

    if (filtered.length < 1) return null;
    return <>
        <Heading.h4 mt={32}><Flex>{productTypeToTitle(type)} <ProductLink /></Flex></Heading.h4>
        <ResourceContainer>
            {filtered.map((it, idx) => {
                const walletsForCard = filteredWallets.filter(w => w.paysFor.name === it.metadata.category.name);
                return (
                    <>
                        <GenericRequestCard
                            key={idx}
                            wb={it}
                            grantFinalized={grantFinalized}
                            showAllocationSelection={false /* TODO state.approver */}
                            isLocked={isLocked}
                            wallets={walletsForCard}
                        />
                        <GenericRequestCard
                            key={`${idx}-n`}
                            wb={it}
                            grantFinalized={grantFinalized}
                            showAllocationSelection={false /* TODO state.approver */}
                            isLocked={isLocked}
                            wallets={walletsForCard}
                        />
                    </>
                )
            })}
        </ResourceContainer>
    </>
};

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
            fetchProjects(findAffiliations({page: 0, itemsPerPage: 100, grantId}))
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

const OptionItem: React.FunctionComponent<{onClick: () => void; text: string; color?: string}> = props => (
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
    reload: () => void
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

const HelpText = styled.p`
    margin: 0;
    font-size: ${theme.fontSizes[1]}px;
    color: var(--gray, #f00);
`;

const PostCommentWidget: React.FunctionComponent<{
    applicationId: string,
    avatar: AvatarType,
    reload: () => void
}> = ({applicationId, avatar, reload}) => {
    const commentBoxRef = useRef<HTMLTextAreaElement>(null);
    const [loading, runWork] = useCloudCommand();
    const submitComment = useCallback(async (e) => {
        e.preventDefault();

        await runWork(commentOnGrantApplication({
            requestId: applicationId,
            comment: commentBoxRef.current!.value
        }));
        reload();
        if (commentBoxRef.current) commentBoxRef.current!.value = "";
    }, [runWork, applicationId, commentBoxRef.current]);
    return <PostCommentWrapper onSubmit={submitComment}>
        <div className="wrapper">
            <UserAvatar avatar={avatar} width={"48px"} />
            <TextArea rows={3} ref={commentBoxRef} placeholder={"Your comment"} />
        </div>
        <div className="buttons">
            <Button disabled={loading}>Send</Button>
        </div>
    </PostCommentWrapper>;
};

function ProductLink(): JSX.Element {
    return <Tooltip
        trigger={<ExternalLink href="/app/skus"><Box style={{
            cursor: "pointer",
            border: "2px var(--black) solid",
            borderRadius: "9999px",
            width: "35px",
            height: "35px",
            marginLeft: "9px",
            paddingLeft: "10px",
            marginTop: "-2px"
        }}> ?</Box></ExternalLink>}
    >
        <Box width="100px">Click to view details for resources</Box>
    </Tooltip>
}

export default GrantApplicationEditor;
