import {WithAllAppTags, WithAppMetadata} from "Applications";
import {
    clearLogo,
    createApplicationTag,
    deleteApplicationTag,
    listByName,
    uploadLogo,
    updateApplicationPermission,
    ApplicationAccessRight,
    UserEntityType
} from "Applications/api";
import {AppToolLogo} from "Applications/AppToolLogo";
import * as Actions from "Applications/Redux/BrowseActions";
import {TagStyle} from "Applications/View";
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
import {Page} from "Types";
import {Button, Flex, VerticalButtonGroup, Checkbox, Label} from "ui-components";
import Box from "ui-components/Box";
import ClickableDropdown from "ui-components/ClickableDropdown";
import * as Heading from "ui-components/Heading";
import Input, {HiddenInputField, InputLabel} from "ui-components/Input";
import {SidebarPages} from "ui-components/Sidebar";
import Table, {TableRow, TableHeaderCell, TableCell, TableHeader} from "ui-components/Table";
import styled from "styled-components";

interface AppOperations {
    onInit: () => void;
    setRefresh: (refresh?: () => void) => void;
    setLoading: (loading: boolean) => void;
}

const App: React.FunctionComponent<RouteComponentProps<{name: string}> & AppOperations> = props => {
    const name = props.match.params.name;
    if (Client.userRole !== "ADMIN") return null;

    const [commandLoading, invokeCommand] = useAsyncCommand();
    const [logoCacheBust, setLogoCacheBust] = useState("" + Date.now());
    const [access, setAccess] = React.useState<ApplicationAccessRight>(ApplicationAccessRight.LAUNCH);
    const [apps, setAppParameters, appParameters] =
        useCloudAPI<Page<WithAppMetadata & WithAllAppTags>>({noop: true}, emptyPage);
    const [appIsPublic, setAppIsPublic] = useState(false)

    const readEditOptions = [
        {text: "Can launch", value: ApplicationAccessRight.LAUNCH},
        {text: "Can only cancel", value: ApplicationAccessRight.CANCEL}
    ];

    const LeftAlignedTableHeader = styled(TableHeader)`
        text-align: left;
    `;


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
    const permissionEntries = [
        {
            "entity": "John",
            "permission": "Can launch"
        },
        {
            "entity": "Alice",
            "permission": "Can launch"
        },
        {
            "entity": "Bob",
            "permission": "Can launch"
        }
    ];

    const newTagField = useRef<HTMLInputElement>(null);
    const newPermissionField = useRef<HTMLInputElement>(null);

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
                <Flex flexDirection="row" flexWrap="wrap">
                    <Box width={550}>
                        <Heading.h2>Tags</Heading.h2>
                        <Box width={500} mb={46}>
                            {tags.map(tag => (
                                <Flex key={tag} mb={16}>
                                    <Box width={400}>
                                        <TagStyle to="#" key={tag}>{tag}</TagStyle>
                                    </Box>
                                    <Box width={100}>
                                        <Button
                                            fullWidth
                                            color={"red"}
                                            type={"button"}

                                            disabled={commandLoading}
                                            onClick={async () => {
                                                await invokeCommand(deleteApplicationTag({applicationName: name, tags: [tag]}));
                                                setAppParameters(listByName({...appParameters.parameters}));
                                            }}
                                        >
                                            Delete
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
                                        <Input ref={newTagField} />
                                    </Box>
                                    <Box ml={8} width={100}>
                                        <Button disabled={commandLoading} type={"submit"} fullWidth>Add tag</Button>
                                    </Box>
                                </Flex>
                            </form>
                        </Box>
                    </Box>
                    <Box width={620}>
                        <Heading.h2>Permissions</Heading.h2>
                        <Box mb={26} mt={16}>
                            <Label fontSize={2}>
                                <Checkbox
                                    checked={appIsPublic}
                                    onChange={e => e.stopPropagation()}
                                    onClick={() => setAppIsPublic(!appIsPublic)}
                                />
                                Public access to application
                            </Label>

                            {appIsPublic ? (
                                <Box ml={20}>Everyone can see and launch this application.</Box>
                            ) : (
                                    <Box ml={20}>Access and permissions to the application is defined below.</Box>
                                )}
                        </Box>
                        {appIsPublic ? null : (
                            <Box width={600} ml={20}>
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
                                        setAppParameters(listByName({...appParameters.parameters}));

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
                                        <InputLabel width={access === ApplicationAccessRight.LAUNCH ? "250px" : "350px"} rightLabel>
                                            <ClickableDropdown
                                                chevron
                                                width="180px"
                                                onChange={(val: ApplicationAccessRight.LAUNCH | ApplicationAccessRight.CANCEL) => setAccess(val)}
                                                trigger={access === ApplicationAccessRight.LAUNCH ? "Can launch" : "Can only cancel"}
                                                options={readEditOptions}
                                            />
                                        </InputLabel>
                                        <Button width="300px" disabled={commandLoading} type={"submit"} ml="5px">Add permission</Button>
                                    </Flex>
                                </form>
                                <Flex key={5} mb={16} mt={26}>
                                    <Box width={800}>
                                        <Table>
                                            <LeftAlignedTableHeader>
                                                <TableRow>
                                                    <TableHeaderCell width="300px">Name</TableHeaderCell>
                                                    <TableHeaderCell>Permission</TableHeaderCell>
                                                    <TableHeaderCell></TableHeaderCell>
                                                </TableRow>
                                            </LeftAlignedTableHeader>
                                            <tbody>
                                                {permissionEntries.map(permissionEntry => (
                                                    <TableRow>
                                                        <TableCell>{permissionEntry.entity}</TableCell>
                                                        <TableCell>{permissionEntry.permission}</TableCell>
                                                        <TableCell textAlign="right">
                                                            <Button
                                                                color={"red"}
                                                                type={"button"}
                                                            >
                                                                Delete
                                                            </Button>
                                                        </TableCell>
                                                    </TableRow>
                                                ))}
                                            </tbody>
                                        </Table>
                                    </Box>
                                </Flex>
                            </Box>
                        )}
                    </Box>
                </Flex>
            )}
        />
    );
};

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
