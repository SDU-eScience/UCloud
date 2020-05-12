import {ReduxObject, emptyPage} from "DefaultObjects";
import * as React from "react";
import {connect} from "react-redux";
import Link from "ui-components/Link";
import {addTrailingSlash} from "UtilityFunctions";
import {useEffect} from "react";
import {Dispatch} from "redux";
import {dispatchSetProjectAction, getStoredProject} from "Project/Redux";
import {Flex, Truncate, Text, Icon, Divider} from "ui-components";
import ClickableDropdown from "ui-components/ClickableDropdown";
import styled from "styled-components";
import {useCloudAPI} from "Authentication/DataHook";
import {Page} from "Types";
import {UserInProject, ListProjectsRequest, listProjects} from "Project";
import {useHistory} from "react-router";
import {History} from "history";
import {fileTablePage} from "Utilities/FileUtilities";
import {Client} from "Authentication/HttpClientInstance";

// eslint-disable-next-line no-underscore-dangle
function _ContextSwitcher(props: ContextSwitcherReduxProps & DispatchProps): JSX.Element | null {
    const [response, setFetchParams, params] = useCloudAPI<Page<UserInProject>, ListProjectsRequest>(
        listProjects({page: 0, itemsPerPage: 10, archived: false}),
        emptyPage
    );
    const activeContext = props.activeProject ?? "Personal Project";

    useEffect(() => {
        const storedProject = getStoredProject();
        props.setProject(storedProject ?? undefined);
    }, []);

    const history = useHistory();

    return (
        <Flex pr="12px" alignItems={"center"}>
            <ClickableDropdown
                trigger={
                    <HoverBox>
                        <Icon name={"projects"} mr={"4px"}/>
                        <Truncate width={"150px"}>{activeContext}</Truncate>
                        <Icon name={"chevronDown"} size={"10px"} ml={"4px"}/>
                    </HoverBox>
                }
                onTriggerClick={() => setFetchParams({...params})}
                left="0px"
                width="250px"
            >
                {props.activeProject ?
                    (
                        <Text onClick={() => onProjectUpdated(history, () => props.setProject(), props.refresh)}>
                            Personal project
                        </Text>
                    ) : null
                }
                {response.data.items.filter(it => !(it.projectId === props.activeProject)).map(project =>
                    <Text
                        key={project.projectId}
                        onClick={() => onProjectUpdated(history, () => props.setProject(project.projectId), props.refresh)}
                    >
                        <Truncate width={"215px"}>{project.projectId}</Truncate>
                    </Text>
                )}
                {props.activeProject || response.data.items.length > 0 ? <Divider/> : null}
                <Link to="/projects"><Text>Manage projects</Text></Link>
            </ClickableDropdown>
        </Flex>
    );
}

const filesPathname = "/app/files/";
const filesSearch = "?path=";

function onProjectUpdated(history: History, runThisFunction: () => void, refresh?: () => void): void {
    const {pathname, search} = window.location;
    runThisFunction();
    if (addTrailingSlash(pathname) === filesPathname && search.startsWith(filesSearch)) {
        history.push(fileTablePage(Client.hasActiveProject ? Client.currentProjectFolder : Client.homeFolder));
    } else {
        refresh?.();
    }
}

const HoverBox = styled.div`
    display: inline-flex;
    flex-wrap: none;
    color: white;
    padding: 4px;
    cursor: pointer;
    user-select: none;
    align-items: center;
    &:hover {
        background-color: rgba(255, 255, 255, .3);
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
