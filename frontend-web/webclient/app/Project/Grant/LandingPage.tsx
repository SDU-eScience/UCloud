import * as React from "react";
import {MainContainer} from "MainContainer/MainContainer";
import {useTitle} from "Navigation/Redux/StatusActions";
import {Text, Button, Flex, Icon, Link} from "ui-components";
import {SidebarPages, useSidebarPage} from "ui-components/Sidebar";
import styled from "styled-components";
import {DashboardCard} from "Dashboard/Dashboard";
import {Center} from "UtilityComponents";
import {IconName} from "ui-components/Icon";
import {ThemeColor} from "ui-components/theme";
import {useProjectManagementStatus} from "Project";

const LandingPageContainer = styled.div`
    display: grid;
    max-width: 1300px;
    grid-template-columns: repeat(auto-fill, minmax(200px, 400px));
    grid-gap: 32px;
    justify-content: center;
`;

const LandingPageCard: React.FunctionComponent<{
    color: ThemeColor;
    icon: IconName;
    title: string;
    linkLocation: string;
}> = props => {
    return <DashboardCard color={props.color} isLoading={false} title={props.title} height={"700px"}>
        <Center my={16}>
            <Icon color={"iconColor"} color2={"iconColor2"} name={props.icon} size={"128px"}/>
        </Center>
        <Link to={props.linkLocation}><Button fullWidth mb={16}>Apply for resources</Button></Link>
        {props.children}
    </DashboardCard>;
};

export const LandingPage: React.FunctionComponent = () => {
    useTitle("Apply for Resources");
    useSidebarPage(SidebarPages.None);
    const projects = useProjectManagementStatus({allowPersonalProject: true, isRootComponent: true});

    return <MainContainer
        main={
            <Flex alignItems={"center"} justifyContent={"center"}>
                <LandingPageContainer>
                    <LandingPageCard
                        color={"green"}
                        icon={"user"}
                        title={"Personal Project"}
                        linkLocation={"/projects/browser/personal"}
                    >
                        <ul>
                            <li>Provides a playground to try out UCloud</li>
                            <li>Perfect for small experiments</li>
                            <li>Most users are given some credits when they join UCloud</li>
                            <li>Provides limited collaboration features via shares</li>
                            <li>Not suitable for large projects</li>
                        </ul>
                    </LandingPageCard>
                    <LandingPageCard
                        color={"purple"}
                        icon={"projects"}
                        title={"New Project"}
                        linkLocation={"/projects/browser/new"}
                    >
                        <ul>
                            <li>Provides a space for a group to collaborate</li>
                            <li>Suitable for large projects</li>
                            <li>Provides better collaboration features with fine-grained permissions</li>
                            <li>Users must apply for resources before creating a project</li>
                        </ul>
                    </LandingPageCard>
                    {projects.projectId === "" || !projects.allowManagement ? null :
                        <LandingPageCard
                            color={"blue"}
                            icon={"userPi"}
                            title={"Existing Project"}
                            linkLocation={"/project/grants/existing"}
                        >
                            <Text><b>Title:</b> {projects.projectDetails.data.title}</Text>
                            <ul>
                                <li>You can apply for additional resources for your existing project</li>
                                <li>
                                    This allows you to extend the resources available in a single project while
                                    keeping the same project space
                                </li>
                            </ul>
                        </LandingPageCard>
                    }
                </LandingPageContainer>
            </Flex>
        }
    />;
};
