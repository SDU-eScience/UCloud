import {
    deleteLicenseServer,
    LicenseServerAccessRight,
    updateLicenseServerPermission,
    UserEntityType,
    addLicenseServerTag,
    deleteLicenseServerTag,
    DetailedAccessEntity
} from "Applications/api";
import {useAsyncCommand} from "Authentication/DataHook";
import {Client} from "Authentication/HttpClientInstance";
import {dialogStore} from "Dialog/DialogStore";
import {MainContainer} from "MainContainer/MainContainer";
import {usePromiseKeeper} from "PromiseKeeper";
import * as React from "react";
import * as ReactModal from "react-modal";
import {snackbarStore} from "Snackbar/SnackbarStore";
import styled from "styled-components";
import {Box, Button, Flex, Icon, Input, Label, Text, Tooltip, Card} from "ui-components";
import ClickableDropdown from "ui-components/ClickableDropdown";
import * as Heading from "ui-components/Heading";
import {InputLabel} from "ui-components/Input";
import Table, {TableCell, TableHeader, TableHeaderCell, TableRow} from "ui-components/Table";
import {TextSpan} from "ui-components/Text";
import {addStandardDialog} from "UtilityComponents";
import {defaultErrorHandler} from "UtilityFunctions";
import {defaultModalStyle} from "Utilities/ModalUtilities";
import {useTitle} from "Navigation/Redux/StatusActions";
import {useSidebarPage, SidebarPages} from "ui-components/Sidebar";

const LeftAlignedTableHeader = styled(TableHeader)`
    text-align: left;
`;

function LicenseServerTagsPrompt({licenseServer}: {licenseServer: LicenseServer | null}): JSX.Element {
    const [tagList, setTagList] = React.useState<TagEntry[]>([]);
    const [, invokeCommand] = useAsyncCommand();

    const newTagField = React.useRef<HTMLInputElement>(null);

    async function loadTags(serverId: string): Promise<TagEntry[]> {
        const {response} = await Client.get<{tags: string[]}>(`/app/license/tag/list?serverId=${serverId}`);
        return response.tags.map(item => ({
            name: item
        }));
    }

    function promptDeleteTag(tag: TagEntry): Promise<string | null> {
        return new Promise(resolve => addStandardDialog({
            title: `Are you sure?`,
            message: (
                <Box>
                    <Text>
                        Delete tag {tag.name}?
                    </Text>
                </Box>
            ),
            onConfirm: async () => {
                if (licenseServer === null) {
                    resolve(null);
                    return;
                }
                await invokeCommand(deleteLicenseServerTag(
                    {
                        serverId: licenseServer.id,
                        tag: tag.name
                    }
                ));
                resolve(licenseServer.id);
            },
            addToFront: true
        }));
    }

    async function loadAndSetTagList(serverId: string): Promise<void> {
        setTagList(await loadTags(serverId));
    }

    React.useEffect(() => {
        if (licenseServer === null) return;
        loadAndSetTagList(licenseServer.id);
    }, []);

    return (
        <Box>
            <div>
                <Flex alignItems={"center"}>
                    <Heading.h3>
                        <TextSpan color="gray">Tags for</TextSpan> {licenseServer?.name}
                    </Heading.h3>
                </Flex>
                <Box mt={16} mb={30}>
                    <form
                        onSubmit={async e => {
                            e.preventDefault();

                            const tagField = newTagField.current;
                            if (tagField === null) return;

                            const tagValue = tagField.value;

                            if (tagValue === "") return;

                            if (licenseServer === null) return;
                            await invokeCommand(addLicenseServerTag(
                                {
                                    serverId: licenseServer.id,
                                    tag: tagValue
                                }
                            ));

                            await loadAndSetTagList(licenseServer.id);
                            tagField.value = "";
                        }}
                    >
                        <Flex height={45}>
                            <Input
                                rightLabel
                                required
                                type="text"
                                ref={newTagField}
                                placeholder="Name of tag"
                            />
                            <Button
                                attached
                                width="200px"
                                type={"submit"}
                            >
                                Add tag
                            </Button>
                        </Flex>
                    </form>
                </Box>
                {tagList.length > 0 ? (
                    <Box maxHeight="80vh">
                        <Table width="500px">
                            <LeftAlignedTableHeader>
                                <TableRow>
                                    <TableHeaderCell>Tag</TableHeaderCell>
                                    <TableHeaderCell width={50}>Delete</TableHeaderCell>
                                </TableRow>
                            </LeftAlignedTableHeader>
                            <tbody>
                                {tagList.map(tagEntry => (
                                    <TableRow key={tagEntry.name}>
                                        <TableCell>{tagEntry.name}</TableCell>
                                        <TableCell textAlign="right">
                                            <Button
                                                color={"red"}
                                                type={"button"}
                                                paddingLeft={10}
                                                paddingRight={10}
                                                onClick={async () => {
                                                    const licenseServerId = await promptDeleteTag(tagEntry);

                                                    if (licenseServerId !== null) {
                                                        loadAndSetTagList(licenseServerId);
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
                        <Text textAlign="center">No tags found</Text>
                    )}
            </div>
        </Box>
    );
}

function LicenseServerAclPrompt({licenseServer}: {licenseServer: LicenseServer | null}): JSX.Element {
    const [accessList, setAccessList] = React.useState<AclEntry[]>([]);
    const [selectedAccess, setSelectedAccess] = React.useState<LicenseServerAccessRight>(LicenseServerAccessRight.READ);
    const [selectedEntityType, setSelectedEntityType] = React.useState<UserEntityType>(UserEntityType.USER);
    const [accessEntryToDelete, setAccessEntryToDelete] = React.useState<AclEntry | null>(null);
    const [, invokeCommand] = useAsyncCommand();
    const promises = usePromiseKeeper()

    const userEntityField = React.useRef<HTMLInputElement>(null);
    const projectEntityField = React.useRef<HTMLInputElement>(null);
    const groupEntityField = React.useRef<HTMLInputElement>(null);

    async function loadAcl(serverId: string): Promise<void> {
        try {
            const {response} = await (
                await promises.makeCancelable(
                    Client.get<AclEntry[]>(`/app/license/listAcl?serverId=${serverId}`)
                ).promise
            );
            setAccessList(response);
        } catch (err) {
            if (!promises.canceledKeeper) {
                snackbarStore.addFailure("Failed to load License Server Permissions", false);
            }
        }
    }

    async function deleteAclEntry(): Promise<void> {
        if (licenseServer == null) return;
        if (accessEntryToDelete == null) return;
        await invokeCommand(updateLicenseServerPermission({
            serverId: licenseServer.id,
            changes: [
                {
                    entity: {
                        user: accessEntryToDelete.entity.user,
                        project: accessEntryToDelete.entity.project ? accessEntryToDelete.entity.project.id : null,
                        group: accessEntryToDelete.entity.group ? accessEntryToDelete.entity.group.id : null
                    },
                    rights: accessEntryToDelete.permission,
                    revoke: true
                }
            ]
        }));
        setAccessEntryToDelete(null);
    }

    async function loadAndSetAccessList(serverId: string): Promise<void> {
        await loadAcl(serverId);
    }

    React.useEffect(() => {
        if (licenseServer === null) return;
        loadAndSetAccessList(licenseServer.id);
    }, []);

    return (
        <Box>
            <div>
                <ReactModal
                    ariaHideApp={false}
                    shouldCloseOnEsc
                    shouldCloseOnOverlayClick
                    onAfterClose={() => setAccessEntryToDelete(null)}
                    isOpen={accessEntryToDelete != null}
                    style={defaultModalStyle}
                >
                    <Heading.h3>Delete entry</Heading.h3>
                    <Box>
                        <Text>
                            Remove access for {accessEntryToDelete?.entity.user !== null ? (
                                accessEntryToDelete?.entity.user
                            ) : (
                                    `${accessEntryToDelete?.entity.project?.title} / ${accessEntryToDelete?.entity.group?.title}`
                            )}?
                        </Text>
                    </Box>
                    <Box mt="6px" alignItems="center">
                        <Button mr="4px" color="red" onClick={() => setAccessEntryToDelete(null)}>Cancel</Button>
                        <Button color="green" onClick={deleteAclEntry}>Delete</Button>
                    </Box>
                </ReactModal>
                <Flex alignItems="center">
                    <Heading.h3>
                        <TextSpan color="gray">Access control for</TextSpan> {licenseServer?.name}
                    </Heading.h3>
                </Flex>
                <Box mt={16} mb={30}>
                    <form
                        onSubmit={async e => {
                            e.preventDefault();

                            if (selectedEntityType == UserEntityType.USER) {
                                const userField = userEntityField.current;
                                if (userField === null) return;

                                const userValue = userField.value;

                                if (userValue === "") return;

                                if (licenseServer === null) return;

                                await invokeCommand(updateLicenseServerPermission(
                                    {
                                        serverId: licenseServer.id,
                                        changes: [
                                            {
                                                entity: {user: userValue, project: null, group: null},
                                                rights: selectedAccess,
                                                revoke: false
                                            }
                                        ]
                                    }
                                ));

                                await loadAndSetAccessList(licenseServer.id);
                                userField.value = "";

                            } else if (selectedEntityType === UserEntityType.PROJECT_GROUP) {
                                const projectField = projectEntityField.current;
                                if (projectField === null) return;

                                const projectValue = projectField.value;

                                if (projectValue === "") return;

                                const groupField = groupEntityField.current;
                                if (groupField === null) return;

                                const groupValue = groupField.value;

                                if (groupValue === "") return;

                                if (licenseServer === null) return;

                                await invokeCommand(updateLicenseServerPermission(
                                    {
                                        serverId: licenseServer.id,
                                        changes: [
                                            {
                                                entity: {user: null, project: projectValue, group: groupValue},
                                                rights: selectedAccess,
                                                revoke: false
                                            }
                                        ]
                                    }
                                ));

                                await loadAndSetAccessList(licenseServer.id);
                                projectField.value = "";
                                groupField.value = "";
                            } else {
                                return;
                            }

                        }}
                    >
                        <Flex height={45}>
                            <InputLabel width={160} leftLabel>
                                <ClickableDropdown
                                    chevron
                                    width="180px"
                                    onChange={(val: UserEntityType) => setSelectedEntityType(val)}
                                    trigger={
                                        <Box as="span" minWidth="220px">{prettifyEntityType(selectedEntityType)}</Box>
                                    }
                                    options={entityTypes}
                                />
                            </InputLabel>
                            {selectedEntityType === UserEntityType.USER ? (
                                <Input
                                    leftLabel
                                    rightLabel
                                    required
                                    type="text"
                                    ref={userEntityField}
                                    placeholder="Username"
                                />
                            ) : (
                                    <>
                                        <Input
                                            leftLabel
                                            rightLabel
                                            required
                                            width={200}
                                            type="text"
                                            ref={projectEntityField}
                                            placeholder="Project name"
                                        />
                                        <Input
                                            leftLabel
                                            rightLabel
                                            required
                                            width={200}
                                            type="text"
                                            ref={groupEntityField}
                                            placeholder="Group name"
                                        />
                                    </>
                                )}


                            <InputLabel width={160} rightLabel>
                                <ClickableDropdown
                                    chevron
                                    width="180px"
                                    onChange={(val: LicenseServerAccessRight) => setSelectedAccess(val)}
                                    trigger={
                                        <Box as="span" minWidth="220px">{prettifyAccessRight(selectedAccess)}</Box>
                                    }
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
                                    <TableHeaderCell width={150}>Type</TableHeaderCell>
                                    <TableHeaderCell width={500}>Name</TableHeaderCell>
                                    <TableHeaderCell width={200}>Permission</TableHeaderCell>
                                    <TableHeaderCell width={50}>Delete</TableHeaderCell>
                                </TableRow>
                            </LeftAlignedTableHeader>
                            <tbody>
                                {accessList.map((accessEntry, index) => (
                                    <TableRow key={index}>
                                        <TableCell>
                                            {accessEntry.entity.user ? (
                                                prettifyEntityType(UserEntityType.USER)
                                            ) : (
                                                    prettifyEntityType(UserEntityType.PROJECT_GROUP)
                                                )}
                                        </TableCell>
                                        <TableCell>
                                            {accessEntry.entity.user ? (
                                                accessEntry.entity.user
                                            ) : (
                                                    `${accessEntry.entity.project?.title} / ${accessEntry.entity.group?.title}`
                                            )}
                                        </TableCell>
                                        <TableCell>{prettifyAccessRight(accessEntry.permission)}</TableCell>
                                        <TableCell textAlign="right">
                                            <Button
                                                color="red"
                                                type="button"
                                                paddingLeft={10}
                                                paddingRight={10}
                                                onClick={() => setAccessEntryToDelete(accessEntry)}
                                            >
                                                <Icon size={16} name="trash" />
                                            </Button>
                                        </TableCell>
                                    </TableRow>
                                ))}
                            </tbody>
                        </Table>
                    </Box>
                ) : <Text textAlign="center">No access entries found</Text>}
            </div>
        </Box>
    );
}

interface LicenseServer {
    id: string;
    name: string;
    address: string;
    port: number;
    license: string | null;
}

function openAclDialog(licenseServer: LicenseServer): void {
    dialogStore.addDialog(<LicenseServerAclPrompt licenseServer={licenseServer} />, () => undefined);
}

function openTagsDialog(licenseServer: LicenseServer): void {
    dialogStore.addDialog(<LicenseServerTagsPrompt licenseServer={licenseServer} />, () => undefined)
}

const entityTypes = [
    {text: prettifyEntityType(UserEntityType.USER), value: UserEntityType.USER},
    {text: prettifyEntityType(UserEntityType.PROJECT_GROUP), value: UserEntityType.PROJECT_GROUP},
];

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

function prettifyEntityType(entityType: UserEntityType): string {
    switch (entityType) {
        case UserEntityType.USER: {
            return "User";
        }
        case UserEntityType.PROJECT_GROUP: {
            return "Project group";
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
    entity: DetailedAccessEntity;
    permission: LicenseServerAccessRight;
}

interface TagEntry {
    name: string;
}

export default function LicenseServers(): JSX.Element | null {
    const [submitted, setSubmitted] = React.useState(false);
    const [name, setName] = React.useState("");
    const [address, setAddress] = React.useState("");
    const [port, setPort] = React.useState(0);
    const [license, setLicense] = React.useState("");
    const [nameError, setNameError] = React.useState(false);
    const [addressError, setAddressError] = React.useState(false);
    const [portError, setPortError] = React.useState(false);
    const [licenseServers, setLicenseServers] = React.useState<LicenseServer[]>([]);
    const promiseKeeper = usePromiseKeeper();
    const [, invokeCommand] = useAsyncCommand();

    React.useEffect(() => {
        loadAndSetLicenseServers();
    }, []);

    useTitle("License Servers");
    useSidebarPage(SidebarPages.Admin);

    async function loadAndSetLicenseServers(): Promise<void> {
        setLicenseServers(await loadLicenseServers());
    }

    async function submit(e: React.SyntheticEvent): Promise<void> {
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
                setSubmitted(true);
                await promiseKeeper.makeCancelable(
                    Client.post("/api/app/license/new", {name, address, port, license}, "")
                ).promise;
                snackbarStore.addSuccess(`License server '${name}' successfully added`, true);
                setName("");
                setAddress("");
                setPort(0);
                setLicense("");
            } catch (err) {
                defaultErrorHandler(err);
            } finally {
                setSubmitted(false);
                loadAndSetLicenseServers();
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
                                            value={port !== 0 ? port : ""}
                                            type="number"
                                            min={0}
                                            max={65535}
                                            leftLabel
                                            error={portError}
                                            maxLength={5}
                                            onChange={e => setPort(parseInt(e.target.value, 10))}
                                            placeholder="Port"
                                        />
                                    </Label>
                                </Flex>
                            </Box>
                            <Label mb="1em">
                                Key
                                <Input
                                    value={license}
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
                            {licenseServers.length > 0 ? (
                                licenseServers.map(licenseServer => (
                                    <Card key={licenseServer.id} mb={2} padding={20} borderRadius={5}>
                                        <Flex justifyContent="space-between">
                                            <Box>
                                                <Heading.h4>{licenseServer.name}</Heading.h4>
                                                <Box>{licenseServer.address}:{licenseServer.port}</Box>
                                            </Box>
                                            <Flex>
                                                <Box>
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
                                                                    mt="8px"
                                                                    mr="8px"
                                                                    color="gray"
                                                                    name="key"
                                                                    ml="5px"
                                                                />
                                                            )}
                                                        >
                                                            {licenseServer.license}
                                                        </Tooltip>
                                                    ) : <Text />}
                                                </Box>
                                                <Box>
                                                    <Icon
                                                        cursor="pointer"
                                                        size="20px"
                                                        mt="6px"
                                                        mr="8px"
                                                        color="gray"
                                                        color2="midGray"
                                                        name="tags"
                                                        onClick={() =>
                                                            openTagsDialog(licenseServer)
                                                        }
                                                    />
                                                </Box>
                                                <Box>
                                                    <Icon
                                                        cursor="pointer"
                                                        size="20px"
                                                        mt="6px"
                                                        mr="8px"
                                                        color="gray"
                                                        color2="midGray"
                                                        name="projects"
                                                        onClick={() =>
                                                            openAclDialog(licenseServer)
                                                        }
                                                    />
                                                </Box>

                                                <Box>
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
                                                                loadAndSetLicenseServers();
                                                            }
                                                        })}
                                                    >
                                                        <Icon size={16} name="trash" />
                                                    </Button>
                                                </Box>
                                            </Flex>
                                        </Flex>
                                    </Card>
                                ))
                            ) : <Text textAlign="center">No license servers found</Text>}
                        </Box>
                    </Box>
                </>
            )}
        />
    );
}
