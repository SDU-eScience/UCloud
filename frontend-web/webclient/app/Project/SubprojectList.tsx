import MainContainer from "@/MainContainer/MainContainer";
import * as React from "react";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {buildQueryString} from "@/Utilities/URIUtilities";
import {NavigateFunction, useNavigate} from "react-router";
import {Box, Button, ButtonGroup, Flex, Icon, Input, Text, Tooltip} from "@/ui-components";
import List, {ListRow, ListRowStat} from "@/ui-components/List";
import {errorMessageOrDefault, preventDefault, stopPropagationAndPreventDefault} from "@/UtilityFunctions";
import {Operation} from "@/ui-components/Operation";
import {ItemRenderer, ItemRow, StandardBrowse} from "@/ui-components/Browse";
import {useToggleSet} from "@/Utilities/ToggleSet";
import {useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {BrowseType} from "@/Resource/BrowseType";
import {useDispatch} from "react-redux";
import {dispatchSetProjectAction} from "./Redux";
import {Toggle} from "@/ui-components/Toggle";
import {ProjectBreadcrumbs} from "./Breadcrumbs";
import api, {isAdminOrPI, OldProjectRole, Project, projectRoleToString, projectRoleToStringIcon, useProjectFromParams, useProjectId} from "./Api";
import ProjectAPI from "@/Project/Api";
import {bulkRequestOf} from "@/DefaultObjects";
import {PaginationRequestV2} from "@/UCloud";
import {UtilityBar} from "@/Playground/Playground";
import {Spacer} from "@/ui-components/Spacer";

interface MemberInProjectCallbacks {
    startCreation: () => void;
    onSetArchivedStatus: (id: string, archive: boolean) => void;
    startRename: (id: string) => void;
    navigate: NavigateFunction;
    setActiveProject: (id: string, title: string) => void;
    isAdminOrPIForParent: boolean;
}

type ProjectOperation = Operation<MemberInProject, MemberInProjectCallbacks>;

const subprojectsRenderer: ItemRenderer<MemberInProject, MemberInProjectCallbacks> = {
    MainTitle({resource}) {
        if (!resource) return null;
        return <Text>{resource.project.title}</Text>;
    },
    Icon() {
        return <Icon color={"iconColor"} color2={"iconColor2"} name="projects" />;
    },
    ImportantStats({resource, callbacks}) {
        if (!resource) return null;
        const projectId = useProjectId();
        const isActive = projectId === resource.project.id;
        return <>
            {resource.project.archived ? <Icon mr="8px" mt="7px" name="tags" /> : null}
            {resource.role ? <Box mt="7px" title="Set as active project" mr="12px">
                <Toggle
                    checked={isActive}
                    onChange={() => callbacks.setActiveProject(resource.project.id, resource.project.title)}
                />
            </Box> : null}
            {resource.role ? <Tooltip
                trigger={(
                    <Icon
                        size="30"
                        squared={false}
                        name={projectRoleToStringIcon(resource.role)}
                        color="gray"
                        color2="midGray"
                        mr=".5em"
                    />
                )}
            >
                <Text>{projectRoleToString(resource.role)}</Text>
            </Tooltip> : null}
        </>;
    },
    Stats({resource}) {
        return <ListRowStat>{resource?.project.fullPath}</ListRowStat>;
    }
};

const projectOperations: ProjectOperation[] = [
    {
        enabled: () => true,
        onClick: (_, extra) => extra.startCreation(),
        text: "Create subproject",
        canAppearInLocation: loc => loc === "SIDEBAR",
        color: "blue",
        primary: true
    },
    {
        enabled: (selected) => selected.length === 1 && isAdminOrPI(selected[0].role),
        onClick: ([{project}], extra) =>  extra.setActiveProject(project.id, project.title),
        text: "View subprojects",
        icon: "projects",
    },
    {
        enabled: (selected) => selected.length === 1 && !selected[0].project.archived && isAdminOrPI(selected[0].role),
        onClick: ([{project}], extras) => extras.onSetArchivedStatus(project.id, true),
        text: "Archive",
        icon: "tags"
    },
    {
        enabled: (selected) => selected.length === 1 && selected[0].project.archived && isAdminOrPI(selected[0].role),
        onClick: ([{project}], extras) => extras.onSetArchivedStatus(project.id, false),
        text: "Unarchive",
        icon: "tags"
    },
    {
        enabled: (selected, extras) => {
            if (selected.length !== 1) return false;
            if (extras.isAdminOrPIForParent || isAdminOrPI(selected[0].role)) {
                return true;
            } else {
                return "Only Admins and PIs can rename.";
            }
        },
        onClick: ([{project}], extras) => extras.startRename(project.id),
        text: "Rename",
        icon: "rename"
    }
];

export default function SubprojectList(): JSX.Element | null {
    useTitle("Subproject");
    const navigate = useNavigate();
    const projectId = useProjectId();
    const [project, fetchProject] = useCloudAPI<Project | null>({noop: true}, null);
    const isPersonalWorkspace = projectId == undefined;

    const reload = React.useCallback(() => {
        if (!isPersonalWorkspace && projectId) {
            fetchProject(api.retrieve({
                id: projectId,
                includePath: true,
                includeMembers: true,
                includeArchived: true,
                includeGroups: true,
                includeSettings: true,
            }));
        }
    }, [projectId]);

    React.useEffect(() => {
        reload();
    }, [projectId]);

    const dispatch = useDispatch();
    const setProject = React.useCallback((id: string, title: string) => {
        dispatchSetProjectAction(dispatch, id);
        snackbarStore.addInformation(
            `${title} is now the active project`,
            false
        );
    }, [dispatch]);

    const [, invokeCommand] = useCloudCommand();

    const [creating, setCreating] = React.useState(false);
    const [renameId, setRenameId] = React.useState("");
    const reloadRef = React.useRef<() => void>(() => undefined);
    const creationRef = React.useRef<HTMLInputElement>(null);
    const renameRef = React.useRef<HTMLInputElement>(null);

    const startCreation = React.useCallback(() => {
        setCreating(true);
    }, []);

    const toggleSet = useToggleSet<MemberInProject>([]);

    const onCreate = React.useCallback(async () => {
        const subprojectName = creationRef.current?.value ?? "";
        if (!subprojectName) {
            snackbarStore.addFailure("Subproject name cannot be empty.", false);
            return;
        }

        if (subprojectName.trim().length !== subprojectName.length) {
            snackbarStore.addFailure("Subproject name cannot end or start with whitespace.", false);
            return;
        }

        setCreating(false);

        try {
            const [result] = (await invokeCommand(ProjectAPI.create(bulkRequestOf({
                title: subprojectName,
                parent: projectId
            })))).responses;
            dispatchSetProjectAction(dispatch, result.id);
            navigate("/project/grants/existing/");
        } catch (e) {
            snackbarStore.addFailure(errorMessageOrDefault(e, "Invalid subproject name"), false);
        }
    }, [creationRef, projectId]);

    const onRenameProject = React.useCallback(async (id: string) => {
        const newProjectName = renameRef.current?.value;
        if (!newProjectName) {
            snackbarStore.addFailure("Invalid subproject name", false);
            return;
        }
        if (newProjectName.trim().length != newProjectName.length) {
            snackbarStore.addFailure("Subproject name cannot end or start with whitespace.", false);
            return;
        }
        try {
            await invokeCommand(ProjectAPI.renameProject(bulkRequestOf({id, newTitle: newProjectName})));
            reloadRef.current();
            toggleSet.uncheckAll();
            setRenameId("");
        } catch (e) {
            snackbarStore.addFailure(errorMessageOrDefault(e, "Invalid subproject name"), false);
        }
    }, [projectId]);

    const onSetArchivedStatus = React.useCallback(async (id: string, archive: boolean) => {
        try {
            const bulk = bulkRequestOf({id});
            const req = archive ? ProjectAPI.archive(bulk) : ProjectAPI.unarchive(bulk);
            await invokeCommand(req);
            toggleSet.uncheckAll();
            reloadRef.current();
        } catch (e) {
            snackbarStore.addFailure(errorMessageOrDefault(e, "Invalid subproject name"), false);
        }
    }, []);


    const generateCall = React.useCallback((next?: string) => {
        setCreating(false);
        return ({
            ...listSubprojects({
                itemsPerPage: 50,
                next,
            }),
            projectOverride: projectId
        });
    }, [projectId]);

    const extra: MemberInProjectCallbacks = {
        startCreation,
        navigate,
        onSetArchivedStatus,
        startRename: setRenameId,
        setActiveProject: setProject,
        isAdminOrPIForParent: isAdminOrPI(project.data?.status.myRole),
    };

    if (isPersonalWorkspace) return null;
    return <MainContainer
        header={<Spacer
            left={<ProjectBreadcrumbs crumbs={[{title: "Subprojects"}]} />}
            right={<Flex mr="36px" height={"26px"}><UtilityBar searchEnabled={false} /></Flex>}
        />}
        main={
            isPersonalWorkspace || !projectId ? <Text fontSize={"24px"}>Missing subproject</Text> :
                <StandardBrowse
                    reloadRef={reloadRef}
                    generateCall={generateCall}
                    pageRenderer={pageRenderer}
                    toggleSet={toggleSet}
                />
        }
    />;

    function pageRenderer(items: MemberInProject[]): JSX.Element {
        return (
            <List bordered onContextMenu={preventDefault}>
                {items.length !== 0 ? null : <Text fontSize="24px" key="no-entries">No subprojects found for project.</Text>}
                {creating ?
                    <ListRow
                        left={
                            <form onSubmit={e => {stopPropagationAndPreventDefault(e); onCreate();}}>
                                <Flex height="56px">
                                    <Icon mx="8px" mt="17.3px" color={"iconColor"} color2={"iconColor2"} name="projects" />
                                    <ButtonGroup height="36px" mt="8px">
                                        <Button color="green" type="submit">Create</Button>
                                        <Button color="red" type="button" onClick={() => setCreating(false)}>Cancel</Button>
                                    </ButtonGroup>
                                    <Input noBorder placeholder="Project name..." inputRef={creationRef} />
                                </Flex>
                            </form>
                        }
                        right={null}
                    /> : null}
                {items.map(it => it.project.id === renameId ? (
                    <form key={it.project.id} onSubmit={e => {stopPropagationAndPreventDefault(e); onRenameProject(it.project.id);}}>
                        <Flex height="56px">
                            <Icon mx="8px" mt="17.3px" color={"iconColor"} color2={"iconColor2"} name="projects" />
                            <ButtonGroup height="36px" mt="8px">
                                <Button color="green" type="submit">Rename</Button>
                                <Button color="red" type="button" onClick={() => setRenameId("")}>Cancel</Button>
                            </ButtonGroup>
                            <Input noBorder placeholder="Project name..." defaultValue={it.project.title} inputRef={renameRef} />
                        </Flex>
                    </form>
                ) : (
                    <ItemRow
                        key={it.project.id}
                        item={it}
                        browseType={BrowseType.MainContent}
                        renderer={subprojectsRenderer}
                        toggleSet={toggleSet}
                        operations={projectOperations}
                        callbacks={extra}
                        itemTitle={"Subproject"}
                    />
                ))}
            </List>
        );
    }
}

// Note(Jonas): Endpoint missing from ProjectV2-api
type ListSubprojectsRequest = PaginationRequestV2;
const listSubprojects = (parameters: ListSubprojectsRequest): APICallParameters<ListSubprojectsRequest> => ({
    method: "GET",
    path: buildQueryString(
        "/projects/sub-projects",
        parameters
    ),
    parameters,
    reloadId: Math.random()
});

export interface OldProject {
    id: string;
    title: string;
    parent?: string;
    archived: boolean;
    fullPath?: string;
}

interface MemberInProject {
    role?: OldProjectRole;
    project: OldProject;
}
