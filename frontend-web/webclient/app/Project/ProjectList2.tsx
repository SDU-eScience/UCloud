import MainContainer from "@/MainContainer/MainContainer";
import { Project, default as Api } from "./Api";
import * as React from "react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { ItemRenderer, ItemRow, StandardBrowse } from "@/ui-components/Browse";
import { PageRenderer } from "@/Pagination/PaginationV2";
import { BrowseType } from "@/Resource/BrowseType";
import { useHistory } from "react-router";
import { Operation, Operations } from "@/ui-components/Operation";
import { useToggleSet } from "@/Utilities/ToggleSet";
import { InvokeCommand, useCloudCommand } from "@/Authentication/DataHook";
import { isAdminOrPI } from "@/Utilities/ProjectUtilities";
import { bulkRequestOf } from "@/DefaultObjects";
import { Client } from "@/Authentication/HttpClientInstance";
import { History } from "history";
import { Box, Icon, Tooltip, Text, Flex } from "@/ui-components";
import { projectRoleToString, projectRoleToStringIcon, useProjectId } from ".";
import { Toggle } from "@/ui-components/Toggle";
import { snackbarStore } from "@/Snackbar/SnackbarStore";

const title = "Project";

const ProjectTooltip: React.FunctionComponent<{text: string}> = props => {
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

    MainTitle: props => {
        if (!props.resource) return null;
        return <>{props.resource.specification.title}</>;
    },

    Stats: props => {
        if (!props.resource) return null;
        return null;
    },

    ImportantStats: ({resource}) => {
        const projectId = useProjectId();
        if (!resource) return null;

        const isActive = projectId === resource.id;

        return <>
            <Flex alignItems="center" height="36.25px">
                {!resource.status.archived ? null : <>
                    <ProjectTooltip text="Archived">
                        <Icon mr={8} name="tags" color="gray"/>
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
                        /*
                        snackbarStore.addInformation(
                            `${e.title} is now the active project`,
                            false
                        );
                        projectStatus.reload();
                        props.setProject(e.projectId);
                        */
                    }}
                />
            </Flex>
        </>;
    }
};

interface Callbacks {
    invokeCommand: InvokeCommand;
    history: History;
}

const operations: Operation<Project, Callbacks>[] = [
    {
        text: "New Project Application",
        canAppearInLocation: loc => loc === "SIDEBAR",
        primary: true,
        enabled: () => true,
        onClick: (projects, cb) => {
            cb.history.push("/projects/browser/new");
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
                projects.every(it => isAdminOrPI(it.status.myRole!));
        },
        onClick: (projects, cb) => {
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
                projects.every(it => isAdminOrPI(it.status.myRole!));
        },
        onClick: (projects, cb) => {
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
        onClick: (projects, cb) => {
            cb.invokeCommand(Api.deleteMember(
                bulkRequestOf({username: Client.username!})
            ));
        }
    },
    {
        text: "Properties",
        icon: "properties",
        enabled: projects => projects.length === 1,
        onClick: ([project], cb) => {
            cb.history.push(`/projects2/${project.id}`);
        }
    }
];

export const ProjectList2: React.FunctionComponent = props => {
    const history = useHistory();
    const toggleSet = useToggleSet<Project>([]);
    const [commandLoading, invokeCommand] = useCloudCommand();

    const fetchProjects = useCallback((next?: string) => {
        return Api.browse({includeFavorite: true, itemsPerPage: 100, next});
    }, []);

    const callbacks: Callbacks = useMemo(() => {
        return {
            invokeCommand,
            history
        };
    }, []);

    const pageRenderer = useCallback<PageRenderer<Project>>((items) => {
        return <>
            {items.map(it =>
                <ItemRow
                    key={it.id}
                    browseType={BrowseType.MainContent}
                    navigate={() => {
                        history.push("/TODO")
                    }}
                    renderer={ProjectRenderer} 
                    callbacks={callbacks} 
                    operations={operations}
                    item={it} 
                    itemTitle={title}
                    toggleSet={toggleSet}
                />
            )}
        </>;
    }, []);

    const main = <StandardBrowse
        generateCall={fetchProjects}
        pageRenderer={pageRenderer}
    />;

    return <MainContainer
        main={main}
        sidebar={<>
            <Operations selected={toggleSet.checked.items} location={"SIDEBAR"}
                entityNameSingular={title}
                extra={callbacks} operations={operations} />
        </>}
    />;
};

export default ProjectList2;