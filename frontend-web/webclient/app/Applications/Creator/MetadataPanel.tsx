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
// It lives on the draft as customMeta and is sent as separate request fields at save time.

import * as React from "react";
import {useState} from "react";
import {Box, Button, Input, Label, Select, Text, TextArea} from "@/ui-components";
import {IconButton} from "@/ui-components/IconButton";
import Icon, {IconName} from "@/ui-components/Icon";
import {TooltipV2} from "@/ui-components/Tooltip";
import {injectStyle} from "@/Unstyled";
import {A2Yaml, A2Software, A2Features, A2SshMode, A2Inference, A2ApplicationToLoad} from "@/Applications/Creator/A2";
import {CreatorDraft, CreatorCustomMeta, creatorIsCustom, creatorIsEditableName, creatorIsEditableVersion} from "@/Applications/Creator/Draft";
import {PanelSection, ToggleRow} from "@/Applications/Creator/ParameterPanelShared";
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
import {doNothing, extractErrorMessage} from "@/UtilityFunctions";
import {sendFailureNotification} from "@/Notifications";
import * as Heading from "@/ui-components/Heading";
import {Divider} from "@/ui-components";
import {useGlobal} from "@/Utilities/ReduxHooks";
import ContainerRepositoryBrowse from "@/ContainerRepositories/Browse";
import {Client} from "@/Authentication/HttpClientInstance";
import {checkIsWorkspaceAdmin} from "@/ui-components/ResourceBrowser";

export interface MetadataPanelProps {
    draft: CreatorDraft;
    // True when the YAML source is invalid. The whole panel becomes read-only by disabling
    // pointer events so the user cannot change visual fields while the source owns the edits.
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
}

export function MetadataPanel(props: MetadataPanelProps): React.ReactNode {
    const {draft} = props;
    const {application, context} = draft;
    const isCustom = creatorIsCustom(context);
    const editableName = creatorIsEditableName(context);
    const editableVersion = creatorIsEditableVersion(context);
    const readOnly = props.readOnly === true;

    return (
        <div className={readOnly ? MetadataReadOnlyClass : undefined}>
            {readOnly ? (
                <Box px="12px" py="8px" mb="4px" background={"color-mix(in srgb, var(--errorMain) 12%, transparent)"} borderRadius={"6px"}>
                    <Text fontSize={12} color="errorMain">
                        YAML source is invalid. Fix the source to re-enable visual editing.
                    </Text>
                </Box>
            ) : null}
            <IdentitySection
                application={application}
                editableName={editableName}
                editableVersion={editableVersion}
                namePlaceholder={context.operation === "fork" ? `${context.existingName ?? "application"}-fork` : undefined}
                versionPlaceholder={context.operation === "fork" ? "1.0" : undefined}
                onNameChange={props.onNameChange}
                onVersionChange={props.onVersionChange}
            />
            <PresentationSection
                application={application}
                onUpdateMetadata={props.onUpdateMetadata}
            />
            <GroupFlavorSection
                draft={draft}
                onUpdateCustomMeta={props.onUpdateCustomMeta}
                groups={props.customGroups}
                refreshPlacement={props.refreshPlacement}
            />
            {isCustom ? (
                <CustomFieldsSection
                    draft={draft}
                    onUpdateCustomMeta={props.onUpdateCustomMeta}
                    eligibility={props.customEligibility}
                    categories={props.customCategories}
                    refreshPlacement={props.refreshPlacement}
                />
            ) : (
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

// Identity: name and version
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
        <PanelSection title="Identity">
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
        </PanelSection>
    );
}

// Presentation: title, description, license
// -------------------------------------------------------------------------------------------------------------------

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
        // Custom applications always use Container. Show only the container image field, not a
        // software-kind selector.
        const image = software.type === "Container" ? software.image : "";
        return (
            <PanelSection title="Software">
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

    // Managed applications can select all A2 software kinds.
    return (
        <PanelSection title="Software">
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

// Native software has a load list of {name, version}. Editing the load list is deferred to the
// YAML view in this milestone; we show a note and a count.
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

// Preserves the existing image when switching between image-based kinds, and starts with an empty
// load list when switching to Native.
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
    onUpdateFeatures: (features: A2Yaml["features"]) => void;
}): React.ReactNode {
    const features = props.application.features ?? defaultFeatures();
    const toggle = (key: keyof A2Features) => {
        const updated = {...features, [key]: !features[key]} as A2Features;
        props.onUpdateFeatures(updated);
    };
    return (
        <PanelSection title="Features">
            <FeatureToggle label="Folders" value={features.folders ?? false} onChange={() => toggle("folders")} id="feature-folders" />
            <FeatureToggle label="Links" value={features.links ?? false} onChange={() => toggle("links")} id="feature-links" />
            <FeatureToggle label="Job linking" value={features.jobLinking ?? false} onChange={() => toggle("jobLinking")} id="feature-jobLinking" />
            <FeatureToggle label="Public IP addresses" value={features.ipAddresses ?? false} onChange={() => toggle("ipAddresses")} id="feature-ipAddresses" />
            <FeatureToggle label="Multi-node jobs" value={features.multiNode} onChange={() => toggle("multiNode")} />
            <FeatureToggle label="Audit logs" value={features.jobAuditLog ?? false} onChange={() => toggle("jobAuditLog")} />
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
        <PanelSection title="Connectivity">
            <WebControl application={props.application} onUpdate={props.onUpdateWeb} />
            <VncControl application={props.application} onUpdate={props.onUpdateVnc} />
            <SshControl application={props.application} onUpdate={props.onUpdateSsh} />
            <InferenceControl application={props.application} onUpdate={props.onUpdateInference} />
        </PanelSection>
    );
}

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
                <span className="panel-field-label">SSH</span>
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

function InferenceControl(props: {
    application: A2Yaml;
    onUpdate: (inference: A2Yaml["inference"]) => void;
}): React.ReactNode {
    const inference = props.application.inference ?? {mode: "None"};
    return (
        <Label className="panel-field">
            <span className="panel-field-label">Inference</span>
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

// An ordered key-value editor. The order is the insertion order of the keys. Adding a row
// appends to the end. The editor keeps a local ordered list so in-progress edits (empty or
// duplicate keys) stay visible while the user types. A valid subset is pushed to the draft on
// every change; invalid keys are reported as local errors.
function KeyValueEditor(props: {
    title: string;
    values: Record<string, string>;
    keyPlaceholder: string;
    valuePlaceholder: string;
    onUpdate: (values: Record<string, string>) => void;
    collapsedByDefault?: boolean;
}): React.ReactNode {
    // Local ordered rows. Each row is [key, value]. We keep this separate from the draft so the
    // user can type empty or duplicate keys before resolving them.
    const [rows, setRows] = useState<[string, string][]>(() => Object.entries(props.values));
    const [error, setError] = useState<string | null>(null);

    // Sync from the draft when it changes externally (e.g. template switch). We compare a
    // serialized form to avoid overwriting local edits on every parent re-render.
    const externalKey = JSON.stringify(props.values);
    const [lastExternal, setLastExternal] = useState(externalKey);
    if (externalKey !== lastExternal) {
        setLastExternal(externalKey);
        setRows(Object.entries(props.values));
        setError(null);
    }

    const applyRows = (next: [string, string][]) => {
        setRows(next);
        const {result, error: validationError} = keyValueFromEntries(next);
        setError(validationError);
        // Push only when all keys are valid. This keeps the draft clean while the user edits.
        if (!validationError) props.onUpdate(result);
    };

    const commitRow = (index: number, key: string, value: string) => {
        const next = [...rows];
        next[index] = [key, value];
        applyRows(next);
    };

    const addRow = () => {
        applyRows([...rows, ["", ""]]);
    };

    const removeRow = (index: number) => {
        applyRows(rows.filter((_, i) => i !== index));
    };

    return (
        <PanelSection title={props.title} collapsedByDefault={props.collapsedByDefault}>
            {rows.length === 0 ? (
                <Text fontSize={12} color="textSecondary">No {props.title.toLowerCase()} values.</Text>
            ) : null}
            {rows.map((entry, index) => {
                const [key, value] = entry;
                return (
                    <KeyValueRow
                        key={index}
                        rowKey={key}
                        rowValue={value}
                        keyPlaceholder={props.keyPlaceholder}
                        valuePlaceholder={props.valuePlaceholder}
                        onCommit={(newKey, newValue) => commitRow(index, newKey, newValue)}
                        onRemove={() => removeRow(index)}
                    />
                );
            })}
            <Button type="button" color="secondaryMain" onClick={addRow} mt="4px">
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

    React.useEffect(() => { setKey(props.rowKey); }, [props.rowKey]);
    React.useEffect(() => { setValue(props.rowValue); }, [props.rowValue]);

    const commitKey = () => props.onCommit(key, value);
    const commitValue = () => props.onCommit(key, value);

    return (
        <div className={KeyValueRowClass}>
            <Input
                className={PanelInputClass}
                value={key}
                placeholder={props.keyPlaceholder}
                onChange={e => setKey(e.target.value)}
                onBlur={commitKey}
            />
            <Input
                className={PanelInputClass}
                value={value}
                placeholder={props.valuePlaceholder}
                onChange={e => setValue(e.target.value)}
                onBlur={commitValue}
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

// Convert ordered entries into a key-value map. Reports empty or duplicate keys.
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

// Group and flavor (custom only)
// -------------------------------------------------------------------------------------------------------------------

interface ResourceOption {
    id: number;
    title: string;
    description: string;
    isCustom: boolean;
}

function ResourceSelectorModal(props: {
    title: string;
    singular: string;
    resourceLabel: string;
    custom: Array<{id: number; backedBy?: number; specification: {title: string; description: string}}>;
    loadManaged: () => Promise<ResourceOption[]>;
    createCustom: (kind: "Custom" | "Managed", managedId: number | null, title: string, description: string) => Promise<number>;
    canCreateCustom?: boolean;
    onSelect: (id: string) => void;
    onCreated: (id: number, title: string, description: string) => Promise<void>;
}): React.ReactNode {
    const [managed, setManaged] = useState<ResourceOption[] | null>(null);
    const [creating, setCreating] = useState(false);
    const [filter, setFilter] = useState("");
    const [creatingNew, setCreatingNew] = useState(false);
    const [newTitle, setNewTitle] = useState("");
    const [newDescription, setNewDescription] = useState("");

    React.useEffect(() => {
        let cancelled = false;
        props.loadManaged().then(items => {
            if (!cancelled) setManaged(items);
        }).catch(() => {
            if (!cancelled) setManaged([]);
        });
        return () => {
            cancelled = true;
        };
    }, []);

    React.useEffect(() => {
        if (!creatingNew) return;
        const listener = (event: KeyboardEvent) => {
            if (event.key === "Escape") {
                event.preventDefault();
                event.stopPropagation();
                setCreatingNew(false);
                setNewTitle("");
                setNewDescription("");
            }
        };
        document.addEventListener("keydown", listener, true);
        return () => document.removeEventListener("keydown", listener, true);
    }, [creatingNew]);

    const byBacked = new Map<number, number>();
    for (const resource of props.custom) {
        if (resource.backedBy != null) byBacked.set(resource.backedBy, resource.id);
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
    for (const resource of props.custom) {
        if (seen.has(resource.id)) continue;
        options.push({id: resource.id, title: resource.specification.title, description: resource.specification.description, isCustom: true});
    }

    const canCreateCustom = props.canCreateCustom ?? true;
    const filtered = (filter.trim()
        ? options.filter(option =>
            option.title.toLowerCase().includes(filter.trim().toLowerCase()) ||
            option.description.toLowerCase().includes(filter.trim().toLowerCase()))
        : options).filter(option => canCreateCustom || option.isCustom);

    const finish = async (id: number, title: string, description: string) => {
        try {
            await props.onCreated(id, title, description);
        } catch {
            // Ignored
        }
        props.onSelect(String(id));
        dialogStore.success();
    };

    const select = (option: ResourceOption) => {
        if (option.isCustom) {
            props.onSelect(String(option.id));
            dialogStore.success();
            return;
        }

        setCreating(true);
        props.createCustom("Managed", option.id, "", "").then(id => {
            finish(id, option.title, option.description);
        }).catch(error => {
            sendFailureNotification(extractErrorMessage(error as {request: XMLHttpRequest; response: any}));
            setCreating(false);
        });
    };

    const createNew = async () => {
        const title = newTitle.trim();
        if (!title) return;
        setCreating(true);
        props.createCustom("Custom", null, title, newDescription.trim()).then(id => {
            finish(id, title, newDescription.trim());
        }).catch(error => {
            sendFailureNotification(extractErrorMessage(error as {request: XMLHttpRequest; response: any}));
            setCreating(false);
        });
    };

    return (
        <div className={CategoryModalClass}>
            <Heading.h3>{props.title}</Heading.h3>
            <Text color="textSecondary" fontSize={13}>
                Choose a {props.singular} for this application. If a {props.singular} does not exist yet you can create it.
            </Text>
            <Divider />
            {canCreateCustom && !creatingNew ? (
                <Flex justifyContent="stretch" mt="8px">
                    <Button color="secondaryMain" width="100%" onClick={() => setCreatingNew(true)} disabled={creating}>
                        <Icon name="heroPlus" size={14} mr={6} />
                        New {props.singular}
                    </Button>
                </Flex>
            ) : canCreateCustom ? (
                <div className={CategoryCreateFormClass}>
                    <Input
                        value={newTitle}
                        onChange={e => setNewTitle(e.target.value)}
                        placeholder={`Name of the new ${props.singular}`}
                        autoFocus
                        onKeyDown={e => {
                            if (e.key === "Enter") void createNew();
                        }}
                    />
                    <TextArea
                        value={newDescription}
                        onChange={e => setNewDescription(e.target.value)}
                        placeholder={`Optional description of the new ${props.singular}`}
                        rows={2}
                    />
                    <Flex gap="8px" justifyContent="flex-end">
                        <Button color="secondaryMain" onClick={() => {
                            setCreatingNew(false);
                            setNewTitle("");
                            setNewDescription("");
                        }}>
                            Cancel
                        </Button>
                        <Button color="successMain" onClick={() => void createNew()} disabled={creating || newTitle.trim() === ""}>
                            Create
                        </Button>
                    </Flex>
                </div>
            ) : null}
            <Input
                className={CategoryFilterClass}
                mt="8px"
                value={filter}
                onChange={e => setFilter(e.target.value)}
                placeholder={`Filter ${props.resourceLabel.toLowerCase()}...`}
            />
            <div className={CategoryModalListClass}>
                {managed == null ? (
                    <Text fontSize={13} color="textSecondary">Loading...</Text>
                ) : filtered.length === 0 ? (
                    <Text fontSize={13} color="textSecondary">No {props.resourceLabel.toLowerCase()} found.</Text>
                ) : filtered.map(option => (
                    <button
                        key={option.id}
                        className={CategoryModalRowClass}
                        type="button"
                        onClick={() => select(option)}
                        disabled={creating}
                    >
                        <span className={CategoryModalRowTitleClass}>{option.title}</span>
                        {option.description ? <span className={CategoryModalRowDescriptionClass}>{option.description}</span> : null}
                    </button>
                ))}
            </div>
        </div>
    );
}

function GroupFlavorSection(props: {
    draft: CreatorDraft;
    onUpdateCustomMeta: (patch: Partial<CreatorCustomMeta>) => void;
    groups?: AppCatalogCustomGroup[];
    refreshPlacement: () => Promise<void>;
}): React.ReactNode {
    const {draft} = props;
    const meta = draft.customMeta;
    const [createdGroup, setCreatedGroup] = useState<{id: number; title: string; description: string} | null>(null);
    if (!meta) {
        // Managed applications do not have custom group or flavor. Show nothing.
        return null;
    }

    const allGroups = props.groups ?? [];
    const selected = meta.group
        ? allGroups.find(group => String(group.id) === meta.group) ?? (
            createdGroup != null && String(createdGroup.id) === meta.group
                ? {id: createdGroup.id, specification: {title: createdGroup.title, description: createdGroup.description}}
                : null
        )
        : null;

    const openGroupSelector = () => {
        dialogStore.addDialog(
            <ResourceSelectorModal
                title="Select a group"
                singular="group"
                resourceLabel="Groups"
                custom={allGroups}
                loadManaged={() => fetchAll<AppStore.ApplicationGroup>(next => callAPI(AppStore.browseGroups({itemsPerPage: 250, next}))).then(groups =>
                    groups.map(group => ({
                        id: group.metadata.id,
                        title: group.specification.title,
                        description: group.specification.description,
                        isCustom: false,
                    })))}
                createCustom={(kind, managedId, title, description) =>
                    callAPI(AppStore.createCustomGroup({
                        kind,
                        ...(managedId != null ? {id: managedId} : {}),
                        ...(kind === "Custom" ? {specification: {title, description}} : {}),
                    })).then(result => result.id)
                }
                onSelect={id => props.onUpdateCustomMeta({group: id})}
                onCreated={(id, title, description) => {
                    setCreatedGroup({id, title, description});
                    return props.refreshPlacement();
                }}
            />,
            doNothing,
            true,
            slimModalStyle,
        );
    };

    return (
        <PanelSection title="Group and flavor">
            <Label className="panel-field">
                <span className="panel-field-label">Flavor<MandatoryField /></span>
                <Input
                    className={PanelInputClass}
                    value={meta.flavor}
                    onChange={e => props.onUpdateCustomMeta({flavor: e.target.value})}
                    placeholder="The flavor name for this application"
                    data-creator-field="custom.flavorName"
                />
            </Label>
            <Label className="panel-field">
                <span className="panel-field-label">Group<MandatoryField /></span>
                <button
                    type="button"
                    className={CategoryFieldClass}
                    onClick={openGroupSelector}
                    data-creator-field="custom.groupId"
                >
                    {selected ? (
                        <>
                            <span className={CategoryFieldTitleClass}>{selected.specification.title}</span>
                            {selected.specification.description ? (
                                <span className={CategoryFieldDescriptionClass}>{selected.specification.description}</span>
                            ) : null}
                        </>
                    ) : (
                        <span className={CategoryFieldPlaceholderClass}>Select a group</span>
                    )}
                </button>
            </Label>
        </PanelSection>
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

// Custom fields: provider, category, publication
// -------------------------------------------------------------------------------------------------------------------


const CategoryModalClass = injectStyle("category-selector-modal", cl => `
    ${cl} {
        display: flex;
        flex-direction: column;
        gap: 8px;
        max-height: 70vh;
    }
`);

const CategoryFilterClass = injectStyle("category-selector-filter", cl => `
    ${cl} {
        flex: 1;
        min-width: 0;
        width: 100%;
    }
`);

const CategoryCreateFormClass = injectStyle("category-selector-create-form", cl => `
    ${cl} {
        display: flex;
        flex-direction: column;
        gap: 8px;
        margin-top: 8px;
        padding: 10px;
        border: 1px solid var(--borderColor);
        border-radius: 6px;
        background: color-mix(in srgb, var(--backgroundDefault) 60%, transparent);
    }
`);

const CategoryModalListClass = injectStyle("category-selector-modal-list", cl => `
    ${cl} {
        display: flex;
        flex-direction: column;
        gap: 4px;
        margin-top: 8px;
        overflow-y: auto;
    }
`);

const CategoryModalRowClass = injectStyle("category-selector-modal-row", cl => `
    ${cl} {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        gap: 2px;
        width: 100%;
        padding: 8px 10px;
        border: 1px solid var(--borderColor);
        border-radius: 6px;
        background: var(--backgroundDefault);
        cursor: pointer;
        text-align: left;
    }

    ${cl}:hover {
        border-color: var(--borderColorHover);
        background: var(--rowHover);
    }
`);

const CategoryModalRowTitleClass = injectStyle("category-selector-modal-row-title", cl => `
    ${cl} {
        font-weight: 600;
        font-size: 14px;
    }
`);

const CategoryModalRowDescriptionClass = injectStyle("category-selector-modal-row-desc", cl => `
    ${cl} {
        font-size: 12px;
        color: var(--textSecondary);
    }
`);

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
`);

const CategoryFieldTitleClass = injectStyle("category-field-title", cl => `
    ${cl} {
        font-weight: 600;
        font-size: 14px;
    }
`);

const CategoryFieldDescriptionClass = injectStyle("category-field-desc", cl => `
    ${cl} {
        font-size: 12px;
        color: var(--textSecondary);
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
        overflow-wrap: anywhere;
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
    const canCreateCategory = Client.userIsAdmin || checkIsWorkspaceAdmin();
    const selected = meta.category
        ? allCategories.find(category => String(category.id) === meta.category) ?? null
        : null;

    const openCategorySelector = () => {
        dialogStore.addDialog(
            <ResourceSelectorModal
                title="Select a category"
                singular="category"
                resourceLabel="Categories"
                custom={allCategories}
                canCreateCustom={canCreateCategory}
                loadManaged={() => fetchAll<AppStore.ApplicationCategory>(next => callAPI(AppStore.browseStudioCategories({itemsPerPage: 250, next}))).then(categories =>
                    categories.map(category => ({
                        id: category.metadata.id,
                        title: category.specification.title,
                        description: category.specification.description ?? "",
                        isCustom: false,
                    })))}
                createCustom={(kind, managedId, title, description) =>
                    callAPI(AppStore.createCustomCategory({
                        kind,
                        ...(managedId != null ? {id: managedId} : {}),
                        ...(kind === "Custom" ? {specification: {title, description}} : {}),
                    })).then(result => result.id)
                }
                onSelect={id => props.onUpdateCustomMeta({category: id})}
                onCreated={() => {
                    setLandingPage(AppStore.emptyLandingPage);
                    return props.refreshPlacement();
                }}
            />,
            doNothing,
            true,
            slimModalStyle,
        );
    };

    return (
        <>
            <PanelSection title="Provider and category">
                <Label className="panel-field">
                    <span className="panel-field-label">Provider<MandatoryField /></span>
                    {props.eligibility?.providers.length ? (
                        <ServiceProviderSelector
                            serviceProvider={meta.provider}
                            serviceProviders={props.eligibility.providers.map(item => ({key: item.provider}))}
                            renderRow={CustomProviderRow}
                            renderSelectedRow={CustomProviderRow}
                            showLabel={false}
                            reserveLabelSpace={false}
                            onSelect={el => props.onUpdateCustomMeta({provider: el.key})}
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
                <Label className="panel-field">
                    <span className="panel-field-label">Category<MandatoryField /></span>
                    <button
                        type="button"
                        className={CategoryFieldClass}
                        onClick={openCategorySelector}
                        data-creator-field="custom.categoryId"
                    >
                        {selected ? (
                            <>
                                <span className={CategoryFieldTitleClass}>{selected.specification.title}</span>
                                {selected.specification.description ? (
                                    <span className={CategoryFieldDescriptionClass}>{selected.specification.description}</span>
                                ) : null}
                            </>
                        ) : (
                            <span className={CategoryFieldPlaceholderClass}>Select a category</span>
                        )}
                    </button>
                </Label>
            </PanelSection>
            <PanelSection title="Publication">
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
            </PanelSection>
        </>
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
            <PanelSection title="Add parameter">
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
                <WidgetDrawerButton key={item.type} item={item} onClick={() => props.onAddParameter(item.type)} />
            ))}
        </div>
    );
}

function WidgetDrawerButton(props: {
    item: typeof WIDGET_DRAWER_ITEMS[number];
    onClick: () => void;
}): React.ReactNode {
    const icon = widgetIcon(props.item.type);
    return (
        <TooltipV2 tooltip={props.item.description}>
            <button
                type="button"
                className={WidgetDrawerButtonClass}
                onClick={props.onClick}
                aria-label={`Add ${props.item.label} parameter`}
            >
                <Icon name={icon} size={16} color="textSecondary" />
                <Text fontSize={13}>{props.item.label}</Text>
            </button>
        </TooltipV2>
    );
}

// Map widget types to icons. The icons are from the heroicons set available in the codebase.
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

const WidgetDrawerGroupClass = injectStyle("creator-drawer-group", k => `
    ${k} {
        display: flex;
        flex-direction: column;
        gap: 4px;
    }
`);

const WidgetDrawerButtonClass = injectStyle("creator-drawer-button", k => `
    ${k} {
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

    ${k}:hover {
        border-color: var(--primaryMain);
        background: var(--rowActive);
    }

    ${k}:focus-visible {
        outline: 2px solid var(--primaryMain);
        outline-offset: 1px;
    }
`);
