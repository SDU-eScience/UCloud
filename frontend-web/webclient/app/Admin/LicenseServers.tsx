import {deleteLicenseServer, LicenseServerAccessRight, updateLicenseServerPermission, UserEntityType} from "Applications/api";
import {useAsyncCommand} from "Authentication/DataHook";
import {Client} from "Authentication/HttpClientInstance";
import {dialogStore} from "Dialog/DialogStore";
import {MainContainer} from "MainContainer/MainContainer";
import {usePromiseKeeper} from "PromiseKeeper";
import * as React from "react";
import {SnackType} from "Snackbar/Snackbars";
import {snackbarStore} from "Snackbar/SnackbarStore";
import styled from "styled-components";
import {Box, Button, Flex, Icon, Input, Label, Text, Tooltip} from "ui-components";
import ClickableDropdown from "ui-components/ClickableDropdown";
import * as Heading from "ui-components/Heading";
import {InputLabel} from "ui-components/Input";
import Table, {TableCell, TableHeader, TableHeaderCell, TableRow} from "ui-components/Table";
import {TextSpan} from "ui-components/Text";
import {addStandardDialog} from "UtilityComponents";
import {defaultErrorHandler} from "UtilityFunctions";

const LeftAlignedTableHeader = styled(TableHeader)`
    text-align: left;
`;

function LicenseServerAclPrompt({licenseServer}: {licenseServer: LicenseServer | null}) {
    const [accessList, setAccessList] = React.useState<AclEntry[]>([]);
    const [selectedAccess, setSelectedAccess] = React.useState<LicenseServerAccessRight>(LicenseServerAccessRight.READ);
    const [commandLoading, invokeCommand] = useAsyncCommand();

    const newPermissionEntityField = React.useRef<HTMLInputElement>(null);

    async function loadAcl(serverId: string): Promise<AclEntry[]> {
        const {response} = await Client.get(`/app/license/listAcl?serverId=${serverId}`);
        return response.map(item => ({
            id: item.entity.id,
            type: item.entity.type,
            permission: item.permission
        }));
    }

    function promptDeleteAclEntry(accessEntry: AclEntry): Promise<string|null> {
        return new Promise(resolve => addStandardDialog({
            title: `Are you sure?`,
            message: (
                <Box>
                    <Text>
                        Remove access for {accessEntry.id}?
                    </Text>
                </Box>
            ),
            onConfirm: async () => {
                if (licenseServer === null) {
                    resolve(null);
                    return;
                };
                await invokeCommand(updateLicenseServerPermission(
                    {
                        serverId: licenseServer.id,
                        changes: [
                            {
                                entity: {id: accessEntry.id, type: UserEntityType.USER},
                                rights: accessEntry.permission,
                                revoke: true
                            }
                        ]
                    }
                ));
                resolve(licenseServer.id);
            },
            addToFront: true
        }));
    }

    async function loadAndSetAccessList(serverId: string) {
        setAccessList(await loadAcl(serverId));
    }

    React.useEffect(() => {
        if (licenseServer === null) return;
        loadAndSetAccessList(licenseServer.id);
    }, []);

    return (
        <Box>
            <div>
                <Flex alignItems={"center"}>
                    <Heading.h3>
                        <TextSpan color="gray">Access control for</TextSpan> {licenseServer?.name}
                    </Heading.h3>
                </Flex>
                <Box mt={16} mb={30}>
                    <form
                        onSubmit={async e => {
                            e.preventDefault();

                            const permissionEntityField = newPermissionEntityField.current;
                            if (permissionEntityField === null) return;

                            const permissionUserValue = permissionEntityField.value;

                            if (permissionUserValue === "") return;

                            if (licenseServer === null) return;
                            await invokeCommand(updateLicenseServerPermission(
                                {
                                    serverId: licenseServer.id,
                                    changes: [
                                        {
                                            entity: {id: permissionUserValue, type: UserEntityType.USER},
                                            rights: selectedAccess,
                                            revoke: false
                                        }
                                    ]
                                }
                            ));

                            await loadAndSetAccessList(licenseServer.id);
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
                                    onChange={(val: LicenseServerAccessRight) => setSelectedAccess(val)}
                                    trigger={<Box as="span" minWidth="220px">{prettifyAccessRight(selectedAccess)}</Box>}
                                    options={permissionLevels}
                                />
                            </InputLabel>
                            <Button
                                attached
                                width="200px"
                                type={"submit"}
                            >
                                Grant access
                            </Button>
                        </Flex>
                    </form>
                </Box>
                {accessList.length > 0 ? (
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
                                {accessList.map(accessEntry => (
                                    <TableRow key={accessEntry.id}>
                                        <TableCell>{accessEntry.id}</TableCell>
                                        <TableCell>{prettifyAccessRight(accessEntry.permission)}</TableCell>
                                        <TableCell textAlign="right">
                                            <Button
                                                color={"red"}
                                                type={"button"}
                                                paddingLeft={10}
                                                paddingRight={10}
                                                onClick={async () => {
                                                    const licenseServerId = await promptDeleteAclEntry(accessEntry);

                                                    if(licenseServerId !== null) {
                                                        loadAndSetAccessList(licenseServerId);
                                                    }
                                                }}
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
        </Box>
    )
}

interface LicenseServer {
    id: string;
    name: string;
    address: string;
    port: string;
    license: string | null;
}

function openAclDialog(licenseServer: LicenseServer) {
    dialogStore.addDialog(<LicenseServerAclPrompt licenseServer={licenseServer} />, () => undefined)
}


const permissionLevels = [
    {text: prettifyAccessRight(LicenseServerAccessRight.READ), value: LicenseServerAccessRight.READ},
    {text: prettifyAccessRight(LicenseServerAccessRight.READ_WRITE), value: LicenseServerAccessRight.READ_WRITE},
];

function prettifyAccessRight(accessRight: LicenseServerAccessRight): string {
    switch (accessRight) {
        case LicenseServerAccessRight.READ: {
            return "Read";
        }
        case LicenseServerAccessRight.READ_WRITE: {
            return "Read/Write";
        }
        default: {
            return "Unknown";
        }

    }
}

async function loadLicenseServers(): Promise<LicenseServer[]> {
    const {response} = await Client.get<LicenseServer[]>(`/app/license/listAll`);
    return response.map(item => ({
        id: item.id,
        name: item.name,
        address: item.address,
        port: item.port,
        license: item.license
    }));
}

interface AclEntry {
    id: string;
    type: string;
    permission: LicenseServerAccessRight;
}

export default function LicenseServers() {
    const [submitted, setSubmitted] = React.useState(false);
    const [name, setName] = React.useState("");
    const [address, setAddress] = React.useState("");
    const [port, setPort] = React.useState("");
    const [license, setLicense] = React.useState("");
    const [nameError, setNameError] = React.useState(false);
    const [addressError, setAddressError] = React.useState(false);
    const [portError, setPortError] = React.useState(false);
    const [licenseServers, setLicenseServers] = React.useState<LicenseServer[]>([]);
    const promiseKeeper = usePromiseKeeper();
    const [commandLoading, invokeCommand] = useAsyncCommand();

    React.useEffect(() => {
        loadAndSetLicenseServers();
    }, []);

    async function loadAndSetLicenseServers() {
        setLicenseServers(await loadLicenseServers());
    }

    async function submit(e: React.SyntheticEvent) {
        e.preventDefault();

        let hasNameError = false;
        let hasAddressError = false;
        let hasPortError = false;

        if (!name) hasNameError = true;
        if (!address) hasAddressError = true;
        if (!port) hasPortError = true;

        setNameError(hasNameError);
        setAddressError(hasAddressError);
        setPortError(hasPortError);

        if (!hasNameError && !hasAddressError && !hasPortError) {
            try {
                await promiseKeeper.makeCancelable(
                    Client.post("/api/app/license/new", {name, address, port, license}, "")
                ).promise;
                snackbarStore.addSnack(
                    {message: `License server '${name}' successfully added`, type: SnackType.Success}
                );
                setName("");
                setAddress("");
                setPort("");
                setLicense("");
            } catch (e) {
                defaultErrorHandler(e);
            } finally {
                loadAndSetLicenseServers()
            }
        }
    }

    if (!Client.userIsAdmin) return null;

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
                                    error={nameError}
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
                                            error={addressError}
                                            onChange={e => setAddress(e.target.value)}
                                            placeholder="IP address or URL"
                                        />
                                    </Label>
                                    <Label mb="1em" width="30%">
                                        Port
                                        <Input
                                            value={port}
                                            type="number"
                                            min={0}
                                            max={65535}
                                            leftLabel
                                            error={portError}
                                            maxLength={5}
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
                                    error={portError}
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
                            {(licenseServers.length > 0) ? (
                                <Table>
                                    <LeftAlignedTableHeader>
                                        <TableRow>
                                            <TableHeaderCell>Name</TableHeaderCell>
                                            <TableHeaderCell>Address</TableHeaderCell>
                                            <TableHeaderCell width={70}>Port</TableHeaderCell>
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
                                                <TableCell>{licenseServer.port}</TableCell>
                                                <TableCell>
                                                    {licenseServer.license !== null ? (
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
                                                                    ml="5px"
                                                                />
                                                            )}
                                                        >
                                                            {licenseServer.license}
                                                        </Tooltip>
                                                    ) : (
                                                            <Text/>
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
                                                        onClick={() =>
                                                            openAclDialog(licenseServer)
                                                        }
                                                    />
                                                </TableCell>
                                                <TableCell textAlign="right">
                                                    <Button
                                                        color={"red"}
                                                        type={"button"}
                                                        paddingLeft={10}
                                                        paddingRight={10}

                                                        onClick={() => addStandardDialog({
                                                            title: `Are you sure?`,
                                                            message: (
                                                                <Box>
                                                                    <Text>
                                                                        Remove license server {licenseServer.name}?
                                                                    </Text>
                                                                </Box>
                                                            ),
                                                            onConfirm: async () => {
                                                                await invokeCommand(deleteLicenseServer({
                                                                    id: licenseServer.id
                                                                }));
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
                </>
            )}
        />
    );
}
