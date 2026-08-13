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
    Tooltip,
    Markdown,
    Card,
    Icon
} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {addStandardDialog, ConfirmCancelButtons} from "@/UtilityComponents";
import {apiRetrieve, apiUpdate, callAPI, callAPIWithErrorHandler, useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {useNavigate} from "react-router-dom";
import {dialogStore} from "@/Dialog/DialogStore";
import {MainContainer} from "@/ui-components/MainContainer";
import {SettingsAction, SettingsNavSection, SettingsPage, SettingsSection} from "@/ui-components/SettingsComponents";
import TabbedCard, {TabbedCardTab} from "@/ui-components/TabbedCard";
import {usePage} from "@/Navigation/Redux";
import {useCallback, useEffect, useRef, useState} from "react";
import {buildQueryString} from "@/Utilities/URIUtilities";
import ProjectAPI, {useProjectId} from "@/Project/Api";
import {bulkRequestOf, copyToClipboard} from "@/UtilityFunctions";
import {Client} from "@/Authentication/HttpClientInstance";
import {useProject} from "./cache";
import {injectStyle} from "@/Unstyled";
import * as Grants from "@/Grants";
import {ProjectLogo} from "@/Grants/ProjectLogo";
import {HiddenInputField} from "@/ui-components/Input";
import {IconButton} from "@/ui-components/IconButton";
import {CopyButton} from "@/ui-components/CopyButton";
import {SimpleRichItem, SimpleRichSelect} from "@/ui-components/RichSelect";
import {inSuccessRange} from "@/UtilityFunctions";
import Table, {TableCell, TableHeaderCell, TableRow} from "@/ui-components/Table";
import {useDidUnmount} from "@/Utilities/ReactUtilities";
import {ProjectSwitcher} from "./ProjectSwitcher";
import WAYF from "@/Grants/wayf-idps.json";
import {FlexClass} from "@/ui-components/Flex";
import {OldProjectRole, isAdminOrPI, isDataSteward} from ".";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import AppRoutes from "@/Routes";
import {sendFailureNotification, sendSuccessNotification} from "@/Notifications";
import {search} from "@/Applications/AppStoreApi";
import {Toggle} from "@/ui-components/Toggle";
import {useDiscovery} from "@/Applications/Hooks";
import {SafeLogo} from "@/Applications/AppToolLogo";
import {NewDataList} from "@/UserSettings/ChangeUserDetails";
import {DataListItem} from "@/UserSettings/types";
import {Tag} from "@/Applications/Card";
import ProviderBrowse from "@/Admin/Providers/Browse";
import ProvidersApi from "@/UCloud/ProvidersApi";
import { useUState } from "@/Utilities/UState";
import { connectionState } from "@/Providers/ConnectionState";
import { ProviderTitle } from "@/Providers/ProviderTitle";
import { ProviderLogo } from "@/Providers/ProviderLogo";

const wayfIdpsPairs = WAYF.wayfIdps.map(it => ({value: it, content: it}));

function createDefaultApplicationField(): Grants.FormField {
    return {
        name: "application",
        title: "Application",
        description: "Please describe why you are applying for resources.",
        optional: false,
        rows: 8,
        maxLength: 240,
    };
}

function ensureApplicationFields(settings: Grants.RequestSettings): Grants.RequestSettings {
    const structured = settings.templates.structured;
    return {
        ...settings,
        templates: {
            ...settings.templates,
            structured: {
                ...structured,
                personalProject: structured.personalProject.length === 0
                    ? [createDefaultApplicationField()]
                    : structured.personalProject,
                existingProject: structured.existingProject.length === 0
                    ? [createDefaultApplicationField()]
                    : structured.existingProject,
                newProject: structured.newProject.length === 0
                    ? [createDefaultApplicationField()]
                    : structured.newProject,
            }
        }
    };
}

function toSnakeCase(value: string): string {
    return value
        .trim()
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, "_")
        .replace(/^_+|_+$/g, "");
}

const ActionContainer = injectStyle("action-container", k => `
    ${k} {
        container-type: inline-size;
    }

    ${k} label {
        font-weight: bold;
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

const GrantSourcesClass = injectStyle("grant-sources", k => `
    ${k} {
        display: grid;
        gap: 16px;
    }

    ${k} > .grant-source-panel {
        background: var(--backgroundCard);
        border: 1px solid var(--borderColor);
        border-radius: 10px;
        display: flex;
        flex-direction: column;
        min-width: 0;
        padding: 16px;
    }

    ${k} .grant-source-description {
        min-height: 42px;
    }

    @container (min-width: 760px) {
        ${k} {
            grid-template-columns: repeat(2, minmax(0, 1fr));
        }
    }
`);

const CriteriaEditorClass = injectStyle("criteria-editor", k => `
    ${k} {
        display: flex;
        flex: 1;
        flex-direction: column;
    }

    ${k} .criteria-row {
        align-items: center;
        display: grid;
        gap: 12px;
        grid-template-columns: minmax(0, 1fr) auto;
        min-height: 48px;
        padding: 8px 0;
    }

    ${k} .criteria-row-copy {
        min-width: 0;
    }

    ${k} .criteria-constraint,
    ${k} .criteria-empty {
        color: var(--textSecondary);
        font-size: 0.9em;
    }

    ${k} .criteria-row-editor {
        align-items: center;
        border-top: 1px solid var(--borderColor);
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
        padding: 12px 0;
    }

    ${k} .criteria-editor-value {
        flex: 1 1 220px;
        min-width: 0;
    }

    ${k} .criteria-editor-type {
        flex: 0 0 150px;
    }

    ${k} .criteria-editor-actions {
        flex: 0 0 175px;
        margin-left: auto;
    }

`);

interface TemplateFormProps {
    projectType: string;
    settings: Grants.RequestSettings;
    setSettings: React.Dispatch<React.SetStateAction<Grants.RequestSettings>>;
    updateFormField: (idx: number, fieldName: string, value: any, projectType: string) => void;
    removeFormField: (idx: number, projectType: string) => void;
    updateFormFieldLimits: (idx: number, fieldName: string, value: any, projectType: string) => void;
}

interface MoveFieldControlsProps {
    idx: number;
    numberOfFields: number;
    projectType: string;
    setSettings: React.Dispatch<React.SetStateAction<Grants.RequestSettings>>;
}

const MoveFieldControls: React.FunctionComponent<MoveFieldControlsProps> = ({
    idx,
    numberOfFields,
    projectType,
    setSettings,
}) => {
    const move = useCallback((direction: "up" | "down") => {
        setSettings(prev => {
            const items = [...prev.templates.structured[projectType]];

            const targetIdx =
                direction === "up" ? idx - 1 : idx + 1;

            if (targetIdx < 0 || targetIdx >= items.length) {
                return prev;
            }

            [items[idx], items[targetIdx]] = [
                items[targetIdx],
                items[idx],
            ];

            return {
                ...prev,
                templates: {
                    ...prev.templates,
                    structured: {
                        ...prev.templates.structured,
                        [projectType]: items,
                    },
                },
            };
        });
    },
        [idx, projectType, setSettings]
    );

    return <Flex gap="4px">
        {idx === 0 ? null : <IconButton
            tooltip="Move field up"
            icon="heroArrowUp"
            onClick={() => move("up")}
        />}
        {idx === numberOfFields - 1 ? null : <IconButton
            tooltip="Move field down"
            icon="heroArrowDown"
            onClick={() => move("down")}
        />}
    </Flex>
};

const TemplateForm: React.FunctionComponent<TemplateFormProps> = ({
    projectType: projectType,
    settings,
    setSettings,
    updateFormField,
    removeFormField,
    updateFormFieldLimits,
}) => {
    return <div>
        {
            settings.templates.structured[projectType].map((field: Grants.FormField, idx: number) => {
                return <React.Fragment key={idx}>
                    <Flex justifyContent={"end"} minHeight={"32px"}>
                        <MoveFieldControls
                            idx={idx}
                            numberOfFields={settings.templates.structured[projectType].length}
                            projectType={projectType}
                            setSettings={setSettings}
                        />
                    </Flex>
                    <Flex gap="20px" justifyContent={"space-evenly"}>
                        <Label fontSize={12}>
                            Title
                            <Input
                                required
                                placeholder="Project summary"
                                value={field.title}
                                onChange={(e) => updateFormField(idx, 'title', e.target.value, projectType)}
                            />
                        </Label>
                        <Label fontSize={12}>
                            Name
                            <Tooltip trigger={(
                                <Input
                                    required
                                    placeholder="project_summary"
                                    value={field.name}
                                    onChange={(e) => updateFormField(idx, 'name', e.target.value, projectType)}
                                />
                            )}>
                                This identifier remains stable and is used to associate fields with grant applications.
                            </Tooltip>
                        </Label>
                    </Flex>
                    <Flex gap="20px" justifyContent={"space-between"}>
                        <Label width={"100%"} fontSize={12}>
                            Description
                            <TextArea
                                width={"100%"}
                                value={field.description}
                                rows={5}
                                placeholder="Describe what the applicant should provide"
                                onChange={(e) => updateFormField(idx, 'description', e.target.value, projectType)}
                            />
                        </Label>
                        <Box width={150}>
                            <Label width={"100%"} fontSize={12}>
                                Row limit
                                <Input
                                    value={field.rows ?? ""}
                                    placeholder="1"
                                    type="number"
                                    onChange={(e) => updateFormFieldLimits(idx, 'rows', e.target.value, projectType)}
                                />
                            </Label>
                            <Label width={"100%"} fontSize={12}>
                                Max length
                                <Input
                                    value={field.maxLength ?? ""}
                                    placeholder="240"
                                    type="number"
                                    onChange={(e) => updateFormFieldLimits(idx, 'maxLength', e.target.value, projectType)}
                                />
                            </Label>
                        </Box>
                    </Flex>
                    <br />
                    <Flex justifyContent={"space-between"}>
                        <span style={{display: "flex"}}>
                            <Label cursor="pointer" width="unset" fontSize={"12px"} marginTop={"5px"}>
                                <Checkbox size={30} checked={field.optional} onChange={() => updateFormField(idx, 'optional', !field.optional, projectType)}>
                                </Checkbox>
                                Optional
                            </Label>
                        </span>
                        <Flex justifyContent={"flex-end"}>
                            <IconButton
                                tooltip={"Remove field"}
                                icon={"heroTrash"}
                                color={"errorMain"}
                                onClick={async () => removeFormField(idx, projectType)}
                            />
                        </Flex>
                    </Flex>
                    {settings.templates.structured[projectType].length > idx + 1 ? <div><br />
                        <hr style={{border: ("solid 1px var(--secondaryDark)")}} />
                    </div> : <></>}
                </React.Fragment>
            })
        }
        <Button fullWidth mt={24} type={"button"} onClick={() => {
            setSettings(prev => ({
                ...prev,
                templates: {
                    ...prev.templates,
                    structured: {
                        ...prev.templates.structured,
                        [projectType]: [...prev.templates.structured[projectType], {
                            description: "",
                            maxLength: 240,
                            name: "",
                            optional: false,
                            rows: 1,
                            title: ""
                        }]
                    }
                }
            }));
        }}>Add field</Button>
    </div>
};

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
            type: "structured",
            structured: {
                personalProject: [createDefaultApplicationField()],
                existingProject: [createDefaultApplicationField()],
                newProject: [createDefaultApplicationField()],
                revisionNumber: -1
            },
        }
    });
    const description = useRef<HTMLInputElement>(null);
    useEffect(() => {
        if (!projectId) {
            navigate(AppRoutes.dashboard.dashboardA());
            return;
        }
        (async () => {
            try {
                const res = await callAPI<Grants.RequestSettings>(
                    {
                        ...Grants.retrieveRequestSettings(),
                        projectOverride: projectId,
                    }
                );

                if (!didUnmount.current) setSettings(ensureApplicationFields(res));
            } catch (e) {
                // Ignoring failure
            }
        })();
    }, [projectId]);

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

    const updateFormFieldLimits = useCallback((idx: number, fieldName: string, value: any, projectType: string) => {
        let parsedValue = value === "" ? null : parseInt(value);

        return updateFormField(idx, fieldName, parsedValue, projectType);
    }, []);

    const updateFormField = useCallback((idx: number, fieldName: string, value: any, projectType: string) => {
        setSettings(prev => {
            const fields = prev.templates.structured[projectType].map((field, i) => {
                if (i !== idx) return field;

                const synchronized = field.name === toSnakeCase(field.title);
                if (fieldName === "title" && synchronized) {
                    return {...field, title: value, name: toSnakeCase(value)};
                }
                if (fieldName === "name") {
                    const name = toSnakeCase(value);
                    return {...field, name};
                }
                return {...field, [fieldName]: value};
            });

            return {
                ...prev,
                templates: {
                    ...prev.templates,
                    structured: {
                        ...prev.templates.structured,
                        [projectType]: fields,
                    }
                }
            };
        });
    }, []);

    const removeFormField = useCallback((idx: number, projectType: string) => {
        setSettings(prev => ({
            ...prev,
            templates: {
                ...prev.templates,
                structured: {
                    ...prev.templates.structured,
                    [projectType]: prev.templates.structured[projectType].filter((_, i) => i !== idx)
                }
            }
        }));
    }, []);

    const onSave = useCallback(async (e) => {
        e.preventDefault();

        await callAPIWithErrorHandler(
            Grants.updateRequestSettings({
                ...settings,
                description: description.current!.value,
                templates: {
                    type: "structured",
                    structured: settings.templates.structured,
                }
            })
        );

        sendSuccessNotification("Project settings saved!");
    }, [settings]);

    if (!projectId || !project) return null;

    const {status} = project;

    if (project.status.personalProviderProjectFor != null) {
        return <MainContainer
            main={
                <>
                    <Heading.h2>Unavailable for this project</Heading.h2>
                    <p>
                        This project belongs to a provider which does not support the accounting and project management
                        features of UCloud. Try again with a different project.
                    </p>
                </>
            }
        />
    }

    const canManageProject = isAdminOrPI(status.myRole);
    const sections: SettingsNavSection[] = [
        {id: "project-information", label: "Project information"},
        ...(canManageProject ? [{id: "grant-applications", label: "Grant applications"}] : []),
        ...(isDataSteward(status.myRole) ? [{id: "project-policies", label: "Project policies"}] : []),
        {id: "project-membership", label: "Project membership"},
    ];

    return <SettingsPage
        key={project.id}
        title="Project settings"
        titleActions={<ProjectSwitcher />}
        sections={sections}
    >
        <div className={ActionContainer}>
            <SettingsSection id="project-information" title="Project information">
                <ChangeProjectTitle
                    projectId={projectId}
                    projectTitle={project.specification.title}
                    onSuccess={() => projectOps.reload()}
                />

                <Label>Project ID</Label>
                <Flex alignItems="center">
                    <Text color={"textSecondary"}>{projectId}</Text>
                    <CopyButton
                        tooltip="Copy project ID"
                        onClick={() => {
                            copyToClipboard(projectId);
                        }}
                    />
                </Flex>

                <SubprojectSettings
                    projectId={projectId}
                    projectRole={status.myRole!}
                    setLoading={() => false}
                />
            </SettingsSection>

            {canManageProject ? <SettingsSection
                id="grant-applications"
                title="Grant applications"
                description="Configure how applicants describe their project and who can submit applications."
            >
                <form onSubmit={onSave}>
                    <Box mb={24}>
                        <UpdateProjectLogo />
                    </Box>
                    <Box mb={24}>
                        <label style={{width: "100%"}}>
                            Project description <br />
                            <TextArea width="100%" rows={5} inputRef={description} />
                        </label>
                    </Box>
                    <Box mb={12}>
                        <Heading.h4 bold>Application form</Heading.h4>
                        <Text color="textSecondary">
                            Define the information applicants must provide for each type of project.
                        </Text>
                    </Box>
                    <TabbedCard>
                        <TabbedCardTab name="Personal projects" icon="heroUser">
                            <TemplateForm
                                projectType="personalProject"
                                settings={settings}
                                setSettings={setSettings}
                                updateFormField={updateFormField}
                                removeFormField={removeFormField}
                                updateFormFieldLimits={updateFormFieldLimits}
                            />
                        </TabbedCardTab>
                        <TabbedCardTab name="Existing projects" icon="heroUsers">
                            <TemplateForm
                                projectType="existingProject"
                                settings={settings}
                                setSettings={setSettings}
                                updateFormField={updateFormField}
                                removeFormField={removeFormField}
                                updateFormFieldLimits={updateFormFieldLimits}
                            />
                        </TabbedCardTab>
                        <TabbedCardTab name="New projects" icon="heroUserPlus">
                            <TemplateForm
                                projectType="newProject"
                                settings={settings}
                                setSettings={setSettings}
                                updateFormField={updateFormField}
                                removeFormField={removeFormField}
                                updateFormFieldLimits={updateFormFieldLimits}
                            />
                        </TabbedCardTab>
                    </TabbedCard>
                    {settings.enabled && <Box mt={32}>
                        <Box mb={12}>
                            <Heading.h4 bold>Application sources</Heading.h4>
                            <Text color="textSecondary">
                                Choose who may apply and optionally exclude specific groups from submitting applications.
                            </Text>
                        </Box>
                        <div className={GrantSourcesClass}>
                            <div className="grant-source-panel">
                                <Heading.h5>Allow applications from</Heading.h5>
                                <Text className="grant-source-description" color="textSecondary">
                                    Applications are accepted from these sources.
                                </Text>
                                <UserCriteriaEditor
                                    criteria={settings.allowRequestsFrom}
                                    projectId={projectId}
                                    onSubmit={onAllowAdd}
                                    isExclusion={false}
                                    onRemove={onAllowRemove}
                                    showSubprojects={settings.enabled}
                                />
                            </div>

                            <div className="grant-source-panel">
                                <Heading.h5>Exclude applications from</Heading.h5>
                                <Text className="grant-source-description" color="textSecondary">
                                    Matching sources are blocked even when otherwise allowed.
                                </Text>
                                <UserCriteriaEditor
                                    criteria={settings.excludeRequestsFrom}
                                    projectId={projectId}
                                    onSubmit={onExcludeAdd}
                                    isExclusion={true}
                                    onRemove={onExcludeRemove}
                                    showSubprojects={false}
                                />
                            </div>
                        </div>
                    </Box>}

                    <Flex justifyContent={"center"} mt={32}>
                        <Button type={"submit"} fullWidth>Save grant application settings</Button>
                    </Flex>
                </form>
            </SettingsSection> : null}

            {isDataSteward(status.myRole) ?
                <SettingsSection id="project-policies" title="Project policies">
                    <ProjectPolicies />
                </SettingsSection> : null}

            <SettingsSection id="project-membership" title="Project membership" mb={0}>
                <LeaveProject
                    onSuccess={() => navigate("/")}
                    projectTitle={project.specification.title}
                    projectId={projectId}
                    projectRole={status.myRole!}
                />
            </SettingsSection>
        </div>
    </SettingsPage>
};


type PolicyName =
    | "RestrictApplications"
    | "RestrictCutAndPaste"
    | "RestrictDownloads"
    | "RestrictIntegratedApplications"
    | "RestrictInternetAccess"
    | "RestrictOrganizationMembers"
    | "RestrictProviderFileTransfers"
    | "RestrictPublicIPs"
    | "RestrictPublicLinks"
    | "RestrictSourceIPRange";

interface Policy {
    schema: PolicySchema;
    specification: Specification;
}

interface PolicySchemaBase {
    name: PolicyName;
    title: string;
    description: string;
    configuration: Record<string, ConfigurationEntry>;
}

interface ConfigurationEntry {
    title: string;
    description: string;
}

interface RestrictApplications extends PolicySchemaBase {
    name: "RestrictApplications";
    configuration: {enabled: ConfigurationEntry; applications: ConfigurationEntry};
}

interface RestrictCutAndPaste extends PolicySchemaBase {
    name: "RestrictCutAndPaste";
    configuration: {enabled: ConfigurationEntry};
}

interface RestrictDownloads extends PolicySchemaBase {
    name: "RestrictDownloads";
    configuration: {enabled: ConfigurationEntry};
}

interface RestrictIntegratedApplications extends PolicySchemaBase {
    name: "RestrictIntegratedApplications";
    configuration: {enabled: ConfigurationEntry; allowList: ConfigurationEntry};
}

interface RestrictInternetAccess extends PolicySchemaBase {
    name: "RestrictInternetAccess";
    configuration: {enabled: ConfigurationEntry; allowedSubnets: ConfigurationEntry};
}

interface RestrictOrganizationMembers extends PolicySchemaBase {
    name: "RestrictOrganizationMembers";
    configuration: {enabled: ConfigurationEntry; organizations: ConfigurationEntry};
}

interface RestrictProviderFileTransfers extends PolicySchemaBase {
    name: "RestrictProviderFileTransfers";
    configuration: {enabled: ConfigurationEntry; allowedProviders: ConfigurationEntry};
}

interface RestrictPublicIPs extends PolicySchemaBase {
    name: "RestrictPublicIPs";
    configuration: {enabled: ConfigurationEntry};
}

interface RestrictPublicLinks extends PolicySchemaBase {
    name: "RestrictPublicLinks";
    configuration: {enabled: ConfigurationEntry};
}

interface RestrictSourceIPRange extends PolicySchemaBase {
    name: "RestrictSourceIPRange";
    configuration: {enabled: ConfigurationEntry; allowedSubnets: ConfigurationEntry};
}

type PolicySchema =
    | RestrictApplications
    | RestrictCutAndPaste
    | RestrictDownloads
    | RestrictIntegratedApplications
    | RestrictInternetAccess
    | RestrictOrganizationMembers
    | RestrictProviderFileTransfers
    | RestrictPublicIPs
    | RestrictPublicLinks
    | RestrictSourceIPRange

interface RetrievePoliciesRequest {
    projectId: string;
}

interface PoliciesUpdateRequest {
    updatedPolicies: Record<PolicyName, Specification>;
}

interface Specification {
    schema: PolicyName;
    project: string;
    values: any;
}

function PolicySchemas({schemas}: {schemas: Record<string, Policy>}) {
    const updateRef = React.useRef<Record<string, Specification>>({});
    const submitChanges = React.useCallback((updatedPolicies: Record<PolicyName, Specification>) => {
        callAPI(PolicyAPI.updatePolicies({updatedPolicies}));
        updateRef.current = {};
    }, []);

    const projectId = useProjectId();
    const togglePolicy = React.useCallback((schemaName: PolicyName) => {
        if (!updateRef.current[schemaName]) {
            updateRef.current[schemaName] = {
                project: projectId!,
                schema: schemaName,
                values: {
                    enabled: true
                }
            }
        } else {
            updateRef.current[schemaName].values.enabled = updateRef.current[schemaName].values.enabled;
        }
    }, [projectId]);

    const updatePolicyRule = React.useCallback((schemaName: PolicyName, rule: string, value: any) => {
        updateRef.current[schemaName].values[rule] = value;
    }, []);

    return <Card>{
        Object.keys(schemas).map(key => {
            const policy = schemas[key];
            return <PolicySchemaEntry key={key} togglePolicy={togglePolicy} policy={policy} updatePolicyRule={updatePolicyRule} />
        })}
        <Button ml="auto" onClick={() => submitChanges(updateRef.current)}>Save changes</Button>
    </Card>;
}

const asCheckBox = true;
function PolicySchemaEntry({ policy, togglePolicy, updatePolicyRule }: { policy: Policy; togglePolicy: (schemaName: PolicyName) => void; updatePolicyRule: (policyName: PolicyName, rule: string, value: any) => void }): React.ReactNode {
    const [enabled, setEnabled] = React.useState(!"This should be based on the enabled key inside the specification".toString());

    return <Box key={policy.schema.name} my="12px" pb="20px" borderBottom={"1px solid var(--borderColor)"}>
        <Flex justifyContent={"space-between"}>
            <b>{policy.schema.title}</b>
            {asCheckBox ? <Label cursor="pointer" style={{gap: "8px", marginTop: 0}} width="fit-content">
                {policy.schema.configuration.enabled.title}
                <Checkbox style={{marginLeft: "6px", marginTop: "-4px"}} checked={enabled} onChange={() => setEnabled(enabled => {
                    togglePolicy(policy.schema.name);
                    return !enabled
                })} />
            </Label> :
                <Flex cursor="pointer" gap="8px" onClick={() => setEnabled(enabled => {
                    togglePolicy(policy.schema.name);
                    return !enabled
                })}>
                    {policy.schema.configuration.enabled.title}
                    <Box mt="1px" mr="8px">
                        <Toggle checked={enabled} onChange={() => setEnabled(enabled => {
                            togglePolicy(policy.schema.name)
                            return !enabled
                        })} height={18} />
                    </Box>
                </Flex>
            }
        </Flex>
        <Box mt="-10px" style={{color: "var(--textSecondary)"}}>
            <Markdown>{policy.schema.description}</Markdown>
        </Box>
        {enabled ? <PolicyConfiguration updatePolicyRule={updatePolicyRule} policy={policy} /> : null}
    </Box>
}

function PolicyConfiguration({ policy, updatePolicyRule }: { policy: Policy; updatePolicyRule: (policyName: PolicyName, rule: string, value: any) => void}): React.ReactNode {
    switch (policy.schema.name) {
        case "RestrictApplications": {
            const [searchApps, setSearchApps] = useState<DataListItem[]>([]);
            const ref = useRef<HTMLInputElement | null>(null);
            const [allowedApps, setAllowedApps] = useState(new Set<string>());
            const timeoutId = useRef(-1);
            const [discovery] = useDiscovery();

            const {applications} = policy.schema.configuration;
            return <ConfigurationEntry entry={applications}>
                {allowedApps.size === 0 ? "No application allowed" : [...allowedApps].map(it => <Tag label={it} />)}
                <NewDataList
                    id={"allowed-apps"}
                    items={searchApps}
                    title={""}
                    didUpdateQuery={(query) => {
                        if (timeoutId.current !== -1) {
                            window.clearTimeout(timeoutId.current);
                        }

                        if (query === "") {
                            setSearchApps([]);
                            return;
                        }

                        if (query.length < 3) return;

                        timeoutId.current = window.setTimeout(() => {
                            callAPI(search({
                                query,
                                discovery: discovery.discovery,
                                itemsPerPage: 100,
                            })).then(result => {
                                setSearchApps(result.items.map(it => ({
                                    key: it.metadata.name,
                                    value: it.metadata.name,
                                    tags: ""
                                })))
                            })
                        }, 500);
                    }}
                    onSelect={it => {
                        const newAllowedApps = new Set([it.value, ...allowedApps]);
                        setAllowedApps(newAllowedApps);
                        updatePolicyRule(policy.schema.name, applications.title, [...newAllowedApps]);
                        (document.getElementById("allowed-apps") as HTMLInputElement).value = "";
                    }}
                    RenderRow={({item}) => (<AppRow appName={item.value} />)}
                    placeholder={"Search by application name..."}
                    ref={ref}
                />
            </ConfigurationEntry>;
        }
        case "RestrictCutAndPaste": {
            // Only contains "enabled". Handled above
            return null;
        }
        case "RestrictDownloads": {
            // Only contains "enabled". Handled above
            return null;
        }
        case "RestrictIntegratedApplications": {
            const {allowList} = policy.schema.configuration;
            const ref = useRef<HTMLInputElement | null>(null);
            const items: DataListItem[] = [{key: "syncthing", value: "syncthing", tags: ""}, {key: "terminal", value: "terminal", tags: ""}]
            const [allowedApps, setAllowedApps] = useState(new Set<string>());

            return <ConfigurationEntry entry={allowList}>
                {allowedApps.size === 0 ? "No integrated app allowed" : [...allowedApps].map(it => <Tag label={<Box>{it} <Icon size="12px" ml="5px" name="close" /></Box>} />)}
                <NewDataList
                    id={"allowed-integrated-apps"}
                    items={items}
                    title={""}
                    onSelect={it => {
                        const newAllowedApps = new Set([it.value, ...allowedApps]);
                        setAllowedApps(newAllowedApps);
                        updatePolicyRule(policy.schema.name, allowList.title, [...newAllowedApps]);
                        (document.getElementById("allowed-integrated-apps") as HTMLInputElement).value = "";
                    }}
                    RenderRow={({item}) => (<AppRow appName={item.value} />)}
                    placeholder={"Integrated application name..."}
                    ref={ref}
                />
            </ConfigurationEntry>;
        }
        case "RestrictInternetAccess": {
            const {allowedSubnets} = policy.schema.configuration;
            return <ConfigurationEntry entry={allowedSubnets}>
                <Input type="text" />
            </ConfigurationEntry>;
        }
        case "RestrictOrganizationMembers": {
            const {organizations} = policy.schema.configuration;
            return <ConfigurationEntry entry={organizations}>
                <Input type="text" />
            </ConfigurationEntry>;
        }
        case "RestrictProviderFileTransfers": {
            const {allowedProviders} = policy.schema.configuration;
            const ref = useRef<HTMLInputElement>(null)
            const [providers, setProviders] = React.useState<DataListItem[]>([]);
            const state = useUState(connectionState);
            React.useEffect(() => {
                state.fetch();
            }, []);

            React.useEffect(() => {
                setProviders(state.providers.map(it => ({
                    key: it.provider,
                    value: it.providerTitle,
                    tags: it.provider + " " + it.providerTitle,
                })))
                // Note(Jonas): Length is used, as state.providers seems to change every render
            }, [state.providers.length]);

            return <ConfigurationEntry entry={allowedProviders}>
                <NewDataList items={providers} id="allowed-providers" title={""} onSelect={provider => {

                }} placeholder={"Search providers..."} ref={ref} RenderRow={({item}) => <ProviderRow providerTitle={item.value} />} />
            </ConfigurationEntry>;
        }
        case "RestrictPublicIPs": {
            // Only contains "enabled". Handled above
            return null;
        }
        case "RestrictPublicLinks": {
            // Only contains "enabled". Handled above
            return null;
        }
        case "RestrictSourceIPRange": {
            const {allowedSubnets} = policy.schema.configuration;
            return <ConfigurationEntry entry={allowedSubnets}>
                <Input type="text" />
            </ConfigurationEntry>;
        }
    }
}

function AppRow({appName}: {appName: string}) {
    return <Flex gap="8px" my="auto">
        <Box my="auto"><SafeLogo name={appName} type={"APPLICATION"} size={"18px"} /></Box>
        <Text my="auto">{appName}</Text>
    </Flex>
}

function ProviderRow({providerTitle}: {providerTitle: string}): React.ReactNode {
   return <Flex gap="8px" my="auto">
       <Box my="auto"><ProviderLogo providerId={providerTitle} size={22}/></Box>
       <Text my="auto"><ProviderTitle providerId={providerTitle}/></Text>
   </Flex>
}

function ConfigurationEntry({entry, children}: {entry: ConfigurationEntry; children: React.ReactNode}): React.ReactNode {
    return <Box mt="12px" borderTop="1px solid var(--borderColor)" pt="12px" ml="24px">
        <b style={{marginBottom: "8px"}}>
            {entry.title}
        </b>
        <Box mt="-10px" style={{color: "var(--textSecondary)"}}>
            <Markdown>
                {entry.description}
            </Markdown>
        </Box>
        {children}
    </Box>
}

const PolicyAPI = new class {
    baseContext = "/api/projects/v2/policies";

    retrievePolicies(request: RetrievePoliciesRequest): APICallParameters<RetrievePoliciesRequest, Record<string, Policy>> {
        return apiRetrieve(request, this.baseContext);
    }

    updatePolicies(request: PoliciesUpdateRequest): APICallParameters<PoliciesUpdateRequest, void> {
        return apiUpdate(request, this.baseContext, "");
    }
}();

function ProjectPolicies() {
    const projectId = useProjectId();
    const [schemas, setSchemas] = useState<Record<string, Policy>>({})
    React.useEffect(() => {
        if (projectId) {
            callAPI(PolicyAPI.retrievePolicies({projectId})).then(setSchemas);
        }
    }, [projectId]);

    return <PolicySchemas schemas={schemas} />;
}


interface ChangeProjectTitleProps {
    projectId: string;
    projectTitle: string;
    onSuccess: () => void;
}

export function ChangeProjectTitle(props: ChangeProjectTitleProps): React.ReactNode {
    const newProjectTitle = React.useRef<HTMLInputElement>(null);
    const [, invokeCommand] = useCloudCommand();
    const [saveDisabled, setSaveDisabled] = React.useState<boolean>(true);

    const [canRename, setCanRename] = useCloudAPI<AllowSubProjectsRenamingResponse, AllowSubProjectsRenamingRequest>(
        {noop: true},
        {allowed: false}
    );

    const project = useProject();

    useEffect(() => {
        setCanRename(getRenamingStatus({projectId: props.projectId}))
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
                    sendFailureNotification("Project name cannot be empty");
                    return;
                }
                if (titleValue.trim().length != titleValue.length) {
                    sendFailureNotification("Project name cannot end or start with whitespace.");
                    return;
                }

                const success = await invokeCommand(ProjectAPI.renameProject(bulkRequestOf({
                    id: props.projectId,
                    newTitle: titleValue
                }))) !== null;

                if (success) {
                    props.onSuccess();
                    sendSuccessNotification("Project renamed successfully");
                } else {
                    sendFailureNotification("Renaming of project failed");
                }
            }}>
                <label>Project title</label>
                <div>
                    <Flex gap="12px" alignItems="flex-start">
                        <Box flexGrow={1}>
                            <Input
                                required
                                width="100%"
                                type="text"
                                inputRef={newProjectTitle}
                                placeholder="New project title"
                                autoComplete="off"
                                onChange={() => {
                                    setSaveDisabled(newProjectTitle.current?.value === props.projectTitle);
                                }}
                                disabled={!canRename.data.allowed}
                            />
                        </Box>
                        <Button type="submit" height="42px" width="160px" disabled={saveDisabled}>
                            Save project title
                        </Button>
                    </Flex>
                </div>
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

function SubprojectSettings(props: AllowRenamingProps): React.ReactNode {
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

    return props.projectRole === OldProjectRole.USER ? null : <Box mt="8px" flexGrow={1}>
        <Label>
            <Checkbox
                size={24}
                checked={allowRenaming.data.allowed}
                onClick={() => toggleAndSet()}
                onChange={() => undefined}
            />
            Allow subprojects to rename
        </Label>
    </Box>;
}

interface LeaveProjectProps {
    projectRole: OldProjectRole;
    projectId: string;
    projectTitle: string;
    onSuccess: () => void;
}

export function LeaveProject(props: LeaveProjectProps): React.ReactNode {
    const description = <>
        <div>If you leave the project:</div>
        <ul>
            <li>All files and compute resources owned by the project become inaccessible to you</li>
            <li>None of your files in the project will be deleted</li>
            <li>Project administrators can recover files from your personal directory in the project</li>
        </ul>
        {props.projectRole !== OldProjectRole.PI ? null : <b>
            You must transfer the principal investigator role to another member before leaving the project.
        </b>}
    </>;

    return <SettingsAction
        title="Leave project"
        description={description}
        action={<Button
            color="errorMain"
            disabled={props.projectRole === OldProjectRole.PI}
            onClick={() => {
                addStandardDialog({
                    title: "Leave project?",
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
                    confirmButtonColor: "errorMain",
                    cancelButtonColor: "primaryMain",
                    addToFront: true
                });
            }}
        >
            Leave project
        </Button>}
    />;
}

export function UpdateProjectLogo(): React.ReactNode {
    const projectId = useProjectId() ?? "";
    const [, setLogoCacheBust] = useState("" + Date.now());

    if (!projectId) return null;
    return <React.Fragment key={"UpdateLogo"}>
        <label style={{width: "fit-content"}}>
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
                            sendFailureNotification("File exceeds 512KB. Not allowed.");
                        } else {
                            if (await uploadProjectLogo({file, projectId})) {
                                setLogoCacheBust("" + Date.now());
                                sendSuccessNotification("Logo changed, refresh to see changes");
                            }
                        }
                        dialogStore.success();
                    }
                }}
            />
        </label>

        <ProjectLogo projectId={projectId} size={"128px"} />
    </React.Fragment>
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
    onSubmit: (c: Grants.UserCriteria, projectId: string) => any,
    onRemove: (idx: number, projectId: string) => any,
    criteria: Grants.UserCriteria[],
    projectId: string,
    showSubprojects: boolean;
    isExclusion: boolean;
}> = props => {
    const [showRequestFromEditor, setShowRequestFromEditor] = useState<boolean>(false);
    return <div className={CriteriaEditorClass}>
        {!props.showSubprojects ? null : <div className="criteria-row">
            <div className="criteria-row-copy">
                <strong>Subprojects</strong>
                <div className="criteria-constraint">All subprojects</div>
            </div>
        </div>}

        {!props.showSubprojects && props.criteria.length === 0 && !showRequestFromEditor ?
            <div className="criteria-row">
                <div className="criteria-empty">{props.isExclusion ? "No exclusions" : "No sources configured"}</div>
            </div> : null}

        {props.criteria.map((criterion, idx) => <div className="criteria-row" key={keyFromCriteria(criterion)}>
            <div className="criteria-row-copy">
                <strong>{userCriteriaTypePrettifier(criterion.type)}</strong>
                <div className="criteria-constraint">{userCriteriaConstraint(criterion)}</div>
            </div>
            <IconButton
                tooltip="Remove source"
                icon="heroTrash"
                color="errorMain"
                onClick={() => props.onRemove(idx, props.projectId)}
            />
        </div>)}

        {showRequestFromEditor ? <UserCriteriaRowEditor
            onSubmit={(criterion) => {
                props.onSubmit(criterion, props.projectId);
                setShowRequestFromEditor(false);
            }}
            onCancel={() => setShowRequestFromEditor(false)}
            allowAnyone={!props.isExclusion && props.criteria.find(it => it.type === "anyone") === undefined}
            allowWayf={!props.isExclusion}
        /> : <Button fullWidth mt={12} type="button" onClick={() => setShowRequestFromEditor(true)}>
            {props.isExclusion ? "Add exclusion" : "Add application source"}
        </Button>}
    </div>;
};


export const UserCriteriaEditorReadOnly: React.FunctionComponent<{
    criteria: Grants.UserCriteria[],
    projectId: string,
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
                {props.criteria.length === 0 && !showRequestFromEditor ? <>
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
                    </TableRow>
                )}
            </tbody>
        </Table>
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
                    sendFailureNotification("This does not look like a valid email domain. Try again.");
                    return;
                }
                if (inputRef.current!.value.indexOf("@") !== -1) {
                    sendFailureNotification("Only the domain should be added. Example: 'sdu.dk'.");
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
                    sendFailureNotification("You must select a WAYF organization");
                    return;
                }
                props.onSubmit({type: "wayf", org: selectedWayfOrg});
                break;
        }
    }, [props.onSubmit, type, selectedWayfOrg]);

    const options: SimpleRichItem[] = [];
    if (props.allowAnyone) {
        options.push({key: "anyone", value: "Anyone"});
    }

    options.push({key: "email", value: "Email"});

    if (props.allowWayf) {
        options.push({key: "wayf", value: "WAYF"});
    }

    return <div className="criteria-row-editor">
        <div className="criteria-editor-type">
            <SimpleRichSelect
                fullWidth
                searchable={false}
                items={options}
                selected={options.find(option => option.key === type.type)}
                onSelect={item => setType({type: item.key} as Pick<Grants.UserCriteria, "type">)}
            />
        </div>
        <div className="criteria-editor-value">
            {type.type === "anyone" ? <Text color="textSecondary">Allow anyone to apply</Text> : null}
            {type.type !== "email" ? null : <Input inputRef={inputRef} placeholder="Email domain" />}
            {type.type !== "wayf" ? null : <DataList
                options={wayfIdpsPairs}
                onSelect={(item) => setSelectedWayfOrg(item)}
                placeholder="Type to search..."
            />}
        </div>
        <div className="criteria-editor-actions">
            <ConfirmCancelButtons onConfirm={onClick} onCancel={props.onCancel} />
        </div>
    </div>;
}

function userCriteriaConstraint(criterion: Grants.UserCriteria): string {
    switch (criterion.type) {
        case "wayf":
            return criterion.org;
        case "email":
            return criterion.domain;
        case "anyone":
            return "No constraint";
    }
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

                    sendFailureNotification(message);
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
