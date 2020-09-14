import * as React from "react";
import {useEffect, useState} from "react";
import {
    AddressApplication,
    InternetProtocol,
    listAddressApplications,
    listMyAddresses,
    PortAndProtocol,
    PublicIP, releaseAddress,
    updatePorts
} from "Applications/IPAddresses/index";
import styled from "styled-components";
import Spinner from "LoadingIcon/LoadingIcon";
import {
    Box,
    Button,
    ButtonGroup,
    ConfirmButton,
    Flex,
    Input,
    Label,
    SelectableText,
    SelectableTextWrapper
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
import {dialogStore} from "Dialog/DialogStore";
import {addStandardDialog} from "UtilityComponents";

export interface IPAddressManagementProps {
    onSelect?: (ip: PublicIP) => void;
}

const Wrapper = styled.div`
    width: 800px;
    height: 80vh;
`;

export const IPAddressManagement: React.FunctionComponent<IPAddressManagementProps> = props => {
    const PAGE_AVAILABLE = 0;
    const PAGE_REQUESTS = 1;
    const PAGE_EDIT = 2;

    const projectId = useProjectId();
    const [activePage, setActivePage] = useState<number>(PAGE_AVAILABLE);
    const [myAddresses, fetchMyAddresses, myAddressesParams] = useCloudAPI<Page<PublicIP>>({noop: true}, emptyPage);
    const [myApplications, fetchMyApplications] = useCloudAPI<Page<AddressApplication>>(
        {noop: true},
        emptyPage
    );
    const [editingIp, setEditingIp] = useState<string | null>(null);
    const [editingProtocol, setEditingProtocol] = useState<InternetProtocol>(InternetProtocol.TCP);
    const editing = myAddresses.data.items.find(it => it.ipAddress === editingIp);
    const [commandLoading, invokeCommand] = useAsyncCommand();

    useEffect(() => {
        fetchMyAddresses(listMyAddresses({itemsPerPage: 25, page: 0}));
    }, [projectId]);

    useEffect(() => {
        fetchMyApplications(listAddressApplications({itemsPerPage: 25, page: 0}));
    }, [projectId]);

    const onUse = (ip: PublicIP) => () => {
        if (props.onSelect) props.onSelect(ip);
    };

    const onDelete = (ip: PublicIP) => () => {
        addStandardDialog({
            title: `Release IP '${ip.ipAddress}'?`,
            message: "Are you sure you wish to release this IP address? You will no longer be able to use it" +
                " in applications!",
            confirmText: "Delete",
            addToFront: true,
            onConfirm: async () => {
                console.log("Confirming!");
                await invokeCommand(releaseAddress({id: ip.id}));
                fetchMyAddresses({...myAddressesParams});
            }
        });
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

        await invokeCommand(updatePorts({id: editing.id, newPortList: editing.openPorts.concat([portAndProtocol])}));
        fetchMyAddresses({...myAddressesParams});
    };

    return <Wrapper>
        <SelectableTextWrapper mb={16}>
            <SelectableText selected={activePage === 0} onClick={() => setActivePage(PAGE_AVAILABLE)}>
                Available IP Addresses {myAddresses.loading ? null : <>({myAddresses.data.itemsInTotal})</>}
            </SelectableText>
            <SelectableText selected={activePage === 1} onClick={() => setActivePage(PAGE_REQUESTS)}>
                Active Requests {myApplications.loading ? null : <>({myApplications.data.itemsInTotal})</>}
            </SelectableText>
        </SelectableTextWrapper>

        {activePage !== PAGE_AVAILABLE ? null : (
            <>
                {!myAddresses.error ? null : <Error error={myAddresses.error.why}/>}
                {myAddresses.loading ? null : (
                    <Pagination.List
                        page={myAddresses.data}
                        loading={myAddresses.loading}
                        onPageChanged={(newPage) => {
                            fetchMyAddresses(listMyAddresses({itemsPerPage: 25, page: newPage}));
                        }}
                        customEmptyPage={
                            <NoResultsCardBody title={"No available IP addresses"}>
                                {/*A public IP address can help you achieve:
                                <ul>
                                    <li>a</li>
                                    <li>b</li>
                                    <li>c</li>
                                </ul>*/}
                                <Button type={"button"} onClick={() => setActivePage(PAGE_REQUESTS)}>
                                    Apply for an IP address
                                </Button>
                            </NoResultsCardBody>
                        }
                        pageRenderer={() => {
                            return <Table>
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
                                        <TableCell>{addr.ipAddress}</TableCell>
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
                                                    onClick={onDelete(addr)}
                                                    disabled={!!addr.inUseBy}
                                                >
                                                    Delete
                                                </ConfirmButton>
                                                <Button type={"button"} onClick={onUse(addr)}>Use</Button>
                                            </ButtonGroup>
                                        </TableCell>
                                    </TableRow>;
                                })}
                                </tbody>
                            </Table>;
                        }}
                    />
                )}
            </>
        )}

        {activePage !== PAGE_REQUESTS ? null : (
            <>
                {!myApplications.error ? null : <Error error={myApplications.error.why}/>}
                {!myApplications.loading ? null : <Spinner />}
                {myApplications.data.itemsInTotal !== 0 ? null :
                    <NoResultsCardBody title={"No active requests"} />
                }
                <Button type={"button"}>Apply for new address</Button>
            </>
        )}

        {activePage !== PAGE_EDIT || editing === undefined ? null : (
            <Flex height={"calc(100% - 40px)"} flexDirection={"column"}>
                <Box mb={16}>
                    <Label>
                        IP Address
                        <Input disabled value={editing.ipAddress} />
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
                        <TableHeaderCell/>
                        </TableRow>
                    </TableHeader>
                    <tbody>

                    {editing.openPorts.map(it => (
                        <TableRow key={it.port}>
                            <TableCell>{it.port}</TableCell>
                            <TableCell>{it.protocol}</TableCell>
                            <TableCell/>
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
    </Wrapper>;
};
