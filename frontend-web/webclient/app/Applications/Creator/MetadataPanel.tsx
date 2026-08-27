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
                onNameChange={props.onNameChange}
                onVersionChange={props.onVersionChange}
            />
            <PresentationSection
                application={application}
                onUpdateMetadata={props.onUpdateMetadata}
            />
            <SoftwareSection
                application={application}
                isCustom={isCustom}
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
            <GroupFlavorSection
                draft={draft}
                onUpdateCustomMeta={props.onUpdateCustomMeta}
                groups={props.customGroups}
            />
            {isCustom ? (
                <CustomFieldsSection
                    draft={draft}
                    onUpdateCustomMeta={props.onUpdateCustomMeta}
                    eligibility={props.customEligibility}
                    categories={props.customCategories}
                />
            ) : (
                <ManagedFieldsSection
                    application={application}
                    onUpdateModules={props.onUpdateModules}
                    onUpdateUcx={props.onUpdateUcx}
                    onUpdateExtensions={props.onUpdateExtensions}
                />
            )}
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
    onNameChange: (name: string) => void;
    onVersionChange: (version: string) => void;
}): React.ReactNode {
    return (
        <PanelSection title="Identity">
            <Label className="panel-field">
                <span className="panel-field-label">Name</span>
                <Input
                    className={PanelInputClass}
                    value={props.application.name}
                    onChange={e => props.onNameChange(e.target.value)}
                    disabled={!props.editableName}
                    placeholder="application-name"
                    data-creator-field="name"
                />
            </Label>
            <Label className="panel-field">
                <span className="panel-field-label">Version</span>
                <Input
                    className={PanelInputClass}
                    value={props.application.version}
                    onChange={e => props.onVersionChange(e.target.value)}
                    disabled={!props.editableVersion}
                    placeholder="1.0.0"
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
                    <span className="panel-field-label">Container image</span>
                    <Input
                        className={PanelInputClass}
                        value={image}
                        onChange={e => props.onUpdateSoftware({type: "Container", image: e.target.value})}
                        placeholder="dreg.cloud.sdu.dk/image:tag"
                        data-creator-field="software.image"
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

function renderSoftwareFields(
    software: A2Software,
    onUpdate: (software: A2Software) => void,
): React.ReactNode {
    switch (software.type) {
        case "Container":
            return (
                <Label className="panel-field">
                    <span className="panel-field-label">Container image</span>
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
                    <span className="panel-field-label">VM image</span>
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
                    <span className="panel-field-label">UCX image</span>
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

function GroupFlavorSection(props: {
    draft: CreatorDraft;
    onUpdateCustomMeta: (patch: Partial<CreatorCustomMeta>) => void;
    groups?: AppCatalogCustomGroup[];
}): React.ReactNode {
    const {draft} = props;
    const meta = draft.customMeta;
    if (!meta) {
        // Managed applications do not have custom group or flavor. Show nothing.
        return null;
    }
    return (
        <PanelSection title="Group and flavor">
            <Label className="panel-field">
                <span className="panel-field-label">Flavor</span>
                <Input
                    className={PanelInputClass}
                    value={meta.flavor}
                    onChange={e => props.onUpdateCustomMeta({flavor: e.target.value})}
                    placeholder="The flavor name for this application"
                    data-creator-field="custom.flavorName"
                />
            </Label>
            <Label className="panel-field">
                <span className="panel-field-label">Group</span>
                {props.groups?.length ? (
                    <Select
                        className={PanelInputClass}
                        value={meta.group}
                        onChange={e => props.onUpdateCustomMeta({group: e.target.value})}
                        data-creator-field="custom.groupId"
                    >
                        {meta.group && !props.groups.some(item => String(item.id) === meta.group) ? (
                            <option value={meta.group}>{meta.group}</option>
                        ) : null}
                        {props.groups.map(item => (
                            <option key={item.id} value={String(item.id)}>
                                {item.specification.title}
                            </option>
                        ))}
                    </Select>
                ) : (
                    <Input
                        className={PanelInputClass}
                        value={meta.group}
                        onChange={e => props.onUpdateCustomMeta({group: e.target.value})}
                        placeholder="The custom group id"
                        data-creator-field="custom.groupId"
                    />
                )}
            </Label>
        </PanelSection>
    );
}

// Custom fields: provider, category, publication
// -------------------------------------------------------------------------------------------------------------------

function CustomFieldsSection(props: {
    draft: CreatorDraft;
    onUpdateCustomMeta: (patch: Partial<CreatorCustomMeta>) => void;
    eligibility?: AppEditorCustomEligibilityResponse | null;
    categories?: AppCatalogCustomCategory[];
}): React.ReactNode {
    const {draft} = props;
    const meta = draft.customMeta;
    if (!meta) return null;

    return (
        <>
            <PanelSection title="Provider and category">
                <Label className="panel-field">
                    <span className="panel-field-label">Provider</span>
                    {props.eligibility?.providers.length ? (
                        <Select
                            className={PanelInputClass}
                            value={meta.provider}
                            onChange={e => props.onUpdateCustomMeta({provider: e.target.value})}
                            data-creator-field="custom.serviceProvider"
                        >
                            {meta.provider && !props.eligibility.providers.some(item => item.provider === meta.provider) ? (
                                <option value={meta.provider}>{meta.provider}</option>
                            ) : null}
                            {props.eligibility.providers.map(item => (
                                <option key={item.provider} value={item.provider}>
                                    {item.provider}{item.eligible ? "" : " (not eligible)"}
                                </option>
                            ))}
                        </Select>
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
                    <span className="panel-field-label">Category</span>
                    {props.categories?.length ? (
                        <Select
                            className={PanelInputClass}
                            value={meta.category}
                            onChange={e => props.onUpdateCustomMeta({category: e.target.value})}
                            data-creator-field="custom.categoryId"
                        >
                            {meta.category && !props.categories.some(item => String(item.id) === meta.category) ? (
                                <option value={meta.category}>{meta.category}</option>
                            ) : null}
                            {props.categories.map(item => (
                                <option key={item.id} value={String(item.id)}>
                                    {item.specification.title}
                                </option>
                            ))}
                        </Select>
                    ) : (
                        <Input
                            className={PanelInputClass}
                            value={meta.category}
                            onChange={e => props.onUpdateCustomMeta({category: e.target.value})}
                            placeholder="The custom category id"
                            data-creator-field="custom.categoryId"
                        />
                    )}
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
