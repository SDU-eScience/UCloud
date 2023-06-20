import {bulkRequestOf, emptyPageV2} from "@/DefaultObjects";
import * as React from "react";
import {useDispatch, useSelector} from "react-redux";
import {displayErrorMessageOrDefault, errorMessageOrDefault, shortUUID, stopPropagationAndPreventDefault} from "@/UtilityFunctions";
import {useEffect} from "react";
import {dispatchSetProjectAction, emitProjects, getStoredProject} from "@/Project/Redux";
import {Flex, Truncate, Text, Icon, Input, Relative, Box, Error} from "@/ui-components";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {callAPI, useCloudCommand} from "@/Authentication/DataHook";
import {NavigateFunction, useNavigate} from "react-router";
import {initializeResources} from "@/Services/ResourceInit";
import {useProject} from "./cache";
import ProjectAPI, {Project, useProjectId} from "@/Project/Api";
import {TextH3} from "@/ui-components/Text";
import {injectStyle} from "@/Unstyled";
import Api from "@/Project/Api";
import {AsyncCache} from "@/Utilities/AsyncCache";
import {PageV2} from "@/UCloud";

const PROJECT_ITEMS_PER_PAGE = 250;

const DEFAULT_FETCH_ARGS = {
    itemsPerPage: PROJECT_ITEMS_PER_PAGE,
    includeFavorite: true,
    sortBy: "favorite" as const,
    sortDirection: "descending" as const
}

export const projectCache = new AsyncCache<PageV2<Project>>();

async function fetchProjects(next?: string): Promise<PageV2<Project>> {
    const result = await callAPI<PageV2<Project>>(ProjectAPI.browse({...DEFAULT_FETCH_ARGS, next}));
    if (result.next) {
        const child = await fetchProjects(result.next);
        return {
            items: result.items.concat(child.items),
            itemsPerPage: result.itemsPerPage + child.itemsPerPage,            
        }
    }

    return result;
}

export function ContextSwitcher(): JSX.Element {
    const activeProject = useSelector((it: ReduxObject) => it.project.project);
    const refresh = useSelector((it: ReduxObject) => it.header.refresh);

    const project = useProject();
    const projectId = useProjectId();
    const [error,setError] = React.useState(""); 

    const [projectList, setProjectList] = React.useState<PageV2<Project>>(emptyPageV2);

    React.useEffect(() => {
        projectCache.retrieve("", () =>
            fetchProjects()
        ).then(res => {
            setProjectList(res);
            emitProjects();
        }).catch(err => {
            setError(errorMessageOrDefault(err, "Failed to fetch your projects."));
        });
    }, []);

    const dispatch = useDispatch();

    const setProject = React.useCallback((id?: string) => {
        dispatchSetProjectAction(dispatch, id);
    }, [dispatch]);

    let activeContext = "My Workspace";
    if (activeProject) {
        const title = projectId === project.fetch().id ? project.fetch().specification.title : projectList.items.find(it => it.id === projectId)?.specification.title ?? "";
        if (title) {
            activeContext = title;
        } else {
            activeContext = shortUUID(activeProject);
        }
    }

    useEffect(() => {
        const storedProject = getStoredProject();
        setProject(storedProject ?? undefined);
    }, []);

    const navigate = useNavigate();

    const [filter, setTitleFilter] = React.useState("");

    const filteredProjects = React.useMemo(() =>
        projectList.items.filter(it => it.specification.title.toLocaleLowerCase().includes(filter.toLocaleLowerCase()))
        , [projectList, filter]);

    return (
        <Flex key={activeContext} pr="12px" alignItems={"center"} data-component={"project-switcher"}>
            <ClickableDropdown
                trigger={
                    <Flex>
                        <Truncate title={activeContext} fontSize={16} width="130px"><b>{activeContext}</b></Truncate>
                        <Icon name="chevronDown" size="12px" ml="4px" mt="6px" />
                    </Flex>
                }
                colorOnHover={false}
                useMousePositioning
                onTriggerClick={() => projectCache.retrieveWithInvalidCache("", () => callAPI(ProjectAPI.browse(DEFAULT_FETCH_ARGS)))}
                left="0px"
                width="500px"
            >
                <div style={{maxHeight: "385px"}}>
                    <TextH3 bold mt="0" mb="8px">Select workspace</TextH3>
                    <Flex>
                        <Input placeholder="Search..." defaultValue={filter} onKeyUp={e => setTitleFilter("value" in (e.target) ? e.target.value as string : "")} type="text" />
                        <Relative right="34px" top="6px" width="0px" height="0px"><Icon name="search" /></Relative></Flex>
                    <div style={{overflowY: "scroll", maxHeight: "285px", marginTop: "6px"}}>
                        {projectId !== undefined ? (
                            <div key={"My Workspace"} style={{width: "100%"}} data-active={projectId === undefined} className={BottomBorderedRow} onClick={() => {
                                onProjectUpdated(navigate, () => setProject(), refresh, "")
                            }}>
                                <Icon onClick={stopPropagationAndPreventDefault} mx="6px" mt="4px" size="16px" color="blue" hoverColor="blue" name={"starFilled"} />
                                <Text fontSize="var(--breadText)">My Workspace</Text>
                            </div>
                        ) : null}
                        {filteredProjects.map(it =>
                            <div key={it.id + it.status.isFavorite} style={{width: "100%"}} data-active={it.id === projectId} className={BottomBorderedRow} onClick={() => {
                                onProjectUpdated(navigate, () => setProject(it.id), refresh, it.id)
                            }}>
                                <Favorite project={it} />
                                <Text fontSize="var(--breadText)">{it.specification.title}</Text>
                            </div>
                        )}

                        <Box mt="8px">
                            <Error error={error} />
                        </Box>
                        {filteredProjects.length !== 0 || error ? null : (
                            <Box mt="8px" textAlign="center">No workspaces found</Box>
                        )}
                    </div>
                </div>
            </ClickableDropdown>
        </Flex>
    );
}

function Favorite({project}: {project: Project}): JSX.Element {
    const [isFavorite, setIsFavorite] = React.useState(project.status.isFavorite);

    const [commandLoading, invokeCommand] = useCloudCommand();
    const onFavorite = React.useCallback((e: React.SyntheticEvent, p: Project) => {
        e.stopPropagation();
        if (commandLoading) return;
        setIsFavorite(f => !f);
        try {
            invokeCommand(Api.toggleFavorite(
                bulkRequestOf({id: p.id})
            ), {defaultErrorHandler: false});
        } catch (e) {
            setIsFavorite(f => !f);
            displayErrorMessageOrDefault(e, "Failed to toggle favorite");
        }
    }, [commandLoading]);

    return <Icon onClick={e => onFavorite(e, project)} mx="6px" mt="4px" size="16px" color="blue" hoverColor="blue" name={isFavorite ? "starFilled" : "starEmpty"} />
}

function onProjectUpdated(navigate: NavigateFunction, runThisFunction: () => void, refresh: (() => void) | undefined, projectId: string): void {
    const {pathname} = window.location;
    runThisFunction();
    const splitPath = pathname.split("/").filter(it => it);
    let doRefresh = true;
    if (["/app/files/", "/app/files"].includes(pathname)) {
        navigate("/drives")
        doRefresh = false;
    } else if (splitPath.length === 5) {
        if (splitPath[0] === "app" && splitPath[2] === "grants" && splitPath[3] == "view") {
            navigate(`/project/grants/ingoing/${projectId}`);
        }
    }
    initializeResources();
    if (doRefresh) refresh?.();
}

const BottomBorderedRow = injectStyle("bottom-bordered-row", k => `
    ${k}:hover, ${k}[data-active="true"] {
        background-color: var(--lightBlue);
    }

    ${k} {
        transition: 0.1s background-color;
        display: flex;
        border-bottom: 0.5px solid var(--blue);
    }
`);