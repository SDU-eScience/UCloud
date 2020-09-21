import * as React from "react";
import {useEffect, useRef, useState} from "react";
import {
    AddressApplication, applyForAddress,
    InternetProtocol,
    listAddressApplications,
    listMyAddresses,
    PortAndProtocol,
    PublicIP, releaseAddress,
    openPorts, closePorts, ApplicationStatus, readableApplicationStatus
} from "Applications/IPAddresses/index";
import styled from "styled-components";
import Spinner from "LoadingIcon/LoadingIcon";
import {
    Box,
    Button,
    ButtonGroup,
    ConfirmButton,
    Flex,
    Icon,
    Input,
    Label,
    SelectableText,
    SelectableTextWrapper, Text, TextArea, theme
} from "ui-components";
import {useAsyncCommand, useCloudAPI} from "Authentication/DataHook";
import {useProjectId} from "Project";
import {emptyPage} from "DefaultObjects";
import Error from "ui-components/Error";
import * as Pagination from "Pagination";
import {NoResultsCardBody} from "Dashboard/Dashboard";
import Table, {TableCell, TableHeader, TableHeaderCell, TableRow} from "ui-components/Table";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {formatRelative} from "date-fns/esm";
import {enGB} from "date-fns/locale";
import {shortUUID} from "UtilityFunctions";

export interface IPAddressManagementProps {
    onSelect?: (ip: PublicIP) => void;
}

const Wrapper = styled.div`
    width: 800px;
    height: 80vh;
`;

const InnerWrapper = styled.div`
    height: calc(100% - 40px);
    overflow-y: auto;
    overflow-x: hidden;
    
    ul {
        margin: 0;
        padding: 0;
        padding-left: 15px;
    }
    
    ${TableRow} {
        vertical-align: top;
    }
`;

export const IPAddressManagement: React.FunctionComponent<IPAddressManagementProps> = props => {
    const PAGE_AVAILABLE = 0;
    const PAGE_REQUESTS = 1;
    const PAGE_EDIT = 2;
    const PAGE_NEW_REQUEST = 3;
    const PAGE_CLOSED = 4

    const projectId = useProjectId();
    const [activePage, setActivePage] = useState<number>(PAGE_AVAILABLE);
    const [myAddresses, fetchMyAddresses, myAddressesParams] = useCloudAPI<Page<PublicIP>>({noop: true}, emptyPage);
    const [myPendingApplications, fetchMyPendingApplications, myPendingApplicationsParams] = useCloudAPI<Page<AddressApplication>>(
        {noop: true},
        emptyPage
    );
    const [myClosedApplications, fetchMyClosedApplications, myClosedApplicationsParams] = useCloudAPI<Page<AddressApplication>>(
        {noop: true},
        emptyPage
    );

    const [editingIp, setEditingIp] = useState<string | null>(null);
    const [editingProtocol, setEditingProtocol] = useState<InternetProtocol>(InternetProtocol.TCP);
    const editing = myAddresses.data.items.find(it => it.ipAddress === editingIp);
    const [commandLoading, invokeCommand] = useAsyncCommand();
    const requestRef = useRef<HTMLTextAreaElement>(null);

    useEffect(() => {
        fetchMyAddresses(listMyAddresses({itemsPerPage: 25, page: 0}));
    }, [projectId]);

    useEffect(() => {
        fetchMyPendingApplications(listAddressApplications({pending: true, itemsPerPage: 25, page: 0}));
        fetchMyClosedApplications(listAddressApplications({pending: false, itemsPerPage: 25, page: 0}));
    }, [projectId]);

    const onUse = (ip: PublicIP) => () => {
        if (props.onSelect) props.onSelect(ip);
    };

    const onDelete = (ip: PublicIP) => async () => {
        await invokeCommand(releaseAddress({id: ip.id}));
        fetchMyAddresses({...myAddressesParams});
    };

    const onEdit = (ip: PublicIP) => () => {
        setEditingIp(ip.ipAddress);
        setActivePage(PAGE_EDIT);
    };

    const onEditSubmit = async (portAndProtocol: PortAndProtocol): Promise<void> => {
        if (editing === undefined) {
            snackbarStore.addFailure("Could not add port (internal error)", false);
            return;
        }

        await invokeCommand(openPorts({id: editing.id, portList: [portAndProtocol]}));
        fetchMyAddresses({...myAddressesParams});
    };


    const onClosePort = async (portAndProtocol: PortAndProtocol): Promise<void> => {
        if (editing === undefined) {
            snackbarStore.addFailure("Could not close port (internal error)", false);
            return;
        }

        await invokeCommand(closePorts({id: editing.id, portList: [portAndProtocol]}));
        fetchMyAddresses({...myAddressesParams});
    };

    const onRequestSubmit = async(): Promise<void> => {
        const application = requestRef.current!.value;
        await invokeCommand(applyForAddress({application}));
        fetchMyPendingApplications({...myPendingApplicationsParams});
        setActivePage(PAGE_REQUESTS);
    };

    return <Wrapper>
        <SelectableTextWrapper mb={16}>
            <SelectableText selected={activePage === 0} onClick={() => setActivePage(PAGE_AVAILABLE)}>
                Available IP Addresses {myAddresses.loading ? null : <>({myAddresses.data.itemsInTotal})</>}
            </SelectableText>
            <SelectableText selected={activePage === 1} onClick={() => setActivePage(PAGE_REQUESTS)}>
                Active Requests {myPendingApplications.loading ? null : <>({myPendingApplications.data.itemsInTotal})</>}
            </SelectableText>
            <SelectableText selected={activePage === 4} onClick={() => setActivePage(PAGE_CLOSED)}>
                Closed Requests {myClosedApplications.loading ? null : <>({myClosedApplications.data.itemsInTotal})</>}
            </SelectableText>
        </SelectableTextWrapper>

        <InnerWrapper>
            {activePage !== PAGE_AVAILABLE ? null : (
                <>
                    {!myAddresses.error ? null : <Error error={myAddresses.error.why}/>}
                    <Pagination.List
                        page={myAddresses.data}
                        loading={myAddresses.loading}
                        onPageChanged={(newPage) => {
                            fetchMyAddresses(listMyAddresses({itemsPerPage: 25, page: newPage}));
                        }}
                        customEmptyPage={
                            <NoResultsCardBody title={"No available IP addresses"}>
                                <Button type={"button"} onClick={() => setActivePage(PAGE_REQUESTS)}>
                                    Apply for an IP address
                                </Button>
                            </NoResultsCardBody>
                        }
                        pageRenderer={() => {
                            return <>
                                <Table>
                                    <TableHeader>
                                        <TableRow>
                                            <TableHeaderCell textAlign={"left"}>IP Address</TableHeaderCell>
                                            <TableHeaderCell textAlign={"left"}>Ports</TableHeaderCell>
                                            <TableHeaderCell/>
                                        </TableRow>
                                    </TableHeader>
                                    <tbody>
                                    {myAddresses.data.items.map(addr => {
                                        return <TableRow key={addr.id}>
                                            <TableCell>
                                                {addr.ipAddress}
                                                {!addr.inUseBy ? null : (
                                                    <>
                                                        <Text>In use by {shortUUID(addr.inUseBy)}</Text>
                                                    </>
                                                )}
                                            </TableCell>
                                            <TableCell>
                                                {addr.openPorts.length === 0 ?
                                                    "No open ports! You will not be able to access any services." :
                                                    <ul>
                                                        {addr.openPorts.map(it => (
                                                            <li key={it.port}>{it.port}/{it.protocol}</li>
                                                        ))}
                                                    </ul>
                                                }
                                            </TableCell>
                                            <TableCell textAlign={"right"}>
                                                <ButtonGroup>
                                                    <Button color={"purple"} type={"button"} onClick={onEdit(addr)}>
                                                        Edit
                                                    </Button>
                                                    <ConfirmButton
                                                        color={"red"}
                                                        type={"button"}
                                                        disabled={!!addr.inUseBy}
                                                        dialogWidth={"170px"}
                                                        dialog={
                                                            <>
                                                                <Text fontSize={theme.fontSizes[1]}>
                                                                    If you delete this you will no longer be able to
                                                                    use this
                                                                    in applications!
                                                                </Text>
                                                                <Button color={"red"} onClick={onDelete(addr)}>
                                                                    Confirm deletion
                                                                </Button>
                                                            </>
                                                        }
                                                    >
                                                        Delete
                                                    </ConfirmButton>
                                                    <Button
                                                        type={"button"}
                                                        disabled={!!addr.inUseBy}
                                                        onClick={onUse(addr)}
                                                    >
                                                        Use
                                                    </Button>
                                                </ButtonGroup>
                                            </TableCell>
                                        </TableRow>;
                                    })}
                                    </tbody>
                                </Table>
                            </>;
                        }}
                    />
                </>
            )}

            {activePage !== PAGE_EDIT || editing === undefined ? null : (
                <Flex height={"100%"} flexDirection={"column"}>
                    <Box mb={16}>
                        <Label>
                            IP Address
                            <Input disabled value={editing.ipAddress}/>
                        </Label>
                    </Box>

                    <Table mt={16} mb={16}>
                        <TableHeader>
                            <TableRow>
                                <TableHeaderCell textAlign={"left"}>
                                    <Label htmlFor={"editPort"}>Port</Label>
                                </TableHeaderCell>
                                <TableHeaderCell textAlign={"left"}>
                                    <Label>Protocol</Label>
                                </TableHeaderCell>
                                <TableHeaderCell textAlign={"right"}>
                                    <Label>Close</Label>
                                </TableHeaderCell>
                            </TableRow>
                        </TableHeader>
                        <tbody>

                        {editing.openPorts.map(it => (
                            <TableRow key={it.port}>
                                <TableCell>{it.port}</TableCell>
                                <TableCell>{it.protocol}</TableCell>
                                <TableCell textAlign={"right"}>
                                    <Button
                                        color={"red"}
                                        type={"button"}
                                        paddingLeft={10}
                                        paddingRight={10}
                                        onClick={() => onClosePort({port: it.port, protocol: it.protocol})}
                                    >
                                        <Icon size={16} name="trash" />
                                    </Button>
                                </TableCell>
                            </TableRow>
                        ))}
                        <TableRow>
                            <TableCell pr={20}>
                                <Input id={"editPort"} type={"number"} min={1} max={65535}/>
                            </TableCell>
                            <TableCell>
                                <ClickableDropdown
                                    trigger={editingProtocol.toString()}
                                    chevron
                                    options={Object.keys(InternetProtocol).map(it => ({text: it, value: it}))}
                                    onChange={v => setEditingProtocol(v as InternetProtocol)}
                                />
                            </TableCell>
                            <TableCell textAlign={"right"}>
                                <Button
                                    type={"button"}
                                    onClick={async () => {
                                        const portInput = document.querySelector("#editPort") as HTMLInputElement;
                                        const port = parseInt(portInput.value, 10);
                                        portInput.value = "";
                                        // NOTE(Dan): We are not resetting the protocol on purpose
                                        await onEditSubmit({port, protocol: editingProtocol});
                                    }}
                                >
                                    Add
                                </Button>
                            </TableCell>
                        </TableRow>
                        </tbody>
                    </Table>
                    <Box mb={16}>
                        Note: Your application must be restarted for changes to take effect.
                    </Box>
                    <Box flexGrow={1}/>
                    <Button type={"button"} onClick={() => setActivePage(PAGE_AVAILABLE)}>Finish editing</Button>
                </Flex>
            )}

            {activePage !== PAGE_REQUESTS ? null : (
                <Flex height={"100%"} flexDirection={"column"}>
                    {!myPendingApplications.error ? null : <Error error={myPendingApplications.error.why}/>}
                    {!myPendingApplications.loading ? null : <Spinner/>}

                    <Pagination.List
                        page={myPendingApplications.data}
                        loading={myPendingApplications.loading}
                        customEmptyPage={
                            <NoResultsCardBody title={"No active requests"}/>
                        }
                        onPageChanged={newPage => {
                            fetchMyPendingApplications(listAddressApplications({pending: true, itemsPerPage: 25, page: newPage}));
                        }}
                        pageRenderer={() => {
                            return <Table>
                                <TableHeader>
                                    <TableRow>
                                        <TableHeaderCell textAlign={"left"}>Submitted at</TableHeaderCell>
                                        <TableHeaderCell textAlign={"left"}>Application</TableHeaderCell>
                                    </TableRow>
                                </TableHeader>
                                <tbody>
                                {myPendingApplications.data.items.map(it => {
                                    return <TableRow key={it.id}>
                                        <TableCell>
                                            {formatRelative(it.createdAt, new Date(), {locale: enGB})}
                                        </TableCell>
                                        <TableCell>{it.application}</TableCell>
                                    </TableRow>;
                                })}
                                </tbody>
                            </Table>;
                        }}
                    />

                    <Box flexGrow={1}/>

                    <Button type={"button"} onClick={() => setActivePage(PAGE_NEW_REQUEST)}>
                        Apply for new address
                    </Button>
                </Flex>
            )}

            {activePage !== PAGE_CLOSED ? null : (
                <Flex height={"100%"} flexDirection={"column"}>
                    {!myClosedApplications.error ? null : <Error error={myClosedApplications.error.why}/>}
                    {!myClosedApplications.loading ? null : <Spinner/>}

                    <Pagination.List
                        page={myClosedApplications.data}
                        loading={myClosedApplications.loading}
                        customEmptyPage={
                            <NoResultsCardBody title={"No active requests"}/>
                        }
                        onPageChanged={newPage => {
                            fetchMyClosedApplications(listAddressApplications({pending: false, itemsPerPage: 25, page: newPage}));
                        }}
                        pageRenderer={() => {
                            return <Table>
                                <TableHeader>
                                    <TableRow>
                                        <TableHeaderCell textAlign={"left"}>Submitted at</TableHeaderCell>
                                        <TableHeaderCell textAlign={"left"}>Application</TableHeaderCell>
                                        <TableHeaderCell textAlign={"right"}>Status</TableHeaderCell>
                                    </TableRow>
                                </TableHeader>
                                <tbody>
                                {myClosedApplications.data.items.map(it => {
                                    return <TableRow key={it.id}>
                                        <TableCell>
                                            {formatRelative(it.createdAt, new Date(), {locale: enGB})}
                                        </TableCell>
                                        <TableCell>{it.application}</TableCell>
                                        <TableCell textAlign={"right"}>{readableApplicationStatus(it.status)}</TableCell>
                                    </TableRow>;
                                })}
                                </tbody>
                            </Table>;
                        }}
                    />

                    <Box flexGrow={1}/>

                    <Button type={"button"} onClick={() => setActivePage(PAGE_NEW_REQUEST)}>
                        Apply for new address
                    </Button>
                </Flex>
            )}



            {activePage !== PAGE_NEW_REQUEST ? null : (
                <Flex flexDirection={"column"}>
                    <TextArea
                        ref={requestRef}
                        rows={10}
                        placeholder={"Please describe briefly why you need a public IP address."}
                    />
                    <Button type={"button"} onClick={onRequestSubmit} mt={16}>
                        Submit
                    </Button>
                </Flex>
            )}
        </InnerWrapper>
    </Wrapper>;
};
