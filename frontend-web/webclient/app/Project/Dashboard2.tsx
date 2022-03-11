import {MainContainer} from "@/MainContainer/MainContainer";
import * as React from "react";
import { useCallback, useEffect } from "react";
import { Text, Flex, Card, Icon, Link } from "@/ui-components";
import { default as Api, Project } from "./Api";
import {GridCardGroup} from "@/ui-components/Grid";
import {useCloudAPI} from "@/Authentication/DataHook";
import Table, {TableCell, TableRow} from "@/ui-components/Table";
import styled from "styled-components";
import { useHistory, useParams } from "react-router";
import { useTitle } from "@/Navigation/Redux/StatusActions";
import { useSidebarPage, SidebarPages } from "@/ui-components/Sidebar";
import { isAdminOrPI } from "@/Utilities/ProjectUtilities";
import { BreadCrumbsBase } from "@/ui-components/Breadcrumbs";
import HighlightedCard from "@/ui-components/HighlightedCard";
import { shorten } from "@/Utilities/TextUtilities";

// Primary user interface
// ================================================================================
const ProjectDashboard: React.FunctionComponent = () => {
    // Input "parameters"
    const history = useHistory();
    const params = useParams<{ project: string }>();
    const projectId = params.project;

    // Remote data
    const [projectFromApi, fetchProject] = useCloudAPI<Project | null>({noop: true}, null);

    // UI state

    // UI callbacks and state manipulation
    const reload = useCallback(() => {
        fetchProject(Api.retrieve({
            id: projectId,
            includePath: true,
            includeMembers: true,
            includeArchived: true,
            includeGroups: true,
        }));
    }, [projectId]);

    // Aliases and computed data
    const project = projectFromApi.data;
    const isAdmin = !project ? false : isAdminOrPI(project.status.myRole!);

    // Effects
    useEffect(() => reload(), [reload]);

    useTitle("Project Dashboard");
    useSidebarPage(SidebarPages.Projects);

    if (!project) return null;

    return (
        <MainContainer
            header={<Flex>
                <ProjectBreadcrumbsWrapper mb="12px" embedded={false}>
                    <span><Link to="/projects2">My Projects</Link></span>
                    <span>
                        <Link to={`/projects2/${projectId}/dashboard`}>
                            {shorten(20, project.specification.title)}
                        </Link>
                    </span>
                    <span>Dashboard</span>
                </ProjectBreadcrumbsWrapper>
            </Flex>}
            sidebar={null}
            main={(
                <>
                    <ProjectDashboardGrid minmax={330}>
                        <HighlightedCard
                            subtitle={<RightArrow />}
                            onClick={() => history.push(`/projects2/${projectId}/members`)}
                            title="Members"
                            icon="user"
                            color="blue"
                        >
                            <Table>
                                {isAdmin ? (
                                    <tbody>
                                        <TableRow cursor="pointer">
                                            <TableCell>Members</TableCell>
                                            <TableCell textAlign="right">{project.status.members!.length}</TableCell>
                                        </TableRow>
                                        <TableRow cursor="pointer">
                                            <TableCell>Groups</TableCell>
                                            <TableCell textAlign="right">{project.status.groups!.length}</TableCell>
                                        </TableRow>
                                    </tbody>) : null}
                            </Table>
                        </HighlightedCard>

                        <HighlightedCard
                            title={"Resources and Usage"}
                            icon="grant"
                            color="purple"
                            onClick={() => history.push("/project/resources")}
                            subtitle={<RightArrow />}
                        >
                        </HighlightedCard>

                        <HighlightedCard
                            subtitle={<RightArrow />}
                            onClick={() => history.push("/project/grants/ingoing")}
                            title="Grant Applications"
                            icon="mail"
                            color="red"
                        >
                            <Table>
                                <tbody>
                                    <TableRow cursor="pointer">
                                        <TableCell>In Progress</TableCell>
                                        <TableCell textAlign="right">
                                            <Text color="red">Not yet implemented</Text>
                                        </TableCell>
                                    </TableRow>
                                </tbody>
                            </Table>
                        </HighlightedCard>

                        {!isAdmin ? null : (
                            <HighlightedCard
                                subtitle={<RightArrow />}
                                onClick={() => history.push("/project/settings")}
                                title="Settings"
                                icon="properties"
                                color="orange"
                            >
                                <Table>
                                    <tbody>
                                        <TableRow cursor="pointer">
                                            <TableCell>Archived</TableCell>
                                            <TableCell textAlign="right">
                                                {project.status.archived ? "Yes" : "No"}
                                            </TableCell>
                                        </TableRow>
                                    </tbody>
                                </Table>
                            </HighlightedCard>
                        )}

                        {!isAdmin ? null :
                            <HighlightedCard
                                subtitle={<RightArrow/>}
                                onClick={() => history.push(`/subprojects?subproject=${projectId}`)}
                                title="Subprojects"
                                icon="projects"
                                color="green"
                            />
                        }
                    </ProjectDashboardGrid>
                </>
            )}
        />
    );
};

// Secondary interface
// ================================================================================
export function RightArrow(): JSX.Element {
    return (
        <Icon name="arrowDown" rotation={-90} size={18} color={"darkGray"} />
    );
}

// Utilities
// ================================================================================


// Styling
// ================================================================================
const ProjectDashboardGrid = styled(GridCardGroup)`
    & > ${Card} {
        position: relative;
        min-height: 200px;
        cursor: pointer;
        transition: transform 0.2s;
        &:hover {
            transform: translateY(-2px);
        }
    }
`;

const ProjectBreadcrumbsWrapper = styled(BreadCrumbsBase)`
  width: 100%;
  max-width: unset;
  flex-grow: 1;
`;

export default ProjectDashboard;
