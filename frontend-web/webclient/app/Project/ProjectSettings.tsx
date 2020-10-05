import * as React from "react";
import Divider from "ui-components/Divider";
import {
    ArchiveProjectRequestBulk,
    fetchDataManagementPlan,
    FetchDataManagementPlanResponse,
    leaveProject,
    ProjectRole,
    renameProject,
    setProjectArchiveStatus,
    setProjectArchiveStatusBulk, updateDataManagementPlan,
    useProjectManagementStatus,
    UserInProject
} from "Project/index";
import {Box, Button, ButtonGroup, Checkbox, Flex, Input, Label, Text, TextArea} from "ui-components";
import * as Heading from "ui-components/Heading";
import styled from "styled-components";
import {addStandardDialog} from "UtilityComponents";
import {callAPIWithErrorHandler, useAsyncCommand, useCloudAPI} from "Authentication/DataHook";
import {useHistory} from "react-router";
import {fileTablePage} from "Utilities/FileUtilities";
import {Client} from "Authentication/HttpClientInstance";
import {dialogStore} from "Dialog/DialogStore";
import {MainContainer} from "MainContainer/MainContainer";
import {ProjectBreadcrumbs} from "Project/Breadcrumbs";
import {GrantProjectSettings, LogoAndDescriptionSettings} from "Project/Grant/Settings";
import {useTitle} from "Navigation/Redux/StatusActions";
import {SidebarPages, useSidebarPage} from "ui-components/Sidebar";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {Toggle} from "ui-components/Toggle";
import {useCallback, useEffect, useRef, useState} from "react";
import {TextSpan} from "ui-components/Text";
import {doNothing} from "UtilityFunctions";
import {
    AllowSubProjectsRenamingRequest,
    AllowSubProjectsRenamingResponse,
    externalApplicationsEnabled,
    ExternalApplicationsEnabledResponse,
    ProjectGrantSettings, readGrantRequestSettings, readTemplates,
    ReadTemplatesResponse, ToggleSubProjectsRenamingRequest
} from "Project/Grant";
import {ProductArea, retrieveFromProvider, RetrieveFromProviderResponse, UCLOUD_PROVIDER} from "Accounting";

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

const Smooth = styled.div`
    & > ul > li {
        display:inline;   
        margin:0 20px; 
    }
    
    a {
        color:inherit;
        text-decoration: none;
    }
    
    h3:target:before {
        content: "";
        display: block;
        height: 150px;
        margin: -150px 0 0;
    }
`;

export const ProjectSettings: React.FunctionComponent = () => {
    const {projectId, projectRole, projectDetails, projectDetailsParams, fetchProjectDetails, reloadProjectStatus} =
        useProjectManagementStatus({isRootComponent: true});

    useTitle("Project Settings");
    useSidebarPage(SidebarPages.Projects);
    const [enabled, fetchEnabled] = useCloudAPI<ExternalApplicationsEnabledResponse>(
        {noop: true},
        {enabled: false}
    );
    useEffect(() => {
        fetchEnabled((externalApplicationsEnabled({projectId})));
    }, [projectId]);

    const history = useHistory();
    return (
        <MainContainer
            header={<ProjectBreadcrumbs crumbs={[{title: "Settings"}]} />}
            main={
                <ActionContainer>
                    <Smooth>
                        <Divider/>
                        <ul>
                            <li><a href={"#Availability"}>Project Availability</a></li>
                            <li><a href={"#Project Information"}>Project Information</a></li>
                            <li><a href={"#DMP"}>Data Management Plan</a></li>
                            {
                                enabled.data.enabled ? <li><a href={"#AppGrantSettings"}>Grant Settings</a></li> :null
                            }
                        </ul>
                        <Divider/>
                        <Heading.h3 id={"Availability"}>Project Availability</Heading.h3>
                        <Divider/>
                        <ArchiveSingleProject
                            isArchived={projectDetails.data.archived}
                            projectId={projectId}
                            projectRole={projectRole}
                            title={projectDetails.data.title}
                            onSuccess={() => history.push("/projects")}
                        />
                        <Divider/>
                        <LeaveProject
                            onSuccess={() => history.push(fileTablePage(Client.homeFolder))}
                            projectDetails={projectDetails.data}
                            projectId={projectId}
                            projectRole={projectRole}
                        />
                        <Divider/>
                        <Heading.h3 id={"Subproject Settings"}>Subproject Settings</Heading.h3>
                        <SubprojectSettings
                            projectId={projectId}
                            projectRole={projectRole}
                            setLoading={() => false}
                        />
                        <Divider/>
                        <Heading.h3 id={"Project Information"}>Project Information</Heading.h3>
                        <Divider/>
                        <ChangeProjectTitle
                            projectId={projectId}
                            projectDetails={projectDetails.data}
                            onSuccess={() => {
                                fetchProjectDetails(projectDetailsParams);
                                reloadProjectStatus();
                            }}
                        />
                        {enabled.data.enabled ? <Divider/> : null}
                        <LogoAndDescriptionSettings/>
                        <DataManagementPlan />
                        <Divider/>
                        <GrantProjectSettings/>
                    </Smooth>
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

const DataManagementPlan: React.FunctionComponent = props => {
    const [dmpResponse, fetchDmp] = useCloudAPI<FetchDataManagementPlanResponse>({noop: true}, {});
    const [, runWork] = useAsyncCommand();
    const projectManagement = useProjectManagementStatus({isRootComponent: false});
    const [hasDmp, setHasDmp] = useState<boolean>(false);
    const dmpRef = useRef<HTMLTextAreaElement>(null);

    const reload = () => {
        if (projectManagement.allowManagement && Client.hasActiveProject) {
            fetchDmp(fetchDataManagementPlan({}));
        }
    };

    useEffect(() => {
        reload();
    }, [projectManagement.projectId, projectManagement.allowManagement]);

    useEffect(() => {
        if (dmpResponse.data.dmp) {
            setHasDmp(true);
            if (dmpRef.current) {
                dmpRef.current.value = dmpResponse.data.dmp;
            }
        } else {
            setHasDmp(false);
            if (dmpRef.current) {
                dmpRef.current.value = "";
            }
        }
    }, [dmpResponse.data.dmp]);

    const updateDmp = useCallback(async () => {
        const res = await runWork(updateDataManagementPlan({
            id: projectManagement.projectId,
            dmp: dmpRef.current!.value
        }));
        if (res) {
            snackbarStore.addSuccess("Your data management plan has been updated", false);
        }
        reload();
    }, [projectManagement.projectId, runWork, dmpRef.current]);

    const deleteDmp = useCallback(async () => {
        addStandardDialog({
            title: "Confirm deleting data management plan",
            message: "",
            confirmText: "Delete",
            onCancel: doNothing,
            onConfirm: async () => {
                const res = await runWork(updateDataManagementPlan({ id: projectManagement.projectId }));
                if (res) {
                    snackbarStore.addSuccess("Your data management plan has been updated", false);
                }
                reload();
            }
        });
    }, [projectManagement.projectId, runWork, dmpRef.current]);

    if (!Client.hasActiveProject || !projectManagement.allowManagement) return null;

    return <Box>
        <Divider/>
        <Heading.h3 id={"DMP"}>Data Management Plan</Heading.h3>
        <Divider/>
        If you have a data management plan then you can attach it to the project here.
        <TextSpan bold>
            You still need to follow your organization&apos;s policies regarding data management plans.
        </TextSpan>
        <br />

        <Label>
            Store a copy of this project&apos;s data management plan in UCloud?{" "}
            <Toggle onChange={() => { console.log("...", hasDmp); setHasDmp(!hasDmp); }} checked={hasDmp} scale={1.5} />
        </Label>

        {!hasDmp ? null : (
            <Box>
                <TextArea
                    placeholder={"Data management plan."}
                    rows={5}
                    width={"100%"}
                    ref={dmpRef}
                />
                <ButtonGroup mt={8}>
                    <Button type={"button"} onClick={updateDmp}>Save Data Management Plan</Button>
                    <Button type={"button"} onClick={deleteDmp} color={"red"}>Delete Data Management Plan</Button>
                </ButtonGroup>
            </Box>
        )}
    </Box>;
};

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

interface AllowRenamingProps {
    projectId: string;
    projectRole: ProjectRole
    setLoading: (loading: boolean) => void;
}

export function toggleRenaming(
    request: ToggleSubProjectsRenamingRequest
): APICallParameters<ToggleSubProjectsRenamingRequest> {
    return {
        method: "POST",
        path: "/projects/toggleRenaming",
        payload: request,
        reloadId: Math.random(),
    };
}

export function getRenamingStatus(
    request: AllowSubProjectsRenamingRequest
): APICallParameters<AllowSubProjectsRenamingRequest> {
    return {
        method: "GET",
        path: "/projects/renameable",
        parameters: request,
        reloadId: Math.random(),
    };
}

const SubprojectSettings: React.FC<AllowRenamingProps> = props => {
    const [allowRenaming, setAllowRenaming] = useCloudAPI<AllowSubProjectsRenamingResponse, AllowSubProjectsRenamingRequest>(
        {noop: true},
        {allowed: false}
    );

    useEffect(() => {
        props.setLoading(allowRenaming.loading);
        setAllowRenaming(getRenamingStatus({projectId: props.projectId}));
    }, []);

    const toggleAndSet = async () => {
        await callAPIWithErrorHandler(toggleRenaming({projectId: props.projectId}));
        setAllowRenaming(getRenamingStatus({projectId: props.projectId}));
        console.log("MOJN")
    };

    return <>
        {props.projectRole === ProjectRole.USER ? null : (
            <ActionBox>
                <Box flexGrow={1}>
                    <Label
                        fontWeight={"normal"}
                        fontSize={"2"}
                    >
                        <Checkbox
                            size={24}
                            checked={allowRenaming.data.allowed}
                            onClick={() => toggleAndSet()}
                            onChange={() => undefined}
                        />
                        Allow subprojects to rename
                    </Label>
                </Box>
            </ActionBox>
        )}
        </>
}


interface ArchiveSingleProjectProps {
    isArchived: boolean;
    projectRole: ProjectRole;
    projectId: string;
    title: string;
    onSuccess: () => void;
}

export const ArchiveSingleProject: React.FC<ArchiveSingleProjectProps> = props => {
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
}

interface ArchiveProjectProps {
    projects: UserInProject[]
    onSuccess: () => void;
}

export const ArchiveProject: React.FC<ArchiveProjectProps> = props => {
    const multipleProjects = props.projects.length > 1;
    const archived = props.projects.every( it => it.archived);
    let projectTitles = "";
    props.projects.forEach( project =>
        projectTitles += project.title + ","
    );
    const anyUserRoles = props.projects.some(it => it.whoami.role === ProjectRole.USER);
    projectTitles = projectTitles.substr(0, projectTitles.length-1);
    return <>
        {anyUserRoles ? null : (
            <ActionBox>
                <Box flexGrow={1}>
                    <Heading.h4>Project Archival</Heading.h4>
                    <Text>
                        {!archived ? null : (
                            <>
                                Unarchiving {multipleProjects ? "projects" : "a project"} will reverse the effects of archival.
                                <ul>
                                    <li>
                                        Your project{multipleProjects ? "s" : ""} will, once again, be visible to you and project
                                        collaborators
                                    </li>
                                    <li>This action <i>is</i> reversible</li>
                                </ul>
                            </>
                        )}
                        {archived ? null : (
                            <>
                                You can archive {multipleProjects ? "projects" : "a project"} if it is no longer relevant for your day-to-day work.

                                <ul>
                                    <li>
                                        The project{multipleProjects ? "s" : ""} will, by default, be hidden for you and project
                                        collaborators
                                    </li>
                                    <li>No data will be deleted from the project{multipleProjects ? "s" : ""}</li>
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
                                    `${archived ? "unarchive" : "archive"} ${projectTitles}?`,
                                onConfirm: async () => {
                                    const success = await callAPIWithErrorHandler(
                                        setProjectArchiveStatusBulk({
                                            projects: props.projects,
                                        })
                                    );
                                    if (success) {
                                        props.onSuccess();
                                        dialogStore.success();
                                    }
                                },
                                addToFront: true,
                                confirmText: `${archived ? "Unarchive" : "Archive"} project${multipleProjects ? "s" : ""}`
                            });
                        }}
                    >
                        {archived ? "Unarchive" : "Archive"}
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
