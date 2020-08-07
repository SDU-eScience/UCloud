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
import {Box, Card, Icon, Text} from "ui-components";
import {useHistory, useParams} from "react-router";
import {DashboardCard} from "Dashboard/Dashboard";
import {ImagePlaceholder, Lorem} from "UtilityComponents";
import styled from "styled-components";
import {GridCardGroup} from "ui-components/Grid";

export const ProjectBrowser: React.FunctionComponent = () => {
    const {action} = useParams();
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
        header={<Heading.h3>Select an affiliation</Heading.h3>}
        main={
            <>
                <Pagination.List
                    loading={projects.loading}
                    page={projects.data}
                    onPageChanged={page => {
                        fetchProjects(browseProjects({itemsPerPage: 50, page}));
                    }}
                    customEmptyPage={
                        <Text>
                            Could not find any projects for which you can apply for more resources.
                            You can contact support for more information.
                        </Text>
                    }
                    pageRenderer={() => {
                        return <>
                            <AffiliationGrid>
                                {projects.data.items.map(it => (
                                    <AffiliationLink
                                        action={action}
                                        projectId={it.projectId}
                                        title={it.title}
                                        key={it.projectId}
                                    />
                                ))}
                            </AffiliationGrid>
                        </>;
                    }}
                />
            </>
        }
    />;
};

const AffiliationLink: React.FunctionComponent<{ action: string, projectId: string, title: string }> = props => {
    const history = useHistory();
    return <DashboardCard
        color={"purple"}
        isLoading={false}
        title={<>
            <ImagePlaceholder height={40} width={40} />
            <Heading.h3 ml={8}>{props.title}</Heading.h3>
        </>}
        subtitle={<Icon name="arrowDown" rotation={-90} size={18} color={"darkGray"} />}
        onClick={() => history.push(`/project/grants/${props.action}/${props.projectId}`)}
    >
        <Box pt={8} pb={16}>
            <Lorem/>
        </Box>
    </DashboardCard>;
};

const AffiliationGrid = styled(GridCardGroup)`
    & > ${Card} {
        position: relative;
        min-height: 200px;
        cursor: pointer;
        transition: transform 0.2s;
        &:hover {
            transform: scale(1.02);
        }
    }
`;
