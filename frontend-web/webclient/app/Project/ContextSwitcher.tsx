import {ReduxObject, emptyPage} from "DefaultObjects";
import * as React from "react";
import {connect} from "react-redux";
import Link from "ui-components/Link";
import {inDevEnvironment, addTrailingSlash} from "UtilityFunctions";
import {useEffect} from "react";
import {Dispatch} from "redux";
import {dispatchSetProjectAction, getStoredProject} from "Project/Redux";
import {Flex, Truncate, Text} from "ui-components";
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
function _AltContextSwitcher(props: ContextSwitcherReduxProps & DispatchProps): JSX.Element | null {
    const [response, setFetchParams, params] = useCloudAPI<Page<UserInProject>, ListProjectsRequest>(
        listProjects({page: 0, itemsPerPage: 10}),
        emptyPage
    );
    const activeContext = props.activeProject ?? "Personal Project";

    useEffect(() => {
        const storedProject = getStoredProject();
        props.setProject(storedProject ?? undefined);
    }, []);

    const history = useHistory();

    if (response.data.items.length === 0 && !response.loading) return null;

    const addMore = response.data.itemsInTotal > 10;

    return (
        <Flex style={{border: "1px solid white", borderRadius: "8px"}} ml="25px" pr="12px">
            <Link to="/projects"><HoverBox width="auto">{activeContext}</HoverBox></Link>
            <ClickableDropdown
                trigger={<div />}
                chevron
                onTriggerClick={() => setFetchParams({...params})}
                left="-136px"
                width="250px"
            >
                {props.activeProject ? <Text onClick={() => promptRedirect(history, () => props.setProject(), props.refresh)}>Personal project</Text> : null}
                {response.data.items.filter(it => !(it.projectId === props.activeProject)).map(project =>
                    <Text
                        key={project.projectId}
                        onClick={() => promptRedirect(history, () => props.setProject(project.projectId), props.refresh)}
                    >
                        {project.projectId}
                    </Text>
                )}
                {addMore ? <Link to="/projects"><Text>More...</Text></Link> : null}
            </ClickableDropdown>
        </Flex>
    );
}

const filesPathname = "/app/files/";
const filesSearch = "?path=";

function promptRedirect(history: History, setProject: () => void, refresh?: () => void): void {
    const {pathname, search} = window.location;
    // Edge cases
    setProject();
    if (addTrailingSlash(pathname) === filesPathname && search.startsWith(filesSearch)) {
        history.push(fileTablePage(Client.hasActiveProject ? Client.projectFolder : Client.homeFolder));
    } else refresh?.();
}

const HoverBox = styled(Truncate)`
    width: auto;
    color: white;
    padding-left: 8px;
    padding-right: 8px;
    border-radius: 8px;
    cursor: pointer;
    border-top-right-radius: 0px;
    border-bottom-right-radius: 0px;
    border-right: 1px solid white;
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

export const AltContextSwitcher = connect(mapStateToProps, mapDispatchToProps)(_AltContextSwitcher);
