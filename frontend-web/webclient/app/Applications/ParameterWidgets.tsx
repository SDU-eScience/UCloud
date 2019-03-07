import * as React from "react";
import { File } from "Files";
import FileSelector from "Files/FileSelector";
import { getFilenameFromPath } from "Utilities/FileUtilities";
import { ParameterTypes, ApplicationParameter } from ".";
import { Box, Flex, Label, Text, Select, Markdown, Button } from "ui-components";
import Input from "ui-components/Input";
import { EllipsedText } from "ui-components/Text";
import styled from "styled-components";
import * as Heading from "ui-components/Heading";
import * as Fuse from "fuse.js";

const parameterTypeToComponent = (type) => {
    switch (type) {
        case ParameterTypes.InputFile:
            return InputFileParameter;
        case ParameterTypes.InputDirectory:
            return InputDirectoryParameter;
        case ParameterTypes.Integer:
            return IntegerParameter;
        case ParameterTypes.FloatingPoint:
            return FloatingParameter;
        case ParameterTypes.Text:
            return TextParameter;
        case ParameterTypes.Boolean:
            return BooleanParameter;
        default:
            console.warn(`Unknown parameter type: ${type}`);
            return GenericNumberParameter; // Must be a constructor or have call signatures
    }
};

interface ParameterProps {
    parameter: ApplicationParameter
    onChange: (name: string, value: any) => void
    value: string | number | object
}
export const Parameter = (props: ParameterProps) => {
    let Component = parameterTypeToComponent(props.parameter.type);
    return (<><Component {...props} /><Box pb="1em" /></>);
};

const InputFileParameter = (props) => {
    const internalOnChange = (file: { path: string }) => {
        props.onChange(props.parameter.name, file.path ? {
            source: file.path,
            destination: getFilenameFromPath(file.path)
        } : undefined);
    };
    const path = props.value ? props.value.source : "";
    return (
        <GenericParameter parameter={props.parameter}>
            <FileSelector
                remove={!!path ? () => internalOnChange({ path: "" }) : undefined}
                onFileSelect={file => internalOnChange(file)}
                path={path}
                isRequired={!props.parameter.optional}
            /* allowUpload */
            />
        </GenericParameter>
    );
};

const InputDirectoryParameter = (props) => {
    const internalOnChange = file => {
        props.onChange(props.parameter.name, {
            source: file.path,
            destination: getFilenameFromPath(file.path)
        });
    };
    const path = props.value ? props.value.source : "";
    return (
        <GenericParameter parameter={props.parameter}>
            <FileSelector
                onFileSelect={(file: File) => internalOnChange(file)}
                path={path}
                canSelectFolders
                onlyAllowFolders
                isRequired={!props.parameter.optional}
            />
        </GenericParameter>
    )
}

const TextParameter = (props) => {
    const internalOnChange = event => {
        event.preventDefault();
        props.onChange(props.parameter.name, event.target.value);
    };

    let placeholder = !!props.parameter.defaultValue ? props.parameter.defaultValue.value : undefined;

    return (
        <GenericParameter parameter={props.parameter}>
            <Input
                placeholder={placeholder}
                required={!props.parameter.optional}
                type="text" onChange={e => internalOnChange(e)}
                value={props.value}
            />
        </GenericParameter>
    );
};


type BooleanParameterOption = { value?: boolean, display: string }

interface BooleanParameter { parameter: ApplicationParameter, onChange: (name: string, value?: boolean) => void }
const BooleanParameter = (props: BooleanParameter) => {
    let options: BooleanParameterOption[] = [{ value: true, display: "Yes" }, { value: false, display: "No" }];
    if (props.parameter.optional) {
        options.unshift({ value: undefined, display: "" });
    }

    const internalOnChange = event => {
        let value: boolean | undefined;
        switch (event.target.value) {
            case "Yes": value = true; break;
            case "No": value = false; break;
            case "": value = undefined; break;
        }
        props.onChange(props.parameter.name, value);
        event.preventDefault();
    };

    return (
        <GenericParameter parameter={props.parameter}>
            <Select id="select" onChange={e => internalOnChange(e)} defaultValue="">
                <option></option>
                <option>Yes</option>
                <option>No</option>
            </Select>
        </GenericParameter>
    );
};

const GenericNumberParameter = (props) => {
    const internalOnChange = (event: React.ChangeEvent<HTMLInputElement>) => {
        event.preventDefault();

        if (event.target.value === "") {
            props.onChange(props.parameter.name, undefined);
        } else {
            let value = props.parseValue(event.target.value);
            if (!isNaN(value)) {
                props.onChange(props.parameter.name, value);
            }
        }
    };

    let value = (props.value != null) ? props.value : "";


    let placeholder = typeof props.parameter.defaultValue === "number" ? props.parameter.defaultValue.value : undefined;

    let baseField = (
        <Input
            placeholder={placeholder}
            required={!props.parameter.optional} name={props.parameter.name}
            type="number"
            step="any"
            value={value}
            id={props.parameter.name}
            /* label={hasLabel ? props.parameter.unitName : "Number"} */
            onChange={e => internalOnChange(e)} />
    );

    let slider: React.ReactNode = null;
    if (props.parameter.min !== null && props.parameter.max !== null) {
        slider = (
            <Input
                mt="2px"
                noBorder
                min={props.parameter.min}
                max={props.parameter.max}
                step={props.parameter.step}
                type="range"
                value={value}
                onChange={e => internalOnChange(e)}
            />
        );
    }

    return (
        <GenericParameter parameter={props.parameter}>
            {baseField}
            {slider}
        </GenericParameter>
    );
};

const IntegerParameter = (props) => {
    let childProps = { ...props };
    childProps.parseValue = (it: string) => parseInt(it);
    return <GenericNumberParameter {...childProps} />;
};

const FloatingParameter = (props) => {
    let childProps = { ...props };
    childProps.parseValue = (it: string) => parseFloat(it);
    return <GenericNumberParameter {...childProps} />;
};

const GenericParameter = ({ parameter, children }: { parameter: ApplicationParameter, children: any }) => (
    <>
        <Label fontSize={1} htmlFor={parameter.name}>
            <Flex>{parameter.title}{parameter.optional ? "" : <Text ml="4px" bold color="red">*</Text>}</Flex>
        </Label>
        {children}
        <Markdown source={parameter.description} />
    </>
);

interface OptionalParameterProps {
    parameters: ApplicationParameter[]
    onUse: (ApplicationParameter) => void
}

interface OptionalParametersState {
    results: ApplicationParameter[]
}

const OptionalParamsBox = styled(Box)`
    max-height: 35em;
    padding-top: 8px;
    padding-right: 8px;
    padding-bottom: 8px;
    overflow-y: auto;
`;

export class OptionalParameters extends React.Component<OptionalParameterProps, OptionalParametersState> {
    private fuse: Fuse<ApplicationParameter>;
    private currentTimeout: number = -1;
    private searchField = React.createRef<HTMLInputElement>();

    constructor(props: OptionalParameterProps) {
        super(props);
        this.state = { results: props.parameters };
        this.initFuse();
    }

    private initFuse() {
        this.fuse = new Fuse(this.props.parameters, {
            shouldSort: true,
            threshold: 0.6,
            location: 0,
            distance: 100,
            maxPatternLength: 32,
            minMatchCharLength: 1,
            keys: [
                "title",
                "description"
            ]
        });

    }

    componentDidUpdate(prevProps: OptionalParameterProps) {
        if (this.props.parameters !== prevProps.parameters) {
            this.initFuse();
            const current = this.searchField.current;
            if (current != null) this.search(current.value);
        }
    }

    private search(searchTerm: string) {
        if (this.currentTimeout !== -1) clearTimeout(this.currentTimeout);

        if (searchTerm === "") {
            this.setState({ results: this.props.parameters });
        } else {
            this.currentTimeout = setTimeout(() => {
                const results = this.fuse.search(searchTerm);
                this.setState({ results });
            }, 300);
        }
    }

    render() {
        const { onUse } = this.props;
        const { results } = this.state;
        const components = results.map((p, i) => <OptionalParameter key={i} parameter={p} onUse={() => onUse(p)} />);

        return (
            <>
                <Flex mb={16} alignItems={"center"}>
                    <Box flexGrow={1}>
                        <Heading.h4>Optional Parameters ({results.length})</Heading.h4>
                    </Box>
                    <Box flexShrink={0}>
                        <Input placeholder={"Search..."} ref={this.searchField} onChange={e => this.search(e.target.value)} />
                    </Box>
                </Flex>
                <OptionalParamsBox>{components}</OptionalParamsBox>
            </>
        );

    }
}

export class OptionalParameter extends React.Component<{ parameter: ApplicationParameter, onUse: () => void }, { open: boolean }> {
    constructor(props) {
        super(props);
        this.state = { open: false };
    }

    private static Base = styled(Flex)`
        align-items: center;
        cursor: pointer;

        strong, & > ${EllipsedText} {
            user-select: none;
        }

        strong {
            margin-right: 16px;
            font-weight: bold;
            flex-shrink: 0;
        }

        & > ${EllipsedText} {
            color: ${(props) => props.theme.colors.gray};
            flex-grow: 1;
        }

        & > ${EllipsedText} > p {
            margin: 0;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        & > ${Button} {
            margin-left: 16px;
            flex-shrink: 0;
        }
    `;

    render() {
        const { parameter, onUse } = this.props;
        const { open } = this.state;

        return (
            <Box mb={8}>
                <OptionalParameter.Base onClick={(e) => { e.preventDefault(); this.setState({ open: !open }) }}>
                    <strong>{parameter.title}</strong>
                    {!open ?
                        <EllipsedText>
                            <Markdown
                                source={parameter.description}
                                allowedTypes={["text", "root", "paragraph"]} />
                        </EllipsedText>
                        : <Box flexGrow={1} />}

                    <Button
                        lineHeight={"16px"}
                        onClick={(e) => {
                            e.stopPropagation();
                            e.preventDefault();
                            onUse();
                        }}>
                        Use
                </Button>
                </OptionalParameter.Base>
                {open ? <Markdown source={parameter.description} /> : null}
            </Box>
        );
    }
}