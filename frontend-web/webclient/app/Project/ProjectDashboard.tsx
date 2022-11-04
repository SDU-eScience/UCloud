import {MainContainer} from "@/MainContainer/MainContainer";
import * as React from "react";
import {Flex, Card, Icon, Box} from "@/ui-components";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {setRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {loadingAction} from "@/Loading";
import {dispatchSetProjectAction} from "@/Project/Redux";
import {GridCardGroup} from "@/ui-components/Grid";
import {ProjectBreadcrumbs} from "@/Project/Breadcrumbs";
import styled from "styled-components";
import {useHistory} from "react-router";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {useSidebarPage, SidebarPages} from "@/ui-components/Sidebar";
import HighlightedCard from "@/ui-components/HighlightedCard";
import {useProject} from "./cache";
import {isAdminOrPI} from "./Api";

const ProjectDashboard: React.FunctionComponent<ProjectDashboardOperations> = () => {
    const project = useProject();
    const fetchedProject = project.fetch();
    const projectId = fetchedProject.id;
    const projectRole = fetchedProject.status.myRole;
    const needsVerification = fetchedProject.status.needsVerification;
        
    function isPersonalProjectActive(id: string): boolean {
        return id === undefined || id === "";
    }

    useTitle("Project Dashboard");
    useSidebarPage(SidebarPages.Projects);

    const history = useHistory();

    return (
        <MainContainer
            header={<Flex>
                <ProjectBreadcrumbs allowPersonalProject crumbs={[]} />
            </Flex>}
            sidebar={null}
            main={(
                <>
                    <ProjectDashboardGrid minmax={330}>
                        {projectId !== undefined && projectId !== "" ? (
                            <HighlightedCard
                                subtitle={<RightArrow />}
                                onClick={() => history.push("/project/members")}
                                title="Members"
                                icon="user"
                                color="blue"
                                isLoading={false}
                            >
                                Manage and project members and groups. This is where you can invite new members to your
                                project.

                                {needsVerification ?
                                    <Box color="red" mt={16}><Icon name="warning" mr="4px" /> Attention required</Box> :
                                    null
                                }
                            </HighlightedCard>
                        ) : null}
                        <HighlightedCard
                            title={"Resource Usage"}
                            icon="grant"
                            color="green"
                            isLoading={false}
                            onClick={() => history.push("/project/resources")}
                            subtitle={<RightArrow />}
                        >
                            Track how many resources you have consumed.
                        </HighlightedCard>
                        <HighlightedCard
                            title={"Resource Allocations"}
                            icon="grant"
                            color="darkGreen"
                            isLoading={false}
                            onClick={() => history.push("/project/allocations")}
                            subtitle={<RightArrow />}
                        >
                            Manage your allocations and grant allocations to sub-projects.
                        </HighlightedCard>

                        {isPersonalProjectActive(projectId) || !isAdminOrPI(projectRole) ? null :
                            <HighlightedCard
                                subtitle={<RightArrow />}
                                onClick={() => history.push("/project/grants/ingoing")}
                                title="Grant Applications"
                                icon="mail"
                                color="red"
                                isLoading={false}
                            >
                                View the grant applications you have received.
                            </HighlightedCard>}
                        {isPersonalProjectActive(projectId) || !isAdminOrPI(projectRole) ? null : (
                            <HighlightedCard
                                subtitle={<RightArrow />}
                                onClick={() => history.push("/project/settings")}
                                title="Settings"
                                icon="properties"
                                color="orange"
                                isLoading={false}
                            >
                                View and manage settings for this project.
                            </HighlightedCard>
                        )}
                        {isPersonalProjectActive(projectId) || !isAdminOrPI(projectRole) ? null :
                            <HighlightedCard
                                subtitle={<RightArrow/>}
                                onClick={() => history.push(`/subprojects?subproject=${projectId}`)}
                                title="Subprojects"
                                icon="projects"
                                color="purple"
                            >
                                View and manage sub-projects.
                            </HighlightedCard>
                        }
                    </ProjectDashboardGrid>
                </>
            )}
        />
    );
};

export function RightArrow(): JSX.Element {
    return (
        <Icon name="arrowDown" rotation={-90} size={18} color={"darkGray"} />
    );
}

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

interface ProjectDashboardOperations {
    setRefresh: (refresh?: () => void) => void;
    setLoading: (loading: boolean) => void;
    setActiveProject: (project: string) => void;
}

const mapDispatchToProps = (dispatch: Dispatch): ProjectDashboardOperations => ({
    setRefresh: refresh => dispatch(setRefreshFunction(refresh)),
    setLoading: loading => dispatch(loadingAction(loading)),
    setActiveProject: project => dispatchSetProjectAction(dispatch, project),
});

const _unused = connect(null, mapDispatchToProps)(ProjectDashboard);
