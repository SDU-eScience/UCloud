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
import {Text, Icon} from "ui-components";
import Link from "ui-components/Link";
import VerticalButtonGroup from "ui-components/VerticalButtonGroup";
import {prettierString} from "UtilityFunctions";
import {List} from "ui-components";
import {updatePageTitle} from "Navigation/Redux/StatusActions";
import {setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {ListRow} from "ui-components/List";
import {useHistory} from "react-router";
import {loadingAction} from "Loading";


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
                            {page.items.map(e => {
                                const isSelected = e.projectId === props.project;
                                return (
                                    <ListRow
                                        key={e.projectId}
                                        navigate={() => history.push(`/projects/view/${encodeURIComponent(e.projectId)}`)}
                                        isSelected={false}
                                        select={() => undefined}
                                        left={<Text cursor="pointer">{e.title}</Text>}
                                        leftSub={<>
                                            <Text fontSize={0} pb="3px"><Icon mt="-2px" size="12px" name="id" /> {e.projectId}</Text>
                                            <Text fontSize={0} ml="4px"><Icon color="white" color2="black" mt="-2px" size="12px" name="user" />
                                                {" "}{prettierString(e.whoami.role)}
                                            </Text>
                                        </>}
                                        right={<>
                                            {isSelected ? <Link to="/projects/repositories"><Button mr="6px">Repositories</Button></Link> : null}
                                            {isSelected ? <Link to="/projects/groups/"><Button mr="38px">Groups</Button></Link> : null}
                                            {isSelected ? <Icon mr="44px" mt="9px" name="check" color="green" /> : (
                                                <Button onClick={() => props.setProject(e.projectId)}>Set active</Button>
                                            )}
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
                    <Link to="/projects/create"><Button>Create</Button></Link>
                    <Button disabled={!props.project} color="red" onClick={() => props.setProject(undefined)}>Clear selection</Button>
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
    setProject: id => dispatch({type: "SET_PROJECT", project: id}),
    setRefresh: refresh => dispatch(setRefreshFunction(refresh)),
    setLoading: loading => dispatch(loadingAction(loading))
});

export default connect(mapStateToProps, mapDispatchToProps)(_List);
