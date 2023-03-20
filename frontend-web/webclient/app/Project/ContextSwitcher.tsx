import {emptyPage} from "@/DefaultObjects";
import * as React from "react";
import {connect} from "react-redux";
import {shortUUID, stopPropagationAndPreventDefault} from "@/UtilityFunctions";
import {useEffect} from "react";
import {Dispatch} from "redux";
import {dispatchSetProjectAction, getStoredProject} from "@/Project/Redux";
import {Flex, Truncate, Text, Icon, Divider} from "@/ui-components";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import styled from "styled-components";
import {useCloudAPI} from "@/Authentication/DataHook";
import {NavigateFunction, useNavigate} from "react-router";
import {initializeResources} from "@/Services/ResourceInit";
import {useProject} from "./cache";
import ProjectAPI, {Project, useProjectId} from "@/Project/Api";

// eslint-disable-next-line no-underscore-dangle
function _ContextSwitcher(props: ContextSwitcherReduxProps & DispatchProps): JSX.Element | null {
    const project = useProject();
    const projectId = useProjectId();
    const [response, setFetchParams, params] = useCloudAPI<Page<Project>>(
        ProjectAPI.browse({
            itemsPerPage: 250,
            includeFavorite: true,
        }),
        emptyPage
    );

    let activeContext = "My Workspace";
    if (props.activeProject) {
        const title = projectId === project.fetch().id ? project.fetch().specification.title : response.data.items.find(it => it.id === projectId)?.specification.title ?? "";
        if (title) {
            activeContext = title;
        } else {
            activeContext = shortUUID(props.activeProject);
        }
    }

    const sortedProjects = React.useMemo(() => {
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
        props.setProject(storedProject ?? undefined);
    }, []);

    const navigate = useNavigate();

    return (
        <Flex pr="12px" alignItems={"center"} data-component={"project-switcher"}>
            <ClickableDropdown
                trigger={
                    <HoverBox>
                        <HoverIcon
                            onClick={e => {
                                stopPropagationAndPreventDefault(e);
                                navigate(`/projects/${projectId ?? "My Workspace"}`);
                            }}
                            name="projects"
                            color2="midGray"
                            mr=".5em"
                        />
                        <Truncate width={"150px"}>{activeContext}</Truncate>
                        <Icon name={"chevronDown"} size={"12px"} ml={"4px"} />
                    </HoverBox>
                }
                colorOnHover={false}
                paddingControlledByContent
                onTriggerClick={() => (setFetchParams({...params}), project.reload())}
                left="0px"
                width="250px"
            >
                <BoxForPadding>
                    {props.activeProject ?
                        (
                            <Text onClick={() => onProjectUpdated(navigate, () => props.setProject(), props.refresh, "My Workspace")}>
                                My Workspace
                            </Text>
                        ) : null
                    }
                    {sortedProjects.filter(it => !(it.id === props.activeProject)).map(project =>
                        <Text
                            key={project.id}
                            onClick={() => onProjectUpdated(navigate, () => props.setProject(project.id), props.refresh, project.id)}
                        >
                            <Truncate width="215px">{project.specification.title}</Truncate>
                        </Text>
                    )}
                    {props.activeProject || response.data.items.length > 0 ? <Divider /> : null}
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

    & > ${Divider} {
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
    } else if (splitPath.length === 5) {
        if (splitPath[0] === "app" && splitPath[2] === "grants" && splitPath[3] == "view") {
            navigate(`/project/grants/ingoing/${projectId}`);
        }
    }
    initializeResources();
    refresh?.();
}

const HoverBox = styled.div`
    display: inline-flex;
    flex-wrap: nowrap;
    color: white;
    padding: 6px 8px;
    cursor: pointer;
    user-select: none;
    align-items: center;
    border-radius: 5px;
    &:hover {
        background-color: rgba(236, 239, 244, 0.25);
        color: white;
        transition: background-color 0.2s;
    }
`;

interface ContextSwitcherReduxProps {
    activeProject?: string;
    refresh?: () => void;
}

interface DispatchProps {
    setProject: (id?: string) => void;
}

const mapStateToProps = (state: ReduxObject): ContextSwitcherReduxProps =>
    ({activeProject: state.project.project, refresh: state.header.refresh});

const mapDispatchToProps = (dispatch: Dispatch): DispatchProps => ({
    setProject: id => dispatchSetProjectAction(dispatch, id)
});

export const ContextSwitcher = connect(mapStateToProps, mapDispatchToProps)(_ContextSwitcher);
