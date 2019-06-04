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

const List: React.FunctionComponent = props => {
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
                        <ProjectSummary summary={e} key={e.id}/>
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

const ProjectSummary: React.FunctionComponent<{ summary: UserInProject }> = props => {
    return <Box>
        In {props.summary.title} you are a {props.summary.whoami.role}. This is a <Link
        to={`/projects/view/${props.summary.id}`}>link</Link>
    </Box>;
};

export default List;