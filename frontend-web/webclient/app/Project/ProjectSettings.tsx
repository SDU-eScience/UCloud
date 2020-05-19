import * as React from "react";
import {useProjectManagementStatus} from "Project/View";
import {Box, Button, Flex, Text} from "ui-components";
import * as Heading from "ui-components/Heading";
import styled from "styled-components";
import {addStandardDialog} from "UtilityComponents";
import {useAsyncCommand} from "Authentication/DataHook";
import {leaveProject, ProjectRole, setProjectArchiveStatus} from "Project/index";
import {useHistory} from "react-router";
import {fileTablePage} from "Utilities/FileUtilities";
import {Client} from "Authentication/HttpClientInstance";

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
    const [, runCommand] = useAsyncCommand();
    const history = useHistory();
    return (
        <ActionContainer>
            {projectRole === ProjectRole.USER ? null : (
                <ActionBox>
                    <Box flexGrow={1}>
                        <Heading.h4>Project Archival</Heading.h4>
                        <Text>
                            {!projectDetails.data.archived ? null : (
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
                            {projectDetails.data.archived ? null : (
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
                                        `${projectDetails.data.archived ? "unarchive" : "archive"} ${projectId}?`,
                                    onConfirm: async () => {
                                        const success =
                                            await runCommand(
                                                setProjectArchiveStatus({
                                                    archiveStatus: !projectDetails.data.archived
                                                })
                                            );
                                        if (success) {
                                            history.push("/projects");
                                        }
                                    },
                                    confirmText: `${projectDetails.data.archived ? "Unarchive" : "Archive"} project`
                                });
                            }}
                        >
                            {projectDetails.data.archived ? "Unarchive" : "Archive"}
                        </Button>
                    </Flex>
                </ActionBox>
            )}

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

                    {projectRole !== ProjectRole.PI ? null : (
                        <Text>
                            <b>You must transfer the principal investigator role to another member before
                            leaving the project!
                            </b>
                        </Text>
                    )}
                </Box>
                <Flex>
                    <Button
                        color={"red"}
                        disabled={projectRole === ProjectRole.PI}
                        onClick={() => {
                            addStandardDialog({
                                title: "Are you sure?",
                                message: `Are you sure you wish to leave ${projectId}?`,
                                onConfirm: async () => {
                                    const success = await runCommand(leaveProject({}));
                                    if (success) {
                                        history.push(fileTablePage(Client.homeFolder));
                                    }
                                },
                                confirmText: "Leave project"
                            });
                        }}
                    >
                        Leave
                    </Button>
                </Flex>
            </ActionBox>
        </ActionContainer>
    );
};
