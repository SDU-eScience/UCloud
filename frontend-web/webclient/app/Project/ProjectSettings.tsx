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
import ProjectAPI, {OldProjectRole, Project, isAdminOrPI, useProjectId} from "@/Project/Api";
import {bulkRequestOf} from "@/DefaultObjects";
import {Client} from "@/Authentication/HttpClientInstance";
import {useProject} from "./cache";
import {ButtonClass} from "@/ui-components/Button";
import {BoxClass} from "@/ui-components/Box";
import {FlexClass} from "@/ui-components/Flex";
import {UtilityBar} from "@/Playground/Playground";
import {injectStyle} from "@/Unstyled";

const ActionContainer = injectStyle("action-container", k => `
    ${k} > * {
        margin-bottom: 16px;
    }
`);

function ActionBox({children}: React.PropsWithChildren): JSX.Element {
    return <div className={ActionBoxClass}>
        {children}
    </div>
}

const ActionBoxClass = injectStyle("action-box", k => `
    ${k} {
        display: flex;
        margin-bottom: 16px;
    }
    
    ${k} > .${BoxClass} {
        flex-grow: 1;
    }
    
    ${k} > .${FlexClass} {
        margin-left: 8px;
        flex-direction: column;
        justify-content: center;
    }
    
    ${k} > .${FlexClass} > .${ButtonClass} {
        min-width: 100px;
    }
`);

export const ProjectSettings: React.FunctionComponent = () => {
    const projectId = useProjectId();
    const projectOps = useProject();
    const project = projectOps.fetch();

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

    console.log(projectId, project);

    const {status} = project;

    return (
        <MainContainer
            key={project.id}
            header={<Flex>
                <ProjectBreadcrumbs allowPersonalProject crumbs={[{title: "Settings"}]} />
                <UtilityBar searchEnabled={false} callbacks={{}} operations={[]} />
            </Flex>}
            headerSize={64}
            main={!isAdminOrPI(status.myRole) ? (
                <Heading.h1>Only project or admin and PIs can view settings.</Heading.h1>
            ) : <div className={ActionContainer}>
                <ArchiveSingleProject
                    isArchived={status.archived}
                    projectId={projectId}
                    projectRole={status.myRole!}
                    title={project.specification.title}
                    onSuccess={() => projectOps.reload()}
                />
                <Divider />
                <LeaveProject
                    onSuccess={() => navigate("/")}
                    projectTitle={project.specification.title}
                    projectId={projectId}
                    projectRole={status.myRole!}
                />
                <ChangeProjectTitle
                    projectId={projectId}
                    projectTitle={project.specification.title}
                    onSuccess={() => projectOps.reload()}
                />
                {enabled.data.enabled ? <>
                    <Divider />
                    <LogoAndDescriptionSettings />
                    <GrantProjectSettings />
                </> : null}
                <SubprojectSettings
                    projectId={projectId}
                    projectRole={status.myRole!}
                    setLoading={() => false}
                />
            </div>}
        />
    );
};

interface ChangeProjectTitleProps {
    projectId: string;
    projectTitle: string;
    onSuccess: () => void;
}

export function ChangeProjectTitle(props: ChangeProjectTitleProps): JSX.Element {
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
        if (newProjectTitle.current) newProjectTitle.current.value = props.projectTitle;
        if (props.projectId === project.fetch().id) project.reload();
    }, [props.projectId, props.projectTitle]);

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
                <Heading.h3>Project Title</Heading.h3>
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
                                if (newProjectTitle.current?.value !== props.projectTitle) {
                                    setSaveDisabled(false);
                                } else {
                                    setSaveDisabled(true);
                                }
                            }}
                            disabled={!allowRenaming.data.allowed}
                        />
                    </Box>
                    <Button
                        height="42px"
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

function SubprojectSettings(props: AllowRenamingProps): JSX.Element {
    const [allowRenaming, setAllowRenaming] = useCloudAPI<AllowSubProjectsRenamingResponse, AllowSubProjectsRenamingRequest>(
        {noop: true},
        {allowed: false}
    );

    useEffect(() => {
        props.setLoading(allowRenaming.loading);
        setAllowRenaming(getRenamingStatusForSubProject({projectId: props.projectId}));
    }, []);

    const toggleAndSet = React.useCallback(async () => {
        await callAPIWithErrorHandler(toggleRenaming({projectId: props.projectId}));
        setAllowRenaming(getRenamingStatusForSubProject({projectId: props.projectId}));
    }, [props.projectId]);

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

export function ArchiveSingleProject(props: ArchiveSingleProjectProps): JSX.Element {
    return <>
        {props.projectRole === OldProjectRole.USER ? null : (
            <ActionBox>
                <Box flexGrow={1}>
                    <Heading.h3>Project Archival</Heading.h3>
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
}

interface ArchiveProjectProps {
    projects: Project[];
    onSuccess: () => void;
}

export function ArchiveProject(props: ArchiveProjectProps): JSX.Element {
    const multipleProjects = props.projects.length > 1;
    const archived = props.projects.every(it => it.status.archived);
    let projectTitles = "";
    props.projects.forEach(project =>
        projectTitles += project.specification.title + ","
    );
    const anyUserRoles = props.projects.some(it => it.status.myRole === OldProjectRole.USER);
    projectTitles = projectTitles.substring(0, projectTitles.length - 1);
    return <>
        {anyUserRoles ? null : (
            <ActionBox>
                <Box flexGrow={1}>
                    <Heading.h3>Project Archival</Heading.h3>
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

export function LeaveProject(props: LeaveProjectProps): JSX.Element {
    return (
        <ActionBox>
            <Box flexGrow={1}>
                <Heading.h3>Leave Project</Heading.h3>
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
