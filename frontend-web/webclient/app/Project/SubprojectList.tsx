import MainContainer from "@/MainContainer/MainContainer";
import * as React from "react";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {getQueryParamOrElse} from "@/Utilities/URIUtilities";
import {useHistory, useLocation} from "react-router";
import {Box, Button, Flex, Icon, Input, Link, Text} from "@/ui-components";
import {createProject, listSubprojectsV2, Project} from ".";
import List, {ListRow, ListRowStat} from "@/ui-components/List";
import {errorMessageOrDefault, preventDefault, stopPropagation} from "@/UtilityFunctions";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {Operations, Operation} from "@/ui-components/Operation";
import {ItemRenderer, ItemRow, StandardBrowse} from "@/ui-components/Browse";
import {useToggleSet} from "@/Utilities/ToggleSet";
import {useCloudCommand} from "@/Authentication/DataHook";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {History} from "history";
import {BrowseType} from "@/Resource/BrowseType";

type ProjectOperation = Operation<Project, {startCreation: () => void; history: History;}>;

const subprojectsRenderer: ItemRenderer<Project> = {
    MainTitle({resource}) {
        if (!resource) return null;
        return <Text>{resource.title}</Text>;
    },
    Icon() {
        return null;
    },
    ImportantStats({resource}) {
        return null;
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
        enabled: (selected) => (console.log(selected.length), selected.length === 1),
        onClick: ([project], extra) => extra.history.push(`/subprojects/?subproject=${project.id}`),
        text: "View subprojects",
        primary: false
    }
];

export default function SubprojectList(): JSX.Element | null {
    useTitle("Subproject");
    const location = useLocation();
    const subprojectFromQuery = getQueryParamOrElse(location.search, "subproject", "");
    const history = useHistory();

    const [, invokeCommand,] = useCloudCommand();

    const [creating, setCreating] = React.useState(false);
    const creationRef = React.useRef<HTMLInputElement>(null);

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
            invokeCommand(createProject({
                title: subprojectName,
                parent: subprojectFromQuery
            }));
        } catch (e) {
            snackbarStore.addFailure(errorMessageOrDefault(e, "Invalid subproject name"), false);
        }
    }, [creationRef, subprojectFromQuery]);

    return <MainContainer
        main={
            !subprojectFromQuery ? <Text fontSize={"24px"}>Missing subproject</Text> :
                <List bordered onContextMenu={preventDefault}>
                    {creating ? <ListRow left={
                        <form onSubmit={onCreate}>
                            <Flex>
                                <Button color="green" type="submit">Create</Button>
                                <Button color="red" type="button" onClick={() => setCreating(false)}>Cancel</Button>
                                <Input noBorder ref={creationRef} />
                            </Flex>
                        </form>
                    }
                        right={undefined} /> : null}
                    <StandardBrowse
                        generateCall={next => ({
                            ...listSubprojectsV2({
                                itemsPerPage: 50,
                                next,
                            }),
                            projectOverride: subprojectFromQuery
                        })}
                        pageRenderer={pageRenderer}
                        toggleSet={toggleSet}
                    />
                </List>
        }
        sidebar={
            <Operations
                location="SIDEBAR"
                operations={projectOperations}
                selected={[]}
                extra={{startCreation, history}}
                entityNameSingular={"Subproject"}
            />
        }
    />;

    function pageRenderer(items: Project[]): JSX.Element[] {
        if (items.length === 0) {
            return [<Text fontSize="24px" key="no-entries">No subprojects found for project.</Text>];
        }
        return items.map(p => (
            <ItemRow
                item={p}
                browseType={BrowseType.MainContent}
                renderer={subprojectsRenderer}
                toggleSet={toggleSet}
                operations={projectOperations}
                callbacks={{startCreation, history}}
                itemTitle={"Subproject"}
            />
        ));
    }
}
