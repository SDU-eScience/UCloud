import * as React from "react";
import {useProjectManagementStatus} from "Project/index";
import {MainContainer} from "MainContainer/MainContainer";
import {ProjectBreadcrumbs} from "Project/Breadcrumbs";
import * as Heading from "ui-components/Heading";
import {Box, Button, Flex, Icon, Input, TextArea, theme} from "ui-components";
import {useCloudAPI} from "Authentication/DataHook";
import {ProductArea, retrieveBalance, RetrieveBalanceResponse} from "Accounting";
import {useCallback, useEffect} from "react";
import {creditFormatter} from "Project/ProjectUsage";
import styled from "styled-components";
import {DashboardCard} from "Dashboard/Dashboard";

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

export const ResourceRequest: React.FunctionComponent = () => {
    const {projectId} = useProjectManagementStatus();
    const [wallets, setWalletParams] = useCloudAPI<RetrieveBalanceResponse>(
        retrieveBalance({id: projectId, type: "PROJECT", includeChildren: true}),
        {wallets: []}
    );
    const projectWallets = wallets.data.wallets.filter(it => it.wallet.id === projectId);

    const reloadWallets = useCallback(() => {
        setWalletParams(retrieveBalance({id: projectId, type: "PROJECT", includeChildren: true}));
    }, [setWalletParams, projectId]);

    useEffect(() => {
        reloadWallets();
    }, [projectId]);

    return (
        <MainContainer
            header={<ProjectBreadcrumbs crumbs={[{title: "Request for Resources"}]}/>}
            sidebar={null}
            main={
                <>
                    <Heading.h2>Resources</Heading.h2>
                    <p style={{color: "red"}}>In this section you will specify which resources you need. NOT YET
                        IMPLEMENTED</p>
                    <ResourceContainer>
                    {projectWallets.map((it, idx) => (
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
                                            <Input value={"0"} />
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
                    <p style={{color: "red"}}>In this section you will fill out a template provided by the parent
                        project. NOT YET IMPLEMENTED</p>

                    <RequestFormContainer>
                        <TextArea rows={25}/>
                    </RequestFormContainer>

                    <Box p={32}>
                        <Button fullWidth>Submit request</Button>
                    </Box>
                </>
            }
        />
    );
};
