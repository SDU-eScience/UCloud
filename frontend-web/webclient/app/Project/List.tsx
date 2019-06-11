import * as React from "react";
import {MainContainer} from "MainContainer/MainContainer";
import Button from "ui-components/Button";
import VerticalButtonGroup from "ui-components/VerticalButtonGroup";
import * as Pagination from "Pagination";
import {emptyPage} from "DefaultObjects";
import {useCloudAPI} from "Authentication/DataHook";
import {Page} from "Types";
import {listProjects, ListProjectsRequest, UserInProject} from "Project/index";
import Link from "ui-components/Link";
import Box from "ui-components/Box";
import {Dispatch} from "redux";
import {connect} from "react-redux";

const List: React.FunctionComponent<DispatchProps> = props => {
    const [response, setFetchParams, params] = useCloudAPI<Page<UserInProject>, ListProjectsRequest>(
        listProjects({page: 0, itemsPerPage: 50}),
        emptyPage
    );

    return <MainContainer
        headerSize={0}
        header={null}
        main={
            <Pagination.List
                page={response.data}
                pageRenderer={page => <>
                    {page.items.map(e =>
                        <ProjectSummary summary={e} setProject={props.setProject} key={e.id}/>
                    )}
                </>}
                loading={response.loading}
                onPageChanged={(newPage, page) => setFetchParams(listProjects({page: newPage, itemsPerPage: 50}))}
            />
        }
        sidebar={
            <VerticalButtonGroup>
                <Link to={"/projects/create"}><Button>Create</Button></Link>
            </VerticalButtonGroup>
        }
    />;
};

const ProjectSummary: React.FunctionComponent<{ summary: UserInProject } & DispatchProps> = props => {
    return <Box>
        In {props.summary.title} you are a {props.summary.whoami.role}. This is a
        <Link to={`/projects/view/${props.summary.id}`}>link</Link>
        <Button onClick={() => props.setProject(props.summary.id)}>Set as active</Button>
    </Box>;
};

interface DispatchProps {
    setProject: (id: string) => void
}

const mapDispatchToProps = (dispatch: Dispatch): DispatchProps => ({
    setProject: (id: string) => dispatch({type: "SET_PROJECT", project: id})
});

export default connect(null, mapDispatchToProps)(List);
