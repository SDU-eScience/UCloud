import * as React from "react";
import {useCallback, useEffect, useRef} from "react";
import {useProjectManagementStatus} from "Project";
import {MainContainer} from "MainContainer/MainContainer";
import {ProjectBreadcrumbs} from "Project/Breadcrumbs";
import * as Heading from "ui-components/Heading";
import {Box, Button, Flex, Icon, Input, Label, TextArea, theme} from "ui-components";
import {useAsyncCommand, useCloudAPI} from "Authentication/DataHook";
import {
    ProductArea, productCategoryEquals,
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
    GrantApplicationStatus,
    GrantRecipient,
    readTemplates,
    ReadTemplatesResponse,
    ResourceRequest,
    submitGrantApplication,
    viewGrantApplication,
    ViewGrantApplicationResponse
} from "Project/Grant/index";
import {useHistory, useParams} from "react-router";
import {Client} from "Authentication/HttpClientInstance";
import {snackbarStore} from "Snackbar/SnackbarStore";

export const RequestForSingleResourceWrapper = styled.div`
    ${Icon} {
        float: right;
        margin-left: 10px;
    }
    
    table {
        max-width: 600px;
        margin: 16px;
    }
    
    th {
        width: 200px;
        min-width: 200px;
        max-width: 200px;
        text-align: left;
    }
    
    td {
        margin-left: 10px;
        width: 100%;
    }
    
    tr {
        vertical-align: top;
        height: 40px;
    }
`;

const ResourceContainer = styled.div`
    display: grid;
    grid-gap: 32px;
    grid-template-columns: repeat(auto-fit, minmax(600px, auto));
    margin: 32px 0;
`;

const RequestFormContainer = styled.div`
    display: flex;
    justify-content: center;

    ${TextArea} {
        max-width: 800px;
        width: 100%;
    }
`;

export enum RequestTarget {
    EXISTING_PROJECT = "existing",
    NEW_PROJECT = "new",
    PERSONAL_PROJECT = "personal",
    VIEW_APPLICATION = "view"
}

// eslint-disable-next-line
function useRequestInformation(target: RequestTarget) {
    let targetProject: string | undefined;
    let wallets: WalletBalance[] = [];
    let reloadWallets: () => void = () => { /* empty */ };
    let recipient: GrantRecipient;
    let applicationId: number | undefined;
    let prefilledDocument: string | undefined;

    let availableProducts: { area: ProductArea, category: ProductCategoryId }[];
    let reloadProducts: () => void;
    {
        const [products, fetchProducts] = useCloudAPI<RetrieveFromProviderResponse>(
            {noop: true},
            []
        );

        const allCategories: { category: ProductCategoryId, area: "compute" | "storage"}[] =
            products.data.map(it => ({ category: it.category, area: it.type }));

        const uniqueCategories: { category: ProductCategoryId, area: "compute" | "storage"}[] = Array.from(
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
            const {projectId, projectDetails} = useProjectManagementStatus();
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
                        status: GrantApplicationStatus.IN_PROGRESS
                    },
                    comments: []
                }
            );

            applicationId = parseInt(appId, 10);
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
            prefilledDocument = grantApplication.data.application.document;

            reloadWallets = useCallback(() => {
                fetchGrantApplication(viewGrantApplication({id: parseInt(appId, 10)}));
            }, [appId]);

            useEffect(() => {
                reloadWallets();
            }, [appId]);

            break;
    }

    useEffect(() => {
        if (targetProject) {
            fetchTemplates(readTemplates({projectId: targetProject}));
            reloadWallets();
            reloadProducts();
        }
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
                    documentRef.current.value = prefilledDocument ?? "";
                    break;
            }
        }
    }, [templates, documentRef.current, prefilledDocument]);

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

    return {wallets: mergedWallets, reloadWallets, targetProject, documentRef, templates, recipient, applicationId};
}

function productCategoryId(pid: ProductCategoryId): string {
    return `${pid.id}/${pid.provider}`;
}

// Note: target is baked into the component to make sure we follow the rules of hooks.
//
// We need to take wildly different paths depending on the target which causes us to use very different hooks. Baking
// the target property ensures a remount of the component.
export const GrantApplicationEditor: (target: RequestTarget) => React.FunctionComponent = target => () => {
    const state = useRequestInformation(target);
    const [loading, runWork] = useAsyncCommand();
    const projectTitleRef = useRef<HTMLInputElement>(null);
    const history = useHistory();

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
            const input = document.querySelector<HTMLInputElement>(
                `input[data-target="${productCategoryId(wb.wallet.paysFor)}"]`
            )!;

            const rawInput = input.value;
            const parsedInput = parseInt(rawInput, 10);
            if (isNaN(parsedInput)) return null;
            return {
                creditsRequested: parsedInput * 1000000,
                productCategory: wb.wallet.paysFor.id,
                productProvider: wb.wallet.paysFor.provider
            } as ResourceRequest;
        }).filter(it => it !== null) as ResourceRequest[];

        const response = await runWork<{id: number}>(submitGrantApplication({
            document: state.documentRef.current!.value,
            requestedBy: Client.username!,
            resourcesOwnedBy: state.targetProject!,
            status: GrantApplicationStatus.IN_PROGRESS, // This is ignored by the backend
            requestedResources,
            grantRecipient
        }));

        if (response) {
            history.push(`/project/resource-request/view/${response.id}`);
        }
    }, [state.targetProject, state.documentRef, state.recipient, state.wallets, projectTitleRef]);

    useEffect(() => {
        if (state.applicationId !== undefined) {
            for (const wallet of state.wallets) {
                const input = document.querySelector<HTMLInputElement>(
                    `input[data-target="${productCategoryId(wallet.wallet.paysFor)}"]`
                )!;

                if (input) {
                    input.value = (wallet.balance / 1000000).toFixed(0);
                }
            }
        }
    }, [state.wallets]);

    return (
        <MainContainer
            header={target === RequestTarget.EXISTING_PROJECT ?
                <ProjectBreadcrumbs crumbs={[{title: "Request for Resources"}]}/> : null
            }
            sidebar={null}
            main={
                <>
                    {target !== RequestTarget.NEW_PROJECT ? null : (
                        <>
                            <Heading.h2>Project Information</Heading.h2>
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
                                Title
                                <Input ref={projectTitleRef}/>
                            </Label>
                        </>
                    )}

                    <Heading.h2>Resources</Heading.h2>
                    <ResourceContainer>
                        {state.wallets.map((it, idx) => (
                            <RequestForSingleResourceWrapper key={idx}>
                                <DashboardCard color={theme.colors.blue} isLoading={false}>
                                    <table>
                                        <tbody>
                                        <tr>
                                            <th>Product</th>
                                            <td>
                                                {it.wallet.paysFor.id} / {it.wallet.paysFor.provider}
                                                <Icon
                                                    name={it.area === ProductArea.COMPUTE ? "cpu" : "ftFileSystem"}
                                                    size={32}
                                                />
                                            </td>
                                        </tr>
                                        {state.applicationId !== undefined ? null : (
                                            <tr>
                                                <th>Current balance</th>
                                                <td>{creditFormatter(it.balance)}</td>
                                            </tr>
                                        )}
                                        <tr>
                                            <th>
                                                {state.applicationId !== undefined ?
                                                    "Resources requested" :
                                                    "Request additional resources"
                                                }
                                            </th>
                                            <td>
                                                <Flex alignItems={"center"}>
                                                    <Input
                                                        placeholder={"0"}
                                                        data-target={productCategoryId(it.wallet.paysFor)}
                                                    />
                                                    <Box ml={10}>DKK</Box>
                                                </Flex>
                                            </td>
                                        </tr>
                                        </tbody>
                                    </table>
                                </DashboardCard>
                            </RequestForSingleResourceWrapper>
                        ))}
                    </ResourceContainer>

                    <Heading.h2>Application</Heading.h2>
                    <RequestFormContainer>
                        <TextArea rows={25} ref={state.documentRef}/>
                    </RequestFormContainer>

                    <Box p={32}>
                        <Button fullWidth onClick={submitRequest}>Submit request</Button>
                    </Box>
                </>
            }
        />
    );
};
