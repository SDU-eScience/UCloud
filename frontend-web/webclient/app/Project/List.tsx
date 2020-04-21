import {useCloudAPI} from "Authentication/DataHook";
import {emptyPage, ReduxObject} from "DefaultObjects";
import {MainContainer} from "MainContainer/MainContainer";
import * as Pagination from "Pagination";
import {listProjects, ListProjectsRequest, UserInProject} from "Project/index";
import * as React from "react";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {Page} from "Types";
import Button from "ui-components/Button";
import {Flex, Icon, List, Text} from "ui-components";
import Link from "ui-components/Link";
import VerticalButtonGroup from "ui-components/VerticalButtonGroup";
import {updatePageTitle} from "Navigation/Redux/StatusActions";
import {setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {ListRow} from "ui-components/List";
import {useHistory} from "react-router";
import {loadingAction} from "Loading";
import {dispatchSetProjectAction} from "Project/Redux";
import {projectRoleToString} from "Project/api";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {isAdminOrPI} from "Utilities/ProjectUtilities";
import {Client} from "Authentication/HttpClientInstance";

// eslint-disable-next-line no-underscore-dangle
const _List: React.FunctionComponent<DispatchProps & {project?: string}> = props => {
    const [response, setFetchParams] = useCloudAPI<Page<UserInProject>, ListProjectsRequest>(
        listProjects({page: 0, itemsPerPage: 50}),
        emptyPage
    );

    React.useEffect(() => {
        props.setLoading(response.loading);
    }, [response.loading]);

    const history = useHistory();

    const reload = (): void =>
        setFetchParams(listProjects({page: response.data.pageNumber, itemsPerPage: response.data.itemsPerPage}));

    React.useEffect(() => {
        props.setPageTitle();
    }, []);

    React.useEffect(() => {
        props.setRefresh(reload);
        return () => props.setRefresh();
    }, [reload]);

    return (
        <MainContainer
            headerSize={58}
            header={<div />}
            main={(
                <Pagination.List
                    page={response.data}
                    pageRenderer={page => (
                        <List>
                            <ListRow
                                left={<Text>Personal project</Text>}
                                leftSub={<Text color="gray" fontSize={0}><Icon size="10" name="id" /> {Client.username}</Text>}
                                right={<Icon
                                    mr="20px"
                                    mt="5px"
                                    name="check"
                                    color={!props.project ? "green" : "gray"}
                                    hoverColor="green"
                                    onClick={() => {
                                        if (!props.project) return;
                                        snackbarStore.addInformation(`Personal project is now the active.`);
                                        props.setProject();
                                    }}
                                />}
                            />
                            {page.items.map(e => {
                                const isSelected = e.projectId === props.project;
                                const showGroups = isSelected && isAdminOrPI(e.whoami.role);
                                return (
                                    <ListRow
                                        key={e.projectId}
                                        navigate={() => history.push(`/projects/view/${encodeURIComponent(e.projectId)}`)}
                                        left={<Text cursor="pointer">{e.title}</Text>}
                                        leftSub={<>
                                            <Text color="gray" fontSize={0}><Icon size="10" name="id" /> {e.projectId}</Text>
                                            <Text ml="4px" color="gray" fontSize={0}><Icon color="white" color2="gray" mt="-2px" size="10" name="user" />
                                                {" "}{projectRoleToString(e.whoami.role)}
                                            </Text>
                                        </>}
                                        right={<>
                                            <Flex alignItems={"center"}>
                                                {!e.needsVerification ? null : (
                                                    <Text fontSize={0} mr={8}><Icon name={"warning"} /> Attention required</Text>
                                                )}
                                                {showGroups ? <Link to="/projects/groups/"><Button mr="38px">Groups</Button></Link> : null}
                                                <Icon
                                                    mr="20px"
                                                    mt="5px"
                                                    name="check"
                                                    color={isSelected ? "green" : "gray"}
                                                    hoverColor="green"
                                                    onClick={() => {
                                                        if (isSelected) return;
                                                        snackbarStore.addInformation(`${e.projectId} is now the active project`);
                                                        props.setProject(e.projectId);
                                                    }}
                                                />
                                            </Flex>
                                        </>}
                                    />
                                );
                            })}
                        </List>
                    )}
                    loading={response.loading}
                    onPageChanged={newPage => setFetchParams(listProjects({page: newPage, itemsPerPage: 50}))}
                />
            )}
            sidebar={(
                <VerticalButtonGroup>
                    <Link to="/projects/create"><Button>Create project</Button></Link>
                </VerticalButtonGroup>
            )}
        />
    );
};

interface ProjectSummaryProps {
    summary: UserInProject;
    isSelected: boolean;
    setProject(id: string): void;
}

interface DispatchProps {
    setProject: (id?: string) => void;
    setPageTitle: () => void;
    setRefresh: (refresh?: () => void) => void;
    setLoading: (loading: boolean) => void;
}

const mapStateToProps = (state: ReduxObject): {project?: string} => state.project;

const mapDispatchToProps = (dispatch: Dispatch): DispatchProps => ({
    setPageTitle: () => dispatch(updatePageTitle("Projects")),
    setRefresh: refresh => dispatch(setRefreshFunction(refresh)),
    setLoading: loading => dispatch(loadingAction(loading)),
    setProject: id => dispatchSetProjectAction(dispatch, id)
});

export default connect(mapStateToProps, mapDispatchToProps)(_List);
