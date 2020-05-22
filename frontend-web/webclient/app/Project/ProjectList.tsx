import {useAsyncCommand, useCloudAPI} from "Authentication/DataHook";
import {emptyPage, ReduxObject, KeyCode} from "DefaultObjects";
import {MainContainer} from "MainContainer/MainContainer";
import * as Pagination from "Pagination";
import {
    listProjects,
    ListProjectsRequest,
    UserInProject,
    createProject,
    IngoingInvite, listIngoingInvites, acceptInvite, rejectInvite
} from "Project/index";
import * as React from "react";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {Page, Operation, Dictionary} from "Types";
import Button from "ui-components/Button";
import {Flex, Icon, List, Text, Input, Box, Checkbox, Label, Link, Tooltip} from "ui-components";
import VerticalButtonGroup from "ui-components/VerticalButtonGroup";
import {updatePageTitle, setActivePage} from "Navigation/Redux/StatusActions";
import {setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {ListRow} from "ui-components/List";
import {useHistory} from "react-router";
import {loadingAction} from "Loading";
import {dispatchSetProjectAction} from "Project/Redux";
import {projectRoleToStringIcon, toggleFavoriteProject} from "Project";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {Client} from "Authentication/HttpClientInstance";
import {stopPropagation} from "UtilityFunctions";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {ThemeColor} from "ui-components/theme";
import {useEffect, useState} from "react";
import * as Heading from "ui-components/Heading";
import {SidebarPages} from "ui-components/Sidebar";
import {Toggle} from "ui-components/Toggle";
import {Spacer} from "ui-components/Spacer";
import {ShareCardBase} from "Shares/List";
import {AvatarType, defaultAvatar} from "UserSettings/Avataaar";
import {UserAvatar} from "AvataaarLib/UserAvatar";
import {loadAvatars} from "Shares";

// eslint-disable-next-line no-underscore-dangle
const _List: React.FunctionComponent<DispatchProps & {project?: string}> = props => {
    const [archived, setArchived] = useState<boolean>(false);
    const [response, setFetchParams] = useCloudAPI<Page<UserInProject>, ListProjectsRequest>(
        {noop: true},
        emptyPage
    );

    const [ingoingInvites, fetchIngoingInvites, ingoingInvitesParams] = useCloudAPI<Page<IngoingInvite>>(
        listIngoingInvites({page: 0, itemsPerPage: 10}),
        emptyPage
    );

    const usernames = ingoingInvites.data.items.map(it => it.invitedBy);

    const [avatars, setAvatarParams, avatarParams] = useCloudAPI<{avatars: Dictionary<AvatarType>}>(
        loadAvatars({usernames: new Set(usernames)}), {avatars: {}}
    );

    React.useEffect(() => {
        if (usernames.length === 0) return;
        setAvatarParams(loadAvatars({usernames: new Set(usernames)}));
    }, [usernames.length]);


    const [creatingProject, setCreatingProject] = React.useState(false);
    const title = React.useRef<HTMLInputElement>(null);
    const [selectedProjects, setSelectedProjects] = React.useState(new Set());
    const [commandLoading, runCommand] = useAsyncCommand();

    useEffect(() => {
        props.setLoading(response.loading);
    }, [response.loading]);

    const history = useHistory();

    const reload = (): void => {
        setFetchParams(listProjects({
            page: response.data.pageNumber,
            itemsPerPage: response.data.itemsPerPage,
            archived
        }));
        fetchIngoingInvites(listIngoingInvites({page: 0, itemsPerPage: 10}));
    };

    useEffect(() => {
        props.onInit();
    }, []);

    useEffect(() => {
        props.setRefresh(reload);
        return () => props.setRefresh();
    }, [reload]);

    useEffect(() => {
        setFetchParams(listProjects({page: 0, itemsPerPage: 50, archived}));
    }, [archived]);

    const projectOperations: ProjectOperation[] = [{
        text: "Settings",
        disabled: projects => projects.length !== 1,
        icon: "properties",
        onClick: ([project]) => {
            props.setProject(project.projectId);
            history.push("/projects/view/-/settings");
        }
    }];

    return (
        <MainContainer
            headerSize={58}
            header={<Heading.h3 mb={16}>Invitations</Heading.h3>}
            main={(
                <>
                    <Pagination.List
                        customEmptyPage={<Box mb={32}>You have no invitations.</Box>}
                        loading={ingoingInvites.loading}
                        page={ingoingInvites.data}
                        onPageChanged={newPage =>
                            fetchIngoingInvites(listIngoingInvites({...ingoingInvitesParams.parameters, page: newPage}))
                        }
                        pageRenderer={() => (
                            <Box mb={32}>
                                {ingoingInvites.data.items.map(invite => (
                                    <ShareCardBase
                                        key={invite.project}
                                        title={invite.project}
                                        body={
                                            <Spacer
                                                left={<>
                                                    <UserAvatar avatar={avatars.data.avatars[invite.invitedBy] ?? defaultAvatar} mr="10px" />
                                                    <Flex alignItems="center">Invited by {invite.invitedBy}</Flex>
                                                </>}
                                                right={<Flex alignItems="center">
                                                    <Button
                                                        color="green"
                                                        height="42px"
                                                        mr={8}
                                                        onClick={async () => {
                                                            await runCommand(acceptInvite({projectId: invite.project}));
                                                            reload();
                                                        }}
                                                    >
                                                        Accept
                                                    </Button>
                                                    <Button
                                                        color="red"
                                                        height="42px"
                                                        onClick={async () => {
                                                            await runCommand(rejectInvite({projectId: invite.project}));
                                                            reload();
                                                        }}
                                                    >
                                                        Reject
                                                    </Button>
                                                </Flex>}
                                            />
                                        }
                                        bottom={<Box height="16px" />}
                                    />
                                ))}
                            </Box>
                        )}
                    />

                    <Heading.h3 mb={16}>My Projects</Heading.h3>
                    <List>
                        <ListRow
                            icon={<Box width="24px" />}
                            left={<Box height="41px" mb="-6px" alignItems="bottom"><Text>Personal project</Text></Box>}
                            leftSub={<div />}
                            right={<>
                                <Toggle scale={1.5} activeColor="green" checked={!props.project} onChange={() => {
                                    if (!props.project) return;
                                    snackbarStore.addInformation("Personal project is now the active.", false);
                                    props.setProject();
                                }} />
                                <Box width="37px" />
                            </>}
                        />
                        {creatingProject ?
                            <ListRow
                                icon={<Icon
                                    cursor="pointer"
                                    size="24"
                                    name={"starEmpty"}
                                    color={"midGray"}
                                    hoverColor="blue"
                                />}
                                left={<form onSubmit={onCreateProject}>
                                    <Flex height="38px">
                                        <Input
                                            my="3px"
                                            pt="0px"
                                            pb="0px"
                                            pr="0px"
                                            pl="0px"
                                            noBorder
                                            fontSize={20}
                                            maxLength={1024}
                                            onKeyDown={e => {
                                                if (e.keyCode === KeyCode.ESC) {
                                                    setCreatingProject(false);
                                                }
                                            }}
                                            borderRadius="0px"
                                            type="text"
                                            width="100%"
                                            autoFocus
                                            ref={title}
                                        />
                                        <Icon
                                            ml="10px"
                                            mt="7px"
                                            cursor="pointer"
                                            name="close"
                                            color="red"
                                            onClick={() => setCreatingProject(false)}
                                        />
                                    </Flex>
                                </form>}
                                right={<>
                                    <Toggle
                                        scale={1.5}
                                        activeColor="green"
                                        checked={false}
                                        onChange={() => undefined}
                                    /><Box width="28px" /> </>}
                            /> : null}
                        <Pagination.List
                            page={response.data}
                            pageRenderer={page =>
                                page.items.map(e => {
                                    const isActive = e.projectId === props.project;
                                    const isFavorite = e.favorite;
                                    return (
                                        <ListRow
                                            key={e.projectId}
                                            select={() => {
                                                if (selectedProjects.has(e.projectId)) selectedProjects.delete(e.projectId);
                                                else selectedProjects.add(e.projectId);
                                                setSelectedProjects(new Set(selectedProjects));
                                            }}
                                            isSelected={selectedProjects.has(e.projectId)}
                                            icon={<Icon
                                                cursor="pointer"
                                                size="24"
                                                name={isFavorite ? "starFilled" : "starEmpty"}
                                                color={isFavorite ? "blue" : "midGray"}
                                                onClick={() => onToggleFavorite(e.projectId)}
                                                hoverColor="blue"
                                            />}
                                            left={
                                                <Box
                                                    onClick={() => {
                                                        props.setProject(e.projectId);
                                                        snackbarStore.addInformation(
                                                            `${e.projectId} is now the active project`,
                                                            false
                                                        );
                                                    }}
                                                    height="30px"
                                                >
                                                    <Link to="/projects/view">
                                                        {e.title}
                                                    </Link>
                                                </Box>
                                            }
                                            right={
                                                <Flex alignItems={"center"}>
                                                    {!e.needsVerification ? null : (
                                                        <Text fontSize={0} mr={8}>
                                                            <Icon name={"warning"} /> Attention required
                                                        </Text>
                                                    )}
                                                    <Tooltip
                                                        tooltipContentWidth="80px"
                                                        wrapperOffsetLeft="0"
                                                        wrapperOffsetTop="4px"
                                                        right="0"
                                                        top="1"
                                                        mb="50px"
                                                        trigger={(
                                                            <Icon
                                                                size="30"
                                                                squared={false}
                                                                name={projectRoleToStringIcon(e.whoami.role)}
                                                                color="gray"
                                                                color2="midGray"
                                                                mr=".5em"
                                                            />
                                                        )}
                                                    >
                                                        <Text fontSize={2}>{e.whoami.role}</Text>
                                                    </Tooltip>

                                                    <Toggle
                                                        scale={1.5}
                                                        activeColor="green"
                                                        checked={isActive}
                                                        onChange={() => {
                                                            if (isActive) return;
                                                            snackbarStore.addInformation(
                                                                `${e.projectId} is now the active project`,
                                                                false
                                                            );
                                                            props.setProject(e.projectId);
                                                        }}
                                                    />
                                                    {selectedProjects.size === 0 && projectOperations.length > 0 ? (
                                                        <div onClick={stopPropagation}>
                                                            <ClickableDropdown
                                                                width="125px"
                                                                left="-105px"
                                                                trigger={(
                                                                    <Icon
                                                                        ml="0.5em"
                                                                        mr="10px"
                                                                        name="ellipsis"
                                                                        size="1em"
                                                                        rotation={90}
                                                                    />
                                                                )}
                                                            >

                                                                <ProjectOperations
                                                                    selectedProjects={[e]}
                                                                    projectOperations={projectOperations}
                                                                />
                                                            </ClickableDropdown>
                                                        </div>
                                                    ) : <Box width="37px" />}
                                                </Flex>}
                                        />
                                    );
                                })}
                            loading={response.loading}
                            onPageChanged={newPage => {
                                setFetchParams(
                                    listProjects({
                                        page: newPage,
                                        itemsPerPage: response.data.itemsPerPage,
                                        archived
                                    })
                                );
                            }}
                            customEmptyPage={<div />}
                        />
                    </List>
                </>
            )}
            sidebar={(<>
                <VerticalButtonGroup>
                    <Button onClick={startCreateProject}>Create project</Button>
                    <Label fontSize={"100%"}>
                        <Checkbox size={24} checked={archived} onChange={() => setArchived(!archived)} />
                        Show archived
                    </Label>
                    {selectedProjects.size > 0 ? `${selectedProjects.size} project${selectedProjects.size > 1 ? "s" : ""} selected` : null}
                    <ProjectOperations
                        selectedProjects={response.data.items.filter(it => selectedProjects.has(it.projectId))}
                        projectOperations={projectOperations}
                    />
                </VerticalButtonGroup>
            </>)}
        />
    );

    function startCreateProject(): void {
        setCreatingProject(true);
    }

    async function onCreateProject(e: React.FormEvent): Promise<void> {
        e.preventDefault();
        if (commandLoading) return;
        const projectId = title.current?.value ?? "";
        if (projectId === "") {
            snackbarStore.addInformation("Project name can't be empty.", false);
            return;
        }

        await runCommand(createProject({title: projectId}));
        setCreatingProject(false);
        props.setProject(projectId);
        history.push("/projects/view");
    }

    async function onToggleFavorite(projectId: string): Promise<void> {
        await runCommand(toggleFavoriteProject({projectId}));
        reload();
    }
};

interface ProjectOperation extends Operation<UserInProject> {
    iconColor2?: ThemeColor;
}

interface ProjectOperations {
    projectOperations: ProjectOperation[];
    selectedProjects: UserInProject[];
}

function ProjectOperations(props: ProjectOperations): JSX.Element | null {
    if (props.projectOperations.length === 0) return null;

    function ProjectOp(op: ProjectOperation): JSX.Element | null {
        if (op.disabled(props.selectedProjects, Client)) return null;
        return <span onClick={() => op.onClick(props.selectedProjects, Client)}>
            <Icon size={16} mr="0.5em" color={op.color} color2={op.iconColor2} name={op.icon} />{op.text}</span>;
    }

    return (
        <Flex
            ml="-17px"
            mr="-17px"
            pl="15px">
            {props.projectOperations.map(ProjectOp)}
        </Flex>
    );
}

interface DispatchProps {
    setProject: (id?: string) => void;
    onInit: () => void;
    setRefresh: (refresh?: () => void) => void;
    setLoading: (loading: boolean) => void;
}

const mapStateToProps = (state: ReduxObject): {project?: string} => state.project;

const mapDispatchToProps = (dispatch: Dispatch): DispatchProps => ({
    onInit: () => {
        dispatch(updatePageTitle("Projects"));
        dispatch(setActivePage(SidebarPages.Projects));
    },
    setRefresh: refresh => dispatch(setRefreshFunction(refresh)),
    setLoading: loading => dispatch(loadingAction(loading)),
    setProject: id => dispatchSetProjectAction(dispatch, id)
});

export default connect(mapStateToProps, mapDispatchToProps)(_List);
