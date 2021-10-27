import MainContainer from "@/MainContainer/MainContainer";
import * as React from "react";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {getQueryParamOrElse} from "@/Utilities/URIUtilities";
import {useLocation} from "react-router";
import {Box, Icon, Input, Text} from "@/ui-components";
import {createProject, listSubprojectsV2, Project} from ".";
import {ListRow} from "@/ui-components/List";
import {stopPropagation} from "@/UtilityFunctions";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {Operations, Operation} from "@/ui-components/Operation";
import {StandardBrowse} from "@/ui-components/Browse";
import {useToggleSet} from "@/Utilities/ToggleSet";
import {useCloudCommand} from "@/Authentication/DataHook";
import {snackbarStore} from "@/Snackbar/SnackbarStore";

type ProjectOperation = Operation<Project, {startCreation: () => void;}>;

const projectOperations: ProjectOperation[] = [
    {
        enabled: () => true,
        onClick: (entries, extra) => extra.startCreation(),
        text: "Create subproject",
        canAppearInLocation: loc => loc === "SIDEBAR",
        color: "blue",
        primary: true
    }
];

export default function SubprojectList(): JSX.Element | null {
    useTitle("Subproject");
    const location = useLocation();
    const subprojectFromQuery = getQueryParamOrElse(location.search, "subproject", "");

    const [, invokeCommand,] = useCloudCommand();


    const [creating, setCreating] = React.useState(false);
    const creationRef = React.useRef<HTMLInputElement>(null);

    const startCreation = React.useCallback(() => {
        setCreating(true);
    }, []);

    const onCreate = React.useCallback(async () => {
        setCreating(false);
        const subprojectName = creationRef.current?.value ?? "";
        if (!subprojectName) {
            snackbarStore.addFailure("Invalid subproject name", false);
            return;
        }
        invokeCommand(createProject({
            title: subprojectName,
            parent: subprojectFromQuery
        }));
    }, [creationRef, subprojectFromQuery]);

    const toggleSet = useToggleSet<Project>([]);

    return <MainContainer
        main={
            !subprojectFromQuery ? <Text>Missing subproject</Text> :
                <>
                    {creating ? <ListRow left={<form onSubmit={onCreate}><Input ref={creationRef} /></form>} right={undefined} /> : null}
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
                </>
        }
        sidebar={
            <Operations
                location="SIDEBAR" operations={projectOperations} selected={[]} extra={{startCreation}} entityNameSingular={"Subproject"}
            />
        }
    />;

    function pageRenderer(items: Project[]): JSX.Element[] {
        return items.map(p => {
            return (
                <ListRow
                    key={p.id}
                    left={p.title}
                    right={
                        <div data-tag="project-dropdown" onClick={stopPropagation}>
                            <ClickableDropdown
                                width="125px"
                                left="-105px"
                                test-tag={`${p.id}-dropdown`}
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
                                <Operations
                                    location={"IN_ROW"}
                                    operations={projectOperations}
                                    selected={[p]}
                                    extra={{startCreation}}
                                    entityNameSingular={"Subproject"}
                                />
                            </ClickableDropdown>
                        </div>}
                />
            );
        });
    }
}
