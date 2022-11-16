import MainContainer from "@/MainContainer/MainContainer";
import {Project, default as Api, ProjectInvite, useProjectId, isAdminOrPI, projectRoleToStringIcon, projectRoleToString} from "./Api";
import * as React from "react";
import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {ItemRenderer, ItemRow, StandardBrowse} from "@/ui-components/Browse";
import {PageRenderer} from "@/Pagination/PaginationV2";
import {BrowseType} from "@/Resource/BrowseType";
import {NavigateFunction, useNavigate} from "react-router";
import {Operation, Operations} from "@/ui-components/Operation";
import {useToggleSet} from "@/Utilities/ToggleSet";
import {callAPI, InvokeCommand, useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {bulkRequestOf, emptyPageV2} from "@/DefaultObjects";
import {Client} from "@/Authentication/HttpClientInstance";
import {Box, Icon, Tooltip, Text, Flex, Link, Card, Button, List} from "@/ui-components";
import {Toggle} from "@/ui-components/Toggle";
import {CheckboxFilter, FilterWidgetProps, ResourceFilter} from "@/Resource/Filter";
import {doNothing} from "@/UtilityFunctions";
import {useDispatch} from "react-redux";
import {dispatchSetProjectAction} from "@/Project/Redux";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import * as Heading from "@/ui-components/Heading";
import {PageV2} from "@/UCloud";
import {SidebarPages, useSidebarPage} from "@/ui-components/Sidebar";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import styled from "styled-components";
import {useAvatars} from "@/AvataaarLib/hook";
import {UserAvatar} from "@/AvataaarLib/UserAvatar";
import {defaultAvatar} from "@/UserSettings/Avataaar";
import {useForcedRender} from "@/Utilities/ReactUtilities";
import {ListRowStat, ListStatContainer} from "@/ui-components/List";

const title = "Project";

export const ProjectList2: React.FunctionComponent = () => {
    const projectId = useProjectId();
    const navigate = useNavigate();
    const toggleSet = useToggleSet<Project>([]);
    const [, invokeCommand] = useCloudCommand();
    const [filters, setFilters] = useState<Record<string, string>>({});
    const avatars = useAvatars();
    const projectReloadRef = useRef<() => void>(doNothing);
    const rerender = useForcedRender();

    const [invites, fetchInvites] = useCloudAPI<PageV2<ProjectInvite>>({noop: true}, emptyPageV2);

    const reloadInvites = useCallback(() => {
        fetchInvites(Api.browseInvites({filterType: "INGOING", itemsPerPage: 10}));
    }, []);

    const onInviteHandled = useCallback((invite: ProjectInvite) => {
        invites.data.items = invites.data.items.filter(it => it !== invite);
        rerender();
    }, [invites.data]);

    const reload = useCallback(() => {
        projectReloadRef.current();
        reloadInvites();
    }, []);

    useEffect(() => {reloadInvites();}, []);

    useEffect(() => {
        avatars.updateCache(invites.data.items.map(it => it.invitedBy));
    }, [invites.data]);

    const fetchProjects = useCallback((next?: string) => {
        return Api.browse({
            includeFavorite: true, includePath: true, sortBy: "favorite", sortDirection: "descending",
            itemsPerPage: 100, next, ...filters
        });
    }, [filters]);

    const callbacks: Callbacks = useMemo(() => {
        return {
            invokeCommand,
            navigate,
            reload,
            rerender
        };
    }, []);

    const pageRenderer = useCallback<PageRenderer<Project>>((items) => {
        const itemToComponent = (it: Project) =>
            it["frontendHide"] == true ? null :
                <ItemRow
                    key={it.id}
                    browseType={BrowseType.MainContent}
                    highlight={it.id === projectId}
                    renderer={ProjectRenderer}
                    callbacks={callbacks}
                    operations={operations}
                    item={it}
                    itemTitle={title}
                    toggleSet={toggleSet}
                />;

        const favorites = items.filter(it => it.status.isFavorite);
        const notFavorite = items.filter(it => !it.status.isFavorite);

        return <>
            {favorites.length === 0 ? null : <>
                <Heading.h3 mb={16}>Favorites</Heading.h3>
                <List bordered>{favorites.map(itemToComponent)}</List>
                <div style={{marginBottom: "10px"}} />
            </>}
            {notFavorite.length === 0 ? null : <>
                <Heading.h3 mb={16}>My Projects</Heading.h3>
                <List bordered>{notFavorite.map(itemToComponent)}</List>
            </>}
        </>;
    }, [projectId]);

    useTitle("Projects");
    useSidebarPage(SidebarPages.Projects);
    useRefreshFunction(reload);

    return <MainContainer
        main={<>
            {invites.data.items.length === 0 ? null : <>
                <Heading.h3 mb={16}>Invitations</Heading.h3>
                {invites.data.items.map(it => (
                    <ProjectInvite invite={it} key={it.invitedTo} onInviteHandled={onInviteHandled}
                        requestReload={reload} />
                ))}

                <div style={{marginBottom: 32}} />
            </>}

            <StandardBrowse
                generateCall={fetchProjects}
                pageRenderer={pageRenderer}
                reloadRef={projectReloadRef}
                setRefreshFunction={false}
            />
        </>}
        sidebar={<>
            <Operations selected={toggleSet.checked.items} location={"SIDEBAR"}
                entityNameSingular={title}
                extra={callbacks} operations={operations} />

            <ResourceFilter
                browseType={BrowseType.MainContent}
                pills={filterPills}
                sortEntries={[]}
                sortDirection={"ascending"}
                filterWidgets={filterWidgets}
                onSortUpdated={doNothing}
                readOnlyProperties={{}}
                properties={filters}
                setProperties={setFilters}
            />
        </>}
    />;
};

// Filters
const [widget, pill] = CheckboxFilter("tags", "includeArchived", "Archived");
const filterPills = [pill];
const filterWidgets: React.FunctionComponent<FilterWidgetProps>[] = [widget];

// Operations
interface Callbacks {
    invokeCommand: InvokeCommand;
    navigate: NavigateFunction;
    reload: () => void;
    rerender: () => void;
}

const operations: Operation<Project, Callbacks>[] = [
    {
        text: "New project application",
        canAppearInLocation: loc => loc === "SIDEBAR",
        primary: true,
        enabled: () => true,
        onClick: (projects, cb) => {
            cb.navigate("/project/grants/new");
        }
    },
    {
        text: "Archive",
        icon: "tags",
        color: "purple",
        confirm: true,
        enabled: projects => {
            return projects.length >= 1 &&
                projects.every(it => !it.status.archived) &&
                projects.every(it => isAdminOrPI(it.status.myRole));
        },
        onClick: (projects, cb) => {
            projects.forEach(it => {
                it.status.archived = true;
                cb.rerender();
            });

            cb.invokeCommand(Api.archive(
                bulkRequestOf(...projects.map(it => ({id: it.id})))
            ));
        }
    },
    {
        text: "Unarchive",
        icon: "tags",
        color: "purple",
        confirm: true,
        enabled: projects => {
            return projects.length >= 1 &&
                projects.every(it => it.status.archived) &&
                projects.every(it => isAdminOrPI(it.status.myRole));
        },
        onClick: (projects, cb) => {
            projects.forEach(it => {
                it.status.archived = false;
                cb.rerender();
            });

            cb.invokeCommand(Api.unarchive(
                bulkRequestOf(...projects.map(it => ({id: it.id})))
            ));
        }
    },
    {
        text: "Leave",
        icon: "open",
        color: "red",
        confirm: true,
        enabled: projects => projects.length >= 1,
        onClick: async ([project], cb) => {
            project["frontendHide"] = true;
            cb.rerender();
            await cb.invokeCommand(
                {
                    ...Api.deleteMember(bulkRequestOf({username: Client.username!})),
                    projectOverride: project.id
                },
            );
            cb.reload();
        }
    },
    {
        text: "Properties",
        icon: "properties",
        enabled: projects => projects.length === 1,
        onClick: ([project], cb) => {
            cb.navigate(`/projects/${project.id}`);
        }
    }
];

// Item renderers
const ProjectRenderer: ItemRenderer<Project> = {
    Icon: ({resource}) => {
        if (!resource) return null;

        const initialIsFavorite = resource.status.isFavorite ?? false;

        const [isFavorite, setIsFavorite] = useState(initialIsFavorite);
        const [commandLoading, invokeCommand] = useCloudCommand();
        const onClick = useCallback((e: React.SyntheticEvent) => {
            e.stopPropagation();
            if (commandLoading) return;
            setIsFavorite(!isFavorite);
            invokeCommand(Api.toggleFavorite(
                bulkRequestOf({id: resource.id})
            ));
        }, [commandLoading, isFavorite]);

        return <Box mt="-6px">
            <Icon
                cursor="pointer"
                size="24"
                name={isFavorite ? "starFilled" : "starEmpty"}
                color={isFavorite ? "blue" : "midGray"}
                onClick={onClick}
                hoverColor="blue"
            />
        </Box>;
    },

    MainTitle: ({resource}) => {
        if (!resource) return null;
        return <Link to={`/projects/${resource.id}`} color={resource.status.archived ? "darkGray" : "text"}>
            {resource.specification.title}
        </Link>;
    },

    Stats: ({resource}) => {
        if (!resource) return null;
        return <ListStatContainer>
            {resource.status.path === "" ? null : <ListRowStat>{resource.status.path}/</ListRowStat>}
        </ListStatContainer>;
    },

    ImportantStats: ({resource}) => {
        const projectId = useProjectId();
        const dispatch = useDispatch();
        const updateProject = useCallback((id: string) => {
            dispatchSetProjectAction(dispatch, id);
        }, [dispatch]);

        if (!resource) return null;
        const isActive = projectId === resource.id;

        return <>
            <Flex alignItems="center" height="36.25px">
                {!resource.status.archived ? null : <>
                    <ProjectTooltip text="Archived">
                        <Icon mr={8} name="tags" color="gray" />
                    </ProjectTooltip>
                </>}
                <ProjectTooltip text={projectRoleToString(resource.status.myRole!)}>
                    <Icon size="30" squared={false} name={projectRoleToStringIcon(resource.status.myRole!)}
                        color="gray" color2="midGray" mr=".5em" />
                </ProjectTooltip>

                <Toggle
                    scale={1.5}
                    activeColor="--green"
                    checked={isActive}
                    onChange={() => {
                        if (isActive) return;
                        updateProject(resource.id);
                        snackbarStore.addInformation(
                            `${resource.specification.title} is now the active project`,
                            false
                        );
                    }}
                />
            </Flex>
        </>;
    }
};

// Utility components
const ProjectTooltip: React.FunctionComponent<{text: string; children?: React.ReactNode;}> = props => {
    return <Tooltip
        tooltipContentWidth="80px"
        wrapperOffsetLeft="0"
        wrapperOffsetTop="4px"
        right="0"
        top="1"
        mb="50px"
        trigger={<>{props.children}</>}
    >
        <Text fontSize={2}>{props.text}</Text>
    </Tooltip>
};

const BorderedFlex = styled(Flex)`
  border-radius: 6px 6px 0 0;
`;

const ProjectInviteBody = styled.div`
  display: flex;
  padding: 16px 16px 0px 16px;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
`;

const ProjectInvite: React.FunctionComponent<{
    invite: ProjectInvite;
    onInviteHandled: (invite: ProjectInvite) => void;
    requestReload: () => void;
}> = ({invite, onInviteHandled, requestReload}) => {
    const avatars = useAvatars();
    const acceptInvite = useCallback(async () => {
        onInviteHandled(invite);
        await callAPI(Api.acceptInvite(bulkRequestOf({project: invite.invitedTo})));
        requestReload();
    }, [onInviteHandled, invite, requestReload]);

    const rejectInvite = useCallback(async () => {
        onInviteHandled(invite);
        await callAPI(Api.deleteInvite(bulkRequestOf({project: invite.invitedTo, username: invite.recipient})));
        requestReload();
    }, [onInviteHandled, invite, requestReload]);
    return (
        <Card overflow={"hidden"} height={"auto"} width={1} boxShadow={"sm"} borderWidth={1} borderRadius={6} mb={12}>
            <BorderedFlex
                bg="lightGray"
                color="darkGray"
                px={3}
                py={2}
                alignItems="center"
            >
                {invite.projectTitle}
            </BorderedFlex>
            <ProjectInviteBody>
                <UserAvatar avatar={avatars.cache[invite.invitedBy] ?? defaultAvatar} mr={"10px"} />
                <div>Invited by {invite.invitedBy}</div>
                <div style={{flexGrow: 1}} />
                <Button color={"green"} height={"42px"} onClick={acceptInvite}>Accept</Button>
                <Button color={"red"} height={"42px"} onClick={rejectInvite}>Reject</Button>
            </ProjectInviteBody>
        </Card>
    );
};

export default ProjectList2;
