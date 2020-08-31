import * as React from "react";
import {
    leaveProject,
    ProjectRole,
    setProjectArchiveStatus,
    useProjectManagementStatus,
    UserInProject,
    renameProject
} from "Project/index";
import {Box, Button, Flex, Text, Input} from "ui-components";
import * as Heading from "ui-components/Heading";
import styled from "styled-components";
import {addStandardDialog} from "UtilityComponents";
import {callAPIWithErrorHandler, useCloudAPI, useAsyncCommand} from "Authentication/DataHook";
import {useHistory} from "react-router";
import {fileTablePage} from "Utilities/FileUtilities";
import {Client} from "Authentication/HttpClientInstance";
import {dialogStore} from "Dialog/DialogStore";
import {MainContainer} from "MainContainer/MainContainer";
import {ProjectBreadcrumbs} from "Project/Breadcrumbs";
import {GrantProjectSettings} from "Project/Grant/Settings";
import {useTitle} from "Navigation/Redux/StatusActions";
import {useSidebarPage, SidebarPages} from "ui-components/Sidebar";
import { snackbarStore } from "Snackbar/SnackbarStore";

const ActionContainer = styled.div`
    & > * {
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
    const {projectId, projectRole, projectDetails, projectDetailsParams, fetchProjectDetails} =
        useProjectManagementStatus({isRootComponent: true});

    useTitle("Project Settings");
    useSidebarPage(SidebarPages.Projects);

    const history = useHistory();
    return (
        <MainContainer
            header={<ProjectBreadcrumbs crumbs={[{title: "Settings"}]} />}
            main={
                <ActionContainer>
                    <ChangeProjectTitle
                        projectId={projectId}
                        projectDetails={projectDetails.data}
                        onSuccess={() => fetchProjectDetails(projectDetailsParams)}
                    />
                    <ArchiveProject
                        isArchived={projectDetails.data.archived}
                        projectId={projectId}
                        projectRole={projectRole}
                        title={projectDetails.data.title}
                        onSuccess={() => history.push("/projects")}
                    />
                    <LeaveProject
                        onSuccess={() => history.push(fileTablePage(Client.homeFolder))}
                        projectDetails={projectDetails.data}
                        projectId={projectId}
                        projectRole={projectRole}
                    />
                    <GrantProjectSettings />
                </ActionContainer>
            }
            sidebar={null}
        />
    );
};

interface ChangeProjectTitleProps {
    projectId: string;
    projectDetails: UserInProject;
    onSuccess: () => void;
}

export const ChangeProjectTitle: React.FC<ChangeProjectTitleProps> = props => {
    const newProjectTitle = React.useRef<HTMLInputElement>(null);
    const [, invokeCommand] = useAsyncCommand();
    const [saveDisabled, setSaveDisabled] = React.useState<boolean>(true);
    return (
            <Box flexGrow={1}>
                <form onSubmit={async e => {
                    e.preventDefault();

                    const titleField = newProjectTitle.current;
                    if (titleField === null) return;

                    const titleValue = titleField.value;

                    if (titleValue === "") return;

                    const success = await invokeCommand(renameProject(
                        {
                            id: props.projectId,
                            newTitle: titleValue
                        }
                    )) !== null;

                    if(success) {
                        props.onSuccess();
                        snackbarStore.addSuccess("Project renamed successfully", true);
                    } else {
                        snackbarStore.addFailure("Renaming of project failed", true);
                    }
                }}>
                    <Heading.h4>Project Title</Heading.h4>
                    <Flex flexGrow={1}>
                        <Box minWidth={500}>
                            <Input
                                rightLabel
                                required
                                type="text"
                                ref={newProjectTitle}
                                placeholder="New project title"
                                autoComplete="off"
                                onChange={() => {
                                    if(newProjectTitle.current?.value !== props.projectDetails.title) {
                                        setSaveDisabled(false);
                                    } else {
                                        setSaveDisabled(true);
                                    }
                                }}
                                defaultValue={props.projectDetails.title}
                            />
                        </Box>
                        <Button
                            attached
                            disabled={saveDisabled}
                        >
                            Save
                        </Button>
                    </Flex>
                </form>
            </Box>
    );
};


interface ArchiveProjectProps {
    isArchived: boolean;
    projectRole: ProjectRole;
    projectId: string;
    title: string;
    onSuccess: () => void;
}

export const ArchiveProject: React.FC<ArchiveProjectProps> = props => {
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
                                    `${props.isArchived ? "unarchive" : "archive"} ${props.title}?`,
                                onConfirm: async () => {
                                    const success = await callAPIWithErrorHandler(
                                        setProjectArchiveStatus({
                                            archiveStatus: !props.isArchived,
                                        }, props.projectId)
                                    );
                                    if (success) {
                                        props.onSuccess();
                                        dialogStore.success();
                                    }
                                },
                                addToFront: true,
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
                                const success = await callAPIWithErrorHandler(leaveProject({}, props.projectId));
                                if (success) {
                                    props.onSuccess();
                                    dialogStore.success();
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
