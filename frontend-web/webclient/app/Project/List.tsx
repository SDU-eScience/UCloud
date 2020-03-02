import {useCloudAPI} from "Authentication/DataHook";
import {emptyPage} from "DefaultObjects";
import {MainContainer} from "MainContainer/MainContainer";
import * as Pagination from "Pagination";
import {listProjects, ListProjectsRequest, UserInProject, ProjectMember, ProjectRole} from "Project/index";
import * as React from "react";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {Page} from "Types";
import Button from "ui-components/Button";
import {Text, Icon, Box} from "ui-components";
import * as Heading from "ui-components/Heading";
import Link from "ui-components/Link";
import VerticalButtonGroup from "ui-components/VerticalButtonGroup";
import {prettierString} from "UtilityFunctions";
import {List, Flex} from "ui-components";
import {Spacer} from "ui-components/Spacer";

function newUserInProject(): UserInProject {
    return {
        id: `${Math.random() * 600 | 0}`,
        title: Math.random() > 0.5 ? "Hello" : "Bye",
        whoami: newProjectMember()
    };
}

function newProjectMember(): ProjectMember {
    return {
        role: Object.values(ProjectRole)[(Math.random() * 4) | 0],
        username: "Frank"
    };
}

const dummyProjects: UserInProject[] = [];
for (let i = 0; i < 10; i++) {
    dummyProjects.push(newUserInProject());
}

// eslint-disable-next-line no-underscore-dangle
const _List: React.FunctionComponent<DispatchProps> = props => {
    const [response, setFetchParams] = useCloudAPI<Page<UserInProject>, ListProjectsRequest>(
        listProjects({page: 0, itemsPerPage: 50}),
        // emptyPage,
        {items: dummyProjects, itemsPerPage: 25, itemsInTotal: 5, pageNumber: 0, pagesInTotal: 1}
    );

    return (
        <MainContainer
            header={<Heading.h2>Your Projects</Heading.h2>}
            main={(
                <Pagination.List
                    page={response.data}
                    pageRenderer={page => (
                        <List>
                            {page.items.map(e =>
                                <ProjectSummary summary={e} setProject={props.setProject} key={e.id} />
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
                    <Button color="red" onClick={() => props.setProject(undefined)}>Clear Project</Button>
                </VerticalButtonGroup>
            )}
        />
    );
};

const ProjectSummary: React.FunctionComponent<{summary: UserInProject} & DispatchProps> = props => (
    <Spacer
        left={
            <Box mx="4px">
                <Link to={`/projects/view/${props.summary.id}`}><Heading.h3>{props.summary.title}</Heading.h3></Link>
                <Flex>
                    <Text fontSize={0}><Icon size="12px" name="id"/> {props.summary.id}</Text>
                    <Text fontSize={0} ml="4px">role: {prettierString(props.summary.whoami.role)}</Text>
                </Flex>
            </Box>
        }
        right={<Button mr="4px" onClick={() => props.setProject(props.summary.id)}>Set as active</Button>}
    />
);

interface DispatchProps {
    setProject: (id?: string) => void;
}

const mapDispatchToProps = (dispatch: Dispatch): DispatchProps => ({
    setProject: (id?: string) => dispatch({type: "SET_PROJECT", project: id})
});

export default connect(null, mapDispatchToProps)(_List);
