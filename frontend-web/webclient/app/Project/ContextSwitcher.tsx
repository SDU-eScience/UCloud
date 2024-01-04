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
import AppRoutes from "@/Routes";
import {GradientWithPolygons} from "@/ui-components/GradientBackground";
import {useRefresh} from "@/Utilities/ReduxUtilities";
import {fuzzySearch} from "@/Utilities/CollectionUtilities";

const PROJECT_ITEMS_PER_PAGE = 250;

export const CONTEXT_SWITCHER_DEFAULT_FETCH_ARGS = {
    itemsPerPage: PROJECT_ITEMS_PER_PAGE,
    includeFavorite: true,
    sortBy: "favorite" as const,
    sortDirection: "descending" as const
}

export const projectCache = new AsyncCache<PageV2<Project>>({globalTtl: 0});

async function fetchProjects(next?: string): Promise<PageV2<Project>> {
    const result = await callAPI<PageV2<Project>>(ProjectAPI.browse({...CONTEXT_SWITCHER_DEFAULT_FETCH_ARGS, next}));
    if (result.next) {
        const child = await fetchProjects(result.next);
        return {
            items: result.items.concat(child.items),
            itemsPerPage: result.itemsPerPage + child.itemsPerPage,
        }
    }

    return result;
}

export function projectTitleFromCache(projectId?: string) {
    if (!projectId) return "My workspace";
    const project = projectCache.retrieveFromCacheOnly("")?.items.find(it => it.id === projectId);
    return project?.specification.title ?? ""
}

const triggerClass = injectStyle("context-switcher-trigger", k => `
    ${k} {
        border: 1px solid var(--midGray);
        border-radius: 6px;
        padding: 6px 12px;
        display: flex;
    }
    
    ${k}:hover {
        border: 1px solid var(--gray);
    }
    
    .${GradientWithPolygons} ${k} {
        border: 1px solid var(--gray);
    }
    
    .${GradientWithPolygons} ${k}:hover {
        border: 1px solid var(--darkGray);
    }
`);

export function ContextSwitcher({managed}: {
    managed?: {
        setLocalProject: (project?: string) => void;
    }
}): JSX.Element {
    const refresh = useRefresh();

    const project = useProject();
    const projectId = useProjectId();
    // Note(Jonas): Only for use if the context switcher data is managed elsewhere.
    const [controlledProject, setControlledProject] = React.useState(projectId);
    const [error, setError] = React.useState("");

    const [projectList, setProjectList] = React.useState<PageV2<Project>>(emptyPageV2);

    React.useEffect(() => {
        projectCache.retrieve("", () =>
            fetchProjects()
        ).then(res => {
            setProjectList(res);
            emitProjects(projectId ?? null);
        }).catch(err => {
            setError(errorMessageOrDefault(err, "Failed to fetch your projects."));
        });
    }, []);

    const dispatch = useDispatch();

    const setProject = React.useCallback((id?: string) => {
        dispatchSetProjectAction(dispatch, id);
    }, [dispatch]);

    let activeContext = "My workspace";
    const activeProject = managed ? controlledProject : projectId;
    if (activeProject) {
        const title = activeProject === project.fetch().id ?
            project.fetch().specification.title :
            projectList.items.find(it => it.id === activeProject)?.specification.title ?? "";
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

    const reload = React.useCallback(() => {
        projectCache.retrieveWithInvalidCache("", () => callAPI(ProjectAPI.browse(CONTEXT_SWITCHER_DEFAULT_FETCH_ARGS)))[1].then(it => {
            setProjectList(it);
        })
    }, []);

    const arrowKeyIndex = React.useRef(-1);

    const navigate = useNavigate();

    const [filter, setTitleFilter] = React.useState("");

    const filteredProjects: Project[] = React.useMemo(() => {
        if (filter === "") return projectList.items;

        const searchResults = fuzzySearch(projectList.items.map(it => it.specification), ["title"], filter, { sort: true });
        return searchResults
            .map(it => projectList.items.find(p => it.title === p.specification.title))
            .filter(it => it !== undefined) as Project[];
    }, [projectList, filter]);

    const divRef = React.useRef<HTMLDivElement>(null);

    const setActiveProject = React.useCallback((id?: string) => {
        if (managed?.setLocalProject) {
            managed.setLocalProject(id);
            setControlledProject(id);
        } else {
            onProjectUpdated(navigate, () => setProject(id), refresh, id ?? "")
        }
    }, [refresh]);

    const showMyWorkspace =
        activeProject !== undefined && "My Workspace".toLocaleLowerCase().includes(filter.toLocaleLowerCase());

    return (
        <Flex key={activeContext} alignItems={"center"} data-component={"project-switcher"}>
            <ClickableDropdown
                trigger={
                    <div className={triggerClass}>
                        <Truncate title={activeContext} fontSize={14} width="180px"><b>{activeContext}</b></Truncate>
                        <Icon name="heroChevronDown" size="14px" ml="4px" mt="4px" />
                    </div>
                }
                rightAligned
                paddingControlledByContent
                arrowkeyNavigationKey="data-active"
                hoverColor={"tableRowHighlight"}
                onSelect={el => {
                    const id = el?.getAttribute("data-project") ?? undefined;
                    setActiveProject(id)
                }}
                onClose={() => {
                    arrowKeyIndex.current = -1;
                    setTitleFilter("");
                }}
                colorOnHover={false}
                onTriggerClick={reload}
                width="500px"
            >
                <div style={{maxHeight: "385px"}}>
                    <Flex>
                        <Input
                            autoFocus
                            className={filterInputClass}
                            placeholder="Search..."
                            defaultValue={filter}
                                onKeyDown={e => {
                                if (["Escape"].includes(e.key) && e.target["value"]) {
                                    setTitleFilter("");
                                    e.target["value"] = "";
                                    e.stopPropagation();
                                }
                            }}
                            onKeyUp={e => {
                                e.stopPropagation();
                                setTitleFilter("value" in e.target ? e.target.value as string : "");
                            }}
                            type="text"
                        />

                        <Relative right="24px" top="5px" width="0px" height="0px">
                            <Icon name="search" />
                        </Relative>
                    </Flex>

                    <div ref={divRef} style={{overflowY: "auto", maxHeight: "285px", lineHeight: "2em"}}>
                        <Error error={error} />

                        {showMyWorkspace ? (
                            <div
                                key={"My Workspace"}
                                style={{width: "100%"}}
                                data-active={activeProject == null}
                                className={BottomBorderedRow}
                                onClick={() => {setActiveProject();}}
                            >
                                <Icon onClick={stopPropagationAndPreventDefault} mx="6px" mt="6px" size="16px" color="blue" hoverColor="blue" name={"starFilled"} />
                                <Text fontSize="var(--breadText)">My Workspace</Text>
                            </div>
                        ) : null}
                        {filteredProjects.map(it =>
                            <div
                                key={it.id + it.status.isFavorite}
                                style={{width: "100%"}}
                                data-active={it.id === activeProject}
                                data-project={it.id}
                                className={BottomBorderedRow}
                                onClick={() => setActiveProject(it.id)}
                            >
                                <Favorite project={it} />
                                <Text fontSize="var(--breadText)">{it.specification.title}</Text>
                            </div>
                        )}

                        {filteredProjects.length !== 0 || showMyWorkspace|| error ? null : (
                            <Box my="32px" textAlign="center">No workspaces found</Box>
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

    return <Icon onClick={e => onFavorite(e, project)} mx="6px" mt="6px" size="16px" color="blue" hoverColor="blue" name={isFavorite ? "starFilled" : "starEmpty"} />
}

function onProjectUpdated(navigate: NavigateFunction, runThisFunction: () => void, refresh: (() => void) | undefined, projectId?: string): void {
    const {pathname} = window.location;
    runThisFunction();
    let doRefresh = true;

    if (["/app/files/", "/app/files"].includes(pathname)) {
        navigate("/drives")
        doRefresh = false;
    }

    if (!projectId) {
        if (["/app/projects/members", "/app/projects/members/"].includes(pathname)
            || ["/app/subprojects/", "/app/subprojects"].includes(pathname)) {
            navigate(AppRoutes.dashboard.dashboardA());
            doRefresh = false;
        }
    }

    initializeResources();
    if (doRefresh) refresh?.();
}

const BottomBorderedRow = injectStyle("bottom-bordered-row", k => `
    ${k}:hover {
        background-color: var(--tableRowHighlight);
    }
    
    ${k}[data-active="true"] {
        background-color: var(--lightBlue);
    }

    ${k} {
        transition: 0.1s background-color;
        display: flex;
        border-bottom: 0.5px solid var(--borderGray);
    }
`);


const filterInputClass = injectStyle("filter-input", k => `
    ${k}:focus {
        border: 0;
        border-bottom: 1px solid var(--borderGray);
        box-shadow: unset;
    }
    
    ${k} {
        box-shadow: none;
        border: 0;
        border-radius: 0;
        width: calc(100%);
        flex-shrink: 0;
        border-bottom: 1px solid var(--borderGray);
        background: transparent;
        color: var(--text);
        outline: none;
    }
`)