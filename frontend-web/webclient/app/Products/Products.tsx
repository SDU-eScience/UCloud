import {listByProductArea, Product, ProductArea, UCLOUD_PROVIDER} from "Accounting";
import {useCloudAPI} from "Authentication/DataHook";
import {emptyPage} from "DefaultObjects";
import {MainContainer} from "MainContainer/MainContainer";
import {List} from "Pagination";
import {Card, Box, Flex, Icon, List as UIList, Text, ContainerForText} from "ui-components";
import * as React from "react";
import {capitalized} from "UtilityFunctions";
import * as Heading from "ui-components/Heading";
import {Table, TableCell, TableHeader, TableHeaderCell, TableRow} from "ui-components/Table";
import {creditFormatter} from "Project/ProjectUsage";
import {MachineTypesWrapper} from "Applications/MachineTypes";
import {Client} from "Authentication/HttpClientInstance";
import {NonAuthenticatedHeader} from "Navigation/Header";
import styled from "styled-components";
import * as ReactModal from "react-modal";
import {defaultModalStyle} from "Utilities/ModalUtilities";
import {Spacer} from "ui-components/Spacer";
import {PRODUCT_NAME} from "../../site.config.json";

function Products(): JSX.Element {
    const main = (
        <ContainerForText>
            <Heading.h2>UCloud SKUs</Heading.h2>
            <Description />

            <Box my="16px" />
            <MachineView area={ProductArea.STORAGE} />
            <Box my="16px" />
            <MachineView area={ProductArea.COMPUTE} />
        </ContainerForText>
    );

    if (!Client.isLoggedIn) return (<>
        <NonAuthenticatedHeader />
        <Box mb="72px" />
        <Box m={[0, 0, "15px"]}>
            {main}
        </Box>
    </>);

    return (<MainContainer main={main} />);
}

const DetailedView = styled(Table)`
    th {
        text-align: left;
        border-top: 1px solid rgba(34,36,38,.1);
    }
    
    th, ${TableCell} {
        padding: 16px 0;
    }
`;

function MachineView({area}: {area: string}): JSX.Element {
    const [machines, refetch] = useCloudAPI<Page<Product>>(
        listByProductArea({itemsPerPage: 100, page: 0, provider: UCLOUD_PROVIDER, area}),
        emptyPage
    );

    const [activeMachine, setActiveMachine] = React.useState<Product | undefined>(undefined);
    const isStorage = ProductArea.STORAGE === area;

    return (<>
        <Card
            my="8px"
            width={1}
            overflow="hidden"
            boxShadow="sm"
            borderWidth={0}
            borderRadius={6}
        >
            <Box style={{borderTop: `5px solid var(--blue, #f00)`}} />
            <Box px={3} py={3} height={"100%"}>
                <Heading.h3 mb={"16px"}>{capitalized(area)}</Heading.h3>

                <Flex alignItems="center">
                    <List
                        page={machines.data}
                        loading={machines.loading}
                        onPageChanged={(newPage) => refetch(listByProductArea({
                            itemsPerPage: machines.data.itemsPerPage, page: newPage, provider: UCLOUD_PROVIDER, area
                        }))}
                        pageRenderer={() => (
                            <MachineTypesWrapper>
                                <Table>
                                    <TableHeader>
                                        <TableRow>
                                            <TableHeaderCell>Name</TableHeaderCell>
                                            {isStorage ? null : <TableHeaderCell>vCPU</TableHeaderCell>}
                                            {isStorage ? null : <TableHeaderCell>RAM (GB)</TableHeaderCell>}
                                            {isStorage ? null : <TableHeaderCell>GPU</TableHeaderCell>}
                                            <TableHeaderCell>Price</TableHeaderCell>
                                            <TableHeaderCell>Description</TableHeaderCell>
                                        </TableRow>
                                    </TableHeader>
                                    <tbody>
                                        {machines.data.items.map(machine => {
                                            if (machine === null) return null;
                                            return <TableRow key={machine.id} onClick={() => setActiveMachine(machine)}>
                                                <TableCell>{machine.id}</TableCell>
                                                {isStorage ? null : <TableCell>{machine.cpu ?? "Unspecified"}</TableCell>}
                                                {isStorage ? null : <TableCell>{machine.memoryInGigs ?? "Unspecified"}</TableCell>}
                                                {isStorage ? null : <TableCell>{machine.gpu ?? 0}</TableCell>}
                                                <TableCell>
                                                    {creditFormatter(machine.pricePerUnit * (isStorage ? 30 : 60), 3)}{isStorage ? " per GB/month" : "/hour"}
                                                </TableCell>
                                                <TruncatedTableCell>{machine.description}</TruncatedTableCell>
                                            </TableRow>;
                                        })}
                                    </tbody>
                                </Table>
                            </MachineTypesWrapper>
                        )}
                    />
                </Flex>
            </Box>
        </Card>
        <ReactModal
            ariaHideApp={false}
            shouldCloseOnEsc
            shouldCloseOnOverlayClick
            onRequestClose={() => setActiveMachine(undefined)}
            isOpen={activeMachine !== undefined}
            style={defaultModalStyle}
        >
            <Spacer
                left={null}
                right={<Icon name="close" cursor="pointer" onClick={() => setActiveMachine(undefined)} />}
            />
            <Box maxWidth="650px">
                {activeMachine === undefined ? null :
                    <DetailedView>
                        <tbody>
                        <TableRow>
                            <TableHeaderCell>Name</TableHeaderCell>
                            <TableCell>{activeMachine.id}</TableCell>
                        </TableRow>
                        {area !== ProductArea.COMPUTE ? null :
                            <>
                                <TableRow>
                                    <th>vCPU</th>
                                    <TableCell>{activeMachine.cpu}</TableCell>
                                </TableRow>

                                <TableRow>
                                    <th>RAM (GB)</th>
                                    <TableCell>{activeMachine.memoryInGigs ?? "Unspecified"}</TableCell>
                                </TableRow>

                                {!activeMachine.gpu ? null :
                                    <TableRow>
                                        <th>GPU</th>
                                        <TableCell>{activeMachine.gpu}</TableCell>
                                    </TableRow>
                                }
                            </>
                        }
                        <TableRow>
                            <th>Price</th>
                            <TableCell>
                                {creditFormatter(activeMachine.pricePerUnit * (area === ProductArea.COMPUTE ? 60 : 30))}
                                {area === ProductArea.COMPUTE ? "/hour" : " per GB/month"}
                            </TableCell>
                        </TableRow>
                        <TableRow>
                            <th>Description</th>
                            <TableCell><Text pl="16px">{activeMachine.description}</Text></TableCell>
                        </TableRow>
                        </tbody>
                    </DetailedView>
                }
            </Box>
        </ReactModal>
    </>);
}

function Description(): JSX.Element {
    return (<>
        Below is the available SKUs on the {PRODUCT_NAME} platform.
        They are divided into different product areas, i.e. storage SKUs and compute SKUs.
        The prices for compute will be visible when starting a job.
    </>);
}

const TruncatedTableCell = styled(TableCell)`
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
`;

export default Products;
