import * as React from "react";
import { File } from "Files";
import FileSelector from "Files/FileSelector";
import { getFilenameFromPath } from "Utilities/FileUtilities";
import * as Types from ".";
import { Box, Flex, Label, Text, Select, Markdown, Button } from "ui-components";
import Input from "ui-components/Input";
import { EllipsedText } from "ui-components/Text";
import styled from "styled-components";
import * as Heading from "ui-components/Heading";
import * as Fuse from "fuse.js";


interface ParameterProps {
    parameter: Types.ApplicationParameter
    onChange: (name: string, value: any) => void
    value: string | number | InputValue
}

export const Parameter = (props: ParameterProps) => {
    let component = <div />
    switch (props.parameter.type) {
        case Types.ParameterTypes.InputFile:
            component = <InputFileParameter onChange={props.onChange} value={props.value as InputValue} parameter={props.parameter} />;
            break;
        case Types.ParameterTypes.InputDirectory:
            component = <InputDirectoryParameter onChange={props.onChange} value={props.value as InputValue} parameter={props.parameter} />;
            break;
        case Types.ParameterTypes.Integer:
            component = <IntegerParameter onChange={props.onChange} value={props.value} parameter={props.parameter} />;
            break;
        case Types.ParameterTypes.FloatingPoint:
            component = <FloatingParameter onChange={props.onChange} value={props.value} parameter={props.parameter} />;
            break;
        case Types.ParameterTypes.Text:
            component = <TextParameter onChange={props.onChange} value={props.value as string} parameter={props.parameter as Types.TextParameter} />;
            break;
        case Types.ParameterTypes.Boolean:
            component = <BooleanParameter onChange={props.onChange} parameter={props.parameter} />;
            break;
    }
    return (<>{component}<Box pb="1em" /></>);
};

interface InputValue {
    source: string
    destination: string
}

interface InputFileParameterProps extends ParameterProps {
    value: InputValue
}

const InputFileParameter = (props: InputFileParameterProps) => {
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

const InputDirectoryParameter = (props: InputFileParameterProps) => {
    const internalOnChange = (file: { path: string }) => {
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

interface TextParameterProps {
    parameter: Types.TextParameter
    value: string
    onChange: (name: string, value: string) => void
}

const TextParameter = (props: TextParameterProps) => {
    const internalOnChange = (event: React.ChangeEvent<HTMLInputElement>) => {
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

interface BooleanParameter { parameter: Types.ApplicationParameter, onChange: (name: string, value?: boolean) => void }
const BooleanParameter = (props: BooleanParameter) => {
    let options: BooleanParameterOption[] = [{ value: true, display: "Yes" }, { value: false, display: "No" }];
    if (props.parameter.optional) {
        options.unshift({ value: undefined, display: "" });
    }

    const internalOnChange = (event: React.ChangeEvent<HTMLInputElement>) => {
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
            <Select id="select" onChange={(e: React.ChangeEvent<HTMLInputElement>) => internalOnChange(e)} defaultValue="">
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

const GenericParameter = ({ parameter, children }: { parameter: Types.ApplicationParameter, children: any }) => (
    <>
        <Label fontSize={1} htmlFor={parameter.name}>
            <Flex>{parameter.title}{parameter.optional ? "" : <Text ml="4px" bold color="red">*</Text>}</Flex>
        </Label>
        {children}
        <Markdown source={parameter.description} />
    </>
);

interface OptionalParameterProps {
    parameters: Types.ApplicationParameter[]
    onUse: (aP: Types.ApplicationParameter) => void
}

interface OptionalParametersState {
    results: Types.ApplicationParameter[]
}

const OptionalParamsBox = styled(Box)`
    max-height: 35em;
    padding-top: 8px;
    padding-right: 8px;
    padding-bottom: 8px;
    overflow-y: auto;
`;

export class OptionalParameters extends React.Component<OptionalParameterProps, OptionalParametersState> {
    private fuse: Fuse<Types.ApplicationParameter>;
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

export class OptionalParameter extends React.Component<{ parameter: Types.ApplicationParameter, onUse: () => void }, { open: boolean }> {
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
                <OptionalParameter.Base onClick={(e) => (e.preventDefault(), this.setState({ open: !open }))}>
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