import * as React from "react";
import {useCallback, useEffect, useRef} from "react";
import {useProjectManagementStatus} from "Project";
import {MainContainer} from "MainContainer/MainContainer";
import {ProjectBreadcrumbs} from "Project/Breadcrumbs";
import * as Heading from "ui-components/Heading";
import {Box, Button, ButtonGroup, Card, Flex, Icon, Input, Label, Text, TextArea, theme} from "ui-components";
import {useAsyncCommand, useCloudAPI} from "Authentication/DataHook";
import {
    ProductArea,
    productCategoryEquals,
    ProductCategoryId,
    retrieveBalance,
    RetrieveBalanceResponse,
    retrieveFromProvider,
    RetrieveFromProviderResponse,
    UCLOUD_PROVIDER,
    WalletBalance
} from "Accounting";
import {creditFormatter} from "Project/ProjectUsage";
import styled from "styled-components";
import {DashboardCard} from "Dashboard/Dashboard";
import {
    approveGrantApplication,
    closeGrantApplication,
    Comment,
    commentOnGrantApplication,
    deleteGrantApplicationComment,
    editGrantApplication,
    GrantApplication,
    GrantApplicationStatus,
    GrantRecipient,
    readTemplates,
    ReadTemplatesResponse,
    rejectGrantApplication,
    ResourceRequest,
    submitGrantApplication,
    viewGrantApplication,
    ViewGrantApplicationResponse,
    isGrantFinalized
} from "Project/Grant/index";
import {useHistory, useParams} from "react-router";
import {Client} from "Authentication/HttpClientInstance";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {dateToString} from "Utilities/DateUtilities";
import {UserAvatar} from "AvataaarLib/UserAvatar";
import {AvatarType, defaultAvatar} from "UserSettings/Avataaar";
import {useAvatars} from "AvataaarLib/hook";
import {Toggle} from "ui-components/Toggle";
import {doNothing} from "UtilityFunctions";
import Table, {TableCell, TableRow} from "ui-components/Table";
import {addStandardDialog} from "UtilityComponents";
import {isAdminOrPI} from "Utilities/ProjectUtilities";
import {useTitle} from "Navigation/Redux/StatusActions";
import {Balance, BalanceExplainer} from "Accounting/Balance";

export const RequestForSingleResourceWrapper = styled.div`
    ${Icon} {
        float: right;
        margin-left: 10px;
    }
    
    ${Card} {
        height: 100%;
    }
    
    table {
        margin: 16px;
    }
    
    th {
        width: 100%;
        text-align: left;
    }
    
    td {
        margin-left: 10px;
        min-width: 350px;
    }
    
    tr {
        vertical-align: top;
        height: 40px;
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

function useRequestInformation(target: RequestTarget) {
    let targetProject: string | undefined;
    let wallets: WalletBalance[] = [];
    let reloadWallets: () => void = () => {
        /* empty */
    };
    let recipient: GrantRecipient;
    let editingApplication: GrantApplication | undefined;
    let approver: boolean = false;
    let comments: Comment[] = [];
    const avatars = useAvatars();

    let availableProducts: {area: ProductArea, category: ProductCategoryId}[];
    let reloadProducts: () => void;
    {
        const [products, fetchProducts] = useCloudAPI<RetrieveFromProviderResponse>(
            {noop: true},
            []
        );

        const allCategories: {category: ProductCategoryId, area: "compute" | "storage"}[] =
            products.data.map(it => ({category: it.category, area: it.type}));

        const uniqueCategories: {category: ProductCategoryId, area: "compute" | "storage"}[] = Array.from(
            new Set(allCategories.map(it => JSON.stringify(it))).values()
        ).map(it => JSON.parse(it));

        availableProducts = uniqueCategories.map(it => {
            return {
                area: it.area === "compute" ? ProductArea.COMPUTE : ProductArea.STORAGE,
                category: it.category,
            };
        });
        reloadProducts = useCallback(() => {
            fetchProducts(retrieveFromProvider({provider: UCLOUD_PROVIDER}));
        }, []);
    }

    const documentRef = useRef<HTMLTextAreaElement>(null);
    const [templates, fetchTemplates] = useCloudAPI<ReadTemplatesResponse>(
        {noop: true},
        {existingProject: "", newProject: "", personalProject: ""}
    );

    switch (target) {
        case RequestTarget.EXISTING_PROJECT: {
            const {projectId, projectDetails} = useProjectManagementStatus({isRootComponent: true});
            targetProject = projectDetails.data.parent;
            const [w, fetchWallets] = useCloudAPI<RetrieveBalanceResponse>(
                {noop: true},
                {wallets: []}
            );
            wallets = w.data.wallets;
            reloadWallets = useCallback(() => {
                fetchWallets(retrieveBalance({id: projectId, type: "PROJECT", includeChildren: false}));
            }, [projectId]);
            recipient = {type: "existing_project", projectId};
            break;
        }

        case RequestTarget.NEW_PROJECT:
        case RequestTarget.PERSONAL_PROJECT: {
            const {projectId} = useParams();
            targetProject = projectId;

            if (target === RequestTarget.NEW_PROJECT) {
                recipient = {type: "new_project", projectTitle: "placeholder"};
            } else {
                recipient = {type: "personal", username: Client.username!};
            }
            break;
        }

        case RequestTarget.VIEW_APPLICATION:
            const {appId} = useParams();

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

            targetProject = grantApplication.data.application.resourcesOwnedBy;
            wallets = grantApplication.data.application.requestedResources.map(it => {
                // Note: Some of these are simply placeholder values and are replaced later
                return {
                    wallet: {
                        paysFor: {
                            id: it.productCategory,
                            provider: it.productProvider
                        },
                        id: "unknown",
                        type: "USER"
                    },
                    balance: it.creditsRequested ?? 0,
                    area: ProductArea.COMPUTE
                };
            });
            recipient = grantApplication.data.application.grantRecipient;
            comments = grantApplication.data.comments;
            approver = grantApplication.data.approver;
            editingApplication = grantApplication.data.application;

            reloadWallets = useCallback(() => {
                fetchGrantApplication(viewGrantApplication({id: parseInt(appId, 10)}));
            }, [appId]);

            useEffect(() => {
                reloadWallets();
            }, [appId]);

            break;
    }

    const reload = useCallback(() => {
        if (targetProject) {
            fetchTemplates(readTemplates({projectId: targetProject}));
            reloadWallets();
            reloadProducts();
        }
    }, [targetProject]);

    useEffect(() => {
        reload();
    }, [targetProject]);

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

    const mergedWallets: WalletBalance[] = [];
    {
        // Put in all products and attach a price, if there is one
        for (const product of availableProducts) {
            mergedWallets.push({
                area: product.area,
                balance: 0,
                wallet: {
                    type: "USER",
                    id: "unknown",
                    paysFor: product.category
                }
            });
        }

        for (const wallet of wallets) {
            for (const pWallet of mergedWallets) {
                if (productCategoryEquals(pWallet.wallet.paysFor, wallet.wallet.paysFor)) {
                    pWallet.balance = wallet.balance;
                    break;
                }
            }
        }
    }

    return {
        wallets: mergedWallets, reloadWallets, targetProject, documentRef, templates, recipient, editingApplication,
        comments, avatars, reload, approver
    };
}

function productCategoryId(pid: ProductCategoryId): string {
    return `${pid.id}/${pid.provider}`;
}

function parseIntegerFromInput(input?: HTMLInputElement | null): number | undefined {
    if (!input) return undefined;
    const rawValue = input.value;
    const parsed = parseInt(rawValue, 10);
    if (isNaN(parsed)) return undefined;
    return parsed;
}

// Note: target is baked into the component to make sure we follow the rules of hooks.
//
// We need to take wildly different paths depending on the target which causes us to use very different hooks. Baking
// the target property ensures a remount of the component.
export const GrantApplicationEditor: (target: RequestTarget) => React.FunctionComponent = target => () => {
    const state = useRequestInformation(target);
    const grantFinalized = isGrantFinalized(state.editingApplication?.status);
    const [, runWork] = useAsyncCommand();
    const projectTitleRef = useRef<HTMLInputElement>(null);
    const history = useHistory();

    switch (target) {
        case RequestTarget.EXISTING_PROJECT:
            useTitle("Viewing Project");
            break;
        case RequestTarget.NEW_PROJECT:
            useTitle("Create Project");
            break;
        case RequestTarget.PERSONAL_PROJECT:
            useTitle("Personal Project");
            break;
        case RequestTarget.VIEW_APPLICATION:
            useTitle("Viewing Application");
            break;
    }

    const submitRequest = useCallback(async () => {
        if (state.targetProject === undefined) {
            snackbarStore.addFailure("Unknown target. Root level projects cannot apply for more resources.", false);
            return;
        }

        let grantRecipient: GrantRecipient = state.recipient;
        if (target === RequestTarget.NEW_PROJECT) {
            grantRecipient = {type: "new_project", projectTitle: projectTitleRef.current!.value};
        }

        const requestedResources = state.wallets.map(wb => {
            let creditsRequested = parseIntegerFromInput(
                document.querySelector<HTMLInputElement>(
                    `input[data-target="${productCategoryId(wb.wallet.paysFor)}"]`
                )
            );
            if (creditsRequested) creditsRequested = creditsRequested * 1000000;

            let quotaRequested = parseIntegerFromInput(
                document.querySelector<HTMLInputElement>(
                    `input[data-target="quota-${productCategoryId(wb.wallet.paysFor)}"]`
                )
            );
            if (quotaRequested) quotaRequested = quotaRequested * (1000 * 1000 * 1000);

            if (creditsRequested === undefined && quotaRequested === undefined) {
                return null;
            }

            return {
                creditsRequested,
                quotaRequested,
                productCategory: wb.wallet.paysFor.id,
                productProvider: wb.wallet.paysFor.provider
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
        }
    }, [state.targetProject, state.documentRef, state.recipient, state.wallets, projectTitleRef,
    state.editingApplication?.id, state.reload]);

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

    const rejectRequest = useCallback(async () => {
        if (state.editingApplication !== undefined) {
            addStandardDialog({
                title: "Reject application?",
                message: "Are you sure you wish to reject this application?",
                onConfirm: async () => {
                    await runWork(rejectGrantApplication({requestId: state.editingApplication!.id}));
                    state.reload();
                }
            });
        }
    }, [state.editingApplication?.id]);

    const closeRequest = useCallback(async () => {
        if (state.editingApplication !== undefined) {
            addStandardDialog({
                title: "Close application?",
                message: "Are you sure you wish to close this application?",
                onConfirm: async () => {
                    await runWork(closeGrantApplication({requestId: state.editingApplication!.id}));
                    state.reload();
                }
            });
        }
    }, [state.editingApplication?.id]);

    useEffect(() => {
        if (state.editingApplication !== undefined) {
            for (const resource of state.editingApplication.requestedResources) {
                const credits = resource.creditsRequested;
                const quota = resource.quotaRequested;

                if (credits) {
                    const input = document.querySelector<HTMLInputElement>(
                        `input[data-target="${productCategoryId({
                            provider: resource.productProvider,
                            id: resource.productCategory
                        })}"]`
                    );

                    if (input) {
                        input.value = (credits / 1000000).toFixed(0);
                    }
                }

                if (quota) {
                    const input = document.querySelector<HTMLInputElement>(
                        `input[data-target="quota-${productCategoryId({
                            provider: resource.productProvider,
                            id: resource.productCategory
                        })}"]`
                    );

                    if (input) {
                        input.value = (quota / (1000 * 1000 * 1000)).toFixed(0);
                    }
                }
            }
        }
    }, [state.editingApplication]);

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

                                            `${Client.userInfo?.firstNames} ${Client.userInfo?.lastName} `
                                            +

                                            `(${Client.username})`

                                        }
                                        disabled
                                    />
                                </Label>
                                <Label mb={16} mt={16}>
                                    Title
                                    <Input ref={projectTitleRef} />
                                </Label>
                            </>
                        )}

                        {target !== RequestTarget.VIEW_APPLICATION ? null : (
                            <>
                                <DashboardCard color="blue" isLoading={false}>
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
                                                                    <Toggle onChange={doNothing} scale={1.5}
                                                                        checked={state.recipient.type === "personal"} />
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td width="100%">New Project</td>
                                                                <td>
                                                                    <Toggle onChange={doNothing} scale={1.5}
                                                                        checked={state.recipient.type === "new_project"} />
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td width="100%">Existing Project</td>
                                                                <td>
                                                                    <Toggle onChange={doNothing} scale={1.5}
                                                                        checked={state.recipient.type === "existing_project"} />
                                                                </td>
                                                            </tr>
                                                        </tbody>
                                                    </table>
                                                </TableCell>
                                            </TableRow>
                                            <TableRow>
                                                <TableCell verticalAlign={"top"} mt={32}>Current Status</TableCell>
                                                <TableCell>
                                                    {
                                                        state.editingApplication!.status === GrantApplicationStatus.IN_PROGRESS ? "In progress" :
                                                            state.editingApplication!.status === GrantApplicationStatus.APPROVED ? "Approved" :
                                                                state.editingApplication!.status === GrantApplicationStatus.REJECTED ? "Rejected" :
                                                                    "Closed"
                                                    }

                                                    <ButtonGroup>

                                                        {target === RequestTarget.VIEW_APPLICATION && state.approver &&
                                                            state.editingApplication!.status === GrantApplicationStatus.IN_PROGRESS ?
                                                            <>
                                                                <Button color="green"
                                                                    onClick={approveRequest}>Approve</Button>
                                                                <Button color="red"
                                                                    onClick={rejectRequest}>Reject</Button>
                                                            </> : null
                                                        }
                                                        {target === RequestTarget.VIEW_APPLICATION && !state.approver &&
                                                            state.editingApplication!.status === GrantApplicationStatus.IN_PROGRESS ?
                                                            <>
                                                                <Button color="red" onClick={closeRequest}>Close</Button>
                                                            </> : null
                                                        }
                                                    </ButtonGroup>
                                                </TableCell>
                                            </TableRow>
                                        </tbody>
                                    </Table>
                                </DashboardCard>
                            </>
                        )}

                        <Heading.h4 mt={32}>Resources Requested</Heading.h4>

                        <ResourceContainer>
                            {state.wallets.map((it, idx) => (
                                <RequestForSingleResourceWrapper key={idx}>
                                    <DashboardCard color="blue" isLoading={false}>
                                        <table>
                                            <tbody>
                                                <tr>
                                                    <th>Product</th>
                                                    <td>
                                                        {it.wallet.paysFor.provider} / {it.wallet.paysFor.id}
                                                        <Icon
                                                            name={
                                                                it.area === ProductArea.COMPUTE ?
                                                                    "cpu" :
                                                                    "ftFileSystem"
                                                            }
                                                            size={32}
                                                        />
                                                    </td>
                                                </tr>
                                                {state.editingApplication !== undefined ? null : (
                                                    <tr>
                                                        <th>Current balance</th>
                                                        <td>
                                                            <Balance
                                                                amount={it.balance}
                                                                productCategory={it.wallet.paysFor}
                                                            />
                                                        </td>
                                                    </tr>
                                                )}
                                                <tr>
                                                    <th>
                                                        {state.editingApplication !== undefined ?
                                                            "Resources requested" :
                                                            "Request additional resources"
                                                        }
                                                    </th>
                                                    <td>
                                                        <Flex alignItems={"center"}>
                                                            <Input
                                                                placeholder={"0"}
                                                                disabled={grantFinalized}
                                                                data-target={productCategoryId(it.wallet.paysFor)}
                                                                autoComplete="off"
                                                                type="number"
                                                            />
                                                            <Box ml={10} width={32} flexShrink={0}>DKK</Box>
                                                        </Flex>
                                                    </td>
                                                </tr>
                                                {it.area === ProductArea.STORAGE ? <>
                                                    <tr>
                                                        <th>
                                                            {state.editingApplication !== undefined ?
                                                                "Additional quota requested" :
                                                                "Request additional quota"
                                                            }
                                                        </th>
                                                        <td>
                                                            <Flex alignItems={"center"}>
                                                                <Input
                                                                    placeholder={"0"}
                                                                    disabled={grantFinalized}
                                                                    data-target={
                                                                        "quota-" + productCategoryId(it.wallet.paysFor)
                                                                    }
                                                                    autoComplete="off"
                                                                    type="number"
                                                                />
                                                                <Box ml={10} width={32} flexShrink={0}>GB</Box>
                                                            </Flex>
                                                        </td>
                                                    </tr>
                                                </> : null}
                                                <tr>
                                                    <th/>
                                                    <td>
                                                        <HelpText>
                                                            1.000 DKK ={" "}
                                                            <BalanceExplainer
                                                                amount={1_000_000_000}
                                                                productCategory={it.wallet.paysFor}
                                                            />
                                                        </HelpText>
                                                    </td>
                                                </tr>
                                            </tbody>
                                        </table>
                                    </DashboardCard>
                                </RequestForSingleResourceWrapper>
                            ))}
                        </ResourceContainer>

                        <CommentApplicationWrapper>
                            <RequestFormContainer>
                                <Heading.h4>Application</Heading.h4>
                                <TextArea disabled={grantFinalized} rows={25} ref={state.documentRef} />
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
                            <Button disabled={grantFinalized} fullWidth onClick={submitRequest}>
                                {target === RequestTarget.VIEW_APPLICATION ?
                                    <>Edit Request {state.approver ? "(Does not approve/reject)" : null}</> :
                                    <>Submit request</>
                                }
                            </Button>
                        </Box>


                    </Box>
                </Flex>
            }
        />
    );
};

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
    const [, runCommand] = useAsyncCommand();
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

        <div className={"body"}>
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
    const [loading, runWork] = useAsyncCommand();
    const submitComment = useCallback(async (e) => {
        e.preventDefault();

        await runWork(commentOnGrantApplication({
            requestId: applicationId,
            comment: commentBoxRef.current!.value
        }));
        reload();
        commentBoxRef.current!.value = "";
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
