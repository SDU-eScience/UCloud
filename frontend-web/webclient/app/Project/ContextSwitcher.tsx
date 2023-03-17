import {emptyPage} from "@/DefaultObjects";
import * as React from "react";
import {useDispatch, useSelector} from "react-redux";
import {shortUUID, stopPropagationAndPreventDefault} from "@/UtilityFunctions";
import {useEffect} from "react";
import {dispatchSetProjectAction, getStoredProject} from "@/Project/Redux";
import {Flex, Truncate, Text, Icon, Divider} from "@/ui-components";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import styled from "styled-components";
import {useCloudAPI} from "@/Authentication/DataHook";
import {NavigateFunction, useNavigate} from "react-router";
import {initializeResources} from "@/Services/ResourceInit";
import {useProject} from "./cache";
import ProjectAPI, {Project, useProjectId} from "@/Project/Api";

export function ContextSwitcher(): JSX.Element | null {
    const {activeProject, refresh} = useSelector((it: ReduxObject) => ({activeProject: it.project.project, refresh: it.header.refresh}));
    const project = useProject();
    const projectId = useProjectId();
    const [response, setFetchParams, params] = useCloudAPI<Page<Project>>(
        ProjectAPI.browse({
            itemsPerPage: 250,
            includeFavorite: true,
        }),
        emptyPage
    );

    const dispatch = useDispatch();

    const setProject = React.useCallback((id?: string) => {
        dispatchSetProjectAction(dispatch, id);
    }, [dispatch]);

    let activeContext = "My Workspace";
    if (activeProject) {
        const title = projectId === project.fetch().id ? project.fetch().specification.title : response.data.items.find(it => it.id === projectId)?.specification.title ?? "";
        if (title) {
            activeContext = title;
        } else {
            activeContext = shortUUID(activeProject);
        }
    }

    const sortedProjects = React.useMemo(() => {
        // Note(Jonas): Is this still relevant? Is this not done by the backend?
        return response.data.items.sort((a, b) => {
            if (a.status.isFavorite === true && b.status.isFavorite !== true) {
                return -1;
            } else if (b.status.isFavorite === true && a.status.isFavorite !== true) {
                return 1;
            } else {
                return a.specification.title.localeCompare(b.specification.title);
            }
        }).slice(0, 25);
    }, [response.data.items]);

    useEffect(() => {
        const storedProject = getStoredProject();
        setProject(storedProject ?? undefined);
    }, []);

    const navigate = useNavigate();

    return (
        <Flex key={activeContext} pr="12px" alignItems={"center"} data-component={"project-switcher"}>
            <ClickableDropdown
                trigger={
                    <Flex>
                        <Truncate fontSize={16} width="100px"><b>{activeContext}</b></Truncate>
                        <Icon name="chevronDown" size="12px" ml="4px" mt="6px" />
                    </Flex>
                }
                colorOnHover={false}
                paddingControlledByContent
                onTriggerClick={() => {
                    setFetchParams({...params});
                    // Note(Jonas): Should this always happen?
                    project.reload()
                }}
                left="0px"
                width="250px"
            >
                <BoxForPadding>
                    {activeProject ?
                        (
                            <Text onClick={() => onProjectUpdated(navigate, () => setProject(), refresh, "My Workspace")}>
                                My Workspace
                            </Text>
                        ) : null
                    }
                    {sortedProjects.filter(it => !(it.id === activeProject)).map(project =>
                        <Text
                            key={project.id}
                            onClick={() => onProjectUpdated(navigate, () => setProject(project.id), refresh, project.id)}
                        >
                            <Truncate width="215px">{project.specification.title}</Truncate>
                        </Text>
                    )}
                    {activeProject || response.data.items.length > 0 ? <Divider /> : null}
                    <Text onClick={() => navigate("/projects")}>Manage projects</Text>
                    <Text onClick={() => navigate(`/projects/${projectId ?? "My Workspace"}`)}>
                        {projectId ? "Manage active project" : "Manage my workspace"}
                    </Text>
                </BoxForPadding>
            </ClickableDropdown>
        </Flex>
    );
}

const BoxForPadding = styled.div`
    & > div:hover {
        background-color: var(--lightBlue);
    }

    & > hr {
        width: 80%;
        margin-left: 26px;
    }

    & > ${Text} {
        padding-left: 10px;
    }

    margin-top: 12px;
    margin-bottom: 12px;
`;

const HoverIcon = styled(Icon)`
    &:hover {
        transform: scale(1.1);
    }
`;

function onProjectUpdated(navigate: NavigateFunction, runThisFunction: () => void, refresh: (() => void) | undefined, projectId: string): void {
    const {pathname} = window.location;
    runThisFunction();
    const splitPath = pathname.split("/").filter(it => it);
    if (pathname === "/app/files") {
        navigate("/drives")
    } else if (splitPath.length === 3) {
        if (splitPath[0] === "app" && splitPath[1] === "projects") {
            navigate(`/projects/${projectId}`);
        }
    }
    initializeResources();
    refresh?.();
}
