import {useCloudAPI} from "Authentication/DataHook";
import {emptyPage, ReduxObject} from "DefaultObjects";
import {MainContainer} from "MainContainer/MainContainer";
import * as Pagination from "Pagination";
import {listProjects, ListProjectsRequest, UserInProject, ProjectMember, ProjectRole} from "Project/index";
import * as React from "react";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {Page} from "Types";
import Button from "ui-components/Button";
import {Text, Icon, Box} from "ui-components";
import Link from "ui-components/Link";
import VerticalButtonGroup from "ui-components/VerticalButtonGroup";
import {prettierString} from "UtilityFunctions";
import {List, Flex} from "ui-components";
import {Spacer} from "ui-components/Spacer";
import {updatePageTitle} from "Navigation/Redux/StatusActions";


// eslint-disable-next-line no-underscore-dangle
const _List: React.FunctionComponent<DispatchProps & {project?: string}> = props => {
    const [response, setFetchParams] = useCloudAPI<Page<UserInProject>, ListProjectsRequest>(
        listProjects({page: 0, itemsPerPage: 50}),
        emptyPage
    );

    React.useEffect(() => {
        props.setPageTitle();
    }, []);

    return (
        <MainContainer
            headerSize={58}
            header={<div />}
            main={(
                <Pagination.List
                    page={response.data}
                    pageRenderer={page => (
                        <List>
                            {page.items.map(e =>
                                <ProjectSummary
                                    summary={e}
                                    isSelected={e.id === props.project}
                                    setProject={props.setProject}
                                    key={e.id}
                                />
                            )}
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

const ProjectSummary: React.FunctionComponent<ProjectSummaryProps> = props => (
    <Spacer
        left={
            <Box mb="4px" mx="4px">
                <Link to={`/projects/view/${props.summary.id}`}><Text fontSize={20}>{props.summary.title}</Text></Link>
                <Flex>
                    <Text fontSize={0} pb="3px"><Icon mt="-2px" size="12px" name="id" /> {props.summary.id}</Text>
                    <Text fontSize={0} ml="4px"><Icon color="white" color2="black" mt="-2px" size="12px" name="user" />
                        {" "}{prettierString(props.summary.whoami.role)}
                    </Text>
                    <Text fontSize={0} ml="4px"><Icon mt="-2px" size="12px" name="projects" /> 42</Text>
                </Flex>
            </Box>
        }
        right={
            <Box pt="6px" mr="6px">
                {props.isSelected ? <Link to="/projects/groups/"><Button mr="38px">Groups</Button></Link> : null}
                {props.isSelected ? <Icon mr="44px" mt="9px" name="check" color="green" /> : (
                    <Button onClick={() => props.setProject(props.summary.id)}>Set active</Button>
                )}
            </Box>
        }
    />
);

interface DispatchProps {
    setProject: (id?: string) => void;
    setPageTitle: () => void;
}

const mapStateToProps = (state: ReduxObject): {project?: string} => state.project;

const mapDispatchToProps = (dispatch: Dispatch): DispatchProps => ({
    setPageTitle: () => dispatch(updatePageTitle("Projects")),
    setProject: (id?: string) => dispatch({type: "SET_PROJECT", project: id})
});

export default connect(mapStateToProps, mapDispatchToProps)(_List);
