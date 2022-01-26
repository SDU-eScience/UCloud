import * as React from "react";
import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {useProjectManagementStatus} from "@/Project";
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
import Input from "@/ui-components/Input";
import Label from "@/ui-components/Label";
import List from "@/ui-components/List";
import Checkbox from "@/ui-components/Checkbox";
import Text from "@/ui-components/Text";
import TextArea from "@/ui-components/TextArea";
import theme from "@/ui-components/theme";
import Tooltip from "@/ui-components/Tooltip";
import {APICallState, useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {
    ProductCategoryId,
    ProductMetadata,
    Product,
    productCategoryEquals,
    usageExplainer,
    productTypeToIcon,
    ProductType,
    productTypeToTitle,
    productTypes, explainAllocation, normalizeBalanceForBackend, normalizeBalanceForFrontend,
} from "@/Accounting";
import styled from "styled-components";
import HighlightedCard from "@/ui-components/HighlightedCard";
import {
    approveGrantApplication,
    closeGrantApplication,
    Comment,
    commentOnGrantApplication,
    deleteGrantApplicationComment,
    editGrantApplication, editReferenceId,
    findAffiliations,
    GrantApplication,
    GrantApplicationStatus,
    GrantRecipient, GrantsRetrieveAffiliationsResponse,
    isGrantFinalized,
    readTemplates,
    ReadTemplatesResponse,
    rejectGrantApplication,
    ResourceRequest,
    submitGrantApplication, transferApplication,
    viewGrantApplication,
    ViewGrantApplicationResponse
} from "@/Project/Grant/index";
import {useHistory, useParams} from "react-router";
import {Client} from "@/Authentication/HttpClientInstance";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {dateToString} from "@/Utilities/DateUtilities";
import {UserAvatar} from "@/AvataaarLib/UserAvatar";
import {AvatarType, defaultAvatar} from "@/UserSettings/Avataaar";
import {AvatarHook, useAvatars} from "@/AvataaarLib/hook";
import Table, {TableCell, TableRow} from "@/ui-components/Table";
import {addStandardDialog} from "@/UtilityComponents";
import {setLoading, useTitle} from "@/Navigation/Redux/StatusActions";
import {useDispatch} from "react-redux";
import {setRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {loadingAction} from "@/Loading";
import * as UCloud from "@/UCloud";
import grantApi = UCloud.grant.grant;
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {TextSpan} from "@/ui-components/Text";
import {default as ReactModal} from "react-modal";
import {defaultModalStyle} from "@/Utilities/ModalUtilities";
import {emptyPage} from "@/DefaultObjects";
import {Spacer} from "@/ui-components/Spacer";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";

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

interface UseRequestInformation {
    productCategories: GrantProductCategory[];
    targetProject?: string;
    documentRef: React.RefObject<HTMLTextAreaElement>;
    templates: APICallState<ReadTemplatesResponse>;
    recipient: GrantRecipient;
    editingApplication?: GrantApplication;
    comments: Comment[];
    avatars: AvatarHook;
    reload: () => void;
    approver: boolean;
    loading: boolean;
}

function useRequestInformation(target: RequestTarget): UseRequestInformation {
    let targetProject: string | undefined;
    let recipient: GrantRecipient;
    let editingApplication: GrantApplication | undefined;
    let approver = false;
    let comments: Comment[] = [];
    const avatars = useAvatars();
    let loading = false;

    const {appId} = useParams<{appId?: string}>();

    const [grantApplication, fetchGrantApplication] = useCloudAPI<ViewGrantApplicationResponse>(
        {noop: true},
        {
            application: {
                document: "",
                grantRecipient: {type: "personal", username: Client.username ?? ""},
                requestedBy: Client.username ?? "",
                requestedResources: [],
                resourcesOwnedBy: "unknown",
                status: GrantApplicationStatus.IN_PROGRESS,
                grantRecipientPi: Client.username ?? "",
                id: 0,
                resourcesOwnedByTitle: "unknown",
                grantRecipientTitle: "",
                createdAt: 0,
                updatedAt: 0
            },
            comments: [],
            approver: false
        }
    );

    const documentRef = useRef<HTMLTextAreaElement>(null);
    const [templates, fetchTemplates] = useCloudAPI<ReadTemplatesResponse>(
        {noop: true},
        {existingProject: "", newProject: "", personalProject: ""}
    );

    switch (target) {
        case RequestTarget.EXISTING_PROJECT: {
            const {projectId, projectDetails} = useProjectManagementStatus({isRootComponent: true});
            targetProject = projectDetails.data.parent;
            recipient = {type: "existing_project", projectId};
            break;
        }

        case RequestTarget.NEW_PROJECT:
        case RequestTarget.PERSONAL_PROJECT: {
            const {projectId} = useParams<{projectId: string}>();
            targetProject = projectId;
            if (target === RequestTarget.NEW_PROJECT) {
                recipient = {type: "new_project", projectTitle: "placeholder"};
            } else {
                recipient = {type: "personal", username: Client.username!};
            }
            break;
        }

        case RequestTarget.VIEW_APPLICATION: {
            targetProject = grantApplication.data.application.resourcesOwnedBy;
            recipient = grantApplication.data.application.grantRecipient;
            comments = grantApplication.data.comments;
            approver = grantApplication.data.approver;
            editingApplication = grantApplication.data.application;

            loading = loading || grantApplication.loading;
            break;
        }
    }

    const [products, fetchProducts] = useCloudAPI<{availableProducts: Product[]}>(
        {noop: true},
        {availableProducts: []}
    );

    const reloadProducts = useCallback(() => {
        if (targetProject && targetProject !== "unknown") {
            fetchProducts(grantApi.retrieveProducts({
                projectId: targetProject,
                recipientType: recipient.type,
                recipientId:
                    recipient.type === "existing_project" ? recipient.projectId :
                        recipient.type === "new_project" ? recipient.projectTitle :
                            recipient.type === "personal" ? recipient.username : "",
                showHidden: false
            }));
        }
    }, [targetProject, recipient.type]);

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

        if (editingApplication !== undefined) {
            for (const request of editingApplication.requestedResources) {
                const existing = result.find(it =>
                    productCategoryEquals(
                        it.metadata.category,
                        {name: request.productCategory, provider: request.productProvider})
                );

                if (existing) {
                    existing.requestedBalance = request.balanceRequested;
                }
            }
        }
        return result;
    }, [products.data, editingApplication]);

    const reload = useCallback(() => {
        if (targetProject && targetProject !== "unknown") {
            fetchTemplates(readTemplates({projectId: targetProject}));
            reloadProducts();
        }

        if (appId) {
            fetchGrantApplication(viewGrantApplication({id: parseInt(appId, 10)}));
        }
    }, [targetProject, appId]);

    useEffect(() => {
        reload();
    }, [targetProject, appId]);

    useEffect(() => {
        if (documentRef.current) {
            switch (target) {
                case RequestTarget.PERSONAL_PROJECT:
                    documentRef.current.value = templates.data.personalProject;
                    break;
                case RequestTarget.EXISTING_PROJECT:
                    documentRef.current.value = templates.data.existingProject;
                    break;
                case RequestTarget.NEW_PROJECT:
                    documentRef.current.value = templates.data.newProject;
                    break;
                case RequestTarget.VIEW_APPLICATION:
                    documentRef.current.value = editingApplication?.document ?? "";
                    break;
            }
        }
    }, [templates, documentRef.current]);

    useEffect(() => {
        const usernames = comments.map(it => it.postedBy);
        usernames.push(Client.username!);
        avatars.updateCache(usernames);
    }, [comments]);

    return {
        productCategories, targetProject, documentRef, templates, recipient, editingApplication,
        comments, avatars, reload, approver, loading
    };
}

function productCategoryId(pid: ProductCategoryId): string {
    return `${pid.name}/${pid.provider}`;
}

function parseIntegerFromInput(input?: HTMLInputElement | null): number | undefined {
    if (!input) return undefined;
    const rawValue = input.value;
    const parsed = parseInt(rawValue, 10);
    if (isNaN(parsed)) return undefined;
    return parsed;
}

const GenericRequestCard: React.FunctionComponent<{
    wb: GrantProductCategory,
    state: UseRequestInformation,
    grantFinalized: boolean,
    isLocked: boolean
}> = ({wb, grantFinalized, isLocked}) => {
    if (wb.metadata.freeToUse) {
        return <RequestForSingleResourceWrapper>
            <HighlightedCard color={"blue"} isLoading={false}>
                <Flex flexDirection={"row"} alignItems={"center"}>
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
                            </td>
                        </tr>
                    </tbody>
                </table>
            </HighlightedCard>
        </RequestForSingleResourceWrapper>;
    }
};

// Note: target is baked into the component to make sure we follow the rules of hooks.
//
// We need to take wildly different paths depending on the target which causes us to use very different hooks. Baking
// the target property ensures a remount of the component.
export const GrantApplicationEditor: (target: RequestTarget) =>
    React.FunctionComponent = target => function MemoizedEditor() {
        const state = useRequestInformation(target);
        const grantFinalized = isGrantFinalized(state.editingApplication?.status);
        const [loading, runWork] = useCloudCommand();
        const projectTitleRef = useRef<HTMLInputElement>(null);
        const projectReferenceIdRef = useRef<HTMLInputElement>(null);
        const history = useHistory();
        const dispatch = useDispatch();
        const [isLocked, setIsLocked] = useState<boolean>(target === RequestTarget.VIEW_APPLICATION);

        switch (target) {
            case RequestTarget.EXISTING_PROJECT:
                useTitle("Viewing Project");
                break;
            case RequestTarget.NEW_PROJECT:
                useTitle("Create Project");
                break;
            case RequestTarget.PERSONAL_PROJECT:
                useTitle("My Workspace");
                break;
            case RequestTarget.VIEW_APPLICATION:
                useTitle("Viewing Application");
                break;
        }

        dispatch(setRefreshFunction(state.reload));
        dispatch(loadingAction(state.loading));
        useEffect(() => {
            return () => {
                dispatch(setRefreshFunction(undefined));
            };
        }, []);

        const discardChanges = useCallback(async () => {
            state.reload();
            setIsLocked(true);
        }, [state.reload]);

        const [isEditingProjectReferenceId, setIsEditingProjectReference] = useState(false);

        const [submitLoading, setSubmissionsLoading] = React.useState(false);

        const submitRequest = useCallback(async () => {
            setSubmissionsLoading(true);
            if (state.targetProject === undefined) {
                snackbarStore.addFailure("Unknown target. Root level projects cannot apply for more resources.", false);
                setSubmissionsLoading(false);
                return;
            }

            let grantRecipient: GrantRecipient = state.recipient;
            if (target === RequestTarget.NEW_PROJECT) {
                grantRecipient = {type: "new_project", projectTitle: projectTitleRef.current!.value};
            }

            const requestedResources = state.productCategories.map(wb => {
                let creditsRequested = parseIntegerFromInput(
                    document.querySelector<HTMLInputElement>(
                        `input[data-target="${productCategoryId(wb.metadata.category)}"]`
                    )
                );
                if (creditsRequested) {
                    creditsRequested = normalizeBalanceForBackend(
                        creditsRequested,
                        wb.metadata.productType, wb.metadata.chargeType, wb.metadata.unitOfPrice
                    );
                }

                if (creditsRequested === undefined) {
                    return null;
                }

                return {
                    balanceRequested: creditsRequested,
                    productCategory: wb.metadata.category.name,
                    productProvider: wb.metadata.category.provider
                } as ResourceRequest;
            }).filter(it => it !== null) as ResourceRequest[];

            const newDocument = state.documentRef.current!.value;
            if (state.editingApplication === undefined) {
                const response = await runWork<{id: number}>(submitGrantApplication({
                    document: newDocument,
                    resourcesOwnedBy: state.targetProject!,
                    requestedResources,
                    grantRecipient
                }));

                if (response) {
                    history.push(`/project/grants/view/${response.id}`);
                }
            } else {
                await runWork(editGrantApplication({
                    id: state.editingApplication.id!,
                    newDocument,
                    newResources: requestedResources
                }));
                state.reload();
                setIsLocked(true);
            }
            setSubmissionsLoading(false);
        }, [state.targetProject, state.documentRef, state.recipient, state.productCategories, projectTitleRef,
        state.editingApplication?.id, state.reload]);

        const updateReferenceID = useCallback(async () => {
            await runWork(editReferenceId({id: state.editingApplication!.id, newReferenceId: projectReferenceIdRef.current?.value}))
            setIsEditingProjectReference(false);
            state.reload();
        }, [state.editingApplication?.id, state.editingApplication?.referenceId]);

        const cancelEditOfRefId = useCallback(async () => {
            setIsEditingProjectReference(false);
            state.reload();
        }, [state.editingApplication?.id, state.editingApplication?.referenceId]);

        const approveRequest = useCallback(async () => {
            if (state.editingApplication !== undefined) {
                addStandardDialog({
                    title: "Approve application?",
                    message: "Are you sure you wish to approve this application?",
                    onConfirm: async () => {
                        await runWork(approveGrantApplication({requestId: state.editingApplication!.id}));
                        state.reload();
                    }
                });
            }
        }, [state.editingApplication?.id]);

        const rejectRequest = useCallback(async (notify: boolean) => {
            if (state.editingApplication !== undefined) {
                addStandardDialog({
                    title: "Reject application?",
                    message: "Are you sure you wish to reject this application?",
                    onConfirm: async () => {
                        await runWork(rejectGrantApplication({requestId: state.editingApplication!.id, notify: notify}));
                        state.reload();
                    }
                });
            }
        }, [state.editingApplication?.id]);

        const transferRequest = useCallback(async (toProjectId: string) => {
            if (state.editingApplication !== undefined) {
                await runWork(transferApplication({
                    applicationId: state.editingApplication!.id,
                    transferToProjectId: toProjectId
                }));
                state.reload();
            }
        }, [state.editingApplication?.id]);

        const closeRequest = useCallback(async () => {
            if (state.editingApplication !== undefined) {
                addStandardDialog({
                    title: "Withdraw application?",
                    message: "Are you sure you wish to withdraw this application?",
                    onConfirm: async () => {
                        await runWork(closeGrantApplication({requestId: state.editingApplication!.id}));
                        state.reload();
                    }
                });
            }
        }, [state.editingApplication?.id]);

        useEffect(() => {
            let timeout = 0;
            if (state.editingApplication !== undefined) {
                for (const resource of state.editingApplication.requestedResources) {
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
                                provider: resource.productProvider,
                                name: resource.productCategory
                            })}"]`
                        );

                        const freeButRequireBalanceCheckbox = document.querySelector<HTMLInputElement>(
                            `input[data-target="checkbox-${productCategoryId({
                                provider: resource.productProvider,
                                name: resource.productCategory
                            })}"]`
                        );

                        if (credits !== undefined) {
                            if (creditsInput) {
                                const category = state.productCategories.find(it =>
                                    productCategoryEquals(
                                        it.metadata.category,
                                        {name: resource.productCategory, provider: resource.productProvider}
                                    )
                                );

                                if (category == null) {
                                    success = false;
                                } else {
                                    const meta = category.metadata;
                                    creditsInput.value = normalizeBalanceForFrontend(
                                        credits,
                                        meta.productType, meta.chargeType, meta.unitOfPrice,
                                        false, 0
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
            }
            return () => {
                clearTimeout(timeout);
            }
        }, [state.editingApplication, state.productCategories]);

        const [transferringApplication, setTransferringApplication] = useState(false);

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
                                                    <TableCell>{state.editingApplication!.resourcesOwnedByTitle}</TableCell>
                                                </TableRow>
                                                <TableRow>
                                                    <TableCell>Project Title</TableCell>
                                                    <TableCell>{state.editingApplication!.grantRecipientTitle}</TableCell>
                                                </TableRow>
                                                <TableRow>
                                                    <TableCell>Principal Investigator (PI)</TableCell>
                                                    <TableCell>{state.editingApplication!.grantRecipientPi}</TableCell>
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
                                                                        {state.recipient.type === "personal" ?
                                                                            <Icon name={"check"} color={"green"} /> :
                                                                            <Icon name={"close"} color={"red"} />}
                                                                    </td>
                                                                </tr>
                                                                <tr>
                                                                    <td width="100%">New Project</td>
                                                                    <td>
                                                                        {state.recipient.type === "new_project" ?
                                                                            <Icon name={"check"} color={"green"} /> :
                                                                            <Icon name={"close"} color={"red"} />}
                                                                    </td>
                                                                </tr>
                                                                <tr>
                                                                    <td width="100%">Existing Project</td>
                                                                    <td>
                                                                        {state.recipient.type === "existing_project" ?
                                                                            <Icon name={"check"} color={"green"} /> :
                                                                            <Icon name={"close"} color={"red"} />}
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
                                                    {state.approver ?
                                                        <TableCell>
                                                            <table>
                                                                <tbody>
                                                                    <tr>
                                                                        {isEditingProjectReferenceId ? (
                                                                            <>
                                                                                <td>
                                                                                    <Input
                                                                                        placeholder={state.editingApplication?.referenceId != null ? state.editingApplication?.referenceId : "e.g. DeiC-SDU-L1-000001"}
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
                                                                                    {state.editingApplication?.referenceId ?? "No ID given"}
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
                                                            {state.editingApplication?.referenceId ?? "No ID given"}
                                                        </TableCell>
                                                    }
                                                </TableRow>
                                                <TableRow>
                                                    <TableCell verticalAlign={"top"} mt={32}>Current Status</TableCell>
                                                    <TableCell>
                                                        {
                                                            state.editingApplication!.status === GrantApplicationStatus.IN_PROGRESS ? "In progress" :
                                                                state.editingApplication!.status === GrantApplicationStatus.APPROVED ? (state.editingApplication?.statusChangedBy === null ? "Approved" : "Approved by " + state.editingApplication?.statusChangedBy) :
                                                                    state.editingApplication!.status === GrantApplicationStatus.REJECTED ? (state.editingApplication?.statusChangedBy === null ? "Rejected" : "Rejected  by " + state.editingApplication?.statusChangedBy) :
                                                                        (state.editingApplication?.statusChangedBy === null ? "Closed" : "Closed by " + state.editingApplication?.statusChangedBy)
                                                        }
                                                        <ButtonGroup>
                                                            {target !== RequestTarget.VIEW_APPLICATION ? null : (
                                                                <>
                                                                    {state.approver && !grantFinalized ?
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
                                                                            {state.editingApplication?.grantRecipient!.type !== "existing_project" && localStorage.getItem("enableprojecttransfer") != null ?
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
                                                                    {!state.approver && !grantFinalized ?
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

                            {productTypes.map(type => (
                                <ProductCategorySection state={state} type={type} isLocked={isLocked} key={type} />
                            ))}

                            <CommentApplicationWrapper>
                                <RequestFormContainer>
                                    <Heading.h4>Application</Heading.h4>
                                    <TextArea
                                        disabled={grantFinalized || isLocked || state.approver}
                                        rows={25}
                                        ref={state.documentRef}
                                    />
                                </RequestFormContainer>

                                {state.editingApplication === undefined ? null : (
                                    <Box width="100%">
                                        <Heading.h4>Comments</Heading.h4>
                                        {state.comments.length > 0 ? null : (
                                            <Box mt={16} mb={16}>
                                                No comments have been posted yet.
                                            </Box>
                                        )}

                                        {state.comments.map(it => (
                                            <CommentBox
                                                key={it.id}
                                                comment={it}
                                                avatar={state.avatars.cache[it.postedBy] ?? defaultAvatar}
                                                reload={state.reload}
                                            />
                                        ))}

                                        <PostCommentWidget
                                            applicationId={state.editingApplication.id!}
                                            avatar={state.avatars.cache[Client.username!] ?? defaultAvatar}
                                            reload={state.reload}
                                        />
                                    </Box>
                                )}
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
                    state.editingApplication != null && state.editingApplication.grantRecipientPi ?
                        <TransferApplicationPrompt
                            isActive={transferringApplication}
                            close={() => setTransferringApplication(false)}
                            transfer={transferRequest}
                            grantId={state.editingApplication.id}
                        /> : null}
            />
        );
    };

const ProductCategorySection: React.FunctionComponent<{
    state: UseRequestInformation;
    type: ProductType;
    isLocked: boolean;
}> = ({state, type, isLocked}) => {
    const grantFinalized = isGrantFinalized(state.editingApplication?.status);
    const filtered = useMemo(
        () => state.productCategories.filter(pc => pc.metadata.productType == type),
        [state.productCategories, type]
    );

    if (filtered.length < 1) return null;
    return <>
        <Heading.h4 mt={32}><Flex>{productTypeToTitle(type)} <ProductLink /></Flex></Heading.h4>
        <ResourceContainer>
            {filtered.map((it, idx) => (
                <GenericRequestCard
                    key={idx}
                    wb={it}
                    state={state}
                    grantFinalized={grantFinalized}
                    isLocked={isLocked}
                />
            ))}
        </ResourceContainer>
    </>
};

interface TransferApplicationPromptProps {
    isActive: boolean;
    grantId: number;

    close(): void;

    transfer(toProjectId: string): Promise<void>;
}

function TransferApplicationPrompt({isActive, close, transfer, grantId}: TransferApplicationPromptProps) {
    const [projects, fetchProjects] = useCloudAPI<GrantsRetrieveAffiliationsResponse>(findAffiliations({
        page: 0,
        itemsPerPage: 100,
        grantId
    }), emptyPage);

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
            <p><strong>{comment.postedBy}</strong> says:</p>
            <p>{comment.comment}</p>
            <time>{dateToString(comment.postedAt)}</time>
        </div>

        {comment.postedBy === Client.username ? (
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
    applicationId: number,
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
