import MainContainer from "@/MainContainer/MainContainer";
import * as React from "react";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {getQueryParamOrElse} from "@/Utilities/URIUtilities";
import {useHistory, useLocation} from "react-router";
import {Box, Button, ButtonGroup, Flex, Icon, Input, Text, Tooltip} from "@/ui-components";
import {createProject, setProjectArchiveStatus, listSubprojects, renameProject, MemberInProject, ProjectRole, projectRoleToStringIcon, projectRoleToString, useProjectId, viewProject, UserInProject, emptyUserInProject, useProjectManagementStatus} from ".";
import List, {ListRow, ListRowStat} from "@/ui-components/List";
import {errorMessageOrDefault, preventDefault, stopPropagationAndPreventDefault} from "@/UtilityFunctions";
import {Operations, Operation} from "@/ui-components/Operation";
import {ItemRenderer, ItemRow, StandardBrowse} from "@/ui-components/Browse";
import {useToggleSet} from "@/Utilities/ToggleSet";
import {useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {History} from "history";
import {BrowseType} from "@/Resource/BrowseType";
import {isAdminOrPI} from "@/Utilities/ProjectUtilities";
import {useDispatch} from "react-redux";
import {dispatchSetProjectAction} from "./Redux";
import {Toggle} from "@/ui-components/Toggle";
import {ProjectBreadcrumbs} from "./Breadcrumbs";

interface MemberInProjectCallbacks {
    startCreation: () => void;
    onSetArchivedStatus: (id: string, archive: boolean) => void;
    startRename: (id: string) => void;
    history: History;
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
                    scale={1.5}
                    activeColor="--green"
                    checked={isActive}
                    onChange={() => callbacks.setActiveProject(resource.project.id, resource.project.title)}
                />
            </Box> : null}
            {resource.role ? <Tooltip
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
                        name={projectRoleToStringIcon(resource.role)}
                        color="gray"
                        color2="midGray"
                        mr=".5em"
                    />
                )}
            >
                <Text fontSize={2}>{projectRoleToString(resource.role)}</Text>
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
        enabled: (selected) => selected.length === 1 && isAdminOrPI(selected[0].role ?? ProjectRole.USER),
        onClick: ([{project}], extra) => extra.history.push(`/subprojects/?subproject=${project.id}`),
        text: "View subprojects",
        icon: "projects",
    },
    {
        enabled: (selected) => selected.length === 1 && !selected[0].project.archived && isAdminOrPI(selected[0].role ?? ProjectRole.USER),
        onClick: ([{project}], extras) => extras.onSetArchivedStatus(project.id, true),
        text: "Archive",
        icon: "tags"
    },
    {
        enabled: (selected) => selected.length === 1 && selected[0].project.archived && isAdminOrPI(selected[0].role ?? ProjectRole.USER),
        onClick: ([{project}], extras) => extras.onSetArchivedStatus(project.id, false),
        text: "Unarchive",
        icon: "tags"
    },
    {
        enabled: (selected, extras) => {
            if (selected.length !== 1) return false;
            if (extras.isAdminOrPIForParent || isAdminOrPI(selected[0].role ?? ProjectRole.USER)) {
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
    const location = useLocation();
    const subprojectFromQuery = getQueryParamOrElse(location.search, "subproject", "");
    const history = useHistory();
    const [overrideRedirect, setOverride] = React.useState(false);

    const project = useProjectManagementStatus({isRootComponent: true});
    const {projectId, projectRole} = project;

    React.useEffect(() => {
        if (!overrideRedirect) {
            history.push(`/subprojects?subproject=${projectId}`);
        }
    }, [projectId, overrideRedirect]);

    const dispatch = useDispatch();
    const setProject = React.useCallback((id: string, title: string) => {
        dispatchSetProjectAction(dispatch, id);
        snackbarStore.addInformation(
            `${title} is now the active project`,
            false
        );
    }, [dispatch]);

    const [, invokeCommand,] = useCloudCommand();

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
            const result = await invokeCommand(createProject({
                title: subprojectName,
                parent: subprojectFromQuery
            }));
            setOverride(true);
            dispatchSetProjectAction(dispatch, result.id);
            history.push("/project/grants/existing/");
        } catch (e) {
            snackbarStore.addFailure(errorMessageOrDefault(e, "Invalid subproject name"), false);
        }
    }, [creationRef, subprojectFromQuery]);

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
            await invokeCommand(renameProject({id, newTitle: newProjectName}));
            reloadRef.current();
            toggleSet.uncheckAll();
            setRenameId("");
        } catch (e) {
            snackbarStore.addFailure(errorMessageOrDefault(e, "Invalid subproject name"), false);
        }
    }, [subprojectFromQuery]);

    const onSetArchivedStatus = React.useCallback(async (id: string, archive: boolean) => {
        try {
            await invokeCommand({
                ...setProjectArchiveStatus({archiveStatus: archive}),
                projectOverride: id
            });
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
            projectOverride: subprojectFromQuery
        });
    }, [subprojectFromQuery]);

    const extra: MemberInProjectCallbacks = {
        startCreation,
        history,
        onSetArchivedStatus,
        startRename: setRenameId,
        setActiveProject: setProject,
        isAdminOrPIForParent: isAdminOrPI(projectRole),
    };

    const [subproject, fetchSubproject] = useCloudAPI<UserInProject>({noop: true}, emptyUserInProject(subprojectFromQuery));

    React.useEffect(() => {
        fetchSubproject(viewProject({id: subprojectFromQuery}));
    }, [subprojectFromQuery]);

    const isActiveProject = subproject.data.projectId === projectId;
    const crumbs = (isActiveProject ? [] : [{title: subproject.data.title}]).concat([{title: "Subprojects"}]);

    return <MainContainer
        main={
            !subprojectFromQuery ? <Text fontSize={"24px"}>Missing subproject</Text> :
                <>
                    <ProjectBreadcrumbs omitActiveProject={!isActiveProject} crumbs={crumbs} />
                    <StandardBrowse
                        reloadRef={reloadRef}
                        generateCall={generateCall}
                        pageRenderer={pageRenderer}
                        toggleSet={toggleSet}
                    />
                </>
        }
        sidebar={
            <Operations
                location="SIDEBAR"
                operations={projectOperations}
                selected={toggleSet.checked.items}
                extra={extra}
                entityNameSingular={"Subproject"}
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
                                    <Input noBorder placeholder="Project name..." ref={creationRef} />
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
                            <Input noBorder placeholder="Project name..." defaultValue={it.project.title} ref={renameRef} />
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
