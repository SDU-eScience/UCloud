import * as React from "react";
import Divider from "@/ui-components/Divider";
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
import {callAPIWithErrorHandler, useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {useNavigate, useParams} from "react-router";
import {dialogStore} from "@/Dialog/DialogStore";
import {MainContainer} from "@/MainContainer/MainContainer";
import {ProjectBreadcrumbs} from "@/Project/Breadcrumbs";
import {GrantProjectSettings, LogoAndDescriptionSettings} from "@/Project/Grant/Settings";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {SidebarPages, useSidebarPage} from "@/ui-components/SidebarPagesEnum";
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
import ProjectAPI, {OldProjectRole, Project, ProjectSpecification, useProjectFromParams} from "@/Project/Api";
import {bulkRequestOf} from "@/DefaultObjects";
import {Client} from "@/Authentication/HttpClientInstance";
import {useProject} from "./cache";
import {ButtonClass} from "@/ui-components/Button";
import {BoxClass} from "@/ui-components/Box";
import {FlexClass} from "@/ui-components/Flex";

const ActionContainer = styled.div`
    & > * {
        margin-bottom: 16px;
    }
`;

const ActionBox = styled.div`
    display: flex;
    margin-bottom: 16px;
    
    & > .${BoxClass} {
        flex-grow: 1;
    }
    
    & > .${FlexClass} {
        margin-left: 8px;
        flex-direction: column;
        justify-content: center;
    }
    
    & > .${FlexClass} > .${ButtonClass} {
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
    activePage: SettingsPage;
    projectId: string;
}> = ({page, title, activePage, projectId}) => {
    return <SelectableText mr={"1em"} fontSize={3} selected={activePage === page}>
        <Link to={`/project/settings/${projectId}/${page}?`}>
            {title}
        </Link>
    </SelectableText>;
};

export const ProjectSettings: React.FunctionComponent = () => {
    const {project, projectId, reload, breadcrumbs} = useProjectFromParams("Settings");

    const params = useParams<{page?: SettingsPage;}>();
    const page = params.page ?? SettingsPage.AVAILABILITY;

    useTitle("Project Settings");
    useSidebarPage(SidebarPages.Projects);
    const [enabled, fetchEnabled] = useCloudAPI<ExternalApplicationsEnabledResponse>(
        {noop: true},
        {enabled: false}
    );

    useEffect(() => {
        if (!projectId) return;
        fetchEnabled((externalApplicationsEnabled({projectId})));
    }, [projectId]);

    const navigate = useNavigate();

    if (!projectId || !project) return null;

    const {status} = project;

    return (
        <MainContainer
            header={<ProjectBreadcrumbs omitActiveProject crumbs={breadcrumbs} />}
            main={
                <ActionContainer>
                    <SelectableTextWrapper>
                        <PageTab activePage={page} projectId={projectId} page={SettingsPage.AVAILABILITY} title={"Project Availability"} />
                        <PageTab activePage={page} projectId={projectId} page={SettingsPage.INFO} title={"Project Information"} />
                        <PageTab activePage={page} projectId={projectId} page={SettingsPage.SUBPROJECTS} title={"Subprojects"} />
                        {!enabled.data.enabled ? null :
                            <PageTab activePage={page} projectId={projectId} page={SettingsPage.GRANT_SETTINGS} title={"Grant Settings"} />
                        }
                    </SelectableTextWrapper>

                    {page !== SettingsPage.AVAILABILITY ? null : (
                        <>
                            <ArchiveSingleProject
                                isArchived={status.archived}
                                projectId={projectId}
                                projectRole={status.myRole!}
                                title={project.specification.title}
                                onSuccess={() => reload()}
                            />
                            <Divider />
                            <LeaveProject
                                onSuccess={() => navigate("/")}
                                projectTitle={project.specification.title}
                                projectId={projectId}
                                projectRole={status.myRole!}
                            />
                        </>
                    )}
                    {page !== SettingsPage.INFO ? null : (
                        <>
                            <ChangeProjectTitle
                                projectId={projectId}
                                projectSpecification={project.specification}
                                onSuccess={() => reload()}
                            />
                            {enabled.data.enabled ? <Divider /> : null}
                            <LogoAndDescriptionSettings />
                        </>
                    )}
                    {page !== SettingsPage.GRANT_SETTINGS ? null : (
                        <GrantProjectSettings />
                    )}
                    {page !== SettingsPage.SUBPROJECTS ? null : (
                        <>
                            <SubprojectSettings
                                projectId={projectId}
                                projectRole={status.myRole!}
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
    projectSpecification: ProjectSpecification;
    onSuccess: () => void;
}

export const ChangeProjectTitle: React.FC<ChangeProjectTitleProps> = props => {
    const newProjectTitle = React.useRef<HTMLInputElement>(null);
    const [, invokeCommand] = useCloudCommand();
    const [saveDisabled, setSaveDisabled] = React.useState<boolean>(true);

    const [allowRenaming, setAllowRenaming] = useCloudAPI<AllowSubProjectsRenamingResponse, AllowSubProjectsRenamingRequest>(
        {noop: true},
        {allowed: false}
    );

    const project = useProject();

    useEffect(() => {
        setAllowRenaming(getRenamingStatusForSubProject({projectId: props.projectId}));
        if (newProjectTitle.current) newProjectTitle.current.value = props.projectSpecification.title;
        if (props.projectId === project.fetch().id) project.reload();
    }, [props.projectId, props.projectSpecification]);

    return (
        <Box flexGrow={1}>
            <form onSubmit={async e => {
                e.preventDefault();

                const titleField = newProjectTitle.current;
                if (titleField === null) return;

                const titleValue = titleField.value;

                if (titleValue === "") {
                    snackbarStore.addFailure("Project name cannot be empty", false);
                    return;
                }
                if (titleValue.trim().length != titleValue.length) {
                    snackbarStore.addFailure("Project name cannot end or start with whitespace.", false);
                    return;
                }

                const success = await invokeCommand(ProjectAPI.renameProject(bulkRequestOf({
                    id: props.projectId,
                    newTitle: titleValue
                }))) !== null;

                if (success) {
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
                            inputRef={newProjectTitle}
                            placeholder="New project title"
                            autoComplete="off"
                            onChange={() => {
                                if (newProjectTitle.current?.value !== props.projectSpecification.title) {
                                    setSaveDisabled(false);
                                } else {
                                    setSaveDisabled(true);
                                }
                            }}
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
    projectRole: OldProjectRole;
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
        {props.projectRole === OldProjectRole.USER ? null : (
            <ActionBox>
                <Box flexGrow={1}>
                    <Label>
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
    </>;
};


interface ArchiveSingleProjectProps {
    isArchived: boolean;
    projectRole: OldProjectRole;
    projectId: string;
    title: string;
    onSuccess: () => void;
}

export const ArchiveSingleProject: React.FC<ArchiveSingleProjectProps> = props => {
    return <>
        {props.projectRole === OldProjectRole.USER ? null : (
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
                                        props.isArchived ?
                                            ProjectAPI.unarchive(bulkRequestOf({id: props.projectId})) :
                                            ProjectAPI.archive(bulkRequestOf({id: props.projectId})));
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
    projects: Project[];
    onSuccess: () => void;
}

export const ArchiveProject: React.FC<ArchiveProjectProps> = props => {
    const multipleProjects = props.projects.length > 1;
    const archived = props.projects.every(it => it.status.archived);
    let projectTitles = "";
    props.projects.forEach(project =>
        projectTitles += project.specification.title + ","
    );
    const anyUserRoles = props.projects.some(it => it.status.myRole === OldProjectRole.USER);
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
                            const operation = archived ? ProjectAPI.unarchive : ProjectAPI.archive;

                            addStandardDialog({
                                title: "Are you sure?",
                                message: `Are you sure you wish to ` +
                                    `${archived ? "unarchive" : "archive"} ${projectTitles}?`,
                                onConfirm: async () => {
                                    const success = await callAPIWithErrorHandler(
                                        operation(bulkRequestOf(...props.projects.map(it => it)))
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
    projectRole: OldProjectRole;
    projectId: string;
    projectTitle: string;
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

                {props.projectRole !== OldProjectRole.PI ? null : (
                    <Text>
                        <b>You must transfer the principal investigator role to another member before
                            leaving the project!</b>
                    </Text>
                )}
            </Box>
            <Flex>
                <Button
                    color="red"
                    disabled={props.projectRole === OldProjectRole.PI}
                    onClick={() => {
                        addStandardDialog({
                            title: "Are you sure?",
                            message: `Are you sure you wish to leave ${props.projectTitle}?`,
                            onConfirm: async () => {
                                const success = await callAPIWithErrorHandler({
                                    ...ProjectAPI.deleteMember(bulkRequestOf({username: Client.username!})),
                                    projectOverride: props.projectId
                                });
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
