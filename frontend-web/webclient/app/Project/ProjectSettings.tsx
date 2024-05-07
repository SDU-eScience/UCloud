import * as React from "react";
import {
    Box,
    Button,
    Flex,
    Input,
    Label,
    Text,
    Checkbox,
    TextArea,
    DataList,
    Icon,
    Card
} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {addStandardDialog, ConfirmCancelButtons} from "@/UtilityComponents";
import {callAPIWithErrorHandler, useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {useNavigate} from "react-router";
import {dialogStore} from "@/Dialog/DialogStore";
import {MainContainer} from "@/ui-components/MainContainer";
import {usePage} from "@/Navigation/Redux";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {useCallback, useEffect, useRef, useState} from "react";
import {buildQueryString} from "@/Utilities/URIUtilities";
import ProjectAPI, {useProjectId} from "@/Project/Api";
import {bulkRequestOf} from "@/UtilityFunctions";
import {Client} from "@/Authentication/HttpClientInstance";
import {useProject} from "./cache";
import {injectStyle} from "@/Unstyled";
import {Spacer} from "@/ui-components/Spacer";
import * as Grants from "@/Grants";
import {ProjectLogo} from "@/Grants/ProjectLogo";
import {HiddenInputField} from "@/ui-components/Input";
import {inSuccessRange} from "@/UtilityFunctions";
import Table, {TableCell, TableHeaderCell, TableRow} from "@/ui-components/Table";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {useDidUnmount} from "@/Utilities/ReactUtilities";
import {projectTitleFromCache} from "./ContextSwitcher";
import WAYF from "@/Grants/wayf-idps.json";
import {FlexClass} from "@/ui-components/Flex";
import {OldProjectRole, isAdminOrPI} from ".";
import {SidebarTabId} from "@/ui-components/SidebarComponents";

const wayfIdpsPairs = WAYF.wayfIdps.map(it => ({value: it, content: it}));

const ActionContainer = injectStyle("action-container", k => `
    ${k} {
        margin-left: auto;
        margin-right: auto;
        container-type: inline-size;
    }

    ${k} > * {  
        margin-bottom: 16px;
    }
    
    ${k} label {
        font-weight: bolder;
        display: block;
        margin-top: 16px;
    }
    
    @container (min-width: 800px) {
        ${k} form label {
            width: 50%;
        }

        ${k} form > .${FlexClass} {
            flex-direction: row;
        }
    }     

    @container (max-width: 799px) {
        ${k} form label {
            width: 100%;
        }

        ${k} form > .${FlexClass} {
            flex-direction: column;
        }
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
`);

export const ProjectSettings: React.FunctionComponent = () => {
    const projectId = useProjectId();
    const projectOps = useProject();
    const project = projectOps.fetch();
    const navigate = useNavigate();
    const didUnmount = useDidUnmount();

    usePage("Project Settings", SidebarTabId.PROJECT);
    const [settings, setSettings] = useState<Grants.RequestSettings>({
        enabled: false,
        description: "No description",
        allowRequestsFrom: [],
        excludeRequestsFrom: [],
        templates: {
            type: "plain_text",
            personalProject: "No template",
            newProject: "No template",
            existingProject: "No template",
        }
    });

    const templatePersonal = useRef<HTMLInputElement>(null);
    const templateExisting = useRef<HTMLInputElement>(null);
    const templateNew = useRef<HTMLInputElement>(null);
    const description = useRef<HTMLInputElement>(null);

    useEffect(() => {
        (async () => {
            const res = await callAPIWithErrorHandler<Grants.RequestSettings>(
                {
                    ...Grants.retrieveRequestSettings(),
                    projectOverride: projectId
                }
            );

            if (!res) return;
            if (!didUnmount.current) setSettings(res);
        })()
    }, [projectId]);

    useEffect(() => {
        const p = templatePersonal.current;
        const e = templateExisting.current;
        const n = templateNew.current;
        if (!p || !e || !n) return;

        p.value = settings.templates.personalProject;
        e.value = settings.templates.existingProject;
        n.value = settings.templates.newProject;
    }, [settings.templates]);

    useEffect(() => {
        const d = description.current;
        if (!d) return;
        d.value = settings.description;
    }, [settings.description]);

    const onAllowAdd = useCallback((criteria: Grants.UserCriteria) => {
        setSettings(prev => {
            return {
                ...prev,
                allowRequestsFrom: [...prev.allowRequestsFrom, criteria]
            }
        });
    }, []);

    const onAllowRemove = useCallback((idx: number) => {
        setSettings(prev => {
            const allowRequestsFrom = [...prev.allowRequestsFrom];
            allowRequestsFrom.splice(idx, 1);

            return {
                ...prev,
                allowRequestsFrom,
            }
        });
    }, []);


    const onExcludeAdd = useCallback((criteria: Grants.UserCriteria) => {
        setSettings(prev => {
            return {
                ...prev,
                excludeRequestsFrom: [...prev.excludeRequestsFrom, criteria]
            }
        });
    }, []);

    const onExcludeRemove = useCallback((idx: number) => {
        setSettings(prev => {
            const excludeRequestsFrom = [...prev.excludeRequestsFrom];
            excludeRequestsFrom.splice(idx, 1);

            return {
                ...prev,
                excludeRequestsFrom,
            }
        });
    }, []);

    const onSave = useCallback((e) => {
        e.preventDefault();

        callAPIWithErrorHandler(
            Grants.updateRequestSettings({
                ...settings,
                description: description.current!.value,
                templates: {
                    type: "plain_text",
                    personalProject: templatePersonal.current!.value,
                    existingProject: templateExisting.current!.value,
                    newProject: templateNew.current!.value,
                }
            })
        );
    }, [settings]);

    if (!projectId || !project) return null;

    const {status} = project;

    return <MainContainer
        key={project.id}
        header={
            <Spacer
                left={<h2 style={{margin: "0"}}>Project settings</h2>}
                right={null}
            />
        }
        headerSize={64}
        main={<div className={ActionContainer}>
            {!isAdminOrPI(status.myRole) ? (<Card>
                <LeaveProject
                    onSuccess={() => navigate("/")}
                    projectTitle={project.specification.title}
                    projectId={projectId}
                    projectRole={status.myRole!}
                    showTitle
                />
            </Card>) : <>
                <Card>
                    <Heading.h3>Project information</Heading.h3>

                    <ChangeProjectTitle
                        projectId={projectId}
                        projectTitle={project.specification.title}
                        onSuccess={() => projectOps.reload()}
                    />

                    <SubprojectSettings
                        projectId={projectId}
                        projectRole={status.myRole!}
                        setLoading={() => false}
                    />

                </Card>

                <Card>
                    <Heading.h3>Grant settings</Heading.h3>

                    <UpdateProjectLogo />

                    <form onSubmit={onSave}>
                        <Flex gap="32px">
                            <label>
                                Project description <br />
                                <TextArea width="100%" rows={5} inputRef={description} />
                            </label>

                            <label>
                                Template for personal projects <br />
                                <TextArea width="100%" rows={5} inputRef={templatePersonal} />
                            </label>
                        </Flex>

                        <Flex gap="32px">
                            <label>
                                Template for existing projects <br />
                                <TextArea rows={5} inputRef={templateExisting} />
                            </label>

                            <label>
                                Template for new projects <br />
                                <TextArea rows={5} inputRef={templateNew} />
                            </label>
                        </Flex>

                        {settings.enabled && <>
                            <Flex flexDirection={"row"} gap={"32px"}>
                                <div>
                                    <label style={{marginBottom: "16px"}}>Allow applications from</label>
                                    <UserCriteriaEditor
                                        criteria={settings.allowRequestsFrom}
                                        onSubmit={onAllowAdd}
                                        isExclusion={false}
                                        onRemove={onAllowRemove}
                                        showSubprojects={settings.enabled}
                                    />
                                </div>

                                <div>
                                    <label>Exclude applications from</label>
                                    <UserCriteriaEditor
                                        criteria={settings.excludeRequestsFrom}
                                        onSubmit={onExcludeAdd}
                                        isExclusion={true}
                                        onRemove={onExcludeRemove}
                                        showSubprojects={false}
                                    />
                                </div>
                            </Flex>
                        </>}

                        <Flex justifyContent={"center"} mt={32}>
                            <Button type={"submit"} fullWidth>Save</Button>
                        </Flex>
                    </form>
                </Card>

                {/* Note(Jonas): Disabling for now  */}
                {/* <Card>
                    <ArchiveSingleProject
                        isArchived={status.archived}
                        projectId={projectId}
                        projectRole={status.myRole!}
                        title={project.specification.title}
                        onSuccess={() => projectOps.reload()}
                    />
                </Card> */}

                <Card>
                    <LeaveProject
                        onSuccess={() => navigate("/")}
                        projectTitle={project.specification.title}
                        projectId={projectId}
                        projectRole={status.myRole!}
                    />
                </Card>
            </>}
        </div>}
    />
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
                <Flex flexGrow={1}>
                    <Box width="100%">
                        <label>
                            Project Title
                            <Input
                                required
                                ml="2px"
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
                        </label>
                    </Box>
                    <Button
                        height="42px"
                        width="72px"
                        ml="12px"
                        disabled={saveDisabled}
                    >
                        Save
                    </Button>
                </Flex>
            </form>
        </Box>
    );
}

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
                <Box mt="8px" flexGrow={1}>
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
}

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
                    <Heading.h3>Project archival</Heading.h3>
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

interface LeaveProjectProps {
    projectRole: OldProjectRole;
    projectId: string;
    showTitle?: boolean;
    projectTitle: string;
    onSuccess: () => void;
}

export function LeaveProject(props: LeaveProjectProps): JSX.Element {
    return (
        <ActionBox>
            <Box flexGrow={1}>
                <Heading.h3>Leave project {props.showTitle ? `"${projectTitleFromCache(Client.projectId)}"` : ""}</Heading.h3>
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
}

export function UpdateProjectLogo(): JSX.Element | null {
    const projectId = useProjectId() ?? "";
    const [, setLogoCacheBust] = useState("" + Date.now());

    if (!projectId) return null;
    return <div>
        <label>
            Project logo (click{" "}
            <span style={{color: "var(--primaryLight)", cursor: "pointer"}}>here</span>
            {" "}to upload a new logo)
            <br />

            <HiddenInputField
                type="file"
                onChange={async e => {
                    const target = e.target;
                    if (target.files) {
                        const file = target.files[0];
                        target.value = "";
                        if (file.size > 1024 * 512) {
                            snackbarStore.addFailure("File exceeds 512KB. Not allowed.", false);
                        } else {
                            if (await uploadProjectLogo({file, projectId})) {
                                setLogoCacheBust("" + Date.now());
                                snackbarStore.addSuccess("Logo changed, refresh to see changes", false);
                            }
                        }
                        dialogStore.success();
                    }
                }}
            />
        </label>

        <ProjectLogo projectId={projectId} size={"128px"} />
    </div>
}

export interface AllowSubProjectsRenamingRequest {
    projectId: string;
}

export interface AllowSubProjectsRenamingResponse {
    allowed: boolean;
}

export interface ToggleSubProjectsRenamingRequest {
    projectId: string;
}

const UserCriteriaEditor: React.FunctionComponent<{
    onSubmit: (c: Grants.UserCriteria) => any,
    onRemove: (idx: number) => any,
    criteria: Grants.UserCriteria[],
    showSubprojects: boolean;
    isExclusion: boolean;
}> = props => {
    const [showRequestFromEditor, setShowRequestFromEditor] = useState<boolean>(false);
    return <>
        <Table mb={16}>
            <thead>
                <TableRow>
                    <TableHeaderCell textAlign={"left"}>Type</TableHeaderCell>
                    <TableHeaderCell textAlign={"left"}>Constraint</TableHeaderCell>
                    <TableHeaderCell />
                </TableRow>
            </thead>
            <tbody>

                {!props.showSubprojects ? null :
                    <TableRow>
                        <TableCell>Subprojects</TableCell>
                        <TableCell>None</TableCell>
                        <TableCell />
                    </TableRow>
                }

                {!props.showSubprojects && props.criteria.length === 0 && !showRequestFromEditor ? <>
                    <TableRow>
                        <TableCell>No one</TableCell>
                        <TableCell>None</TableCell>
                        <TableCell />
                    </TableRow>
                </> : null}

                {props.criteria.map((it, idx) =>
                    <TableRow key={keyFromCriteria(it)}>
                        <TableCell textAlign={"left"}>{userCriteriaTypePrettifier(it.type)}</TableCell>
                        <TableCell textAlign={"left"}>
                            {it.type === "wayf" ? it.org : null}
                            {it.type === "email" ? it.domain : null}
                            {it.type === "anyone" ? "None" : null}
                        </TableCell>
                        <TableCell textAlign={"right"}>
                            <Icon color={"errorMain"} name={"trash"} cursor={"pointer"} onClick={() => props.onRemove(idx)} />
                        </TableCell>
                    </TableRow>
                )}

                {showRequestFromEditor ?
                    <UserCriteriaRowEditor
                        onSubmit={(c) => {
                            props.onSubmit(c);
                            setShowRequestFromEditor(false);
                        }}
                        onCancel={() => setShowRequestFromEditor(false)}
                        allowAnyone={!props.isExclusion && props.criteria.find(it => it.type === "anyone") === undefined}
                        allowWayf={!props.isExclusion}
                    /> :
                    null
                }

            </tbody>
        </Table>
        <Flex justifyContent={"center"} mb={32}>
            {!showRequestFromEditor ?
                <Button
                    type={"button"}
                    onClick={() => setShowRequestFromEditor(true)}
                >
                    Add new row
                </Button> :
                null
            }
        </Flex>
    </>;
};

const UserCriteriaRowEditor: React.FunctionComponent<{
    onSubmit: (c: Grants.UserCriteria) => any,
    onCancel: () => void,
    allowAnyone?: boolean;
    allowWayf?: boolean;
}> = props => {
    const [type, setType] = useState<Pick<Grants.UserCriteria, "type">>(
        props.allowWayf ? ({type: "wayf"}) : ({type: "email"})
    );
    const [selectedWayfOrg, setSelectedWayfOrg] = useState("");
    const inputRef = useRef<HTMLInputElement>(null);
    const onClick = useCallback((e) => {
        e.preventDefault();
        switch (type.type) {
            case "email":
                if (inputRef.current!.value.indexOf(".") === -1 || inputRef.current!.value.indexOf(" ") !== -1) {
                    snackbarStore.addFailure("This does not look like a valid email domain. Try again.", false);
                    return;
                }
                if (inputRef.current!.value.indexOf("@") !== -1) {
                    snackbarStore.addFailure("Only the domain should be added. Example: 'sdu.dk'.", false);
                    return;
                }

                const domain = inputRef.current!.value;
                props.onSubmit({type: "email", domain});
                break;
            case "anyone":
                props.onSubmit({type: "anyone"});
                break;
            case "wayf":
                if (selectedWayfOrg === "") {
                    snackbarStore.addFailure("You must select a WAYF organization", false);
                    return;
                }
                props.onSubmit({type: "wayf", org: selectedWayfOrg});
                break;
        }
    }, [props.onSubmit, type, selectedWayfOrg]);

    const options: {text: string, value: string}[] = [];
    if (props.allowAnyone) {
        options.push({text: "Anyone", value: "anyone"});
    }

    options.push({text: "Email", value: "email"});

    if (props.allowWayf) {
        options.push({text: "WAYF", value: "wayf"});
    }

    return <TableRow>
        <TableCell>
            <ClickableDropdown
                trigger={userCriteriaTypePrettifier(type.type)}
                options={options}
                onChange={t => setType({type: t} as Pick<Grants.UserCriteria, "type">)}
                chevron
            />
        </TableCell>
        <TableCell>
            <div style={{minWidth: "350px"}}>
                <Flex height={47}>
                    {type.type !== "anyone" ? null : null}
                    {type.type !== "email" ? null : <>
                        <Input inputRef={inputRef} placeholder={"Email domain"} />
                    </>}
                    {type.type !== "wayf" ? null : <>
                        {/* WAYF idps extracted from https://metadata.wayf.dk/idps.js*/}
                        {/* curl https://metadata.wayf.dk/idps.js 2>/dev/null | jq 'to_entries[].value.schacHomeOrganization' | sort | uniq | xargs -I _ printf '"_",\n' */}
                        <DataList
                            options={wayfIdpsPairs}
                            onSelect={(item) => setSelectedWayfOrg(item)}
                            placeholder={"Type to search..."}
                        />
                    </>}
                    <ConfirmCancelButtons height={"unset"} onConfirm={onClick} onCancel={props.onCancel} />
                </Flex>
            </div>
        </TableCell>
        <TableCell />
    </TableRow>;
}

function userCriteriaTypePrettifier(t: string): string {
    switch (t) {
        case "anyone":
            return "Anyone";
        case "email":
            return "Email";
        case "wayf":
            return "WAYF";
        default:
            return t;
    }
}

function keyFromCriteria(userCriteria: Grants.UserCriteria): string {
    switch (userCriteria.type) {
        case "anyone": {
            return "anyone";
        }
        case "email": {
            return userCriteria.domain;
        }
        case "wayf": {
            return userCriteria.org;
        }
    }
}

export interface UploadLogoProps {
    file: File;
    projectId: string;
}

async function uploadProjectLogo(props: UploadLogoProps): Promise<boolean> {
    const token = await Client.receiveAccessTokenOrRefreshIt();

    return new Promise((resolve) => {
        const request = new XMLHttpRequest();
        request.open("POST", Client.computeURL("/api", `/grants/v2/uploadLogo`));
        request.setRequestHeader("Authorization", `Bearer ${token}`);
        request.responseType = "text";
        request.setRequestHeader("Project", props.projectId);
        request.onreadystatechange = () => {
            if (request.status !== 0) {
                if (!inSuccessRange(request.status)) {
                    let message = "Logo upload failed";
                    try {
                        message = JSON.parse(request.responseText).why;
                    } catch (e) {
                        // tslint:disable-next-line: no-console
                        console.log(e);
                        // Do nothing
                    }

                    snackbarStore.addFailure(message, false);
                    resolve(false);
                } else {
                    resolve(true);
                }
            }
        };

        request.send(props.file);
    });
}

export default ProjectSettings;
