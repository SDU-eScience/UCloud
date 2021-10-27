import MainContainer from "@/MainContainer/MainContainer";
import * as React from "react";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {getQueryParamOrElse} from "@/Utilities/URIUtilities";
import {useLocation} from "react-router";
import {Box, Flex, Icon, Text, Tooltip} from "@/ui-components";
import {listSubprojects, listSubprojectsV2, Project} from ".";
import List, {ListRow} from "@/ui-components/List";
import * as Pagination from "@/Pagination";
import {emptyPage, emptyPageV2} from "@/DefaultObjects";
import {useCloudAPI} from "@/Authentication/DataHook";
import {stopPropagation} from "@/UtilityFunctions";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {Operations, Operation} from "@/ui-components/Operation";
import {PageV2} from "@/UCloud";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {ItemRow, StandardBrowse} from "@/ui-components/Browse";
import {ResourceBrowse} from "@/Resource/Browse";
import {useToggleSet} from "@/Utilities/ToggleSet";

type ProjectOperation = Operation<Project, undefined>;

const projectOperations: ProjectOperation[] = [
    {
        enabled: () => true,
        onClick: () => console.log("todo"),
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


    const [creating, setCreating] = React.useState(false);
    const creationRef = React.useRef<HTMLInputElement>();

    const startCreation = React.useCallback(() => {
        setCreating(true);
    }, []);

    const toggleSet = useToggleSet<Project>([]);

    return <MainContainer
        main={
            !subprojectFromQuery ? <Text>Missing subproject</Text> :
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
        }
        sidebar={
            <Box />
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
