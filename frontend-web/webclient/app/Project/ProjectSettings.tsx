import * as React from "react";
import {useProjectManagementStatus} from "Project/Members";
import {Box, Button, Flex, Text, Link} from "ui-components";
import * as Heading from "ui-components/Heading";
import styled from "styled-components";
import {addStandardDialog} from "UtilityComponents";
import {useAsyncCommand} from "Authentication/DataHook";
import {leaveProject, ProjectRole, setProjectArchiveStatus, UserInProject} from "Project/index";
import {useHistory} from "react-router";
import {fileTablePage} from "Utilities/FileUtilities";
import {Client} from "Authentication/HttpClientInstance";
import {MainContainer} from "MainContainer/MainContainer";
import {MembersBreadcrumbs} from "./MembersPanel";

const ActionContainer = styled.div`
    & > ${Flex} {
        margin-bottom: 16px;
    }
`;

const ActionBox = styled.div`
    display: flex;
    margin-bottom: 16px;
    
    & > ${Box} {
        flex-grow: 1;
    }
    
    & > ${Flex} {
        margin-left: 8px;
        flex-direction: column;
        justify-content: center;
    }
    
    & > ${Flex} > ${Button} {
        min-width: 100px;
    }
`;

export const ProjectSettings: React.FunctionComponent = () => {
    const {projectId, projectRole, projectDetails} = useProjectManagementStatus();
    const history = useHistory();
    return (
        <MainContainer
            header={
                <MembersBreadcrumbs>
                    <li>
                        <Link to="/projects">
                            My Projects
                        </Link>
                    </li>
                    <li>
                        <Link to={`/project/dashboard`}>
                            {projectId}
                        </Link>
                    </li>
                    <li>Settings</li>
                </MembersBreadcrumbs>
            }
            main={
                <ActionContainer>
                    <ArchiveProject
                        isArchived={projectDetails.data.archived}
                        projectId={projectId}
                        projectRole={projectRole}
                        onSuccess={() => history.push("/projects")}
                    />
                    <LeaveProject
                        onSuccess={() => history.push(fileTablePage(Client.homeFolder))}
                        projectDetails={projectDetails.data}
                        projectId={projectId}
                        projectRole={projectRole}
                    />
                </ActionContainer>
            }
            sidebar={null}
        />
    );
};

interface ArchiveProjectProps {
    isArchived: boolean;
    projectRole: ProjectRole;
    projectId: string;
    onSuccess: () => void;
}

export const ArchiveProject: React.FC<ArchiveProjectProps> = props => {
    const [, runCommand] = useAsyncCommand();
    return <>
        {props.projectRole === ProjectRole.USER ? null : (
            <ActionBox>
                <Box flexGrow={1}>
                    <Heading.h4>Project Archival</Heading.h4>
                    <Text>
                        {!props.isArchived ? null : (
                            <>
                                Unarchiving a project will reverse the effects of archival.
                            <ul>
                                    <li>
                                        Your projects will, once again, by visible to you and project
                                        collaborators
                                </li>
                                    <li>This action <i>is</i> reversible</li>
                                </ul>
                            </>
                        )}
                        {props.isArchived ? null : (
                            <>
                                You can archive a project if it is no longer relevant for your day-to-day work.

                                <ul>
                                    <li>
                                        The project will, by default, be hidden for you and project
                                        collaborators
                                    </li>
                                    <li>No data will be deleted from the project</li>
                                    <li>This action <i>is</i> reversible</li>
                                </ul>
                            </>
                        )}
                    </Text>
                </Box>
                <Flex>
                    <Button
                        color={"orange"}
                        onClick={() => {
                            addStandardDialog({
                                title: "Are you sure?",
                                message: `Are you sure you wish to ` +
                                    `${props.isArchived ? "unarchive" : "archive"} ${props.projectId}?`,
                                onConfirm: async () => {
                                    const success = await runCommand(
                                        setProjectArchiveStatus({
                                            archiveStatus: !props.isArchived
                                        })
                                    );
                                    if (success) {
                                        props.onSuccess();
                                    }
                                },
                                confirmText: `${props.isArchived ? "Unarchive" : "Archive"} project`
                            });
                        }}
                    >
                        {props.isArchived ? "Unarchive" : "Archive"}
                    </Button>
                </Flex>
            </ActionBox>
        )}
    </>;
};

interface LeaveProjectProps {
    projectRole: ProjectRole;
    projectId: string;
    projectDetails: UserInProject;
    onSuccess: () => void;
}

export const LeaveProject: React.FC<LeaveProjectProps> = props => {
    const [, runCommand] = useAsyncCommand();
    return (
        <ActionBox>
            <Box flexGrow={1}>
                <Heading.h4>Leave Project</Heading.h4>
                <Text>
                    If you leave the project the following will happen:

                    <ul>
                        <li>
                            All files and compute resources owned by the project become
                            inaccessible to you
                        </li>

                        <li>
                            None of your files in the project will be deleted
                        </li>

                        <li>
                            Project administrators can recover files from your personal directory in
                            the project
                        </li>
                    </ul>
                </Text>

                {props.projectRole !== ProjectRole.PI ? null : (
                    <Text>
                        <b>You must transfer the principal investigator role to another member before
                        leaving the project!</b>
                    </Text>
                )}
            </Box>
            <Flex>
                <Button
                    color="red"
                    disabled={props.projectRole === ProjectRole.PI}
                    onClick={() => {
                        addStandardDialog({
                            title: "Are you sure?",
                            message: `Are you sure you wish to leave ${props.projectDetails.title}?`,
                            onConfirm: async () => {
                                const success = await runCommand(leaveProject({}));
                                if (success) {
                                    props.onSuccess();
                                }
                            },
                            confirmText: "Leave project",
                            addToFront: true
                        });
                    }}
                >
                    Leave
                </Button>
            </Flex>
        </ActionBox>
    );
};
