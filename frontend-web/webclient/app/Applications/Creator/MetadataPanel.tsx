// Application metadata panel and widget drawer
// =====================================================================================================================
// When no parameter is selected, the properties island shows the application metadata panel. The
// panel contains every metadata section from the root design, with managed-only fields hidden for
// custom applications instead of showing disabled placeholders.
//
// The panel also contains the widget drawer at the bottom. The drawer groups widgets into basic
// values and UCloud resources. Clicking an item appends a new parameter, selects it, and the
// editor panel immediately shows the new row's settings.
//
// Custom-only metadata (provider, category, group, flavor, publication) is not part of the A2 YAML.
// It lives on the draft as customMeta and is sent as separate request fields at save time. Custom
// applications derive the name from the group and flavor; the name field is hidden unless the user
// set a name by hand in the YAML view.

import * as React from "react";
import {useState} from "react";
import {useRef} from "react";
import {Box, Button, Input, Label, Select, Text, TextArea} from "@/ui-components";
import {IconButton} from "@/ui-components/IconButton";
import Icon, {IconName} from "@/ui-components/Icon";
import {TooltipV2} from "@/ui-components/Tooltip";
import {injectStyle} from "@/Unstyled";
import {A2Yaml, A2Software, A2Features, A2SshMode, A2Inference, A2ApplicationToLoad} from "@/Applications/Creator/A2";
import {CreatorDraft, CreatorCustomMeta, creatorIsCustom, creatorIsEditableName, creatorIsEditableVersion} from "@/Applications/Creator/Draft";
import {PanelSection, ToggleRow, InfoDot} from "@/Applications/Creator/ParameterPanelShared";
import {WIDGET_DRAWER_ITEMS, WidgetDrawerGroup} from "@/Applications/Creator/WidgetDefaults";
import type {
    AppCatalogCustomCategory,
    AppCatalogCustomGroup,
    AppEditorCustomEligibilityResponse,
} from "@/Applications/AppStoreApi";
import {ServiceProviderSelector} from "@/Applications/ApiTokens/Add";
import {Flex} from "@/ui-components";
import {ProviderLogo} from "@/Providers/ProviderLogo";
import {ProviderTitle} from "@/Providers/ProviderTitle";
import {RichSelectProps} from "@/ui-components/RichSelect";
import {MandatoryField} from "@/UtilityComponents";
import {dialogStore} from "@/Dialog/DialogStore";
import {fileSelectorModalStyle, slimModalStyle} from "@/Utilities/ModalUtilities";
import {callAPI} from "@/Authentication/DataHook";
import * as AppStore from "@/Applications/AppStoreApi";
import {fetchAll} from "@/Utilities/PageUtilities";
import {doNothing, extractErrorMessage, isLikelyMac, stopPropagation} from "@/UtilityFunctions";
import {FieldGroup, FieldRow} from "@/Applications/Jobs/Widgets";
import {FORM_NAVIGATION_SELECTOR, KeyboardNavigation, SubmitShortcut} from "@/Applications/KeyboardNavigation";
import {sendFailureNotification} from "@/Notifications";
import * as Heading from "@/ui-components/Heading";
import {Divider} from "@/ui-components";
import {useGlobal} from "@/Utilities/ReduxHooks";
import {LineCappedMarkdown} from "@/ui-components/Markdown";
import ContainerRepositoryBrowse from "@/ContainerRepositories/Browse";
import {customAppsWorkspaceAdmin} from "@/Applications/AppStoreApi";

export interface MetadataPanelProps {
    draft: CreatorDraft;
    readOnly?: boolean;
    onNameChange: (name: string) => void;
    onVersionChange: (version: string) => void;
    onUpdateMetadata: (patch: Partial<Pick<A2Yaml, "title" | "description" | "license" | "documentation" | "invocation">>) => void;
    onUpdateSoftware: (software: A2Software) => void;
    onUpdateFeatures: (features: A2Yaml["features"]) => void;
    onUpdateWeb: (web: A2Yaml["web"]) => void;
    onUpdateVnc: (vnc: A2Yaml["vnc"]) => void;
    onUpdateSsh: (ssh: A2Yaml["ssh"]) => void;
    onUpdateInference: (inference: A2Yaml["inference"]) => void;
    onUpdateModules: (modules: A2Yaml["modules"]) => void;
    onUpdateUcx: (ucx: A2Yaml["ucx"]) => void;
    onUpdateExtensions: (extensions: string[]) => void;
    onUpdateEnvironment: (environment: Record<string, string>) => void;
    onUpdateSbatch: (sbatch: Record<string, string>) => void;
    onUpdateCustomMeta: (patch: Partial<CreatorCustomMeta>) => void;
    onAddParameter: (type: import("@/Applications/Creator/WidgetDefaults").A2WidgetType) => void;
    customEligibility?: AppEditorCustomEligibilityResponse | null;
    customGroups?: AppCatalogCustomGroup[];
    customCategories?: AppCatalogCustomCategory[];
    refreshPlacement: () => Promise<void>;
    onInlineCreatedGroup?: (group: {id: number; title: string; description: string} | null) => void;
}

export function MetadataPanel(props: MetadataPanelProps): React.ReactNode {
    const {draft} = props;
    const {application, context} = draft;
    const isCustom = creatorIsCustom(context);
    const editableName = creatorIsEditableName(context);
    const editableVersion = creatorIsEditableVersion(context);
    const readOnly = props.readOnly === true;
    const showNameField = !isCustom || draft.nameManuallySet;

    return (
        <div className={readOnly ? MetadataReadOnlyClass : undefined}>
            {readOnly ? (
                <Box px="12px" py="8px" mb="4px" background={"color-mix(in srgb, var(--errorMain) 12%, transparent)"} borderRadius={"6px"}>
                    <Text fontSize={12} color="errorMain">
                        YAML source is invalid. Fix the source to re-enable visual editing.
                    </Text>
                </Box>
            ) : null}
            <PanelSection title="Metadata" id="creator-section-metadata" shortcut="M">
                {isCustom ? (
                    <GroupFlavorSection
                        draft={draft}
                        showNameField={showNameField}
                        editableName={editableName}
                        onNameChange={props.onNameChange}
                        onVersionChange={props.onVersionChange}
                        onUpdateCustomMeta={props.onUpdateCustomMeta}
                        groups={props.customGroups}
                        refreshPlacement={props.refreshPlacement}
                        onInlineCreated={props.onInlineCreatedGroup}
                    />
                ) : (
                    <IdentitySection
                        application={application}
                        editableName={editableName}
                        editableVersion={editableVersion}
                        namePlaceholder={context.operation === "fork" ? `${context.existingName ?? "application"}-fork` : undefined}
                        versionPlaceholder={context.operation === "fork" ? "1.0" : undefined}
                        onNameChange={props.onNameChange}
                        onVersionChange={props.onVersionChange}
                    />
                )}
                {!isCustom ? (
                    <PresentationSection
                        application={application}
                        onUpdateMetadata={props.onUpdateMetadata}
                    />
                ) : null}
                {isCustom ? (
                    <CustomFieldsSection
                        draft={draft}
                        onUpdateCustomMeta={props.onUpdateCustomMeta}
                        eligibility={props.customEligibility}
                        categories={props.customCategories}
                        refreshPlacement={props.refreshPlacement}
                    />
                ) : null}
            </PanelSection>
            {isCustom ? null : (
                <ManagedFieldsSection
                    application={application}
                    onUpdateModules={props.onUpdateModules}
                    onUpdateUcx={props.onUpdateUcx}
                    onUpdateExtensions={props.onUpdateExtensions}
                />
            )}
            <SoftwareSection
                application={application}
                isCustom={isCustom}
                customProvider={draft.customMeta?.provider}
                onUpdateSoftware={props.onUpdateSoftware}
            />
            <RuntimeFeaturesSection
                application={application}
                isCustom={isCustom}
                onUpdateFeatures={props.onUpdateFeatures}
            />
            <ConnectivitySection
                application={application}
                onUpdateWeb={props.onUpdateWeb}
                onUpdateVnc={props.onUpdateVnc}
                onUpdateSsh={props.onUpdateSsh}
                onUpdateInference={props.onUpdateInference}
            />
            <EnvironmentAndSchedulerSection
                application={application}
                onUpdateEnvironment={props.onUpdateEnvironment}
                onUpdateSbatch={props.onUpdateSbatch}
            />
            <WidgetDrawerSection onAddParameter={props.onAddParameter} />
        </div>
    );
}

const MetadataReadOnlyClass = injectStyle("creator-metadata-readonly", k => `
    ${k} {
        pointer-events: none;
        opacity: 0.85;
    }
`);

// Identity: name and version. Rendered inside the "Metadata" section.
// -------------------------------------------------------------------------------------------------------------------

function IdentitySection(props: {
    application: A2Yaml;
    editableName: boolean;
    editableVersion: boolean;
    namePlaceholder?: string;
    versionPlaceholder?: string;
    onNameChange: (name: string) => void;
    onVersionChange: (version: string) => void;
}): React.ReactNode {
    return (
        <>
            <Label className="panel-field">
                <span className="panel-field-label">Name<MandatoryField /></span>
                <Input
                    className={PanelInputClass}
                    value={props.application.name}
                    onChange={e => props.onNameChange(e.target.value)}
                    disabled={!props.editableName}
                    placeholder={props.namePlaceholder ?? "application-name"}
                    data-creator-field="name"
                />
            </Label>
            <Label className="panel-field">
                <span className="panel-field-label">Version<MandatoryField /></span>
                <Input
                    className={PanelInputClass}
                    value={props.application.version}
                    onChange={e => props.onVersionChange(e.target.value)}
                    disabled={!props.editableVersion}
                    placeholder={props.versionPlaceholder ?? "1.0.0"}
                    data-creator-field="version"
                />
            </Label>
        </>
    );
}

function VersionField(props: {
    application: A2Yaml;
    editableVersion: boolean;
    versionPlaceholder?: string;
    onVersionChange: (version: string) => void;
}): React.ReactNode {
    return (
        <Label className="panel-field">
            <span className="panel-field-label">Version<MandatoryField /></span>
            <Input
                className={PanelInputClass}
                value={props.application.version}
                onChange={e => props.onVersionChange(e.target.value)}
                disabled={!props.editableVersion}
                placeholder={props.versionPlaceholder ?? "1.0.0"}
                data-creator-field="version"
            />
        </Label>
    );
}

// Presentation: title, description, license
// -------------------------------------------------------------------------------------------------------------------
// Custom applications hide this section. Their presentation is derived from the group and flavor
// (see draftCustomDerivedPresentation); showing editable fields would suggest control the backend
// discards.

function PresentationSection(props: {
    application: A2Yaml;
    onUpdateMetadata: (patch: Partial<Pick<A2Yaml, "title" | "description" | "license" | "documentation">>) => void;
}): React.ReactNode {
    const {application} = props;
    return (
        <PanelSection title="Presentation">
            <Label className="panel-field">
                <span className="panel-field-label">Title</span>
                <Input
                    className={PanelInputClass}
                    value={application.title ?? ""}
                    onChange={e => props.onUpdateMetadata({title: e.target.value})}
                    placeholder="My application"
                    data-creator-field="title"
                />
            </Label>
            <Label className="panel-field">
                <span className="panel-field-label">Description</span>
                <TextArea
                    className={PanelInputClass}
                    rows={3}
                    value={application.description ?? ""}
                    onChange={e => props.onUpdateMetadata({description: e.target.value})}
                    placeholder="A short description shown to users."
                    data-creator-field="description"
                />
            </Label>
            <Label className="panel-field">
                <span className="panel-field-label">License</span>
                <Input
                    className={PanelInputClass}
                    value={application.license ?? ""}
                    onChange={e => props.onUpdateMetadata({license: e.target.value})}
                    placeholder="Apache-2.0"
                    data-creator-field="license"
                />
            </Label>
            <Label className="panel-field">
                <span className="panel-field-label">Documentation URL</span>
                <Input
                    className={PanelInputClass}
                    value={application.documentation ?? ""}
                    onChange={e => props.onUpdateMetadata({documentation: e.target.value})}
                    placeholder="https://example.org/docs"
                />
            </Label>
        </PanelSection>
    );
}

// Software: kind selector (managed) or container image (custom)
// -------------------------------------------------------------------------------------------------------------------

function SoftwareSection(props: {
    application: A2Yaml;
    isCustom: boolean;
    customProvider?: string;
    onUpdateSoftware: (software: A2Software) => void;
}): React.ReactNode {
    const {application, isCustom} = props;
    const software = application.software;

    if (isCustom) {
        const image = software.type === "Container" ? software.image : "";
        return (
            <PanelSection title="Software" id="creator-section-software" shortcut="C">
                <Label className="panel-field">
                    <span className="panel-field-label">Container image<MandatoryField /></span>
                    <ContainerImageSelector
                        image={image}
                        provider={props.customProvider}
                        onSelect={imageUrl => props.onUpdateSoftware({type: "Container", image: imageUrl})}
                    />
                </Label>
            </PanelSection>
        );
    }

    return (
        <PanelSection title="Software" id="creator-section-software" shortcut="C">
            <Label className="panel-field">
                <span className="panel-field-label">Kind</span>
                <Select
                    value={software.type}
                    onChange={e => {
                        const kind = e.target.value as A2Software["type"];
                        props.onUpdateSoftware(softwareForKind(kind, software));
                    }}
                >
                    <option value="Container">Container</option>
                    <option value="Native">Native</option>
                    <option value="VirtualMachine">Virtual machine</option>
                    <option value="UCX">UCX</option>
                </Select>
            </Label>
            {renderSoftwareFields(software, props.onUpdateSoftware)}
        </PanelSection>
    );
}

function ContainerImageSelector(props: {
    image: string;
    provider?: string;
    onSelect: (imageUrl: string) => void;
}): React.ReactNode {
    const openSelector = () => {
        dialogStore.addDialog(
            <ContainerRepositoryBrowse
                opts={{
                    isModal: true,
                    additionalFilters: props.provider ? {filterProvider: props.provider} : undefined,
                }}
                imageSelection={{
                    text: "Use",
                    onSelect: (_image, imageUrl) => {
                        props.onSelect(imageUrl);
                        dialogStore.success();
                    },
                }}
            />,
            doNothing,
            true,
            fileSelectorModalStyle,
        );
    };

    return (
        <button
            type="button"
            className={CategoryFieldClass}
            onClick={openSelector}
            disabled={!props.provider}
            data-navigation-field
            data-creator-field="software.image"
            title={props.image || "Select a container image"}
        >
            {props.image ? (
                <span className={ContainerImageFieldValueClass}>{props.image}</span>
            ) : (
                <span className={CategoryFieldPlaceholderClass}>Select a container image</span>
            )}
        </button>
    );
}

function renderSoftwareFields(
    software: A2Software,
    onUpdate: (software: A2Software) => void,
): React.ReactNode {
    switch (software.type) {
        case "Container":
            return (
                <Label className="panel-field">
                    <span className="panel-field-label">Container image<MandatoryField /></span>
                    <Input
                        className={PanelInputClass}
                        value={software.image}
                        onChange={e => onUpdate({type: "Container", image: e.target.value})}
                        placeholder="dreg.cloud.sdu.dk/image:tag"
                        data-creator-field="software.image"
                    />
                </Label>
            );
        case "VirtualMachine":
            return (
                <Label className="panel-field">
                    <span className="panel-field-label">VM image<MandatoryField /></span>
                    <Input
                        className={PanelInputClass}
                        value={software.image}
                        onChange={e => onUpdate({type: "VirtualMachine", image: e.target.value})}
                        placeholder="image-id"
                    />
                </Label>
            );
        case "UCX":
            return (
                <Label className="panel-field">
                    <span className="panel-field-label">UCX image<MandatoryField /></span>
                    <Input
                        className={PanelInputClass}
                        value={software.image}
                        onChange={e => onUpdate({type: "UCX", image: e.target.value})}
                        placeholder="image-id"
                    />
                </Label>
            );
        case "Native":
            return (
                <NativeLoadEditor software={software} />
            );
    }
}

function NativeLoadEditor(props: {
    software: { type: "Native"; load: A2ApplicationToLoad[] };
}): React.ReactNode {
    const count = props.software.load.length;
    return (
        <Label className="panel-field">
            <span className="panel-field-label">Applications to load</span>
            <Text fontSize={12} color="textSecondary">
                {count === 0
                    ? "No applications. Edit the load list in the YAML view."
                    : `${count} application${count === 1 ? "" : "s"} loaded. Edit the load list in the YAML view.`}
            </Text>
        </Label>
    );
}

function softwareForKind(kind: A2Software["type"], current: A2Software): A2Software {
    if (kind === "Native") return {type: "Native", load: []};
    const image = current.type === "Container" || current.type === "VirtualMachine" || current.type === "UCX"
        ? current.image
        : "";
    if (kind === "Container") return {type: "Container", image};
    if (kind === "VirtualMachine") return {type: "VirtualMachine", image};
    return {type: "UCX", image};
}

// Features
// -------------------------------------------------------------------------------------------------------------------

function RuntimeFeaturesSection(props: {
    application: A2Yaml;
    isCustom: boolean;
    onUpdateFeatures: (features: A2Yaml["features"]) => void;
}): React.ReactNode {
    const features = props.application.features ?? defaultFeatures();
    const toggle = (key: keyof A2Features) => {
        const updated = {...features, [key]: !features[key]} as A2Features;
        props.onUpdateFeatures(updated);
    };
    return (
        <PanelSection title="Features" id="creator-section-features" shortcut="F">
            <FeatureToggle label="Folders" value={features.folders ?? false} onChange={() => toggle("folders")} id="feature-folders" />
            <FeatureToggle label="Links" value={features.links ?? false} onChange={() => toggle("links")} id="feature-links" />
            <FeatureToggle label="Job linking" value={features.jobLinking ?? false} onChange={() => toggle("jobLinking")} id="feature-jobLinking" />
            <FeatureToggle label="Public IP addresses" value={features.ipAddresses ?? false} onChange={() => toggle("ipAddresses")} id="feature-ipAddresses" />
            <FeatureToggle label="Multi-node jobs" value={features.multiNode} onChange={() => toggle("multiNode")} />
            {!props.isCustom ? (
                <FeatureToggle label="Audit logs" value={features.jobAuditLog ?? false} onChange={() => toggle("jobAuditLog")} />
            ) : null}
        </PanelSection>
    );
}

function FeatureToggle(props: {label: string; value: boolean; onChange: () => void; id?: string}): React.ReactNode {
    return (
        <ToggleRow label={props.label} checked={props.value} onChange={props.onChange} id={props.id} />
    );
}

function defaultFeatures(): A2Features {
    return {multiNode: false, links: false, ipAddresses: false, folders: false, jobLinking: false, jobAuditLog: false};
}

// Connectivity: web, vnc, ssh, inference
// -------------------------------------------------------------------------------------------------------------------

function ConnectivitySection(props: {
    application: A2Yaml;
    onUpdateWeb: (web: A2Yaml["web"]) => void;
    onUpdateVnc: (vnc: A2Yaml["vnc"]) => void;
    onUpdateSsh: (ssh: A2Yaml["ssh"]) => void;
    onUpdateInference: (inference: A2Yaml["inference"]) => void;
}): React.ReactNode {
    return (
        <PanelSection title="Connectivity" id="creator-section-connectivity" shortcut="N">
            <WebControl application={props.application} onUpdate={props.onUpdateWeb} />
            <div className={ConnectivityDividerClass} />
            <VncControl application={props.application} onUpdate={props.onUpdateVnc} />
            <div className={ConnectivityDividerClass} />
            <SshControl application={props.application} onUpdate={props.onUpdateSsh} />
            <div className={ConnectivityDividerClass} />
            <InferenceControl application={props.application} onUpdate={props.onUpdateInference} />
        </PanelSection>
    );
}

const ConnectivityDividerClass = injectStyle("creator-connectivity-divider", k => `
    ${k} {
        height: 1px;
        background: var(--borderColor);
        margin: 4px 0;
    }
`);

function WebControl(props: {
    application: A2Yaml;
    onUpdate: (web: A2Yaml["web"]) => void;
}): React.ReactNode {
    const web = props.application.web ?? {enabled: false, port: null};
    const enabled = web.enabled;
    return (
        <>
            <FeatureToggle
                label="Web"
                value={enabled}
                onChange={() => props.onUpdate({enabled: !enabled, port: web.port ?? null})}
            />
            {enabled ? (
                <Label className="panel-field">
                    <span className="panel-field-label">Port</span>
                    <Input
                        className={PanelInputClass}
                        type="number"
                        value={web.port ?? ""}
                        onChange={e => props.onUpdate({enabled, port: e.target.value === "" ? null : parseInt(e.target.value, 10)})}
                        placeholder="8080"
                    />
                </Label>
            ) : null}
        </>
    );
}

function VncControl(props: {
    application: A2Yaml;
    onUpdate: (vnc: A2Yaml["vnc"]) => void;
}): React.ReactNode {
    const vnc = props.application.vnc ?? {enabled: false, port: null, password: null};
    const enabled = vnc.enabled;
    return (
        <>
            <FeatureToggle
                label="VNC"
                value={enabled}
                onChange={() => props.onUpdate({enabled: !enabled, port: vnc.port ?? null, password: vnc.password ?? null})}
            />
            {enabled ? (
                <>
                    <Label className="panel-field">
                        <span className="panel-field-label">Port</span>
                        <Input
                            className={PanelInputClass}
                            type="number"
                            value={vnc.port ?? ""}
                            onChange={e => props.onUpdate({...vnc, port: e.target.value === "" ? null : parseInt(e.target.value, 10)})}
                            placeholder="5900"
                        />
                    </Label>
                    <Label className="panel-field">
                        <span className="panel-field-label">Password</span>
                        <Input
                            className={PanelInputClass}
                            value={vnc.password ?? ""}
                            onChange={e => props.onUpdate({...vnc, password: e.target.value})}
                            placeholder="Optional"
                        />
                    </Label>
                </>
            ) : null}
        </>
    );
}

function SshControl(props: {
    application: A2Yaml;
    onUpdate: (ssh: A2Yaml["ssh"]) => void;
}): React.ReactNode {
    const ssh = props.application.ssh ?? {mode: "Optional"};
    return (
        <div id="feature-ssh" className={SshHighlightWrapperClass}>
            <Label className="panel-field">
                <span className="panel-field-label">
                    SSH
                    <InfoDot tooltip={SshTooltipContent} />
                </span>
                <Select
                    value={ssh.mode}
                    onChange={e => props.onUpdate({mode: e.target.value as A2SshMode})}
                >
                    <option value="Optional">Optional</option>
                    <option value="Mandatory">Mandatory</option>
                    <option value="Disabled">Disabled</option>
                </Select>
            </Label>
        </div>
    );
}

const SshTooltipContent = (
    <>
        Connect with <code>{"ssh ucloud@<host> -p <port>"}</code>. UCloud forwards an
        external port to port 22 on the job. Log in as the user <code>ucloud</code> (uid 11042);
        your uploaded public keys are injected automatically.
    </>
);

function InferenceControl(props: {
    application: A2Yaml;
    onUpdate: (inference: A2Yaml["inference"]) => void;
}): React.ReactNode {
    const inference = props.application.inference ?? {mode: "None"};
    return (
        <Label className="panel-field">
            <span className="panel-field-label">
                Inference
                <InfoDot tooltip={InferenceTooltipContent} />
            </span>
            <Select
                value={inference.mode}
                onChange={e => props.onUpdate({mode: e.target.value as A2Inference["mode"]})}
            >
                <option value="None">None</option>
                <option value="Optional">Optional</option>
                <option value="Mandatory">Mandatory</option>
            </Select>
        </Label>
    );
}

const InferenceTooltipContent = (
    <>
        When enabled, UCloud creates a short-lived API token for the inference API and injects it
        into the job. Requires an active allocation. The container receives:
        <ul>
            <li><code>UCLOUD_INFERENCE_SERVERS</code>: JSON array of <code>{"{server, token}"}</code></li>
            <li><code>UCLOUD_INFERENCE_SERVER_BASE_0</code>: API base URL of the first server</li>
            <li><code>UCLOUD_INFERENCE_SERVER_TOKEN_0</code>: Bearer token for the first server</li>
        </ul>
        Use the token as <code>Authorization: Bearer $UCLOUD_INFERENCE_SERVER_TOKEN_0</code>. Indexes
        increment per server.
    </>
);

// Environment and scheduler values: ordered key-value rows
// -------------------------------------------------------------------------------------------------------------------

function EnvironmentAndSchedulerSection(props: {
    application: A2Yaml;
    onUpdateEnvironment: (environment: Record<string, string>) => void;
    onUpdateSbatch: (sbatch: Record<string, string>) => void;
}): React.ReactNode {
    const isNative = props.application.software.type === "Native";
    return (
        <>
            <KeyValueEditor
                title="Environment"
                values={props.application.environment}
                keyPlaceholder="VARIABLE_NAME"
                valuePlaceholder="value"
                onUpdate={props.onUpdateEnvironment}
                collapsedByDefault
            />
            {isNative ? (
                <KeyValueEditor
                    title="Scheduler values"
                    values={props.application.sbatch}
                    keyPlaceholder="--partition"
                    valuePlaceholder="value"
                    onUpdate={props.onUpdateSbatch}
                    collapsedByDefault
                />
            ) : null}
        </>
    );
}

function KeyValueEditor(props: {
    title: string;
    values: Record<string, string>;
    keyPlaceholder: string;
    valuePlaceholder: string;
    onUpdate: (values: Record<string, string>) => void;
    collapsedByDefault?: boolean;
}): React.ReactNode {
    const nextRowId = useRef(0);
    const [rows, setRows] = useState(() => Object.entries(props.values).map(([key, value]) => ({
        id: nextRowId.current++,
        key,
        value,
    })));
    const [error, setError] = useState<string | null>(null);

    const externalKey = JSON.stringify(props.values);
    const lastExternal = useRef(externalKey);
    React.useLayoutEffect(() => {
        if (externalKey === lastExternal.current) return;
        lastExternal.current = externalKey;
        const externalValues = JSON.parse(externalKey) as Record<string, string>;
        setRows(Object.entries(externalValues).map(([key, value]) => ({
            id: nextRowId.current++,
            key,
            value,
        })));
        setError(null);
    }, [externalKey]);

    const applyRows = (next: {id: number; key: string; value: string}[]) => {
        setRows(next);
        const entries: [string, string][] = next.map(row => [row.key, row.value]);
        const {result, error: validationError} = keyValueFromEntries(entries);
        setError(validationError);
        if (!validationError) {
            lastExternal.current = JSON.stringify(result);
            props.onUpdate(result);
        }
    };

    const commitRow = (index: number, key: string, value: string) => {
        const next = [...rows];
        next[index] = {...next[index], key, value};
        applyRows(next);
    };

    const addRow = (event: React.MouseEvent<HTMLButtonElement>) => {
        const section = event.currentTarget.closest<HTMLElement>("[data-panel-section]");
        applyRows([...rows, {id: nextRowId.current++, key: "", value: ""}]);
        window.requestAnimationFrame(() => {
            const addedRows = section?.querySelectorAll<HTMLElement>("[data-key-value-row]");
            const addedRow = addedRows?.[addedRows.length - 1];
            addedRow?.querySelector<HTMLElement>(FORM_NAVIGATION_SELECTOR)?.focus();
        });
    };

    const removeRow = (index: number) => {
        applyRows(rows.filter((_, i) => i !== index));
    };

    return (
        <PanelSection title={props.title} collapsedByDefault={props.collapsedByDefault}>
            {rows.length === 0 ? (
                <Text fontSize={12} color="textSecondary">No {props.title.toLowerCase()} values.</Text>
            ) : null}
            {rows.map((entry, index) => (
                <KeyValueRow
                    key={entry.id}
                    rowKey={entry.key}
                    rowValue={entry.value}
                    keyPlaceholder={props.keyPlaceholder}
                    valuePlaceholder={props.valuePlaceholder}
                    onCommit={(newKey, newValue) => commitRow(index, newKey, newValue)}
                    onRemove={() => removeRow(index)}
                />
            ))}
            <Button
                type="button"
                color="secondaryMain"
                className={AddValueButtonClass}
                onClick={addRow}
                mt="4px"
                data-add-value
                data-navigation-field
            >
                <Icon name="heroPlus" mr={6} size={14} />
                Add value
            </Button>
            {error ? <Text fontSize={12} color="errorMain" mt="4px">{error}</Text> : null}
        </PanelSection>
    );
}

function KeyValueRow(props: {
    rowKey: string;
    rowValue: string;
    keyPlaceholder: string;
    valuePlaceholder: string;
    onCommit: (key: string, value: string) => void;
    onRemove: () => void;
}): React.ReactNode {
    const [key, setKey] = useState(props.rowKey);
    const [value, setValue] = useState(props.rowValue);
    const deleting = useRef(false);

    React.useEffect(() => { setKey(props.rowKey); }, [props.rowKey]);
    React.useEffect(() => { setValue(props.rowValue); }, [props.rowValue]);

    const commitKey = () => {
        if (!deleting.current) props.onCommit(key, value);
    };
    const commitValue = () => {
        if (!deleting.current) props.onCommit(key, value);
    };
    const onDeleteEmpty = (event: React.KeyboardEvent<HTMLInputElement>, fieldValue: string) => {
        if (event.key !== "Delete" || fieldValue !== "" || event.metaKey || event.ctrlKey || event.altKey) return;
        event.preventDefault();
        event.stopPropagation();
        const section = event.currentTarget.closest<HTMLElement>("[data-panel-section]");
        const currentRow = event.currentTarget.closest<HTMLElement>("[data-key-value-row]");
        const rows = section ? Array.from(section.querySelectorAll<HTMLElement>("[data-key-value-row]")) : [];
        const rowIndex = currentRow == null ? -1 : rows.indexOf(currentRow);
        deleting.current = true;
        props.onRemove();
        window.requestAnimationFrame(() => {
            const remainingRows = section ? Array.from(section.querySelectorAll<HTMLElement>("[data-key-value-row]")) : [];
            const nextRow = remainingRows[Math.min(Math.max(0, rowIndex), remainingRows.length - 1)];
            const nextField = nextRow?.querySelector<HTMLElement>(FORM_NAVIGATION_SELECTOR) ??
                section?.querySelector<HTMLElement>("[data-add-value]");
            nextField?.focus();
        });
    };

    return (
        <div className={KeyValueRowClass} data-key-value-row>
            <Input
                className={PanelInputClass}
                value={key}
                placeholder={props.keyPlaceholder}
                onChange={e => setKey(e.target.value)}
                onBlur={commitKey}
                onKeyDown={event => onDeleteEmpty(event, key)}
            />
            <Input
                className={PanelInputClass}
                value={value}
                placeholder={props.valuePlaceholder}
                onChange={e => setValue(e.target.value)}
                onBlur={commitValue}
                onKeyDown={event => onDeleteEmpty(event, value)}
            />
            <IconButton
                icon="heroTrash"
                tooltip="Remove value"
                color="errorMain"
                onClick={props.onRemove}
                compact
            />
        </div>
    );
}

function keyValueFromEntries(entries: [string, string][]): {result: Record<string, string>; error: string | null} {
    const result: Record<string, string> = {};
    for (const [k, v] of entries) {
        if (!k || k.trim() === "") {
            return {result: {}, error: "Key must not be empty."};
        }
        if (result[k.trim()] !== undefined) {
            return {result: {}, error: `Duplicate key: "${k.trim()}".`};
        }
        result[k.trim()] = v;
    }
    return {result, error: null};
}

// Group and flavor (custom only). Rendered inside the "Metadata" section.
// -------------------------------------------------------------------------------------------------------------------

interface ResourceOption {
    id: number;
    title: string;
    description: string;
    isCustom: boolean;
}

function closeAutocomplete(
    setOpen: React.Dispatch<React.SetStateAction<boolean>>,
    inputRef: React.RefObject<HTMLInputElement | null>,
): void {
    setOpen(false);
    window.requestAnimationFrame(() => inputRef.current?.focus());
}

function GroupFlavorSection(props: {
    draft: CreatorDraft;
    showNameField: boolean;
    editableName: boolean;
    onNameChange: (name: string) => void;
    onVersionChange: (version: string) => void;
    onUpdateCustomMeta: (patch: Partial<CreatorCustomMeta>) => void;
    groups?: AppCatalogCustomGroup[];
    refreshPlacement: () => Promise<void>;
    onInlineCreated?: (group: {id: number; title: string; description: string} | null) => void;
}): React.ReactNode {
    const {draft} = props;
    const meta = draft.customMeta;
    const [createdGroup, setCreatedGroup] = useState<{id: number; title: string; description: string} | null>(null);
    if (!meta) {
        return null;
    }

    const allGroups = props.groups ?? [];
    const selected = allGroups.find(group => String(group.id) === meta.group) ?? (
        createdGroup != null && String(createdGroup.id) === meta.group
            ? {specification: {title: createdGroup.title, description: createdGroup.description}}
            : null
    );

    return (
        <>
            {props.showNameField ? (
                <Label className="panel-field">
                    <span className="panel-field-label">Name<MandatoryField /></span>
                    <Input
                        className={PanelInputClass}
                        value={draft.application.name}
                        onChange={e => props.onNameChange(e.target.value)}
                        disabled={!props.editableName}
                        placeholder="application-name"
                        data-creator-field="name"
                    />
                </Label>
            ) : null}
            <GroupAutocompleteField
                draft={draft}
                groups={allGroups}
                createdGroup={createdGroup}
                onCreatedGroup={setCreatedGroup}
                onUpdateCustomMeta={props.onUpdateCustomMeta}
                refreshPlacement={props.refreshPlacement}
                onInlineCreated={props.onInlineCreated}
            />
            <Label className="panel-field">
                <span className="panel-field-label">Flavor</span>
                <Input
                    className={PanelInputClass}
                    value={meta.flavor}
                    onChange={e => props.onUpdateCustomMeta({flavor: e.target.value})}
                    placeholder="Default"
                    data-creator-field="custom.flavorName"
                />
            </Label>
            <VersionField
                application={draft.application}
                editableVersion={creatorIsEditableVersion(draft.context)}
                onVersionChange={props.onVersionChange}
            />
        </>
    );
}

function CustomProviderRow(props: RichSelectProps<{key: string}>): React.ReactNode {
    const height = props.dataProps == null ? "31.5px" : "38px";
    const key = props.element?.key;
    if (key == null) return null;
    return <Flex height={height} pl="8px" key={key} {...props.dataProps} onClick={props.onSelect} alignItems={"center"} gap={"8px"}>
        {!key ? <span>Select a provider</span> : <>
            <ProviderLogo providerId={key} size={24} />
            <ProviderTitle providerId={key} />
        </>}
    </Flex>;
}

function GroupAutocompleteField(props: {
    draft: CreatorDraft;
    groups: AppCatalogCustomGroup[];
    createdGroup: {id: number; title: string; description: string} | null;
    onCreatedGroup: (group: {id: number; title: string; description: string} | null) => void;
    onUpdateCustomMeta: (patch: Partial<CreatorCustomMeta>) => void;
    refreshPlacement: () => Promise<void>;
    onInlineCreated?: (group: {id: number; title: string; description: string} | null) => void;
}): React.ReactNode {
    const {draft} = props;
    const meta = draft.customMeta;
    const [query, setQuery] = useState("");
    const [open, setOpen] = useState(false);
    const [focused, setFocused] = useState(false);
    const [savingEdit, setSavingEdit] = useState(false);
    const [creating, setCreating] = useState(false);
    const [highlight, setHighlight] = useState(0);
    const [managed, setManaged] = useState<ResourceOption[] | null>(null);
    const wrapperRef = useRef<HTMLDivElement>(null);
    const dropdownRef = useRef<HTMLDivElement>(null);
    const inputRef = useRef<HTMLInputElement>(null);

    const selected = props.groups.find(group => String(group.id) === meta?.group) ?? (
        props.createdGroup != null && meta != null && String(props.createdGroup.id) === meta.group
            ? {id: props.createdGroup.id, createdAt: 0, owner: {createdBy: ""}, backedBy: undefined, specification: {title: props.createdGroup.title, description: props.createdGroup.description}}
            : null
    );

    React.useEffect(() => {
        if (!open || managed != null) return;
        let cancelled = false;
        fetchAll<AppStore.ApplicationGroup>(next => callAPI(AppStore.browseGroups({itemsPerPage: 250, next}))).then(groups => {
            if (cancelled) return;
            setManaged(groups.map(group => ({
                id: group.metadata.id,
                title: group.specification.title,
                description: group.specification.description ?? "",
                isCustom: false,
            })));
        }).catch(() => {
            if (!cancelled) setManaged([]);
        });
        return () => {
            cancelled = true;
        };
    }, [open, managed]);

    React.useEffect(() => {
        if (!open) return;
        const listener = (event: PointerEvent) => {
            if (wrapperRef.current && !wrapperRef.current.contains(event.target as Node)) setOpen(false);
        };
        document.addEventListener("pointerdown", listener);
        return () => document.removeEventListener("pointerdown", listener);
    }, [open]);

    React.useEffect(() => {
        const dropdown = dropdownRef.current;
        if (!dropdown || !open) return;
        const row = dropdown.querySelector<HTMLElement>('[data-highlighted="true"]');
        if (!row) return;
        const rowTop = row.offsetTop;
        const rowBottom = rowTop + row.offsetHeight;
        if (rowTop < dropdown.scrollTop) {
            dropdown.scrollTop = rowTop;
        } else if (rowBottom > dropdown.scrollTop + dropdown.clientHeight) {
            dropdown.scrollTop = rowBottom - dropdown.clientHeight;
        }
    }, [highlight, open]);

    const trimmed = query.trim().toLowerCase();

    const byBacked = new Map<number, number>();
    for (const group of props.groups) {
        if (group.backedBy != null) byBacked.set(group.backedBy, group.id);
    }

    const options: ResourceOption[] = [];
    const seen = new Set<number>();
    for (const option of managed ?? []) {
        seen.add(option.id);
        const backing = byBacked.get(option.id);
        if (backing != null) {
            seen.add(backing);
            options.push({...option, id: backing, isCustom: true});
        } else {
            options.push(option);
        }
    }
    for (const group of props.groups) {
        if (seen.has(group.id)) continue;
        options.push({id: group.id, title: group.specification.title, description: group.specification.description, isCustom: true});
    }

    const matches = trimmed === ""
        ? options
        : options.filter(option =>
            option.title.toLowerCase().includes(trimmed) ||
            option.description.toLowerCase().includes(trimmed));
    const canCreate = trimmed !== "" && !options.some(option => option.title.toLowerCase() === trimmed);

    const selectGroup = (group: {id: number; title: string; description: string; isCustom: boolean}) => {
        if (group.isCustom) {
            props.onUpdateCustomMeta({group: String(group.id)});
            setQuery(group.title);
            closeAutocomplete(setOpen, inputRef);
            return;
        }
        setCreating(true);
        callAPI(AppStore.createCustomGroup({kind: "Managed", id: group.id})).then(result => {
            props.onCreatedGroup({id: result.id, title: group.title, description: group.description});
            props.onInlineCreated?.({id: result.id, title: group.title, description: group.description});
            props.onUpdateCustomMeta({group: String(result.id)});
            setQuery(group.title);
            closeAutocomplete(setOpen, inputRef);
            return props.refreshPlacement();
        }).catch(error => {
            sendFailureNotification(extractErrorMessage(error as {request: XMLHttpRequest; response: any}));
        }).finally(() => {
            setCreating(false);
        });
    };

    const openCreateDialog = () => {
        const title = query.trim();
        const submit = async (name: string, description: string) => {
            setCreating(true);
            try {
                const result = await callAPI(AppStore.createCustomGroup({
                    kind: "Custom",
                    specification: {title: name, description},
                }));
                props.onCreatedGroup({id: result.id, title: name, description});
                props.onInlineCreated?.({id: result.id, title: name, description});
                props.onUpdateCustomMeta({group: String(result.id)});
                setQuery(name);
                closeAutocomplete(setOpen, inputRef);
                await props.refreshPlacement();
                dialogStore.success();
            } catch (error) {
                sendFailureNotification(extractErrorMessage(error as {request: XMLHttpRequest; response: any}));
            } finally {
                setCreating(false);
            }
        };
        dialogStore.addDialog(
            <ResourceNameDialog
                title="New application group"
                placeholder="My group"
                nameDescription="The name of the group, shown in the user-interface"
                descriptionRequired
                creating={creating}
                initialTitle={title}
                initialDescription=""
                onSubmit={submit}
            />,
            doNothing,
            true,
            slimModalStyle,
        );
    };

    const openEditDialog = () => {
        if (!selected || selected.backedBy == null) return;
        const managedId = selected.backedBy;
        const groupId = selected.id;
        const submit = async (title: string, description: string) => {
            setSavingEdit(true);
            try {
                await callAPI(AppStore.updateGroup({
                    id: managedId,
                    newTitle: title,
                    newDescription: description,
                }));
                props.onCreatedGroup({id: groupId, title, description});
                await props.refreshPlacement();
                dialogStore.success();
            } catch (error) {
                sendFailureNotification(extractErrorMessage(error as {request: XMLHttpRequest; response: any}));
            } finally {
                setSavingEdit(false);
            }
        };
        dialogStore.addDialog(
            <ResourceNameDialog
                title="Edit application group"
                placeholder="My group"
                nameDescription="The name of the group, shown in the user-interface"
                descriptionRequired
                creating={savingEdit}
                initialTitle={selected.specification.title}
                initialDescription={selected.specification.description}
                onSubmit={submit}
            />,
            doNothing,
            true,
            slimModalStyle,
        );
    };

    const onKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
        if (e.key === "Escape") {
            closeAutocomplete(setOpen, inputRef);
            return;
        }
        if (!open) {
            if (e.key === "Enter") {
                e.preventDefault();
                setHighlight(0);
                setOpen(true);
            }
            return;
        }
        const rowCount = matches.length + (canCreate ? 1 : 0);
        if (rowCount === 0) return;
        if (e.key === "ArrowDown") {
            e.preventDefault();
            setHighlight(h => (h + 1) % rowCount);
        } else if (e.key === "ArrowUp") {
            e.preventDefault();
            setHighlight(h => (h - 1 + rowCount) % rowCount);
        } else if (e.key === "Enter") {
            e.preventDefault();
            if (highlight < matches.length) {
                selectGroup(matches[highlight]);
            } else if (canCreate) {
                openCreateDialog();
            }
        }
    };

    return (
        <div ref={wrapperRef}>
            <Label className="panel-field">
                <span className="panel-field-label">Application group<MandatoryField /></span>
                <div className={GroupInputWrapperClass}>
                    <div className={GroupInputRowClass}>
                        <Input
                            inputRef={inputRef}
                            className={PanelInputClass}
                            value={query}
                            onChange={e => {
                                setQuery(e.target.value);
                                setHighlight(0);
                                setOpen(true);
                            }}
                            onFocus={() => {
                                setFocused(true);
                            }}
                            onBlur={() => setFocused(false)}
                            onKeyDown={onKeyDown}
                            role="combobox"
                            aria-autocomplete="list"
                            aria-expanded={open}
                            placeholder="Search or create an application group"
                            data-creator-field="custom.groupId"
                        />
                        {selected == null && !focused && !open && query.trim() !== "" ? (
                            <TooltipV2 tooltip="Type to search or create a group, then select one">
                                <span className={GroupWarningClass} role="img" aria-label="No group selected">
                                    <Icon name="heroExclamationTriangle" size={14} color="warningMain" />
                                </span>
                            </TooltipV2>
                        ) : null}
                        {selected != null && selected.backedBy != null && query.trim().toLowerCase() === selected.specification.title.toLowerCase() ? (
                            <IconButton
                                icon="heroPencil"
                                tooltip="Edit group"
                                onClick={openEditDialog}
                                compact
                            />
                        ) : null}
                    </div>
                    {open ? (
                        <GroupDropdown
                            dropdownRef={dropdownRef}
                            query={query}
                            highlight={highlight}
                            matches={matches}
                            canCreate={canCreate}
                            creating={creating}
                            managedLoaded={managed != null}
                            onSelect={selectGroup}
                            onCreate={openCreateDialog}
                            onHover={setHighlight}
                        />
                    ) : null}
                </div>
            </Label>
        </div>
    );
}

function ResourceNameDialog(props: {
    title: string;
    placeholder: string;
    nameDescription: string;
    descriptionRequired?: boolean;
    creating: boolean;
    initialTitle: string;
    initialDescription: string;
    onSubmit: (title: string, description: string) => Promise<void>;
}): React.ReactNode {
    const [titleError, setTitleError] = useState<string | undefined>(undefined);
    const [descriptionError, setDescriptionError] = useState<string | undefined>(undefined);

    React.useEffect(() => {
        const titleField = document.getElementById("resource-dialog-title") as HTMLInputElement | null;
        if (titleField) {
            titleField.value = props.initialTitle;
            titleField.focus();
        }
        const descriptionField = document.getElementById("resource-dialog-description") as HTMLTextAreaElement | null;
        if (descriptionField) descriptionField.value = props.initialDescription;
    }, []);

    const submit = () => {
        const titleField = document.getElementById("resource-dialog-title") as HTMLInputElement | null;
        const descriptionField = document.getElementById("resource-dialog-description") as HTMLTextAreaElement | null;
        const title = (titleField?.value ?? "").trim();
        const description = (descriptionField?.value ?? "").trim();
        if (props.creating) return;
        if (title === "") {
            setTitleError("Name cannot be blank");
            setDescriptionError(undefined);
            return;
        }
        if (props.descriptionRequired && description === "") {
            setTitleError(undefined);
            setDescriptionError("Description cannot be blank");
            return;
        }
        setTitleError(undefined);
        setDescriptionError(undefined);
        void props.onSubmit(title, description);
    };

    const onSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        submit();
    };

    const onKeyDown = (e: React.KeyboardEvent) => {
        stopPropagation(e);
        if (e.key !== "Enter" || !e.altKey) return;
        const primaryPressed = isLikelyMac ? e.metaKey : e.ctrlKey;
        if (!primaryPressed) return;
        e.preventDefault();
        submit();
    };

    return (
        <Box onKeyDown={onKeyDown}>
            <Heading.h3>{props.title}</Heading.h3>
            <form onSubmit={onSubmit}>
                <Box pb="20px">
                    <KeyboardNavigation>
                        <FieldGroup>
                            <FieldRow
                                title="Name"
                                description={props.nameDescription}
                                required
                                error={titleError}
                                control={<Input id="resource-dialog-title" width="100%" placeholder={props.placeholder} />}
                            />
                            <FieldRow
                                title="Description"
                                description="A short description shown to users."
                                required={props.descriptionRequired === true}
                                error={descriptionError}
                                control={<TextArea id="resource-dialog-description" width="100%" rows={5}
                                    placeholder="A short description shown to users." />}
                            />
                        </FieldGroup>
                    </KeyboardNavigation>
                </Box>
                <Flex justifyContent="end" px="20px" py="12px" mx="-20px" mb="-20px" background="var(--dialogToolbar)" gap="8px">
                    <Button color="errorMain" type="button" onClick={() => dialogStore.failure()}>Cancel</Button>
                    <Button color="successMain" type="submit" disabled={props.creating}>
                        {props.creating ? <Icon name="refresh" spin /> : null}
                        Create<SubmitShortcut />
                    </Button>
                </Flex>
            </form>
        </Box>
    );
}

function GroupDropdown(props: {
    dropdownRef: React.RefObject<HTMLDivElement | null>;
    query: string;
    highlight: number;
    matches: ResourceOption[];
    canCreate: boolean;
    creating: boolean;
    managedLoaded: boolean;
    createLabel?: string;
    onSelect: (option: ResourceOption) => void;
    onCreate: () => void;
    onHover: (index: number) => void;
}): React.ReactNode {
    const label = props.createLabel ?? "group";
    return (
        <div className={GroupDropdownClass} ref={props.dropdownRef}>
            {!props.managedLoaded ? (
                <div className={GroupDropdownEmptyClass}>Loading...</div>
            ) : (
                <>
                    {props.matches.map((option, index) => (
                        <button
                            key={option.id}
                            type="button"
                            className={GroupDropdownRowClass}
                            data-highlighted={index === props.highlight ? "true" : undefined}
                            disabled={props.creating}
                            onMouseDown={e => e.preventDefault()}
                            onMouseEnter={() => props.onHover(index)}
                            onClick={() => props.onSelect(option)}
                        >
                            <span className={CategoryFieldTitleClass}>{option.title}</span>
                            {option.description ? (
                                <LineCappedMarkdown width="100%" lines={1}>{option.description}</LineCappedMarkdown>
                            ) : null}
                        </button>
                    ))}
                    {props.canCreate ? (
                        <button
                            type="button"
                            className={GroupDropdownRowClass}
                            data-highlighted={props.highlight === props.matches.length ? "true" : undefined}
                            disabled={props.creating}
                            onMouseDown={e => e.preventDefault()}
                            onMouseEnter={() => props.onHover(props.matches.length)}
                            onClick={props.onCreate}
                        >
                            <Flex alignItems="center" gap="6px">
                                <Icon name="heroPlus" size={14} />
                                <span>Create {label} "{props.query.trim()}"</span>
                            </Flex>
                        </button>
                    ) : null}
                    {props.matches.length === 0 && !props.canCreate ? (
                        <div className={GroupDropdownEmptyClass}>
                            {props.query.trim() === "" ? `No ${label}s available` : `No matching ${label}s`}
                        </div>
                    ) : null}
                </>
            )}
        </div>
    );
}// Custom fields: provider, category, publication. Rendered inside the "Metadata" section.
// -------------------------------------------------------------------------------------------------------------------

const CategoryFieldClass = injectStyle("category-field", cl => `
    ${cl} {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        gap: 2px;
        width: 100%;
        padding: 8px 10px;
        border: 1px solid var(--borderColor);
        border-radius: 5px;
        background: var(--backgroundDefault);
        cursor: pointer;
        text-align: left;
        font: inherit;
    }

    ${cl}:hover {
        border-color: var(--borderColorHover);
    }

    ${cl}:focus {
        outline: 2px solid var(--primaryMain);
        outline-offset: 2px;
    }
`);

const CategoryFieldTitleClass = injectStyle("category-field-title", cl => `
    ${cl} {
        font-weight: 600;
        font-size: 14px;
    }
`);

const CategoryFieldPlaceholderClass = injectStyle("category-field-placeholder", cl => `
    ${cl} {
        color: var(--textSecondary);
        font-size: 14px;
    }
`);

const ContainerImageFieldValueClass = injectStyle("container-image-field-value", cl => `
    ${cl} {
        overflow-wrap: anywhere;    }
`);

const GroupInputWrapperClass = injectStyle("group-input-wrapper", cl => `
    ${cl} {
        position: relative;
        width: 100%;
    }
`);

const GroupDropdownClass = injectStyle("group-dropdown", cl => `
    ${cl} {
        position: absolute;
        top: calc(100% + 4px);
        left: 0;
        right: 0;
        z-index: 100;
        display: flex;
        flex-direction: column;
        gap: 4px;
        max-height: 260px;
        overflow-y: auto;
        padding: 6px;
        border: 1px solid var(--borderColor);
        border-radius: 6px;
        background: var(--backgroundDefault);
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
    }
`);

const GroupDropdownRowClass = injectStyle("group-dropdown-row", cl => `
    ${cl} {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        gap: 2px;
        width: 100%;
        padding: 8px 10px;
        border: 1px solid var(--borderColor);
        border-radius: 5px;
        background: var(--backgroundDefault);
        cursor: pointer;
        text-align: left;
        font: inherit;
    }

    ${cl}:hover, ${cl}[data-highlighted="true"] {
        border-color: var(--borderColorHover);
        background: var(--rowHover);
    }
`);

const GroupDropdownEmptyClass = injectStyle("group-dropdown-empty", cl => `
    ${cl} {
        padding: 8px 10px;
        font-size: 13px;
        color: var(--textSecondary);
    }
`);

const GroupInputRowClass = injectStyle("group-input-row", cl => `
    ${cl} {
        display: flex;
        align-items: center;
        gap: 4px;
        width: 100%;
        min-width: 0;
    }

    ${cl} input {
        flex: 1;
        min-width: 0;
    }
`);

const GroupWarningClass = injectStyle("group-warning-icon", cl => `
    ${cl} {
        display: inline-flex;
        vertical-align: middle;
        margin-left: 4px;
    }
`);

function CustomFieldsSection(props: {
    draft: CreatorDraft;
    onUpdateCustomMeta: (patch: Partial<CreatorCustomMeta>) => void;
    eligibility?: AppEditorCustomEligibilityResponse | null;
    categories?: AppCatalogCustomCategory[];
    refreshPlacement: () => Promise<void>;
}): React.ReactNode {
    const {draft} = props;
    const meta = draft.customMeta;
    const [, setLandingPage] = useGlobal("catalogLandingPage", AppStore.emptyLandingPage);
    if (!meta) return null;

    const allCategories = props.categories ?? [];
    const canCreateCategory = customAppsWorkspaceAdmin();
    const selected = meta.category
        ? allCategories.find(category => String(category.id) === meta.category) ?? null
        : null;

    return (
        <>
            <Label className="panel-field">
                <span className="panel-field-label">Service Provider<MandatoryField /></span>
                {props.eligibility?.providers.length ? (
                    <ServiceProviderSelector
                        serviceProvider={meta.provider}
                        serviceProviders={props.eligibility.providers.map(item => ({key: item.provider}))}
                        renderRow={CustomProviderRow}
                        renderSelectedRow={CustomProviderRow}
                        showLabel={false}
                        reserveLabelSpace={false}
                        onSelect={el => props.onUpdateCustomMeta({provider: el.key})}
                        data-navigation-field
                        data-creator-field="custom.serviceProvider"
                    />
                ) : (
                    <Input
                        className={PanelInputClass}
                        value={meta.provider}
                        onChange={e => props.onUpdateCustomMeta({provider: e.target.value})}
                        placeholder="The service provider id"
                    />
                )}
            </Label>
            <CategoryAutocompleteField
                draft={draft}
                categories={allCategories}
                canCreate={canCreateCategory}
                onLandingPageInvalidated={() => setLandingPage(AppStore.emptyLandingPage)}
                onUpdateCustomMeta={props.onUpdateCustomMeta}
                refreshPlacement={props.refreshPlacement}
            />
            <ToggleRow
                label="Publish to project"
                checked={meta.publishedToProject}
                onChange={() => props.onUpdateCustomMeta({publishedToProject: !meta.publishedToProject})}
                disabled={!meta.canPublish}
                id="custom-published-to-project"
            />
            {!meta.canPublish ? (
                <Text fontSize={12} color="textSecondary" mt="4px">
                    Publication is unavailable in a personal workspace. Open a project to publish this application.
                </Text>
            ) : null}
        </>
    );
}

function CategoryAutocompleteField(props: {
    draft: CreatorDraft;
    categories: AppCatalogCustomCategory[];
    canCreate: boolean;
    onLandingPageInvalidated: () => void;
    onUpdateCustomMeta: (patch: Partial<CreatorCustomMeta>) => void;
    refreshPlacement: () => Promise<void>;
}): React.ReactNode {
    const {draft} = props;
    const meta = draft.customMeta;
    const [query, setQuery] = useState("");
    const [open, setOpen] = useState(false);
    const [focused, setFocused] = useState(false);
    const [creating, setCreating] = useState(false);
    const [highlight, setHighlight] = useState(0);
    const [managed, setManaged] = useState<ResourceOption[] | null>(null);
    const wrapperRef = useRef<HTMLDivElement>(null);
    const dropdownRef = useRef<HTMLDivElement>(null);
    const inputRef = useRef<HTMLInputElement>(null);

    const selected = props.categories.find(category => meta != null && String(category.id) === meta.category) ?? null;

    React.useEffect(() => {
        if (!open || managed != null) return;
        let cancelled = false;
        fetchAll<AppStore.ApplicationCategory>(next => callAPI(AppStore.browseStudioCategories({itemsPerPage: 250, next}))).then(categories => {
            if (cancelled) return;
            setManaged(categories.map(category => ({
                id: category.metadata.id,
                title: category.specification.title,
                description: category.specification.description ?? "",
                isCustom: false,
            })));
        }).catch(() => {
            if (!cancelled) setManaged([]);
        });
        return () => {
            cancelled = true;
        };
    }, [open, managed]);

    React.useEffect(() => {
        if (!open) return;
        const listener = (event: PointerEvent) => {
            if (wrapperRef.current && !wrapperRef.current.contains(event.target as Node)) setOpen(false);
        };
        document.addEventListener("pointerdown", listener);
        return () => document.removeEventListener("pointerdown", listener);
    }, [open]);

    React.useEffect(() => {
        const dropdown = dropdownRef.current;
        if (!dropdown || !open) return;
        const row = dropdown.querySelector<HTMLElement>('[data-highlighted="true"]');
        if (!row) return;
        const rowTop = row.offsetTop;
        const rowBottom = rowTop + row.offsetHeight;
        if (rowTop < dropdown.scrollTop) {
            dropdown.scrollTop = rowTop;
        } else if (rowBottom > dropdown.scrollTop + dropdown.clientHeight) {
            dropdown.scrollTop = rowBottom - dropdown.clientHeight;
        }
    }, [highlight, open]);

    const trimmed = query.trim().toLowerCase();

    const byBacked = new Map<number, number>();
    for (const category of props.categories) {
        if (category.backedBy != null) byBacked.set(category.backedBy, category.id);
    }

    const options: ResourceOption[] = [];
    const seen = new Set<number>();
    for (const option of managed ?? []) {
        seen.add(option.id);
        const backing = byBacked.get(option.id);
        if (backing != null) {
            seen.add(backing);
            options.push({...option, id: backing, isCustom: true});
        } else {
            options.push(option);
        }
    }
    for (const category of props.categories) {
        if (seen.has(category.id)) continue;
        options.push({id: category.id, title: category.specification.title, description: category.specification.description, isCustom: true});
    }

    const matches = trimmed === ""
        ? options
        : options.filter(option =>
            option.title.toLowerCase().includes(trimmed) ||
            option.description.toLowerCase().includes(trimmed));
    const canCreate = props.canCreate && trimmed !== "" && !options.some(option => option.title.toLowerCase() === trimmed);

    const selectCategory = (option: ResourceOption) => {
        if (option.isCustom) {
            props.onUpdateCustomMeta({category: String(option.id)});
            setQuery(option.title);
            closeAutocomplete(setOpen, inputRef);
            return;
        }
        setCreating(true);
        callAPI(AppStore.createCustomCategory({kind: "Managed", id: option.id})).then(result => {
            props.onLandingPageInvalidated();
            props.onUpdateCustomMeta({category: String(result.id)});
            setQuery(option.title);
            closeAutocomplete(setOpen, inputRef);
            return props.refreshPlacement();
        }).catch(error => {
            sendFailureNotification(extractErrorMessage(error as {request: XMLHttpRequest; response: any}));
        }).finally(() => {
            setCreating(false);
        });
    };

    const onKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
        if (e.key === "Escape") {
            closeAutocomplete(setOpen, inputRef);
            return;
        }
        if (!open) {
            if (e.key === "Enter") {
                e.preventDefault();
                setHighlight(0);
                setOpen(true);
            }
            return;
        }
        const rowCount = matches.length + (canCreate ? 1 : 0);
        if (rowCount === 0) return;
        if (e.key === "ArrowDown") {
            e.preventDefault();
            setHighlight(h => (h + 1) % rowCount);
        } else if (e.key === "ArrowUp") {
            e.preventDefault();
            setHighlight(h => (h - 1 + rowCount) % rowCount);
        } else if (e.key === "Enter") {
            e.preventDefault();
            if (highlight < matches.length) {
                selectCategory(matches[highlight]);
            } else if (canCreate) {
                openCreateDialog();
            }
        }
    };

    const openCreateDialog = () => {
        const submit = async (name: string, description: string) => {
            setCreating(true);
            try {
                const result = await callAPI<{id: number}>(AppStore.createCustomCategory({
                    kind: "Custom",
                    specification: {title: name, description},
                }));
                props.onLandingPageInvalidated();
                props.onUpdateCustomMeta({category: String(result.id)});
                setQuery(name);
                closeAutocomplete(setOpen, inputRef);
                await props.refreshPlacement();
                dialogStore.success();
            } catch (error) {
                sendFailureNotification(extractErrorMessage(error as {request: XMLHttpRequest; response: any}));
            } finally {
                setCreating(false);
            }
        };
        dialogStore.addDialog(
            <ResourceNameDialog
                title="New category"
                placeholder="My category"
                nameDescription="The category name is shown on the applications landing page."
                creating={creating}
                initialTitle={query.trim()}
                initialDescription=""
                onSubmit={submit}
            />,
            doNothing,
            true,
            slimModalStyle,
        );
    };

    return (
        <div ref={wrapperRef}>
            <Label className="panel-field">
                <span className="panel-field-label">
                    Category
                    <MandatoryField />
                    {selected == null && !focused && !open && query.trim() !== "" ? (
                        <TooltipV2 tooltip="Type to search or create a category, then select one">
                            <span className={GroupWarningClass} role="img" aria-label="No category selected">
                                <Icon name="heroExclamationTriangle" size={14} color="warningMain" />
                            </span>
                        </TooltipV2>
                    ) : null}
                </span>
                <div className={GroupInputWrapperClass}>
                    <div className={GroupInputRowClass}>
                        <Input
                            inputRef={inputRef}
                            className={PanelInputClass}
                            value={query}
                            onChange={e => {
                                setQuery(e.target.value);
                                setHighlight(0);
                                setOpen(true);
                            }}
                            onFocus={() => {
                                setFocused(true);
                            }}
                            onBlur={() => setFocused(false)}
                            onKeyDown={onKeyDown}
                            role="combobox"
                            aria-autocomplete="list"
                            aria-expanded={open}
                            placeholder="Search or create a category"
                            data-creator-field="custom.categoryId"
                        />
                    </div>
                    {open ? (
                        <GroupDropdown
                            dropdownRef={dropdownRef}
                            query={query}
                            highlight={highlight}
                            matches={matches}
                            canCreate={canCreate}
                            creating={creating}
                            managedLoaded={managed != null}
                            createLabel="category"
                            onSelect={selectCategory}
                            onCreate={openCreateDialog}
                            onHover={setHighlight}
                        />
                    ) : null}
                </div>
            </Label>
        </div>
    );
}

// Managed fields: modules, ucx, documentation, extensions
// -------------------------------------------------------------------------------------------------------------------

function ManagedFieldsSection(props: {
    application: A2Yaml;
    onUpdateModules: (modules: A2Yaml["modules"]) => void;
    onUpdateUcx: (ucx: A2Yaml["ucx"]) => void;
    onUpdateExtensions: (extensions: string[]) => void;
}): React.ReactNode {
    const {application} = props;
    return (
        <>
            <ModulesEditor application={application} onUpdate={props.onUpdateModules} />
            <UcxEditor application={application} onUpdate={props.onUpdateUcx} />
            <ExtensionsEditor application={application} onUpdate={props.onUpdateExtensions} />
        </>
    );
}

function ModulesEditor(props: {
    application: A2Yaml;
    onUpdate: (modules: A2Yaml["modules"]) => void;
}): React.ReactNode {
    const modules = props.application.modules ?? {mountPath: "", optional: []};
    const [optionalText, setOptionalText] = useState(modules.optional.join("\n"));

    React.useEffect(() => { setOptionalText(modules.optional.join("\n")); }, [modules.optional.join("\n")]);

    return (
        <PanelSection title="Modules" collapsedByDefault>
            <Label className="panel-field">
                <span className="panel-field-label">Mount path</span>
                <Input
                    className={PanelInputClass}
                    value={modules.mountPath}
                    onChange={e => props.onUpdate({mountPath: e.target.value, optional: modules.optional})}
                    placeholder="/opt/modules"
                />
            </Label>
            <Label className="panel-field">
                <span className="panel-field-label">Optional modules (one per line)</span>
                <TextArea
                    className={PanelInputClass}
                    rows={3}
                    value={optionalText}
                    onChange={e => {
                        setOptionalText(e.target.value);
                        const optional = e.target.value.split("\n").map(s => s.trim()).filter(s => s.length > 0);
                        props.onUpdate({mountPath: modules.mountPath, optional});
                    }}
                    placeholder="module1\nmodule2"
                />
            </Label>
        </PanelSection>
    );
}

function UcxEditor(props: {
    application: A2Yaml;
    onUpdate: (ucx: A2Yaml["ucx"]) => void;
}): React.ReactNode {
    const ucx = props.application.ucx ?? {executable: null};
    const exe = ucx.executable ?? {manifestUrl: "", publicKey: "", binaryName: ""};

    return (
        <PanelSection title="UCX" collapsedByDefault>
            <Label className="panel-field">
                <span className="panel-field-label">Manifest URL</span>
                <Input
                    className={PanelInputClass}
                    value={exe.manifestUrl}
                    onChange={e => props.onUpdate({executable: {...exe, manifestUrl: e.target.value}})}
                    placeholder="https://..."
                />
            </Label>
            <Label className="panel-field">
                <span className="panel-field-label">Public key</span>
                <Input
                    className={PanelInputClass}
                    value={exe.publicKey}
                    onChange={e => props.onUpdate({executable: {...exe, publicKey: e.target.value}})}
                    placeholder="Public key"
                />
            </Label>
            <Label className="panel-field">
                <span className="panel-field-label">Binary name</span>
                <Input
                    className={PanelInputClass}
                    value={exe.binaryName}
                    onChange={e => props.onUpdate({executable: {...exe, binaryName: e.target.value}})}
                    placeholder="binary"
                />
            </Label>
        </PanelSection>
    );
}

function ExtensionsEditor(props: {
    application: A2Yaml;
    onUpdate: (extensions: string[]) => void;
}): React.ReactNode {
    const [text, setText] = useState(props.application.extensions.join("\n"));

    React.useEffect(() => { setText(props.application.extensions.join("\n")); }, [props.application.extensions.join("\n")]);

    return (
        <PanelSection title="Extensions" collapsedByDefault>
            <Label className="panel-field">
                <span className="panel-field-label">Extensions (one per line)</span>
                <TextArea
                    className={PanelInputClass}
                    rows={3}
                    value={text}
                    onChange={e => {
                        setText(e.target.value);
                        const extensions = e.target.value.split("\n").map(s => s.trim()).filter(s => s.length > 0);
                        props.onUpdate(extensions);
                    }}
                    placeholder="extension"
                />
            </Label>
        </PanelSection>
    );
}

// Widget drawer
// -------------------------------------------------------------------------------------------------------------------

function WidgetDrawerSection(props: {
    onAddParameter: (type: import("@/Applications/Creator/WidgetDefaults").A2WidgetType) => void;
}): React.ReactNode {
    const basicItems = WIDGET_DRAWER_ITEMS.filter(i => i.group === "basic");
    const resourceItems = WIDGET_DRAWER_ITEMS.filter(i => i.group === "resources");
    return (
        <>
            <PanelSection title="Add parameter" id="creator-section-add-parameter" shortcut="A">
                <Text fontSize={12} color="textSecondary" mb="8px">
                    Click a widget to append a new parameter row.
                </Text>
                <WidgetDrawerGroup items={basicItems} group="basic" onAddParameter={props.onAddParameter} />
                <Box mt="12px" />
                <WidgetDrawerGroup items={resourceItems} group="resources" onAddParameter={props.onAddParameter} />
            </PanelSection>
        </>
    );
}

function WidgetDrawerGroup(props: {
    items: typeof WIDGET_DRAWER_ITEMS;
    group: WidgetDrawerGroup;
    onAddParameter: (type: import("@/Applications/Creator/WidgetDefaults").A2WidgetType) => void;
}): React.ReactNode {
    return (
        <div className={WidgetDrawerGroupClass}>
            <Text fontWeight={600} fontSize={12} color="textSecondary">
                {props.group === "basic" ? "Basic values" : "UCloud resources"}
            </Text>
            {props.items.map(item => (
                <WidgetDrawerButton
                    key={item.type}
                    item={item}
                    onClick={() => props.onAddParameter(item.type)}
                    basicGroup={props.group === "basic"}
                />
            ))}
        </div>
    );
}

function WidgetDrawerButton(props: {
    item: typeof WIDGET_DRAWER_ITEMS[number];
    onClick: () => void;
    basicGroup?: boolean;
}): React.ReactNode {
    const icon = widgetIcon(props.item.type);
    return (
        <TooltipV2 tooltip={props.item.description}>
            <button
                type="button"
                className={WidgetDrawerButtonClass}
                onClick={props.onClick}
                aria-label={`Add ${props.item.label} parameter`}
                data-navigation-field
                data-creator-widget={props.basicGroup ? "basic" : undefined}
            >
                <Icon name={icon} size={16} color="textSecondary" />
                <Text fontSize={13}>{props.item.label}</Text>
            </button>
        </TooltipV2>
    );
}

function widgetIcon(type: string): IconName {
    switch (type) {
        case "Text": return "heroChatBubbleLeftRight";
        case "TextArea": return "heroDocumentText";
        case "Boolean": return "heroCheckCircle";
        case "Integer": return "heroHashtag";
        case "FloatingPoint": return "heroHashtag";
        case "Enumeration": return "heroListBullet";
        case "File": return "heroDocument";
        case "Directory": return "heroFolder";
        case "License": return "heroTicket";
        case "PublicIP": return "heroGlobeAlt";
        default: return "heroPlus";
    }
}

// Styling
// -------------------------------------------------------------------------------------------------------------------

const PanelInputClass = injectStyle("creator-panel-input-meta", k => `
    ${k} {
        width: 100%;
    }
`);

const SshHighlightWrapperClass = injectStyle("creator-ssh-highlight-wrapper", k => `
    ${k} {
        border-radius: 6px;
        padding: 2px 4px;
        margin: -2px -4px;
        transition: box-shadow 0.2s ease;
    }
`);

const KeyValueRowClass = injectStyle("creator-key-value-row", k => `
    ${k} {
        display: flex;
        align-items: center;
        gap: 6px;
    }
`);

const AddValueButtonClass = injectStyle("creator-add-value-button", k => `
    ${k}:focus {
        outline: 2px solid var(--primaryMain);
        outline-offset: 2px;
    }
`);

const WidgetDrawerGroupClass = injectStyle("creator-drawer-group", k => `
    ${k} {
        display: flex;
        flex-direction: column;
        gap: 4px;
    }
`);

const WidgetDrawerButtonClass = injectStyle("creator-drawer-button", k => `
    ${k} {
        --creatorDrawerHoverBorder: var(--primaryMain);
        --creatorDrawerHoverBackground: var(--rowActive);
        display: flex;
        align-items: center;
        gap: 8px;
        width: 100%;
        padding: 8px 10px;
        border: 1px solid var(--borderColor);
        border-radius: 6px;
        background: var(--backgroundCard);
        cursor: pointer;
        text-align: left;
        transition: border-color 0.15s ease, background-color 0.15s ease;
    }

    html.dark ${k} {
        --creatorDrawerHoverBorder: var(--blue-50);
        --creatorDrawerHoverBackground: var(--blue-80);
    }

    ${k}:hover {
        border-color: var(--creatorDrawerHoverBorder);
        background: var(--creatorDrawerHoverBackground);
    }

    ${k}:focus-visible {
        outline: 2px solid var(--primaryMain);
        outline-offset: 1px;
    }
`);
