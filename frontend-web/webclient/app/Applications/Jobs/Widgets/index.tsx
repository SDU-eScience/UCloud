import * as React from "react";
import {BoolParameter, BoolSetter, BoolValidator} from "@/Applications/Jobs/Widgets/Bool";
import * as UCloud from "@/UCloud";
import * as Heading from "@/ui-components/Heading";
import {Box, Button, Flex, Icon, Input, Label, Markdown, Relative, Text} from "@/ui-components";
import {FilesParameter, FilesSetter, FilesValidator} from "./GenericFiles";
import {EllipsedText, TextClass, TextP, TextSpan} from "@/ui-components/Text";
import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import Fuse from "fuse.js";
import {GenericTextParameter, GenericTextAreaAppParameter, GenericTextSetter, GenericTextValidator} from "@/Applications/Jobs/Widgets/GenericText";
import {EnumParameter, EnumSetter, EnumValidator} from "@/Applications/Jobs/Widgets/Enum";
import {PeerParameter, PeerSetter, PeerValidator} from "@/Applications/Jobs/Widgets/Peer";
import {LicenseParameter, LicenseSetter, LicenseValidator} from "@/Applications/Jobs/Widgets/License";
import {IngressParameter, IngressSetter, IngressValidator} from "@/Applications/Jobs/Widgets/Ingress";
import {NetworkIPParameter, NetworkIPSetter, NetworkIPValidator} from "@/Applications/Jobs/Widgets/NetworkIP";
import {ButtonClass} from "@/ui-components/Button";
import {JobCreateInput} from "./Reservation";
import {injectStyle, injectStyleSimple} from "@/Unstyled";
import {FlexCProps} from "@/ui-components/Flex";
import {ApplicationParameter} from "@/Applications/AppStoreApi";
import {compute} from "@/UCloud";
import AppParameterValue = compute.AppParameterValue;

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
];

export interface WidgetProps {
    provider?: string;
    parameter: ApplicationParameter;
    errors: Record<string, string>;
    setWarning?: (warning: string) => void;
    setErrors: (errors: Record<string, string>) => void;
}

interface RootWidgetProps {
    onRemove?: () => void;
    active?: boolean;
    onActivate?: () => void;
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
        font-weight: bold;
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
        margin-top: 8px;
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
    const moveUp = parameter.type === "peer" && (parameter.optional || props.onRemove);
    if (moveUp) {
        body = <Relative top={"-25px"}>{body}</Relative>;
    }

    if (props.active !== false) {
        return <Box data-param-type={props.parameter.type} data-component={`app-parameter`}>
            <Label htmlFor={`app-param-${parameter.name}`} style={{display: "block"}}>
                <Flex>
                    <Flex data-component={"param-title"}>
                        {parameter.title}
                        {parameter.optional ? null : <MandatoryField />}
                    </Flex>
                    {!parameter.optional || !props.onRemove ? null : (
                        <>
                            <Box ml="auto" />
                            <Text color="errorMain" cursor="pointer" mb="4px" onClick={props.onRemove} selectable={false}
                                data-component={"param-remove"} zIndex={1000}>
                                Remove
                                <Icon ml="6px" size={16} name="heroXMark" />
                            </Text>
                        </>
                    )}
                </Flex>
            </Label>
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
                        <Markdown allowedElements={["text", "paragraph"]}>
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
                <Heading.h4>Optional Parameters</Heading.h4>
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

export const MandatoryField: React.FunctionComponent = () => <TextSpan ml="4px" bold color="errorMain">*</TextSpan>;
