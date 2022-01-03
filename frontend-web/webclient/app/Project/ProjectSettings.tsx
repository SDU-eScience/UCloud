import * as React from "react";
import Divider from "@/ui-components/Divider";
import {
    leaveProject,
    ProjectRole,
    renameProject,
    setProjectArchiveStatus,
    setProjectArchiveStatusBulk,
    useProjectManagementStatus,
    UserInProject
} from "@/Project/index";
import {
    Box,
    Button,
    Flex,
    Input,
    Label,
    Link,
    SelectableText,
    SelectableTextWrapper,
    Text,
    Checkbox
} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import styled from "styled-components";
import {addStandardDialog} from "@/UtilityComponents";
import {callAPIWithErrorHandler, useAsyncCommand, useCloudAPI} from "@/Authentication/DataHook";
import {useHistory, useParams} from "react-router";
import {dialogStore} from "@/Dialog/DialogStore";
import {MainContainer} from "@/MainContainer/MainContainer";
import {ProjectBreadcrumbs} from "@/Project/Breadcrumbs";
import {GrantProjectSettings, LogoAndDescriptionSettings} from "@/Project/Grant/Settings";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {SidebarPages, useSidebarPage} from "@/ui-components/Sidebar";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {useEffect} from "react";
import {
    AllowSubProjectsRenamingRequest,
    AllowSubProjectsRenamingResponse,
    externalApplicationsEnabled,
    ExternalApplicationsEnabledResponse,
    ToggleSubProjectsRenamingRequest
} from "@/Project/Grant";
import {buildQueryString} from "@/Utilities/URIUtilities";

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

enum SettingsPage {
    AVAILABILITY = "availability",
    INFO = "info",
    GRANT_SETTINGS = "grant",
    SUBPROJECTS = "subprojects"
}

const PageTab: React.FunctionComponent<{
    page: SettingsPage,
    title: string,
    activePage: SettingsPage
}> = ({page, title, activePage}) => {
    return <SelectableText mr={"1em"} fontSize={3} selected={activePage === page}>
        <Link to={`/project/settings/${page}`}>
            {title}
        </Link>
    </SelectableText>;
};

export const ProjectSettings: React.FunctionComponent = () => {
    const {projectId, projectRole, projectDetails, projectDetailsParams, fetchProjectDetails, reloadProjectStatus} =
        useProjectManagementStatus({isRootComponent: true});
    const params = useParams<{ page?: SettingsPage }>();
    const page = params.page ?? SettingsPage.AVAILABILITY;

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
            header={<ProjectBreadcrumbs crumbs={[{title: "Settings"}]}/>}
            main={
                <ActionContainer>
                    <SelectableTextWrapper>
                        <PageTab activePage={page} page={SettingsPage.AVAILABILITY} title={"Project Availability"}/>
                        <PageTab activePage={page} page={SettingsPage.INFO} title={"Project Information"}/>
                        <PageTab activePage={page} page={SettingsPage.SUBPROJECTS} title={"Subprojects"}/>
                        {!enabled.data.enabled ? null :
                            <PageTab activePage={page} page={SettingsPage.GRANT_SETTINGS} title={"Grant Settings"}/>
                        }
                    </SelectableTextWrapper>

                    {page !== SettingsPage.AVAILABILITY ? null : (
                        <>
                            <ArchiveSingleProject
                                isArchived={projectDetails.data.archived}
                                projectId={projectId}
                                projectRole={projectRole}
                                title={projectDetails.data.title}
                                onSuccess={() => history.push("/projects")}
                            />
                            <Divider/>
                            <LeaveProject
                                onSuccess={() => history.push("/")}
                                projectDetails={projectDetails.data}
                                projectId={projectId}
                                projectRole={projectRole}
                            />
                        </>
                    )}
                    {page !== SettingsPage.INFO ? null : (
                        <>
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
                        </>
                    )}
                    {page !== SettingsPage.GRANT_SETTINGS ? null : (
                        <GrantProjectSettings/>
                    )}
                    {page !== SettingsPage.SUBPROJECTS ? null : (
                        <>
                            <SubprojectSettings
                                projectId={projectId}
                                projectRole={projectRole}
                                setLoading={() => false}
                            />
                        </>
                    )}
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

    const [allowRenaming, setAllowRenaming] = useCloudAPI<AllowSubProjectsRenamingResponse, AllowSubProjectsRenamingRequest>(
        {noop: true},
        {allowed: false}
    );

    useEffect(() => {
        setAllowRenaming(getRenamingStatus({projectId: props.projectId}))
    }, [props.projectId]);
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
                                disabled={!allowRenaming.data.allowed}
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

export function getRenamingStatusForSubProject(
    parameters: AllowSubProjectsRenamingRequest
): APICallParameters<AllowSubProjectsRenamingRequest> {
    return {
        method: "GET",
        path: buildQueryString(
            "/projects/renameable-sub",
            parameters
        ),
        parameters,
        reloadId: Math.random()
    };
}

export function getRenamingStatus(
    parameters: AllowSubProjectsRenamingRequest
): APICallParameters<AllowSubProjectsRenamingRequest> {
    return {
        method: "GET",
        path: buildQueryString(
            "/projects/renameable",
            parameters
        ),
        parameters,
        reloadId: Math.random()
    };
}

const SubprojectSettings: React.FC<AllowRenamingProps> = props => {
    const [allowRenaming, setAllowRenaming] = useCloudAPI<AllowSubProjectsRenamingResponse, AllowSubProjectsRenamingRequest>(
        {noop: true},
        {allowed: false}
    );

    useEffect(() => {
        props.setLoading(allowRenaming.loading);
        setAllowRenaming(getRenamingStatusForSubProject({projectId: props.projectId}));
    }, []);

    const toggleAndSet = async () => {
        await callAPIWithErrorHandler(toggleRenaming({projectId: props.projectId}));
        setAllowRenaming(getRenamingStatusForSubProject({projectId: props.projectId}));
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
};

interface ArchiveProjectProps {
    projects: UserInProject[]
    onSuccess: () => void;
}

export const ArchiveProject: React.FC<ArchiveProjectProps> = props => {
    const multipleProjects = props.projects.length > 1;
    const archived = props.projects.every(it => it.archived);
    let projectTitles = "";
    props.projects.forEach(project =>
        projectTitles += project.title + ","
    );
    const anyUserRoles = props.projects.some(it => it.whoami.role === ProjectRole.USER);
    projectTitles = projectTitles.substr(0, projectTitles.length - 1);
    return <>
        {anyUserRoles ? null : (
            <ActionBox>
                <Box flexGrow={1}>
                    <Heading.h4>Project Archival</Heading.h4>
                    <Text>
                        {!archived ? null : (
                            <>
                                Unarchiving {multipleProjects ? "projects" : "a project"} will reverse the effects of
                                archival.
                                <ul>
                                    <li>
                                        Your project{multipleProjects ? "s" : ""} will, once again, be visible to you
                                        and project
                                        collaborators
                                    </li>
                                    <li>This action <i>is</i> reversible</li>
                                </ul>
                            </>
                        )}
                        {archived ? null : (
                            <>
                                You can archive {multipleProjects ? "projects" : "a project"} if it is no longer
                                relevant for your day-to-day work.

                                <ul>
                                    <li>
                                        The project{multipleProjects ? "s" : ""} will, by default, be hidden for you and
                                        project
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

export default ProjectSettings;
