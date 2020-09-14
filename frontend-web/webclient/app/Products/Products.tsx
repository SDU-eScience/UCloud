import {listByProductArea, Product, ProductArea, UCLOUD_PROVIDER} from "Accounting";
import {useCloudAPI} from "Authentication/DataHook";
import {emptyPage} from "DefaultObjects";
import {MainContainer} from "MainContainer/MainContainer";
import {List} from "Pagination";
import {Card, Box, Flex, Icon, List as UIList, Text} from "ui-components";
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

function Products(): JSX.Element {

    const main = (
        <>
            <MachineView area={ProductArea.STORAGE} />
            <Box my="32px" />
            <MachineView area={ProductArea.COMPUTE} />
        </>
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
            <Flex ml={"0.5em"} alignItems="center">
                <Heading.h3>{capitalized(area)}</Heading.h3>
                <Box flexGrow={1} />
            </Flex>
            <Box px={3} py={1} height={"100%"}>
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
                                            <TableHeaderCell>Description</TableHeaderCell>
                                            <TableHeaderCell>Price</TableHeaderCell>
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
                                                <TruncatedTableCell>{machine.description}</TruncatedTableCell>
                                                <TableCell>{creditFormatter(machine.pricePerUnit * 60)}/hour</TableCell>
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
                    area === ProductArea.STORAGE ?
                        <UIList>
                            <Flex><Text bold width="120px">Name:</Text> {activeMachine.id}</Flex>
                            <Flex><Text bold width="120px">Price:</Text> {creditFormatter(activeMachine.pricePerUnit * 60)}/hour</Flex>
                            <Flex><Text bold width="120px">Description:</Text> <Text pl="16px">{activeMachine.description}</Text></Flex>
                        </UIList>
                        : <UIList>
                            <Flex><Text bold width="120px">Name:</Text> {activeMachine.id}</Flex>
                            <Flex><Text bold width="120px">CPU:</Text> {activeMachine.cpu}</Flex>
                            <Flex><Text bold width="120px">RAM:</Text> {activeMachine.memoryInGigs}</Flex>
                            {activeMachine.gpu ? <Flex><Text bold width="120px">GPU:</Text> {activeMachine.gpu}</Flex> : null}
                            <Flex><Text bold width="120px">Price:</Text> {creditFormatter(activeMachine.pricePerUnit * 60)}/hour</Flex>
                            <Flex><Text bold width="120px">Description:</Text> <Text pl="16px">{activeMachine.description}</Text></Flex>
                        </UIList>
                }
            </Box>
        </ReactModal>
    </>);
}

const TruncatedTableCell = styled(TableCell)`
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
`;

export default Products;
