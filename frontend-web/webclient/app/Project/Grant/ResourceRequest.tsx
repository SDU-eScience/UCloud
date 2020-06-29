import * as React from "react";
import {useCallback, useEffect, useRef} from "react";
import {useProjectManagementStatus} from "Project";
import {MainContainer} from "MainContainer/MainContainer";
import {ProjectBreadcrumbs} from "Project/Breadcrumbs";
import * as Heading from "ui-components/Heading";
import {Box, Button, Flex, Icon, Input, Label, TextArea, theme} from "ui-components";
import {useAsyncCommand, useCloudAPI} from "Authentication/DataHook";
import {
    ProductArea, ProductCategoryId,
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
    GrantApplicationStatus, GrantRecipient,
    readTemplates,
    ReadTemplatesResponse, ResourceRequest,
    submitGrantApplication
} from "Project/Grant/index";
import {useParams} from "react-router";
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
    EXISTING = "existing",
    NEW = "new",
    PERSONAL = "personal"
}

// eslint-disable-next-line
function useRequestInformation(target: RequestTarget) {
    let targetProject: string | undefined;
    let wallets: WalletBalance[];
    let reloadWallets: () => void;
    let recipient: GrantRecipient;

    const documentRef = useRef<HTMLTextAreaElement>(null);
    const [templates, fetchTemplates] = useCloudAPI<ReadTemplatesResponse>(
        {noop: true},
        {existingProject: "", newProject: "", personalProject: ""}
    );

    switch (target) {
        case RequestTarget.EXISTING: {
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

        case RequestTarget.NEW:
        case RequestTarget.PERSONAL: {
            const {projectId} = useParams();
            const [products, fetchProducts] = useCloudAPI<RetrieveFromProviderResponse>(
                {noop: true},
                []
            );
            targetProject = projectId;

            const allCategories: { category: ProductCategoryId, area: "compute" | "storage"}[] =
                products.data.map(it => ({ category: it.category, area: it.type }));

            const uniqueCategories: { category: ProductCategoryId, area: "compute" | "storage"}[] = Array.from(
                new Set(allCategories.map(it => JSON.stringify(it))).values()
            ).map(it => JSON.parse(it));

            wallets = uniqueCategories.map(it => {
                return {
                    area: it.area === "compute" ? ProductArea.COMPUTE : ProductArea.STORAGE,
                    wallet: {
                        id: "unknown",
                        paysFor: it.category,
                        type: target === RequestTarget.NEW ? "PROJECT" : "USER"
                    },
                    balance: 0
                };
            });
            reloadWallets = useCallback(() => {
                fetchProducts(retrieveFromProvider({provider: UCLOUD_PROVIDER}));
            }, []);

            if (target === RequestTarget.NEW) {
                recipient = {type: "new_project", projectTitle: "placeholder"};
            } else {
                recipient = {type: "personal", username: Client.username!};
            }
            break;
        }
    }

    useEffect(() => {
        if (targetProject) {
            fetchTemplates(readTemplates({projectId: targetProject}));
            reloadWallets();
        }
    }, [targetProject, reloadWallets]);

    useEffect(() => {
        if (documentRef.current) {
            switch (target) {
                case RequestTarget.PERSONAL:
                    documentRef.current.value = templates.data.personalProject;
                    break;
                case RequestTarget.EXISTING:
                    documentRef.current.value = templates.data.existingProject;
                    break;
                case RequestTarget.NEW:
                    documentRef.current.value = templates.data.newProject;
                    break;
            }
        }
    }, [templates, documentRef.current]);

    return {wallets, reloadWallets, targetProject, documentRef, templates, recipient};
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

    const submitRequest = useCallback(() => {
        if (state.targetProject === undefined) {
            snackbarStore.addFailure("Unknown target. Root level projects cannot apply for more resources.", false);
            return;
        }

        let grantRecipient: GrantRecipient = state.recipient;
        if (target === RequestTarget.NEW) {
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

        runWork(submitGrantApplication({
            document: state.documentRef.current!.value,
            requestedBy: Client.username!,
            resourcesOwnedBy: state.targetProject!,
            status: GrantApplicationStatus.IN_PROGRESS, // This is ignored by the backend
            requestedResources,
            grantRecipient
        }));
    }, [state.targetProject, state.documentRef, state.recipient, state.wallets, projectTitleRef]);

    return (
        <MainContainer
            header={target === RequestTarget.EXISTING ?
                <ProjectBreadcrumbs crumbs={[{title: "Request for Resources"}]}/> : null
            }
            sidebar={null}
            main={
                <>
                    {target !== RequestTarget.NEW ? null : (
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
                                        <tr>
                                            <th>Current balance</th>
                                            <td>{creditFormatter(it.balance)}</td>
                                        </tr>
                                        <tr>
                                            <th>Request additional resources</th>
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
