import {SafeLogo} from "@/Applications/AppToolLogo";
import {callAPI, useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {Client} from "@/Authentication/HttpClientInstance";
import {MainContainer} from "@/ui-components/MainContainer";
import {useCallback, useEffect} from "react";
import * as React from "react";
import {useState} from "react";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {Button, Checkbox, Flex, Label, Link, Select, Text} from "@/ui-components";
import Box from "@/ui-components/Box";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import * as Heading from "@/ui-components/Heading";
import Input, {InputLabel} from "@/ui-components/Input";
import Table, {TableCell, TableHeaderCell, TableRow} from "@/ui-components/Table";
import {PropType, stopPropagation, useEffectSkipMount} from "@/UtilityFunctions";
import {useLoading, usePage} from "@/Navigation/Redux";
import {useParams} from "react-router";
import {injectStyleSimple} from "@/Unstyled";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import {emptyPage, emptyPageV2} from "@/Utilities/PageUtilities";
import {
    ApplicationGroup,
    ApplicationSummaryWithFavorite,
    DetailedEntityWithPermission
} from "@/Applications/AppStoreApi";
import * as AppStore from "@/Applications/AppStoreApi";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";

interface AppVersion {
    version: string;
    isPublic: boolean;
}

enum AccessEntityType {
    USER = "USER",
    PROJECT_GROUP = "PROJECT_GROUP"
}

const entityTypes = [
    {text: prettifyEntityType(AccessEntityType.USER), value: AccessEntityType.USER},
    {text: prettifyEntityType(AccessEntityType.PROJECT_GROUP), value: AccessEntityType.PROJECT_GROUP},
];

type ApplicationAccessRight = PropType<DetailedEntityWithPermission, "permission">;

function prettifyAccessRight(accessRight: ApplicationAccessRight): "Can launch" {
    switch (accessRight) {
        case "LAUNCH":
            return "Can launch";
    }
}

function prettifyEntityType(entityType: AccessEntityType): string {
    switch (entityType) {
        case AccessEntityType.USER: {
            return "User";
        }
        case AccessEntityType.PROJECT_GROUP: {
            return "Project group";
        }
        default: {
            return "Unknown";
        }
    }
}

function LeftAlignedTableHeader(props: React.PropsWithChildren): React.ReactNode {
    return <thead className={LeftAlignedTableHeaderClass}>
    {props.children}
    </thead>
}

const LeftAlignedTableHeaderClass = injectStyleSimple("table-header", `
    text-align: left;
`);

export const App: React.FunctionComponent = () => {
    const name = useParams<{ name: string }>().name!;

    const [commandLoading, invokeCommand] = useCloudCommand();
    const [access, setAccess] = React.useState<ApplicationAccessRight>("LAUNCH");
    const [allGroupsPage, setGroups] = useCloudAPI<PageV2<ApplicationGroup>>(
        {noop: true},
        emptyPageV2,
    );

    const allGroups = allGroupsPage.data.items;
    const [selectedGroup, setSelectedGroup] = useState<number | undefined>(undefined);

    const [permissionEntries, fetchPermissionEntries] = useCloudAPI(
        AppStore.retrieveAcl({name: name}),
        { entries: [] }
    );

    const [apps, setAppParameters] = useCloudAPI<Page<ApplicationSummaryWithFavorite>>(
        AppStore.findByName({appName: name, itemsPerPage: 50, page: 0}),
        emptyPage
    );
    const [versions, setVersions] = useState<AppVersion[]>([]);
    const [selectedEntityType, setSelectedEntityType] = useState<AccessEntityType>(AccessEntityType.USER);

    const permissionLevels = [
        {text: prettifyAccessRight("LAUNCH"), value: "LAUNCH"}
    ];

    // Loading of permission entries
    useEffectSkipMount(() => {
        fetchPermissionEntries(AppStore.retrieveAcl({name: name}));
    }, [name]);

    useEffect(() => {
        setGroups(AppStore.browseGroups({itemsPerPage: 1000}));
    }, [name]);

    useEffect(() => {
        console.log(selectedGroup);
        if (!apps.data.items[0]) return;
        if (!selectedGroup) return;
        if (selectedGroup === apps.data.items[0].metadata.group?.metadata.id) return;

        invokeCommand(AppStore.assignApplicationToGroup({
            group: selectedGroup,
            name: apps.data.items[0].metadata.name
        }));
    }, [selectedGroup]);

    useEffect(() => {
        if (!allGroups) return;
        if (!apps.data.items[0]) return;

        setSelectedGroup(apps.data.items[0].metadata.group?.metadata.id ?? undefined);
    }, [allGroups, apps]);

    // Loading of application versions
    useEffect(() => {
        const appVersions: AppVersion[] = [];
        apps.data.items.forEach(item => {
            appVersions.push({version: item.metadata.version, isPublic: item.metadata.public});
        });
        setVersions(appVersions);
        if (apps.data.items.length && flavorField.current) {
            flavorField.current.value = apps.data.items[0].metadata.flavorName ?? "";
        }
    }, [apps.data.items]);

    usePage("Application Studio | Applications", SidebarTabId.ADMIN);

    const refresh = useCallback(() => {
        setAppParameters(AppStore.findByName({appName: name, itemsPerPage: 50, page: 0}));
        setGroups(AppStore.browseGroups({itemsPerPage: 250}));
    }, [name]);

    useSetRefreshFunction(refresh);
    useLoading(commandLoading || apps.loading);

    const appTitle = apps.data.items.length > 0 ? apps.data.items[0].metadata.title : name;
    const flavorName = apps.data.items.length > 0 ? (apps.data.items[0].metadata.flavorName ?? appTitle) : undefined;
    const userEntityField = React.useRef<HTMLInputElement>(null);
    const flavorField = React.useRef<HTMLInputElement>(null);
    const projectEntityField = React.useRef<HTMLInputElement>(null);
    const groupEntityField = React.useRef<HTMLInputElement>(null);

    const groupOptions = React.useMemo(() => {
        return allGroups.map((appGroup) => ({
            text: appGroup.specification.title,
            value: appGroup.metadata.id
        })).sort((a, b) => a.text > b.text ? 1 : -1)
    }, [allGroups]);
    console.log("groupOptions " + groupOptions.length);


    if (Client.userRole !== "ADMIN") return null;
    return (
        <MainContainer
            header={(
                <Flex justifyContent="left" verticalAlign="center" maxWidth="800px" ml="auto" mr="auto">
                    <SafeLogo name={name} type={"APPLICATION"} size={"48px"}/>
                    <Heading.h3 pt="15px" pl="15px">
                        {appTitle}
                    </Heading.h3>
                </Flex>
            )}
            headerSize={70}
            main={(<>
                <Flex flexDirection="column">
                    <Box maxWidth="800px" width="100%" ml="auto" mr="auto" mt="25px">
                        <form
                            onSubmit={async e => {
                                e.preventDefault();
                                if (commandLoading) return;

                                if (!selectedGroup) return;

                                await invokeCommand(AppStore.assignApplicationToGroup({
                                    group: selectedGroup,
                                    name,
                                }));

                                snackbarStore.addSuccess(`Added to group ${selectedGroup}`, false);

                                refresh();
                            }}
                        >
                            <Label>Group</Label>
                            <Box mb="20px" textAlign="right">
                                    <Select
                                        name="group" 
                                        value={selectedGroup}
                                        onChange={(event) => setSelectedGroup(event.target.value)}
                                    >
                                        {groupOptions.map(it => (
                                            <option value={it.value}>{it.text}</option>
                                        ))}
                                    </Select>
                                {selectedGroup ? (
                                    <Link to={`/applications/studio/g/${selectedGroup}`}>Go to group management page</Link>
                                ) : <></>}
                            </Box>

                            <Label>Flavor (name)</Label>
                            <Flex>
                                <Input rightLabel inputRef={flavorField} defaultValue={flavorName}/>
                                <Button
                                    attached
                                    onClick={async () => {
                                        if (commandLoading) return;

                                        const flavorFieldCurrent = flavorField.current;
                                        if (flavorFieldCurrent === null) return;

                                        const flavorFieldValue = flavorFieldCurrent.value;
                                        if (flavorFieldValue === "") return;

                                        await invokeCommand(AppStore.updateApplicationFlavor({
                                            applicationName: name,
                                            flavorName: flavorFieldValue
                                        }));
                                        refresh();
                                    }}
                                >
                                    Save
                                </Button>
                            </Flex>
                        </form>
                    </Box>
                    <Box maxWidth="800px" width="100%" ml="auto" mr="auto" mt="25px">
                        <Heading.h2>Permissions</Heading.h2>
                        <Box mt={16}>
                            <form
                                onSubmit={async e => {
                                    e.preventDefault();
                                    if (commandLoading) return;

                                    if (selectedEntityType === AccessEntityType.USER) {
                                        const userField = userEntityField.current;
                                        if (userField === null) return;

                                        const userValue = userField.value;
                                        if (userValue === "") return;

                                        await invokeCommand(AppStore.updateAcl({
                                            name: name,
                                            changes: [{
                                                entity: {user: userValue},
                                                rights: access,
                                                revoke: false
                                            }]
                                        }));
                                        fetchPermissionEntries(AppStore.retrieveAcl({name}));
                                        userField.value = "";
                                    } else if (selectedEntityType === AccessEntityType.PROJECT_GROUP) {
                                        const projectField = projectEntityField.current;
                                        if (projectField === null) return;

                                        const projectValue = projectField.value;
                                        if (projectValue === "") return;

                                        const groupField = groupEntityField.current;
                                        if (groupField === null) return;

                                        const groupValue = groupField.value;
                                        if (groupValue === "") return;

                                        await invokeCommand(AppStore.updateAcl({
                                            name: name,
                                            changes: [
                                                {
                                                    entity: {project: projectValue, group: groupValue},
                                                    rights: access,
                                                    revoke: false
                                                }
                                            ]
                                        }));
                                        fetchPermissionEntries(AppStore.retrieveAcl({name}));
                                        projectField.value = "";
                                        groupField.value = "";
                                    }
                                }}
                            >
                                <Flex height={45}>
                                    <InputLabel width={350} leftLabel>
                                        <ClickableDropdown
                                            chevron
                                            width="180px"
                                            onChange={(val: AccessEntityType) => setSelectedEntityType(val)}
                                            trigger={
                                                <span style={{minWidth: "100px"}}>
                                                    {prettifyEntityType(selectedEntityType)}
                                                </span>
                                            }
                                            options={entityTypes}
                                        />
                                    </InputLabel>
                                    {selectedEntityType === AccessEntityType.USER ? (
                                        <Input
                                            rightLabel
                                            leftLabel
                                            required
                                            type="text"
                                            inputRef={userEntityField}
                                            placeholder="Username"
                                        />
                                    ) : (
                                        <>
                                            <Input
                                                leftLabel
                                                rightLabel
                                                required
                                                width={180}
                                                type="text"
                                                inputRef={projectEntityField}
                                                placeholder="Project path or ID"
                                            />
                                            <Input
                                                leftLabel
                                                rightLabel
                                                required
                                                width={180}
                                                type="text"
                                                inputRef={groupEntityField}
                                                placeholder="Group name or ID"
                                            />
                                        </>
                                    )}
                                    <InputLabel width={200} rightLabel leftLabel>
                                        <ClickableDropdown
                                            chevron
                                            width="180px"
                                            onChange={(val: ApplicationAccessRight) => setAccess(val)}
                                            trigger={<span
                                                style={{minWidth: "100px"}}>{prettifyAccessRight(access)}</span>}
                                            options={permissionLevels}
                                        />
                                    </InputLabel>
                                    <Button attached width="300px" disabled={commandLoading} type={"submit"}>Add
                                        permission</Button>
                                </Flex>
                            </form>
                        </Box>
                        <Flex key={5} mb={16} mt={26}>
                            <Box width={800}>
                                {(permissionEntries.data.entries.length > 0) ? (
                                    <Table>
                                        <LeftAlignedTableHeader>
                                            <TableRow>
                                                <TableHeaderCell minWidth="300px">Name</TableHeaderCell>
                                                <TableHeaderCell width="100px">Permission</TableHeaderCell>
                                                <TableHeaderCell textAlign="right">Delete</TableHeaderCell>
                                            </TableRow>
                                        </LeftAlignedTableHeader>
                                        <tbody>
                                        {permissionEntries.data.entries.map((permissionEntry, index) => (
                                            <TableRow key={index}>
                                                <TableCell>
                                                    {(permissionEntry.entity.user) ? (
                                                        permissionEntry.entity.user
                                                    ) : (
                                                        `${permissionEntry.entity.project?.title} / ${permissionEntry.entity.group?.title}`
                                                    )}</TableCell>
                                                <TableCell>{prettifyAccessRight(permissionEntry.permission)}</TableCell>
                                                <TableCell>
                                                    <Flex justifyContent="right">
                                                        <ConfirmationButton
                                                            icon="heroTrash"
                                                            actionKey={
                                                                permissionEntry.entity.user ?? "" +
                                                                permissionEntry.entity.project?.id ?? "" +
                                                                permissionEntry.entity.group?.id ?? "" +
                                                                permissionEntry.permission
                                                            }
                                                            onAction={async () => {
                                                                await invokeCommand(AppStore.updateAcl({
                                                                    name: name,
                                                                    changes: [
                                                                        {
                                                                            entity: {
                                                                                user: permissionEntry.entity.user,
                                                                                project: permissionEntry.entity.project?.id,
                                                                                group: permissionEntry.entity.group?.id
                                                                            },
                                                                            rights: permissionEntry.permission,
                                                                            revoke: true
                                                                        }
                                                                    ]
                                                                }));
                                                                fetchPermissionEntries(AppStore.retrieveAcl({name}));
                                                            }}
                                                        />
                                                    </Flex>
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
                    <Box maxWidth="800px" width="100%" ml="auto" mr="auto" mt="25px">
                        <Heading.h2>Versions</Heading.h2>
                        <Box mb={26} mt={26}>
                            <Table>
                                <LeftAlignedTableHeader>
                                    <TableRow>
                                        <TableHeaderCell width={100}>Version</TableHeaderCell>
                                        <TableHeaderCell>Settings</TableHeaderCell>
                                    </TableRow>
                                </LeftAlignedTableHeader>
                                <tbody>
                                {versions.map(version => (
                                    <TableRow key={version.version}>
                                        <TableCell>
                                            <div className={WordBreakDivClass}>
                                                {version.version}
                                            </div>
                                        </TableCell>
                                        <TableCell>
                                            <Box mb={26} mt={16}>
                                                <Label>
                                                    <Flex>
                                                        <Checkbox
                                                            checked={version.isPublic}
                                                            onChange={stopPropagation}
                                                            onClick={() => {
                                                                callAPI(AppStore.updatePublicFlag({
                                                                    name,
                                                                    version: version.version,
                                                                    public: !version.isPublic
                                                                }));

                                                                setVersions(versions.map(v =>
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
                                                    <Box ml={28}>Everyone can see and launch this version
                                                        of {appTitle}.</Box>
                                                ) : (
                                                    <Box ml={28}>Access to this version is restricted as defined in
                                                        Permissions.</Box>
                                                )}
                                            </Box>
                                        </TableCell>
                                    </TableRow>
                                ))}
                                </tbody>
                            </Table>
                        </Box>
                    </Box>
                </Flex>
            </>)}
        />
    );
};

const WordBreakDivClass = injectStyleSimple("work-break", `
    word-break: break-word;
    width: 100%;
`);

export default App;
