import {useAsyncCommand, useCloudAPI} from "Authentication/DataHook";
import {emptyPage} from "DefaultObjects";
import {MainContainer} from "MainContainer/MainContainer";
import * as Pagination from "Pagination";
import {
    listProjects,
    ListProjectsRequest,
    UserInProject,
    IngoingInvite,
    listIngoingInvites,
    acceptInvite,
    rejectInvite,
    ListFavoriteProjectsRequest,
    listFavoriteProjects,
    ProjectRole, projectRoleToString
} from "Project/index";
import * as React from "react";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {Operation} from "Types";
import {Button, Flex, Icon, List, Text, Box, Checkbox, Label, Link, Tooltip} from "ui-components";
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
import {defaultAvatar} from "UserSettings/Avataaar";
import {UserAvatar} from "AvataaarLib/UserAvatar";
import {useAvatars} from "AvataaarLib/hook";
import {dialogStore} from "Dialog/DialogStore";
import {ArchiveProject, LeaveProject} from "./ProjectSettings";
import {isAdminOrPI} from "Utilities/ProjectUtilities";

// eslint-disable-next-line no-underscore-dangle
const _List: React.FunctionComponent<DispatchProps & {project?: string}> = props => {
    const [archived, setArchived] = useState<boolean>(false);
    const [response, setFetchParams] = useCloudAPI<Page<UserInProject>, ListProjectsRequest>(
        {noop: true},
        emptyPage
    );
    const [favorites, setFavoriteParams] = useCloudAPI<Page<UserInProject>, ListFavoriteProjectsRequest>(
        listFavoriteProjects({page: 0, itemsPerPage: 25, archived}),
        emptyPage
    );

    const [ingoingInvites, fetchIngoingInvites, ingoingInvitesParams] = useCloudAPI<Page<IngoingInvite>>(
        listIngoingInvites({page: 0, itemsPerPage: 10}),
        emptyPage
    );

    const usernames = ingoingInvites.data.items.map(it => it.invitedBy);
    const avatars = useAvatars();

    useEffect(() => {
        avatars.updateCache(usernames);
    }, [usernames]);

    const [selectedProjects, setSelectedProjects] = React.useState(new Set());
    const [, runCommand] = useAsyncCommand();

    useEffect(() => {
        props.setLoading(response.loading);
    }, [response.loading]);

    const history = useHistory();

    const reload = (): void => {
        setFavoriteParams(listFavoriteProjects({
            page: favorites.data.pageNumber,
            itemsPerPage: response.data.itemsPerPage,
            archived
        }));
        setFetchParams(listProjects({
            page: response.data.pageNumber,
            itemsPerPage: response.data.itemsPerPage,
            archived,
            noFavorites: true
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
        setFetchParams(listProjects({page: 0, itemsPerPage: 50, archived, noFavorites: true}));
    }, [archived]);

    const projectOperations: ProjectOperation[] = [
        {
            text: "Archive",
            disabled: projects =>
                projects.length !== 1 ||
                projects.every(it => it.archived) ||
                projects.some(it => !isAdminOrPI(it.whoami.role)),
            icon: "tags",
            onClick: ([project]) => dialogStore.addDialog(
                <ArchiveProject
                    onSuccess={() => dialogStore.success()}
                    isArchived={project.archived}
                    title={project.title}
                    projectId={project.projectId}
                    projectRole={project.whoami.role}
                />,
                () => undefined
            )
        },
        {
            text: "Unarchive",
            disabled: projects => projects.length !== 1 || projects.every(it => !it.archived),
            icon: "tags",
            onClick: ([project]) => dialogStore.addDialog(
                <ArchiveProject
                    onSuccess={() => dialogStore.success()}
                    isArchived={project.archived}
                    projectId={project.projectId}
                    title={project.title}
                    projectRole={project.whoami.role}
                />,
                () => undefined
            )
        },
        {
            text: "Leave",
            disabled: projects => projects.length !== 1,
            icon: "open",
            onClick: ([project]) => dialogStore.addDialog(
                <LeaveProject
                    onSuccess={() => dialogStore.success()}
                    projectId={project.projectId}
                    projectRole={project.whoami.role}
                    projectDetails={project}
                />,
                () => undefined)
        },
        {
            text: "Manage",
            disabled: projects => projects.length !== 1,
            icon: "properties",
            onClick: ([project]) => {
                props.setProject(project.projectId);
                history.push("/project/dashboard");
            }
        }
    ];

    const personalProjectOperations: ProjectOperation[] = [{
        text: "Manage",
        disabled: () => false,
        icon: "properties",
        onClick: () => {
            props.setProject();
            history.push("/project/dashboard");
        }
    }];

    return (
        <MainContainer
            headerSize={58}
            main={(
                <>
                    {ingoingInvites.data.itemsInTotal > 0 ? (
                        <>
                            <Heading.h3 mb={16}>Invitations</Heading.h3>
                            <Pagination.List
                                customEmptyPage={<Box mb={32}>You have no invitations.</Box>}
                                loading={ingoingInvites.loading}
                                page={ingoingInvites.data}
                                onPageChanged={newPage =>
                                    fetchIngoingInvites(
                                        listIngoingInvites({...ingoingInvitesParams.parameters, page: newPage})
                                    )
                                }
                                pageRenderer={() => (
                                    <Box mb={32}>
                                        {ingoingInvites.data.items.map(invite => (
                                            <ShareCardBase
                                                key={invite.project}
                                                title={invite.title}
                                                body={
                                                    <Spacer
                                                        left={<>
                                                            <UserAvatar
                                                                avatar={
                                                                    avatars.cache[invite.invitedBy] ?? defaultAvatar
                                                                }
                                                                mr="10px"
                                                            />
                                                            <Flex alignItems="center">
                                                                Invited by {invite.invitedBy}
                                                            </Flex>
                                                        </>}
                                                        right={<Flex alignItems="center">
                                                            <Button
                                                                color="green"
                                                                height="42px"
                                                                mr={8}
                                                                onClick={async () => {
                                                                    await runCommand(
                                                                        acceptInvite({projectId: invite.project})
                                                                    );
                                                                    reload();
                                                                }}
                                                            >
                                                                Accept
                                                            </Button>
                                                            <Button
                                                                color="red"
                                                                height="42px"
                                                                onClick={async () => {
                                                                    await runCommand(
                                                                        rejectInvite({projectId: invite.project})
                                                                    );
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
                        </>
                    ) : (
                            <></>
                        )}
                    {favorites.data.items.length === 0 ? null : (<>
                        <Heading.h3>Favorites</Heading.h3>
                        <List mb="10px">
                            <Pagination.List
                                page={favorites.data}
                                loading={false}
                                onPageChanged={newPage => {
                                    setFavoriteParams(
                                        listFavoriteProjects({
                                            page: newPage,
                                            itemsPerPage: response.data.itemsPerPage,
                                            archived
                                        })
                                    );
                                }}
                                pageRenderer={pageRenderer}
                            />
                        </List>
                    </>)}
                    <Heading.h3 mb={16}>My Projects</Heading.h3>
                    <List>
                        <ListRow
                            icon={<Box width="24px" />}
                            left={
                                <>
                                    <Box
                                        onClick={() => {
                                            if (props.project !== undefined && props.project !== "") {
                                                props.setProject();
                                                snackbarStore.addInformation("Personal project is now the active.", false);
                                            }
                                        }}
                                        height="30px"
                                    >
                                        <Link to="/project/dashboard">
                                            Personal project
                                        </Link>
                                    </Box>
                                </>
                            }
                            right={<Flex alignItems="center" height="36.25px">
                                <Toggle scale={1.5} activeColor="green" checked={!props.project} onChange={() => {
                                    if (!props.project) return;
                                    snackbarStore.addInformation("Personal project is now the active.", false);
                                    props.setProject();
                                }} />
                                {selectedProjects.size === 0 && projectOperations.length > 0 ?
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
                                            selectedProjects={[{
                                                archived: false,
                                                favorite: false,
                                                needsVerification: false,
                                                projectId: "",
                                                title: "Personal Project",
                                                whoami: {role: ProjectRole.ADMIN, username: Client.username!}
                                            }]}
                                            projectOperations={personalProjectOperations}
                                        />
                                    </ClickableDropdown>
                                    : <Box width="37px" />}
                            </Flex>}
                        />
                        <Pagination.List
                            page={response.data}
                            pageRenderer={pageRenderer}
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
                    <Box height={58}/>
                    <Link to="/project/grants/outgoing"><Button color="green">Resource Applications</Button></Link>
                    <Link to={`/projects/browser/new`}><Button>Create Project Application</Button></Link>
                    <Label fontSize={"100%"}>
                        <Checkbox size={24} checked={archived} onChange={() => setArchived(!archived)} />
                        Show archived
                    </Label>
                    {selectedProjects.size > 0 ? `${selectedProjects.size} project${selectedProjects.size > 1 ? "s" : ""} selected` : null}
                    <ProjectOperations
                        selectedProjects={[...response.data.items, ...favorites.data.items].filter(it => selectedProjects.has(it.projectId))}
                        projectOperations={projectOperations}
                    />
                </VerticalButtonGroup>
            </>)}
        />
    );

    function pageRenderer(page: Page<UserInProject>): JSX.Element[] {
        return page.items.map(e => {
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
                        <>
                            <Box
                                test-tag={e.projectId}
                                onClick={() => {
                                    if (e.projectId !== props.project) {
                                        props.setProject(e.projectId);
                                        snackbarStore.addInformation(
                                            `${e.title} is now the active project`,
                                            false
                                        );
                                    }
                                }}
                                height="30px"
                            >
                                <Link to="/project/dashboard">
                                    {e.title}
                                </Link>
                            </Box>
                        </>
                    }
                    right={
                        <Flex alignItems="center" height="36.25px">
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
                                <Text fontSize={2}>{projectRoleToString(e.whoami.role)}</Text>
                            </Tooltip>

                            <Toggle
                                scale={1.5}
                                activeColor="green"
                                checked={isActive}
                                onChange={() => {
                                    if (isActive) return;
                                    snackbarStore.addInformation(
                                        `${e.title} is now the active project`,
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
                                        test-tag={`${e.projectId}-dropdown`}
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
        });
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
        return (
            <Box
                key={op.text}
                ml="-17px"
                mr="-17px"
                pl="15px"
                cursor="pointer"
                onClick={() => op.onClick(props.selectedProjects, Client)}
            >
                <Icon
                    size={16}
                    mr="0.5em"
                    color={op.color}
                    color2={op.iconColor2}
                    name={op.icon}
                />
                {op.text}
            </Box>
        );
    }

    return (
        <>
            {props.projectOperations.map(ProjectOp)}
        </>
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
