import * as React from "react";
import {BoolParameter, BoolSetter, BoolValidator} from "@/Applications/Jobs/Widgets/Bool";
import * as Heading from "@/ui-components/Heading";
import {Box, Button, Flex, Icon, Input, Label, Markdown, Relative, Text} from "@/ui-components";
import {FilesParameter, FilesSetter, FilesValidator} from "./GenericFiles";
import {EllipsedText, TextClass, TextP, TextSpan} from "@/ui-components/Text";
import {CSSProperties, useCallback, useEffect, useMemo, useRef, useState} from "react";
import Fuse from "fuse.js";
import {GenericTextParameter, GenericTextAreaAppParameter, GenericTextSetter, GenericTextValidator} from "@/Applications/Jobs/Widgets/GenericText";
import {EnumParameter, EnumSetter, EnumValidator} from "@/Applications/Jobs/Widgets/Enum";
import {PeerParameter, PeerSetter, PeerValidator} from "@/Applications/Jobs/Widgets/Peer";
import {LicenseParameter, LicenseSetter, LicenseValidator} from "@/Applications/Jobs/Widgets/License";
import {IngressParameter, IngressSetter, IngressValidator} from "@/Applications/Jobs/Widgets/Ingress";
import {NetworkIPParameter, NetworkIPSetter, NetworkIPValidator} from "@/Applications/Jobs/Widgets/NetworkIP";
import {PrivateNetworkParameter, PrivateNetworkSetter, PrivateNetworkValidator} from "@/Applications/Jobs/Widgets/PrivateNetwork";
import {ButtonClass} from "@/ui-components/Button";
import {JobCreateInput} from "./Reservation";
import {injectStyle, injectStyleSimple} from "@/Unstyled";
import {FlexCProps} from "@/ui-components/Flex";
import {Application, ApplicationParameter} from "@/Applications/AppStoreApi";
import {compute} from "@/UCloud";
import AppParameterValue = compute.AppParameterValue;
import {WorkflowParameter, WorkflowSetter, WorkflowValidator} from "@/Applications/Jobs/Widgets/Workflow";
import {MandatoryField} from "@/UtilityComponents";
import {ReadmeParameter} from "./Readme";
import {ModuleListParameter, ModuleListSetter, ModuleListValidator} from "@/Applications/Jobs/Widgets/ModuleList";
import {isLikelyMac} from "@/UtilityFunctions";

// Creating a new widget? Look here. Add it to the WidgetBody, validators and setters.
export type WidgetValidator = (param: ApplicationParameter) => WidgetValidationAnswer;
export type WidgetSetter = (param: ApplicationParameter, value: AppParameterValue) => void;

const WidgetBody: React.FunctionComponent<WidgetProps> = props => {
    switch (props.parameter.type) {
        case "boolean":
            return <BoolParameter {...props} parameter={props.parameter} />;
        case "input_directory":
        case "input_file":
            return <FilesParameter {...props} parameter={props.parameter} />;
        case "text":
        case "floating_point":
        case "integer":
            return <GenericTextParameter {...props} parameter={props.parameter} />;
        case "textarea":
            return <GenericTextAreaAppParameter {...props} parameter={props.parameter} />;
        case "enumeration":
            return <EnumParameter {...props} parameter={props.parameter} />;
        case "peer":
            return <PeerParameter {...props} parameter={props.parameter} />;
        case "license_server":
            return <LicenseParameter {...props} parameter={props.parameter} />;
        case "ingress":
            return <IngressParameter {...props} parameter={props.parameter} />;
        case "network_ip":
            return <NetworkIPParameter {...props} parameter={props.parameter} />;
        case "private_network":
            return <PrivateNetworkParameter {...props} parameter={props.parameter} />;
        case "workflow":
            return <WorkflowParameter {...props} parameter={props.parameter} />;
        case "readme":
            return <ReadmeParameter {...props} parameter={props.parameter} />;
        case "modules":
            return <ModuleListParameter {...props} parameter={props.parameter} />;
    }
};

const validators: WidgetValidator[] = [
    BoolValidator,
    GenericTextValidator,
    FilesValidator,
    EnumValidator,
    PeerValidator,
    LicenseValidator,
    IngressValidator,
    NetworkIPValidator,
    PrivateNetworkValidator,
    WorkflowValidator,
    ModuleListValidator,
];

const setters: WidgetSetter[] = [
    BoolSetter,
    GenericTextSetter,
    FilesSetter,
    EnumSetter,
    PeerSetter,
    LicenseSetter,
    IngressSetter,
    NetworkIPSetter,
    PrivateNetworkSetter,
    WorkflowSetter,
    ModuleListSetter,
];

export interface WidgetProps {
    application: Application;
    provider?: string;
    bindLinkToPort?: boolean;
    parameter: ApplicationParameter;
    errors: Record<string, string>;
    setWarning?: (warning: string) => void;
    setErrors: React.Dispatch<React.SetStateAction<Record<string, string>>>;

    // NOTE(Dan): This can only be done by the workflow parameter (of which there should only be at most one)
    injectWorkflowParameters: (parameters: ApplicationParameter[]) => void;
    onValueChange?: () => void;

    // HACK(Dan): If more of these are needed, consider adding a proper "attachment" abstraction.
    initScriptCache?: {
        parameter: ApplicationParameter;
        enabled: boolean;
        onChange: (enabled: boolean) => void;
    };
}

interface RootWidgetProps {
    onRemove?: () => void;
    active?: boolean;
    onActivate?: () => void;
    compact?: boolean;
    fieldGroup?: boolean;
    selected?: boolean;
    onSelectedChange?: (selected: boolean) => void;
    displayTitle?: string;
    onClear?: () => void;
}

export function FieldGroup({children}: React.PropsWithChildren): React.ReactNode {
    const onKeyDown = (event: React.KeyboardEvent<HTMLDivElement>) => {
        const target = event.target as HTMLElement;
        if (target instanceof HTMLSelectElement && (event.key === "Enter" || event.key === " ") &&
            !event.metaKey && !event.ctrlKey && !event.altKey) {
            event.preventDefault();
            if (target.showPicker) target.showPicker();
            else target.click();
            return;
        }
        if ((event.key === "Enter" || event.key === " ") && target.hasAttribute("data-field-activator") &&
            !event.metaKey && !event.ctrlKey && !event.altKey) {
            event.preventDefault();
            target.click();
        }
    };

    return <div data-field-group onKeyDown={onKeyDown}>{children}</div>;
}

export function FieldRow(props: {
    title: React.ReactNode;
    description?: string;
    control: React.ReactNode;
    bold?: boolean;
    required?: boolean;
    error?: string;
    onDelete?: () => void;
    onClear?: () => void;
    onInputCapture?: () => void;
    onChangeCapture?: () => void;
    parameterType?: string;
}): React.ReactNode {
    const rowRef = React.useRef<HTMLDivElement>(null);
    const action = props.onDelete ?? props.onClear;
    const actionLabel = "Clear";
    const runAction = () => {
        if (!action) return;
        const row = rowRef.current;
        const group = row?.closest<HTMLElement>("[data-field-group]");
        const rows = group ? Array.from(group.querySelectorAll<HTMLElement>("[data-field-row]")) : [];
        const rowIndex = row ? rows.indexOf(row) : -1;
        const sameTypeRows = rows.filter(candidate => candidate.dataset.paramType === props.parameterType);
        const typeIndex = row ? sameTypeRows.indexOf(row) : -1;
        action();
        window.requestAnimationFrame(() => {
            if (!group) return;
            const remainingRows = Array.from(group.querySelectorAll<HTMLElement>("[data-field-row]"));
            const remainingTypeRows = remainingRows.filter(candidate => candidate.dataset.paramType === props.parameterType);
            const nextRow = remainingTypeRows[Math.min(typeIndex, remainingTypeRows.length - 1)] ??
                remainingRows[Math.min(rowIndex, remainingRows.length - 1)];
            nextRow?.querySelector<HTMLElement>(
                "input:not([type='hidden']), select, textarea, [role='switch'], [data-field-activator]"
            )?.focus();
        });
    };
    const onKeyDown = (event: React.KeyboardEvent<HTMLDivElement>) => {
        if (!action) return;
        const target = event.target as HTMLElement;
        const deletePressed = event.key === "Delete" && !event.metaKey && !event.ctrlKey && !event.altKey;
        const primaryBackspace = event.key === "Backspace" && (isLikelyMac ? event.metaKey : event.ctrlKey) &&
            !event.altKey;
        if (!deletePressed && !primaryBackspace) return;

        const editableInput = target instanceof HTMLTextAreaElement ||
            (target instanceof HTMLInputElement && !target.readOnly &&
                ["text", "search", "tel", "url", "email", "password", "number"].includes(target.type));
        if (editableInput) return;

        event.preventDefault();
        event.stopPropagation();
        runAction();
    };

    return <div
        ref={rowRef}
        data-field-row
        data-has-description={props.description ? "true" : "false"}
        data-param-type={props.parameterType}
        data-component={props.parameterType ? "app-parameter" : undefined}
        className={FieldRowClass}
        onKeyDown={onKeyDown}
        onInputCapture={props.onInputCapture}
        onChangeCapture={props.onChangeCapture}
    >
        <div className={FieldDescriptionClass}>
            <span style={{fontWeight: props.bold ? 600 : 400}}>{props.title}</span>
            {props.required ? <MandatoryField /> : null}
            {!props.description ? null : <div className={FieldMarkdownClass}>
                <Markdown>{props.description}</Markdown>
            </div>}
        </div>
        <div className={FieldControlClass}>
            <div className={FieldControlBodyClass}>{props.control}</div>
            {!action ? null : <Button type="button" color="secondaryMain" onClick={runAction}>
                {actionLabel}
            </Button>}
            {props.error ? <TextP style={{gridColumn: "1 / -1"}} color="errorMain">{props.error}</TextP> : null}
        </div>
    </div>;
}

function InactiveWidget(props: React.PropsWithChildren<FlexCProps>) {
    return <Flex className={InactiveWidgetClass} {...props} />
}

const InactiveWidgetClass = injectStyle("inactive-widget", k => `
    ${k} {
        align-items: center;
        cursor: pointer;
    }

    ${k} > strong, ${k} > .${TextClass} {
        -webkit-user-select: none;
        user-select: none;
    }

    ${k} strong {
        margin-right: 16px;
        font-weight: 500;
        flex-shrink: 0;
    }

    ${k} > .${TextClass} {
        color: var(--textSecondary);
        flex-grow: 1;
    }

    ${k} > .${TextClass} > p {
        margin: 0;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
    }

    ${k} > .${ButtonClass} {
        margin-left: 16px;
        flex-shrink: 0;
    }
`);

const MarkdownWrapper = injectStyle("md-wrapper", k => `
    ${k} {
        color: var(--textSecondary);
        font-style: italic;
        -webkit-user-select: none;
        user-select: none;
    }
    
    ${k} p:first-child {
        margin-top: 0;
    }
    
    ${k} p:last-child {
        margin-bottom: 0;
    }
`);

export const Widget: React.FunctionComponent<WidgetProps & RootWidgetProps> = props => {
    const error = props.errors[props.parameter.name];
    const parameter = props.parameter;
    const [open, setOpen] = useState<boolean>(false);
    const toggleOpen = useCallback(() => {
        setOpen(o => !o);
    }, []);

    let body = <WidgetBody {...props} />;
    const moveUp = !props.fieldGroup && !props.compact && parameter.type === "peer" && (parameter.optional || props.onRemove);
    if (moveUp) {
        body = <Relative top={"-25px"}>{body}</Relative>;
    }

    if (props.active === true && props.parameter.type === "workflow" && !props.parameter.optional) {
        return body;
    }

    if (props.active === true && props.parameter.type === "readme") {
        return body;
    }

    if (props.active === true && props.parameter.type === "modules") {
        return body;
    }

    if (props.fieldGroup || props.compact) {
        const selected = parameter.optional ? props.selected === true : true;
        const updateSelectedState = () => {
            const onSelectedChange = props.onSelectedChange;
            if (!parameter.optional || !onSelectedChange) return;
            window.requestAnimationFrame(() => {
                const element = findElement(parameter);
                onSelectedChange(element?.value !== (element?.dataset.defaultValue ?? ""));
            });
        };

        return <FieldRow
            title={<WidgetLabel parameter={parameter} bold={selected} title={props.displayTitle} />}
            description={parameter.description}
            control={body}
            bold={selected}
            required={!parameter.optional}
            error={error}
            onDelete={props.onRemove}
            onClear={selected ? props.onClear : undefined}
            onInputCapture={updateSelectedState}
            onChangeCapture={updateSelectedState}
            parameterType={parameter.type}
        />;
    }

    if (props.active !== false) {
        return <Box data-param-type={props.parameter.type} data-component={`app-parameter`}>
            <Box>
                <Flex>
                    <Flex data-component={"param-title"}>
                        <WidgetLabel parameter={props.parameter} />
                        {parameter.optional ? null : <MandatoryField />}
                    </Flex>
                    {!parameter.optional || !props.onRemove ? null : (
                        <Text ml="auto" color="errorMain" cursor="pointer" mb="4px" onClick={props.onRemove} selectable={false}
                            data-component={"param-remove"} zIndex={1000}>
                            Remove
                            <Icon ml="6px" size={16} name="close" />
                        </Text>
                    )}
                </Flex>
            </Box>
            {body}
            {error ? <TextP color={"errorMain"}>{error}</TextP> : null}
            <div className={MarkdownWrapper}>
                <Markdown>{parameter.description}</Markdown>
            </div>
        </Box>;
    } else {
        return <Box data-param-type={props.parameter.type} data-component={"app-parameter"}>
            <InactiveWidget onClick={toggleOpen}>
                <strong data-component={"param-title"}>{parameter.title}</strong>
                {!open ? (
                    <EllipsedText width="200px">
                        <Markdown allowedElements={["p", "br", "strong", "b", "i", "em", "a"]}>
                            {parameter.description}
                        </Markdown>
                    </EllipsedText>
                ) : <Box flexGrow={1} />}

                <Button
                    type="button"
                    lineHeight={"16px"}
                    onClick={e => {
                        e.stopPropagation();
                        if (props.onActivate) props.onActivate();
                    }}
                >
                    Use
                </Button>
            </InactiveWidget>
            {open ? <div className={MarkdownWrapper}><Markdown>{parameter.description}</Markdown></div> : null}
        </Box>;
    }
};

injectStyleSimple("job-field-group-section", `
    padding: 12px 0;

    &:not(:first-child) {
        margin-top: 8px;
    }

    .field-group-section-title {
        font-weight: 600;
    }

    .field-group-section-description {
        margin-top: 4px;
        color: var(--textSecondary);
        font-size: 13px;
    }
`);

const FieldRowClass = injectStyleSimple("job-field-row", `
    display: grid;
    grid-template-columns: minmax(180px, 2fr) minmax(240px, 3fr);
    min-height: 64px;
    background: var(--backgroundCard);
    column-gap: 24px;

    &:has(> div:nth-child(2) > div:first-child :focus) > div:first-child {
        font-style: italic;
    }

    &[data-has-description="false"] > div:first-child {
        align-self: center;
    }

    @media (max-width: 700px) {
        grid-template-columns: minmax(0, 1fr);
    }
`);

const FieldDescriptionClass = injectStyleSimple("job-field-description", `
    min-width: 0;
    padding: 12px 0px;
`);

const FieldControlClass = injectStyleSimple("job-field-control", `
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 8px;
    align-content: center;
    align-items: start;
    min-width: 0;
    padding: 10px 0;
`);

const FieldControlBodyClass = injectStyleSimple("job-field-control-body", `
    min-width: 0;

    > * {
        max-width: 100%;
    }
`);

const FieldMarkdownClass = injectStyleSimple("job-field-description-markdown", `
    margin-top: 3px;
    color: var(--textSecondary);
    font-size: 13px;

    p {
        margin: 0;
    }
`);

const OptionalWidgetSearchWrapper = injectStyleSimple("optional-widget-search", `
    display: grid;
    grid-template-columns: 1fr;
    grid-gap: 10px;
    max-height: 35em;
    padding-top: 8px;
    padding-right: 8px;
    padding-bottom: 8px;
    overflow-y: auto;
`);

const DefaultParameterTitle: Record<string, string> = {
    "input_file": "File name",
    "input_directory": "Folder name",
    "ingress": "Public link",
    "network_ip": "Public IP",
    "private_network": "Private network"
};

function WidgetLabel(props: {parameter: ApplicationParameter; bold?: boolean; title?: string}): React.ReactNode {
    const style: CSSProperties = {fontWeight: props.bold ? 600 : 400};
    const suppliedTitle = props.title;
    switch (props.parameter.type) {
        case "ingress":
        case "input_directory":
        case "input_file":
        case "network_ip":
        case "private_network":
            const title = suppliedTitle ?? (props.parameter.title ? props.parameter.title : DefaultParameterTitle[props.parameter.type]);
            return <Label htmlFor={widgetId(props.parameter) + "visual"} style={style}> {title}</Label >
    }
    return <Label htmlFor={widgetId(props.parameter)} style={style}>{suppliedTitle ?? props.parameter.title}</Label>
}

export function findElement<HTMLElement = HTMLInputElement>(param: {name: string}): HTMLElement | null {
    return document.getElementById(widgetId(param)) as HTMLElement | null;
}

export function WidgetSetProvider(param: {name: string}, provider: string): void {
    const elem = findElement(param);
    if (elem) {
        if (provider.length === 0) {
            elem.removeAttribute("data-provider");
        } else {
            elem.setAttribute("data-provider", provider);
        }
    }
}

export const OptionalWidgetSearch: React.FunctionComponent<{
    pool: ApplicationParameter[];
    mapper: (p: ApplicationParameter) => React.ReactNode;
}> = ({pool, mapper}) => {
    const currentTimeout = useRef<number>(-1);
    const [results, setResults] = useState(pool);
    const searchRef = useRef<HTMLInputElement>(null);

    const fuse = useMemo(() => {
        return new Fuse(pool, {
            shouldSort: true,
            threshold: 0.6,
            location: 0,
            distance: 100,
            minMatchCharLength: 1,
            keys: [
                "title",
                "description"
            ]
        });
    }, [pool]);

    const search = useCallback((term: string, delay = 300) => {
        if (currentTimeout.current !== -1) clearTimeout(currentTimeout.current);

        if (term === "") {
            setResults(pool);
        } else {
            currentTimeout.current = window.setTimeout(() => {
                const newResults = fuse.search(term);
                setResults(newResults.map(it => it.item));
            }, delay);
        }
    }, [fuse, pool]);


    useEffect(() => {
        search(searchRef.current!.value, 0);
    }, [pool]);


    return <Box>
        <Flex mb={16} alignItems={"center"}>
            <Box flexGrow={1}>
                <Heading.h5>Optional parameters</Heading.h5>
            </Box>
            <Box flexShrink={0}>
                <Input
                    inputRef={searchRef}
                    className={JobCreateInput}
                    placeholder={"Search"}
                    onChange={(e) => search(e.target.value)}
                />
            </Box>
        </Flex>
        <div className={OptionalWidgetSearchWrapper}>
            {results.map(it => mapper(it))}
        </div>
    </Box>;
};


interface ValidatedWidgets {
    errors: Record<string, string>;
    values: Record<string, AppParameterValue>;
}

export function validateWidgets(params: ApplicationParameter[]): ValidatedWidgets {
    const result: ValidatedWidgets = {errors: {}, values: {}};
    for (const param of params) {
        for (const validator of validators) {
            const validation = validator(param);
            if (!validation.valid) {
                result.errors[param.name] = validation.message ?? "Invalid";
            }

            if (validation.value) {
                result.values[param.name] = validation.value;
            }
        }

        if (!result.errors[param.name] && !result.values[param.name] && !param.optional && param.defaultValue == null) {
            result.errors[param.name] = "A value is missing for this mandatory field";
        }
    }
    return result;
}

export function setWidgetValues(values: {param: ApplicationParameter, value: AppParameterValue}[]): void {
    for (const value of values) {
        for (const setter of setters) {
            setter(value.param, value.value);
        }
    }
}

export interface WidgetValidationAnswer {
    valid: boolean;
    message?: string;
    value?: AppParameterValue;
}

export function widgetId(param: {name: string}): string {
    return `app-param-${param.name}`;
}

export function clearWidgetValue(parameter: ApplicationParameter): void {
    const ids = [widgetId(parameter)];
    if (parameter.type === "peer") {
        ids.push(widgetId(parameter) + "name", widgetId(parameter) + "job");
    } else if (parameter.type === "input_directory" || parameter.type === "input_file" ||
        parameter.type === "ingress" || parameter.type === "network_ip" || parameter.type === "private_network") {
        ids.push(widgetId(parameter) + "visual");
    }
    if (parameter.type === "ingress") ids.push(widgetId(parameter) + "-port");

    for (const id of ids) {
        const element = document.getElementById(id) as HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement | null;
        if (!element) continue;
        element.value = element.dataset.defaultValue ?? "";
        element.removeAttribute("data-provider");
        element.dispatchEvent(new Event("input", {bubbles: true}));
        element.dispatchEvent(new Event("change", {bubbles: true}));
    }
}
