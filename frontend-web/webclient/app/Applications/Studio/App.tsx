import {WithAllAppTags, WithAppMetadata} from "Applications";
import {
    ApplicationAccessRight,
    ApplicationPermissionEntry,
    clearLogo,
    createApplicationTag,
    deleteApplicationTag,
    listByName,
    updateApplicationPermission,
    uploadLogo,
    UserEntity,
    UserEntityType
} from "Applications/api";
import {AppToolLogo} from "Applications/AppToolLogo";
import * as Actions from "Applications/Redux/BrowseActions";
import {Tag} from "Applications/Card";
import {useAsyncCommand, useCloudAPI} from "Authentication/DataHook";
import {Client} from "Authentication/HttpClientInstance";
import {emptyPage} from "DefaultObjects";
import {dialogStore} from "Dialog/DialogStore";
import {loadingAction, LoadingAction} from "Loading";
import {MainContainer} from "MainContainer/MainContainer";
import {HeaderActions, setPrioritizedSearch, setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {setActivePage, StatusActions, updatePageTitle} from "Navigation/Redux/StatusActions";
import {useEffect, useRef} from "react";
import * as React from "react";
import {useState} from "react";
import {connect} from "react-redux";
import {RouteComponentProps} from "react-router";
import {Dispatch} from "redux";
import {snackbarStore} from "Snackbar/SnackbarStore";
import styled from "styled-components";
import {Page} from "Types";
import {Button, Checkbox, Flex, Icon, Label, Text, VerticalButtonGroup} from "ui-components";
import Box from "ui-components/Box";
import ClickableDropdown from "ui-components/ClickableDropdown";
import * as Heading from "ui-components/Heading";
import Input, {HiddenInputField, InputLabel} from "ui-components/Input";
import {SidebarPages} from "ui-components/Sidebar";
import Table, {TableCell, TableHeader, TableHeaderCell, TableRow} from "ui-components/Table";
import {addStandardDialog} from "UtilityComponents";
import {stopPropagation} from "UtilityFunctions";

interface AppOperations {
    onInit: () => void;
    setRefresh: (refresh?: () => void) => void;
    setLoading: (loading: boolean) => void;
}

interface AppVersion {
    version: string;
    isPublic: boolean;
}

function prettifyAccessRight(accessRight: ApplicationAccessRight) {
    switch (accessRight) {
        case ApplicationAccessRight.LAUNCH:
            return "Can launch";
    }
}

async function loadApplicationPermissionEntries(appName: string): Promise<ApplicationPermissionEntry[]> {
    const {response} = await Client.get<Array<{entity: UserEntity, permission: ApplicationAccessRight}>>(`/hpc/apps/list-acl/${appName}`);
    return response.map(item => {
        const entityObj: UserEntity = { id: item.entity.id, type: item.entity.type };
        const entry: ApplicationPermissionEntry = {
            entity: entityObj,
            permission: item.permission,
        };
        return entry;
    });
}

const App: React.FunctionComponent<RouteComponentProps<{name: string}> & AppOperations> = props => {
    const name = props.match.params.name;

    const [commandLoading, invokeCommand] = useAsyncCommand();
    const [logoCacheBust, setLogoCacheBust] = useState("" + Date.now());
    const [access, setAccess] = React.useState<ApplicationAccessRight>(ApplicationAccessRight.LAUNCH);
    const [permissionEntries, setPermissionEntries] = React.useState<ApplicationPermissionEntry[]>([]);
    const [apps, setAppParameters, appParameters] =
        useCloudAPI<Page<WithAppMetadata & WithAllAppTags>>({noop: true}, emptyPage);
    const [versions, setVersions] = useState<AppVersion[]>([]);

    const permissionLevels = [
        {text: prettifyAccessRight(ApplicationAccessRight.LAUNCH), value: ApplicationAccessRight.LAUNCH}
    ];

    const LeftAlignedTableHeader = styled(TableHeader)`
        text-align: left;
    `;

    async function setPermissionsOnInit() {
        setPermissionEntries(await loadApplicationPermissionEntries(name));
    }

    // Loading of permission entries
    useEffect(() =>  {
        setPermissionsOnInit();
    }, []);

    // Loading of application versions
    useEffect(() =>  {
        const appVersions: AppVersion[] = [];
        apps.data.items.forEach(item => {
            appVersions.push({ version: item.metadata.version, isPublic: item.metadata.public });
        });
        setVersions(appVersions);
    }, [apps.data.items]);


    useEffect(() => props.onInit(), []);

    useEffect(() => {
        setAppParameters(listByName({name, itemsPerPage: 50, page: 0}));
        props.setRefresh(() => {
            setAppParameters(listByName({name, itemsPerPage: 50, page: 0}));
        });
        return () => props.setRefresh();
    }, [name]);

    useEffect(() => {
        props.setLoading(commandLoading || apps.loading);
    }, [commandLoading, apps.loading]);

    const appTitle = apps.data.items.length > 0 ? apps.data.items[0].metadata.title : name;
    const tags = apps.data.items.length > 0 ? apps.data.items[0].tags : [];
    const newTagField = useRef<HTMLInputElement>(null);
    const newPermissionField = useRef<HTMLInputElement>(null);

    if (Client.userRole !== "ADMIN") return null;
    return (
        <MainContainer
            header={(
                <Heading.h1>
                    <AppToolLogo name={name} type={"APPLICATION"} size={"64px"} cacheBust={logoCacheBust} />
                    {" "}
                    {appTitle}
                </Heading.h1>
            )}

            sidebar={(
                <VerticalButtonGroup>
                    <Button fullWidth as="label">
                        Upload Logo
                    <HiddenInputField
                            type="file"
                            onChange={async e => {
                                const target = e.target;
                                if (target.files) {
                                    const file = target.files[0];
                                    target.value = "";
                                    if (file.size > 1024 * 512) {
                                        snackbarStore.addFailure("File exceeds 512KB. Not allowed.");
                                    } else {
                                        if (await uploadLogo({name, file, type: "APPLICATION"})) {
                                            setLogoCacheBust("" + Date.now());
                                        }
                                    }
                                    dialogStore.success();
                                }
                            }}
                        />
                    </Button>

                    <Button
                        type="button"
                        color="red"
                        disabled={commandLoading}
                        onClick={async () => {
                            await invokeCommand(clearLogo({type: "APPLICATION", name}));
                            setLogoCacheBust("" + Date.now());
                        }}
                    >
                        Remove Logo
                    </Button>
                </VerticalButtonGroup>
            )}

            main={(
                <Flex flexDirection="column">
                    <Box maxWidth="650px" width="100%" ml="auto" mr="auto">
                        <Heading.h2>Tags</Heading.h2>
                        <Box mb={46} mt={26}>
                            {tags.map(tag => (
                                <Flex key={tag} mb={16}>
                                    <Box flexGrow={1}>
                                        <Tag key={tag} label={tag}/>
                                    </Box>
                                    <Box>
                                        <Button
                                            color={"red"}
                                            type={"button"}

                                            disabled={commandLoading}
                                            onClick={async () => {
                                                await invokeCommand(deleteApplicationTag({applicationName: name, tags: [tag]}));
                                                setAppParameters(listByName({...appParameters.parameters}));
                                            }}
                                        >

                                            <Icon size={16} name="trash" />
                                        </Button>
                                    </Box>
                                </Flex>
                            ))}
                            <form
                                onSubmit={async e => {
                                    e.preventDefault();
                                    if (commandLoading) return;

                                    const tagField = newTagField.current;
                                    if (tagField === null) return;

                                    const tagValue = tagField.value;
                                    if (tagValue === "") return;

                                    await invokeCommand(createApplicationTag({applicationName: name, tags: [tagValue]}));
                                    setAppParameters(listByName({...appParameters.parameters}));

                                    tagField.value = "";
                                }}
                            >
                                <Flex>
                                    <Box flexGrow={1}>
                                        <Input type="text"
                                            ref={newTagField}
                                            rightLabel
                                            height={35} />
                                    </Box>
                                    <Button disabled={commandLoading} type={"submit"} width={100} attached>Add tag</Button>
                                </Flex>
                            </form>
                        </Box>
                    </Box>
                    <Box maxWidth="650px" width="100%" ml="auto" mr="auto" mt="25px">
                        <Heading.h2>Permissions</Heading.h2>
                        <Box mt={16}>
                            <form
                                onSubmit={async e => {
                                    e.preventDefault();
                                    if (commandLoading) return;

                                    const permissionField = newPermissionField.current;
                                    if (permissionField === null) return;

                                    const permissionValue = permissionField.value;
                                    if (permissionValue === "") return;

                                    await invokeCommand(updateApplicationPermission(
                                        {
                                            applicationName: name,
                                            changes: [
                                                {
                                                    entity: { id: permissionValue, type: UserEntityType.USER },
                                                    rights: access,
                                                    revoke: false
                                                }
                                            ]
                                        }
                                    ));
                                    setPermissionEntries(await loadApplicationPermissionEntries(name));

                                    permissionField.value = "";
                                }}
                            >
                                <Flex height={45}>
                                    <Input
                                        rightLabel
                                        required
                                        type="text"
                                        ref={newPermissionField}
                                        placeholder="Username"
                                    />
                                    <InputLabel width="250px" rightLabel>
                                        <ClickableDropdown
                                            chevron
                                            width="180px"
                                            onChange={(val: ApplicationAccessRight.LAUNCH) => setAccess(val)}
                                            trigger={<Box as="span" minWidth="250px">{prettifyAccessRight(access)}</Box>}
                                            options={permissionLevels}
                                        />
                                    </InputLabel>
                                    <Button attached width="300px" disabled={commandLoading} type={"submit"}>Add permission</Button>
                                </Flex>
                            </form>
                        </Box>
                        <Flex key={5} mb={16} mt={26}>
                            <Box width={800}>
                                { (permissionEntries.length > 0) ? (
                                    <Table>
                                        <LeftAlignedTableHeader>
                                            <TableRow>
                                                <TableHeaderCell width="300px">Name</TableHeaderCell>
                                                <TableHeaderCell>Permission</TableHeaderCell>
                                                <TableHeaderCell width={50}>Delete</TableHeaderCell>
                                            </TableRow>
                                        </LeftAlignedTableHeader>
                                        <tbody>
                                            {permissionEntries.map(permissionEntry => (
                                                <TableRow key={permissionEntry.entity.id}>
                                                    <TableCell>{permissionEntry.entity.id}</TableCell>
                                                    <TableCell>{prettifyAccessRight(permissionEntry.permission)}</TableCell>
                                                    <TableCell textAlign="right">
                                                        <Button
                                                            color={"red"}
                                                            type={"button"}
                                                            onClick={() => addStandardDialog({
                                                                title: `Are you sure?`,
                                                                message: (
                                                                    <Box>
                                                                        <Text>
                                                                            Remove permission for {permissionEntry.entity.id}
                                                                        </Text>
                                                                    </Box>
                                                                ),
                                                                onConfirm: async () => {
                                                                    await invokeCommand(updateApplicationPermission(
                                                                        {
                                                                            applicationName: name,
                                                                            changes: [
                                                                                {
                                                                                    entity: {
                                                                                        id: permissionEntry.entity.id,
                                                                                        type: UserEntityType.USER
                                                                                    },
                                                                                    rights: permissionEntry.permission,
                                                                                    revoke: true
                                                                                }
                                                                            ]
                                                                        }
                                                                    ));
                                                                    await setPermissionEntries(await loadApplicationPermissionEntries(name));
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
                                    <Text textAlign="center">No explicit permissions set for this application</Text>
                                )}
                            </Box>
                        </Flex>
                    </Box>
                    <Box maxWidth="650px" width="100%" ml="auto" mr="auto" mt="25px">
                        <Heading.h2>Versions</Heading.h2>
                        <Box mb={26} mt={26}>
                            <Table>
                                <LeftAlignedTableHeader>
                                    <TableRow>
                                        <TableHeaderCell width={100}>Version</TableHeaderCell>
                                        <TableHeaderCell>Settings</TableHeaderCell>
                                        <TableHeaderCell width={50}>Delete</TableHeaderCell>
                                    </TableRow>
                                </LeftAlignedTableHeader>
                                <tbody>
                                    {versions.map(version => (
                                        <TableRow key={version.version}>
                                            <TableCell>
                                                <WordBreakBox>
                                                    {version.version}
                                                </WordBreakBox>
                                            </TableCell>
                                            <TableCell>
                                                <Box mb={26} mt={16}>
                                                    <Label fontSize={2}>
                                                        <Flex>
                                                            <Checkbox
                                                                checked={version.isPublic}
                                                                onChange={stopPropagation}
                                                                onClick={() => {
                                                                    Client.post(`/hpc/apps/setPublic`, {
                                                                        appName: name,
                                                                        appVersion: version.version,
                                                                        public: !version.isPublic
                                                                    });

                                                                    setVersions(versions.map( v =>
                                                                        (v.version === version.version) ?
                                                                        {
                                                                            version: v.version,
                                                                            isPublic: !v.isPublic
                                                                        } : v
                                                                    ));
                                                                }}
                                                            />
                                                            <Box ml={8} mt="2px">Public</Box>
                                                        </Flex>
                                                    </Label>
                                                    {version.isPublic ? (
                                                        <Box ml={28}>Everyone can see and launch this version of {appTitle}.</Box>
                                                    ) : (
                                                        <Box ml={28}>Access to this version is restricted as defined in Permissions.</Box>
                                                    )}
                                                </Box>
                                            </TableCell>
                                            <TableCell textAlign="right">
                                                <Button
                                                    color={"red"}
                                                    type={"button"}
                                                    onClick={() => addStandardDialog({
                                                        title: `Delete ${name} version ${version.version}`,
                                                        message: (
                                                            <Box>
                                                                <Text>
                                                                    Are you sure?
                                                                </Text>
                                                            </Box>
                                                        ),
                                                        onConfirm: async () => {
                                                            await Client.delete("/hpc/apps", { appName: name, appVersion: version.version });
                                                            setAppParameters(listByName({...appParameters.parameters}));
                                                        },
                                                        confirmText: "Delete"
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
                    </Box>
                </Flex>
            )}
        />
    );
};

const WordBreakBox = styled(Box)`
    word-break: break-word;
    width: 100%;
`;



const mapDispatchToProps = (
    dispatch: Dispatch<Actions.Type | HeaderActions | StatusActions | LoadingAction>
): AppOperations => ({
    onInit: () => {
        dispatch(updatePageTitle("Application Studio/Apps"));
        dispatch(setPrioritizedSearch("applications"));
        dispatch(setActivePage(SidebarPages.AppStore));
    },

    setRefresh: refresh => dispatch(setRefreshFunction(refresh)),

    setLoading: loading => dispatch(loadingAction(loading))
});

export default connect(null, mapDispatchToProps)(App);
