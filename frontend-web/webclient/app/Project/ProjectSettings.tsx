import * as React from "react";
import {useProjectManagementStatus} from "Project/View";
import {Box, Button, Flex, Icon, Link, Text} from "ui-components";
import {MembersBreadcrumbs} from "Project/MembersPanel";
import * as Heading from "ui-components/Heading";
import styled from "styled-components";
import {addStandardDialog} from "UtilityComponents";
import {useAsyncCommand} from "Authentication/DataHook";
import {leaveProject, ProjectRole} from "Project/index";
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
    const {projectId, group, projectRole} = useProjectManagementStatus();
    const [, runCommand] = useAsyncCommand();
    const history = useHistory();
    return (
        <>
            <Flex>
                <MembersBreadcrumbs>
                    <li>
                        <Link to={`/projects/view/${group ? encodeURIComponent(group) : "-"}`}>
                            Members of {`${projectId.slice(0, 20).trim()}${projectId.length > 20 ? "..." : ""}`}
                        </Link>
                    </li>
                    <li>Settings</li>
                </MembersBreadcrumbs>
                <Link to={`/projects/view/${group ? encodeURIComponent(group) : "-"}/settings`}>
                    <Icon
                        name={"properties"}
                        m={8}
                        hoverColor={"blue"}
                        cursor={"pointer"}
                    />
                </Link>
            </Flex>

            <ActionContainer>
                {/*
                <ActionBox>
                    <Box flexGrow={1}>
                        <Heading.h4>Project Archival</Heading.h4>
                        <Text color={"gray"}>
                            Help text goes here. Lorem ipsum dolor sit amet, consectetur adipisicing elit.
                            Accusantium fuga magni nisi omnis, perferendis sed tempore voluptas voluptatem. Eaque, ipsa,
                            ullam. Aspernatur dolores doloribus magni quos sapiente veritatis, voluptas voluptate!
                        </Text>
                    </Box>
                    <Flex>
                        <Button color={"orange"}>Archive</Button>
                    </Flex>
                </ActionBox>
                */}

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
        </>
    );
};
