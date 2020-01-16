import {setActivePage, setLoading, SetStatusLoading} from "Navigation/Redux/StatusActions";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {SidebarPages} from "ui-components/Sidebar";
import {MainContainer} from "MainContainer/MainContainer";
import * as React from "react";
import {Button, Input, Label, Flex, Box, Text, Icon, Tooltip} from "ui-components";
import * as Heading from "ui-components/Heading";
import {usePromiseKeeper} from "PromiseKeeper";
import {Client} from "Authentication/HttpClientInstance";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {SnackType} from "Snackbar/Snackbars";
import styled from "styled-components";
import {defaultErrorHandler} from "UtilityFunctions";
import {useAsyncCommand, useCloudAPI} from "Authentication/DataHook";
import Table, {TableCell, TableHeader, TableHeaderCell, TableRow} from "ui-components/Table";
import {addStandardDialog} from "UtilityComponents";
import * as ReactModal from "react-modal";
import {defaultModalStyle} from "Utilities/ModalUtilities";
import {TextSpan} from "ui-components/Text";
import {InputLabel} from "ui-components/Input";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {UserEntityType, LicenseServerAccessRight, updateLicenseServerPermission} from "Applications/api";

interface LicenseServer {
    id: string;
    name: string;
    address: string;
    port: string;
    license: string | null;
}


const permissionLevels = [
    {text: prettifyAccessRight(LicenseServerAccessRight.READ), value: LicenseServerAccessRight.READ},
    {text: prettifyAccessRight(LicenseServerAccessRight.READ_WRITE), value: LicenseServerAccessRight.READ_WRITE},
];

function prettifyAccessRight(p: LicenseServerAccessRight): string {
    switch(p) {
        case LicenseServerAccessRight.READ: {
            return "Read";
        }
        case LicenseServerAccessRight.READ_WRITE: {
            return "Read/Write";
        }
        default: {
            return "Unknown"
        }

    }
}

interface AclEntry {
    id: string;
    type: string;
    permission: LicenseServerAccessRight;
}


function updateServerPermission() {}


async function loadLicenseServers(): Promise<LicenseServer[]> {
    const {response} = await Client.get<LicenseServer[]>(`/app/license/listAll`);
    return response.map(item => {
        const entry: LicenseServer = {
            id: item.id,
            name: item.name,
            address: item.address,
            port: item.port,
            license: item.license
        };
        return entry;
    });
}

async function loadAcl(serverId: string): Promise<AclEntry[]> {
    const {response} = await Client.get(`/app/license/listAcl?serverId=${serverId}`);
    return response.map(item => {
        const entry: AclEntry = {
            id: item.entity.id,
            type: item.entity.type,
            permission: item.permission
        };
        return entry;
    });
}

function LicenseServers(props: LicenseServersOperations) {
    const [submitted, setSubmitted] = React.useState(false);
    const [name, setName] = React.useState("");
    const [address, setAddress] = React.useState("");
    const [port, setPort] = React.useState("");
    const [license, setLicense] = React.useState("");
    const [nameError, setNameError] = React.useState(false);
    const [addressError, setAddressError] = React.useState(false);
    const [portError, setPortError] = React.useState(false);
    const [licenseServers, setLicenseServers] = React.useState<LicenseServer[]>([]);
    const [isAccessListOpen, setAccessListOpen] = React.useState<boolean>(false);
    const [openAccessListServer, setOpenAccessListServer] = React.useState<LicenseServer|null>(null);
    const [accessList, setAccessList] = React.useState<AclEntry[]|null>(null);
    const [selectedAccess, setSelectedAccess] = React.useState<LicenseServerAccessRight>(LicenseServerAccessRight.READ);
    const [commandLoading, invokeCommand] = useAsyncCommand();

    const promiseKeeper = usePromiseKeeper();

    React.useEffect(() => {
        props.setActivePage();
    }, []);


    async function loadAndSetLicenseServers() {
        setLicenseServers(await loadLicenseServers());
    }

    async function loadAndSetAccessList(serverId: string) {
        setAccessList(await loadAcl(serverId));
    }


    React.useEffect(() => {
        loadAndSetLicenseServers();
    }, []);

    React.useEffect(() => {
        if (isAccessListOpen && openAccessListServer !== null) {
            loadAndSetAccessList(openAccessListServer.id);
        } else {
            setAccessList(null);
        }
    }, [isAccessListOpen]);


    const newPermissionEntityField = React.useRef<HTMLInputElement>(null);

    async function submit(e: React.SyntheticEvent) {
        e.preventDefault();

        let hasNameError = false;
        let hasAddressError = false;
        let hasPortError = false;

        if (!name) hasNameError = true;
        if (!address) hasAddressError = true;
        if (!port) hasPortError = true;

        setNameError(hasNameError)
        setAddressError(hasAddressError)
        setPortError(hasPortError);

        if (!hasNameError && !hasAddressError && !hasPortError) {
            try {
                props.setLoading(true);
                await promiseKeeper.makeCancelable(
                    Client.post("/api/app/license/new", {name, address, port, license}, "")
                ).promise;
                snackbarStore.addSnack(
                    {message: `License server '${name}' successfully added`, type: SnackType.Success}
                );
                setName(() => "");
                setAddress(() => "");
                setPort(() => "");
                setLicense(() => "");
            } catch (e) {
                const status = defaultErrorHandler(e);
            } finally {
                props.setLoading(false);
                loadAndSetLicenseServers()
            }
        }
    }

    if (!Client.userIsAdmin) return null;

    /*const {
        nameError,
        addressError,
        portError,
        name,
        address,
        port,
        submitted
    } = state;*/

    const LeftAlignedTableHeader = styled(TableHeader)`
        text-align: left;
    `;






    return (
        <MainContainer
            header={<Heading.h1>License Servers</Heading.h1>}
            headerSize={64}
            main={(
                <>
                    <Box maxWidth={800} mt={30} marginLeft="auto" marginRight="auto">
                        <form onSubmit={e => submit(e)}>
                            <Label mb="1em">
                                Name
                                <Input
                                    value={name}
                                    color={nameError ? "red" : undefined}
                                    onChange={e => setName(e.target.value)}
                                    placeholder="Identifiable name for the license server"
                                />
                            </Label>
                            <Box marginBottom={30}>
                                <Flex height={45}>
                                    <Label mb="1em">
                                        Address
                                        <Input
                                            value={address}
                                            rightLabel
                                            color={addressError ? "red" : undefined}
                                            onChange={e => setAddress(e.target.value)}
                                            placeholder="IP address or URL"
                                        />
                                    </Label>
                                    <Label mb="1em" width="30%">
                                        Port
                                        <Input
                                            value={port}
                                            leftLabel
                                            color={portError ? "red" : undefined}
                                            onChange={e => setPort(e.target.value)}
                                            placeholder="Port"
                                        />
                                    </Label>
                                </Flex>
                            </Box>
                            <Label mb="1em">
                                Key
                                <Input
                                    value={license}
                                    color={portError ? "red" : undefined}
                                    onChange={e => setLicense(e.target.value)}
                                    placeholder="License or key (if needed)"
                                />
                            </Label>
                            <Button
                                type="submit"
                                color="green"
                                disabled={submitted}
                            >
                                Add License Server
                            </Button>
                        </form>

                        <Box mt={30}>
                            { (licenseServers.length > 0) ? (
                                <Table>
                                    <LeftAlignedTableHeader>
                                        <TableRow>
                                            <TableHeaderCell>Name</TableHeaderCell>
                                            <TableHeaderCell>Address</TableHeaderCell>
                                            <TableHeaderCell width={50}>Key</TableHeaderCell>
                                            <TableHeaderCell width={70}>Access</TableHeaderCell>
                                            <TableHeaderCell width={50}>Delete</TableHeaderCell>
                                        </TableRow>
                                    </LeftAlignedTableHeader>
                                    <tbody>
                                        {licenseServers.map(licenseServer => (
                                            <TableRow key={licenseServer.id}>
                                                <TableCell>{licenseServer.name}</TableCell>
                                                <TableCell>{licenseServer.address}</TableCell>
                                                <TableCell textAlign="center">
                                                    { licenseServer.license !== null ? (
                                                        <Tooltip
                                                            tooltipContentWidth="300px"
                                                            wrapperOffsetLeft="0"
                                                            wrapperOffsetTop="4px"
                                                            right="0"
                                                            top="1"
                                                            mb="50px"
                                                            trigger={(
                                                                <Icon
                                                                    size="20px"
                                                                    mt="4px"
                                                                    mr="8px"
                                                                    color="gray"
                                                                    name="key"
                                                                />
                                                            )}
                                                        >
                                                            { licenseServer.license }
                                                        </Tooltip>
                                                    ) : (
                                                        <Text></Text>
                                                    )}
                                                </TableCell>
                                                <TableCell textAlign="center">
                                                    <Icon
                                                        cursor="pointer"
                                                        size="20px"
                                                        mt="4px"
                                                        mr="8px"
                                                        color="gray"
                                                        color2="midGray"
                                                        name="projects"
                                                        onClick={() => {
                                                            setAccessListOpen(true)
                                                            setOpenAccessListServer(licenseServer)
                                                        }}
                                                    />
                                                </TableCell>
                                                <TableCell textAlign="right">
                                                    <Button
                                                        color={"red"}
                                                        type={"button"}
                                                        onClick={() => addStandardDialog({
                                                            title: `Are you sure?`,
                                                            message: (
                                                                <Box>
                                                                    <Text>
                                                                        Remove license server {licenseServer.id}?
                                                                    </Text>
                                                                </Box>
                                                            ),
                                                            onConfirm: async () => {
                                                                /*await invokeCommand(updateLicenseServer(
                                                                    {
                                                                        licenseId: licenseServer.id,
                                                                        changes: [
                                                                            {
                                                                                name: licenseServer.name,
                                                                                address: licenseServer.address,
                                                                                port: licenseServer.port,
                                                                                license: licenseServer.license
                                                                            }
                                                                        ]
                                                                    }
                                                                ));*/
                                                                loadAndSetLicenseServers()
                                                            }
                                                        })}
                                                    >
                                                        <Icon size={16} name="trash" />
                                                    </Button>
                                                </TableCell>
                                            </TableRow>
                                        ))}
                                    </tbody>
                                </Table>
                            ) : (
                                <Text textAlign="center">No license servers found</Text>
                            )}
                        </Box>
                    </Box>
                    <ReactModal
                        isOpen={isAccessListOpen}
                        onRequestClose={() => setAccessListOpen(false)}
                        shouldCloseOnEsc={true}
                        ariaHideApp={false}
                        style={defaultModalStyle}
                    >
                        <div>
                            <Flex alignItems={"center"}>
                                <Heading.h3>
                                    <TextSpan color="gray">Access control for</TextSpan> { openAccessListServer?.name }
                                </Heading.h3>
                            </Flex>
                            <Box mt={16} mb={30}>
                                <form
                                    onSubmit={async e => {
                                        e.preventDefault();

                                        const permissionEntityField = newPermissionEntityField.current;
                                        if (permissionEntityField === null) return;

                                        const permissionUserValue = permissionEntityField.value;

                                        console.log(permissionUserValue);
                                        if (permissionUserValue === "") return;

                                        if (openAccessListServer === null) return;
                                        await invokeCommand(await updateLicenseServerPermission(
                                            {
                                                serverId: openAccessListServer.id,
                                                changes: [
                                                    {
                                                        entity: { id: permissionUserValue, type: UserEntityType.USER },
                                                        rights: selectedAccess,
                                                        revoke: false
                                                    }
                                                ]
                                            }
                                        ));

                                        if (openAccessListServer !== null) {
                                            loadAndSetAccessList(openAccessListServer.id);
                                        }

                                        permissionEntityField.value = "";
                                    }}
                                >
                                    <Flex height={45}>
                                        <Input
                                            rightLabel
                                            required
                                            type="text"
                                            ref={newPermissionEntityField}
                                            placeholder="Username"
                                        />
                                        <InputLabel width="220px" rightLabel>
                                            <ClickableDropdown
                                                chevron
                                                width="180px"
                                                onChange={(val: LicenseServerAccessRight.READ | LicenseServerAccessRight.READ_WRITE) => setSelectedAccess(val)}
                                                trigger={<Box as="span" minWidth="220px">{prettifyAccessRight(selectedAccess)}</Box>}
                                                options={permissionLevels}
                                            />
                                        </InputLabel>
                                        <Button
                                            attached
                                            width="200px"
                                            //disabled={commandLoading}
                                            type={"submit"}
                                        >
                                            Grant access
                                        </Button>
                                    </Flex>
                                </form>
                            </Box>
                            { (accessList !== null && accessList.length > 0 && isAccessListOpen) ? (
                                <Box maxHeight="80vh">
                                    <Table width="700px">
                                        <LeftAlignedTableHeader>
                                            <TableRow>
                                                <TableHeaderCell>Name</TableHeaderCell>
                                                <TableHeaderCell width={200}>Permission</TableHeaderCell>
                                                <TableHeaderCell width={50}>Delete</TableHeaderCell>
                                            </TableRow>
                                        </LeftAlignedTableHeader>
                                        <tbody>
                                            {accessList!.map(accessEntry => (
                                                <TableRow key={accessEntry.id}>
                                                    <TableCell>{accessEntry.id}</TableCell>
                                                    <TableCell>{prettifyAccessRight(accessEntry.permission)}</TableCell>
                                                    <TableCell textAlign="right">
                                                        <Button
                                                            color={"red"}
                                                            type={"button"}
                                                            onClick={() => addStandardDialog({
                                                                title: `Are you sure?`,
                                                                message: (
                                                                    <Box>
                                                                        <Text>
                                                                            Remove access for {accessEntry.id}?
                                                                        </Text>
                                                                    </Box>
                                                                ),
                                                                onConfirm: async () => {
                                                                    /*await invokeCommand(updateLicenseServer(
                                                                        {
                                                                            licenseId: licenseServer.id,
                                                                            changes: [
                                                                                {
                                                                                    name: licenseServer.name,
                                                                                    address: licenseServer.address,
                                                                                    port: licenseServer.port,
                                                                                    license: licenseServer.license
                                                                                }
                                                                            ]
                                                                        }
                                                                    ));*/
                                                                    loadAndSetLicenseServers()
                                                                }
                                                            })}
                                                        >
                                                            <Icon size={16} name="trash" />
                                                        </Button>
                                                    </TableCell>
                                                </TableRow>
                                            ))}
                                        </tbody>
                                    </Table>
                                </Box>
                            ) : (
                                <Text textAlign="center">No access entries found</Text>
                            )}
                        </div>
                    </ReactModal>
                </>
            )}
        />
    );

}

interface LicenseServersOperations extends SetStatusLoading {
    setActivePage: () => void;
}

const mapDispatchToProps = (dispatch: Dispatch): LicenseServersOperations => ({
    setActivePage: () => dispatch(setActivePage(SidebarPages.Admin)),
    setLoading: loading => dispatch(setLoading(loading))
});

export default connect(null, mapDispatchToProps)(LicenseServers);