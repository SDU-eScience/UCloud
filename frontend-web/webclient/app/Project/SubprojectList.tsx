import MainContainer from "@/MainContainer/MainContainer";
import * as React from "react";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {getQueryParamOrElse} from "@/Utilities/URIUtilities";
import {useHistory, useLocation} from "react-router";
import {Button, Flex, Icon, Input, Text} from "@/ui-components";
import {createProject, setProjectArchiveStatus, listSubprojectsV2, Project, renameProject} from ".";
import List, {ListRow, ListRowStat} from "@/ui-components/List";
import {errorMessageOrDefault, preventDefault, stopPropagationAndPreventDefault} from "@/UtilityFunctions";
import {Operations, Operation} from "@/ui-components/Operation";
import {ItemRenderer, ItemRow, StandardBrowse} from "@/ui-components/Browse";
import {useToggleSet} from "@/Utilities/ToggleSet";
import {useCloudCommand} from "@/Authentication/DataHook";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {History} from "history";
import {BrowseType} from "@/Resource/BrowseType";

type ProjectOperation = Operation<Project, {
    startCreation: () => void;
    onSetArchivedStatus: (id: string, archive: boolean) => void;
    startRename: (id: string) => void;
    history: History;
}>;

const subprojectsRenderer: ItemRenderer<Project> = {
    MainTitle({resource}) {
        if (!resource) return null;
        return <Text>{resource.title}</Text>;
    },
    Icon() {
        return <Icon name="projects" />;
    },
    ImportantStats({resource}) {
        if (!resource?.archived) return null;
        return <Icon name="tags" />;
    },
    Stats({resource}) {
        return <ListRowStat>{resource?.fullPath}</ListRowStat>;
    }
}

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
        enabled: (selected) => selected.length === 1,
        onClick: ([project], extra) => extra.history.push(`/subprojects/?subproject=${project.id}`),
        text: "View subprojects",
        icon: "projects",
    },
    {
        enabled: (selected) => selected.length === 1 && !selected[0].archived,
        onClick: ([project], extras) => extras.onSetArchivedStatus(project.id, true),
        text: "Archive",
        icon: "tags"
    },
    {
        enabled: (selected) => selected.length === 1 && selected[0].archived,
        onClick: ([project], extras) => extras.onSetArchivedStatus(project.id, false),
        text: "Unarchive",
        icon: "tags"
    },
    {
        enabled: (selected) => selected.length === 1,
        onClick: ([project], extras) => extras.startRename(project.id),
        text: "Rename",
        icon: "rename"
    }
];

export default function SubprojectList(): JSX.Element | null {
    useTitle("Subproject");
    const location = useLocation();
    const subprojectFromQuery = getQueryParamOrElse(location.search, "subproject", "");
    const history = useHistory();

    const [, invokeCommand,] = useCloudCommand();

    const [creating, setCreating] = React.useState(false);
    const [renameId, setRenameId] = React.useState("");
    const reloadRef = React.useRef<() => void>(() => undefined);
    const creationRef = React.useRef<HTMLInputElement>(null);
    const renameRef = React.useRef<HTMLInputElement>(null);

    const startCreation = React.useCallback(() => {
        setCreating(true);
    }, []);

    const toggleSet = useToggleSet<Project>([]);

    const onCreate = React.useCallback(async () => {
        setCreating(false);
        const subprojectName = creationRef.current?.value ?? "";
        if (!subprojectName) {
            snackbarStore.addFailure("Invalid subproject name", false);
            return;
        }
        try {
            await invokeCommand(createProject({
                title: subprojectName,
                parent: subprojectFromQuery
            }));
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


    const generateCall = React.useCallback((next?: string) => ({
        ...listSubprojectsV2({
            itemsPerPage: 50,
            next,
        }),
        projectOverride: subprojectFromQuery
    }), [subprojectFromQuery]);


    const extra = {
        startCreation,
        history,
        onSetArchivedStatus,
        startRename: setRenameId
    };

    return <MainContainer
        main={
            !subprojectFromQuery ? <Text fontSize={"24px"}>Missing subproject</Text> :
                <StandardBrowse
                    reloadRef={reloadRef}
                    generateCall={generateCall}
                    pageRenderer={pageRenderer}
                    toggleSet={toggleSet}
                />
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

    function pageRenderer(items: Project[]): JSX.Element {
        if (items.length === 0) {
            return <Text fontSize="24px" key="no-entries">No subprojects found for project.</Text>;
        }
        return (
            <List bordered onContextMenu={preventDefault}>
                {creating ?
                    <ListRow
                        left={
                            <form onSubmit={e => {stopPropagationAndPreventDefault(e); onCreate();}}>
                                <Flex height="56px">
                                    <Button height="36px" mt="8px" color="green" type="submit">Create</Button>
                                    <Button height="36px" mt="8px" color="red" type="button" onClick={() => setCreating(false)}>Cancel</Button>
                                    <Input noBorder placeholder="Project name..." ref={creationRef} />
                                </Flex>
                            </form>
                        }
                        right={undefined}
                    /> : null}
                {items.map(p => p.id === renameId ? (
                    <form key={p.id} onSubmit={e => {stopPropagationAndPreventDefault(e); onRenameProject(p.id)}}>
                        <Flex height="56px">
                            <Button height="36px" mt="8px" color="green" type="submit">Create</Button>
                            <Button height="36px" mt="8px" color="red" type="button" onClick={() => setRenameId("")}>Cancel</Button>
                            <Input noBorder placeholder="Project name..." defaultValue={p.title} ref={renameRef} />
                        </Flex>
                    </form>
                ) : (
                    <ItemRow
                        key={p.id}
                        item={p}
                        browseType={BrowseType.MainContent}
                        renderer={subprojectsRenderer}
                        toggleSet={toggleSet}
                        operations={projectOperations}
                        callbacks={extra}
                        itemTitle={"Subproject"}
                    />
                ))}
            </List>
        )
    }
}
