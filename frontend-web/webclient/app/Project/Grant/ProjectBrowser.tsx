import * as React from "react";
import {SidebarPages, useSidebarPage} from "ui-components/Sidebar";
import {useLoading, useTitle} from "Navigation/Redux/StatusActions";
import {MainContainer} from "MainContainer/MainContainer";
import * as Heading from "ui-components/Heading";
import {useCloudAPI} from "Authentication/DataHook";
import {browseProjects, BrowseProjectsResponse} from "Project/Grant/index";
import {emptyPage} from "DefaultObjects";
import * as Pagination from "Pagination";
import {useRefreshFunction} from "Navigation/Redux/HeaderActions";
import {List} from "ui-components";
import {ListRow} from "ui-components/List";
import {useHistory, useParams} from "react-router";

export const ProjectBrowser: React.FunctionComponent = () => {
    const {action} = useParams();
    const history = useHistory();
    useTitle("Project Browser");
    useSidebarPage(SidebarPages.Projects);

    const [projects, fetchProjects, projectsParams] = useCloudAPI<BrowseProjectsResponse>(
        browseProjects({itemsPerPage: 50, page: 0}),
        emptyPage
    );

    useRefreshFunction(() => fetchProjects({...projectsParams, reloadId: Math.random()}));
    useLoading(projects.loading);

    if (action !== "new" && action !== "personal") return null;

    return <MainContainer
        header={<Heading.h2>Select an affiliation</Heading.h2>}
        main={
            <>
                <List>
                    <Pagination.List
                        loading={projects.loading}
                        page={projects.data}
                        onPageChanged={page => {
                            fetchProjects(browseProjects({itemsPerPage: 50, page}));
                        }}
                        pageRenderer={() => {
                            return projects.data.items.map(it => (
                                <ListRow
                                    left={it.title}
                                    right={<></>}
                                    key={it.projectId}
                                    navigate={() => {
                                        history.push(`/project/grants/${action}/${it.projectId}`);
                                    }}
                                    leftSub={<></>}
                                />
                            ));
                        }}
                    />
                </List>
            </>
        }
    />;
};
