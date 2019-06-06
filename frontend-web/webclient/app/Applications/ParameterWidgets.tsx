import * as React from "react";
import FileSelector from "Files/FileSelector";
import * as Types from ".";
import {Box, Flex, Label, Text, Select, Markdown, Button, Icon} from "ui-components";
import Input, {InputLabel} from "ui-components/Input";
import {EllipsedText} from "ui-components/Text";
import styled from "styled-components";
import * as Heading from "ui-components/Heading";
import * as Fuse from "fuse.js";
import {Cloud} from "Authentication/SDUCloudObject";
import {replaceHomeFolder, resolvePath} from "Utilities/FileUtilities";
import {addTrailingSlash} from "UtilityFunctions";


interface ParameterProps {
    initialSubmit: boolean
    parameter: Types.ApplicationParameter
    parameterRef: React.RefObject<HTMLInputElement | HTMLSelectElement>
    onParamRemove: () => void
}

export const Parameter = (props: ParameterProps) => {
    let component = (<div />);
    switch (props.parameter.type) {
        case Types.ParameterTypes.InputFile:
            component = <InputFileParameter {...props} />;
            break;
        case Types.ParameterTypes.InputDirectory:
            component = <InputDirectoryParameter {...props} />;
            break;
        case Types.ParameterTypes.Integer:
            component = <IntegerParameter
                onParamRemove={props.onParamRemove}
                initialSubmit={props.initialSubmit}
                parameterRef={props.parameterRef as React.RefObject<HTMLInputElement>}
                parameter={props.parameter}
            />;
            break;
        case Types.ParameterTypes.FloatingPoint:
            component = <FloatingParameter
                onParamRemove={props.onParamRemove}
                initialSubmit={props.initialSubmit}
                parameterRef={props.parameterRef as React.RefObject<HTMLInputElement>}
                parameter={props.parameter}
            />;
            break;
        case Types.ParameterTypes.Text:
            component = <TextParameter
                onParamRemove={props.onParamRemove}
                initialSubmit={props.initialSubmit}
                parameterRef={props.parameterRef}
                parameter={props.parameter}
            />;
            break;
        case Types.ParameterTypes.Boolean:
            component = <BooleanParameter
                onParamRemove={props.onParamRemove}
                initialSubmit={props.initialSubmit}
                parameterRef={props.parameterRef as React.RefObject<HTMLSelectElement>}
                parameter={props.parameter}
            />;
            break;
    }
    return (<>{component}<Box pb="1em" /></>);
};

interface InputFileParameterProps extends ParameterProps {
    onRemove?: () => void
    defaultValue?: string
}

const InputFileParameter = (props: InputFileParameterProps) => (
    <GenericParameter parameter={props.parameter} onRemove={props.onParamRemove}>
        <FileSelector
            showError={props.initialSubmit || props.parameter.optional}
            key={props.parameter.name}
            path={props.parameterRef.current && props.parameterRef.current.value || ""}
            onFileSelect={file => {props.parameterRef.current!.value = resolvePath(replaceHomeFolder(file.path, Cloud.homeFolder))}}
            inputRef={props.parameterRef as React.RefObject<HTMLInputElement>}
            isRequired={!props.parameter.optional}
            unitName={props.parameter.unitName}
        />
    </GenericParameter>
);

export const InputDirectoryParameter = (props: InputFileParameterProps) => (
    <GenericParameter parameter={props.parameter} onRemove={props.onParamRemove}>
        <FileSelector
            defaultValue={props.defaultValue}
            showError={props.initialSubmit || props.parameter.optional}
            key={props.parameter.name}
            path={props.parameterRef.current && props.parameterRef.current.value || ""}
            onFileSelect={file => {props.parameterRef.current!.value = addTrailingSlash(resolvePath(replaceHomeFolder(file.path, Cloud.homeFolder)))}}
            inputRef={props.parameterRef as React.RefObject<HTMLInputElement>}
            canSelectFolders
            onlyAllowFolders
            isRequired={!props.parameter.optional}
            unitName={props.parameter.unitName}
            remove={props.onRemove}
        />
    </GenericParameter>
);

interface TextParameterProps extends ParameterProps {
    parameter: Types.TextParameter
}

const TextParameter = (props: TextParameterProps) => {
    let placeholder = !!props.parameter.defaultValue ? props.parameter.defaultValue.value : undefined;
    const hasUnitName = !!props.parameter.unitName;
    return (
        <GenericParameter parameter={props.parameter} onRemove={props.onParamRemove}>
            <Input
                showError={props.initialSubmit || props.parameter.optional}
                key={props.parameter.name}
                ref={props.parameterRef as React.RefObject<HTMLInputElement>}
                placeholder={placeholder}
                required={!props.parameter.optional}
                type="text"
                rightLabel={!!props.parameter.unitName}
            />
            {hasUnitName ? <InputLabel rightLabel>{props.parameter.unitName}</InputLabel> : null}
        </GenericParameter>
    );
};

type BooleanParameterOption = {value?: boolean, display: string}

interface BooleanParameter extends ParameterProps {
    parameter: Types.BooleanParameter
    parameterRef: React.RefObject<HTMLSelectElement>
}
const BooleanParameter = (props: BooleanParameter) => {
    let options: BooleanParameterOption[] = [{value: true, display: "Yes"}, {value: false, display: "No"}];
    if (props.parameter.optional) {
        options.unshift({value: undefined, display: ""});
    }

    const defaultValue = props.parameter.defaultValue ? props.parameter.defaultValue.value : null;

    const hasUnitName = !!props.parameter.unitName;

    return (
        <GenericParameter parameter={props.parameter} onRemove={props.onParamRemove}>
            <Flex>
                <Select
                    showError={props.initialSubmit || props.parameter.optional}
                    id="select"
                    selectRef={props.parameterRef}
                    key={props.parameter.name}
                    rightLabel={hasUnitName}
                    required={!props.parameter.optional}
                >
                    <option />
                    <option selected={defaultValue === true}>Yes</option>
                    <option selected={defaultValue === false}>No</option>
                </Select>
                {hasUnitName ? <InputLabel rightLabel margin="0">{props.parameter.unitName}</InputLabel> : null}
            </Flex>
        </GenericParameter>
    );
};

const GenericNumberParameter = (props: NumberParameterProps) => {
    const {parameter, parameterRef} = props;
    const optSliderRef = React.useRef<HTMLInputElement>(null);
    const hasUnitName = !!props.parameter.unitName;
    if (optSliderRef.current && parameterRef.current && parameterRef.current.value !== optSliderRef.current!.value)
        optSliderRef.current.value = parameterRef.current.value;

    let baseField = (
        <Flex>
            <Input
                showError={props.initialSubmit || props.parameter.optional}
                required={!props.parameter.optional}
                name={props.parameter.name}
                type="number"
                step="any"
                ref={parameterRef}
                key={parameter.name}
                onChange={e => {
                    if (optSliderRef.current) optSliderRef.current.value = e.target.value
                }}
                max={parameter.max != null ? parameter.max : undefined}
                min={parameter.min != null ? parameter.min : undefined}
                rightLabel={hasUnitName}
            />
            {hasUnitName ? <InputLabel rightLabel>{props.parameter.unitName}</InputLabel> : null}
        </Flex>
    );

    let slider: React.ReactNode = null;
    if (parameter.min !== null && parameter.max !== null) {
        slider = (
            <Input
                showError={props.initialSubmit || props.parameter.optional}
                key={`${parameter.name}-slider`}
                mt="2px"
                noBorder
                ref={optSliderRef}
                min={parameter.min}
                max={parameter.max}
                step={parameter.step!}
                onChange={e => parameterRef.current!.value = e.target.value}
                type="range"
            />
        );
    }

    return (
        <GenericParameter parameter={props.parameter} onRemove={props.onParamRemove}>
            {baseField}
            {slider}
        </GenericParameter>
    );
};

interface NumberParameterProps extends ParameterProps {
    parameter: Types.NumberParameter
    parameterRef: React.RefObject<HTMLInputElement>
    initialSubmit: boolean
}

const IntegerParameter = (props: NumberParameterProps) => {
    let childProps = {...props};
    return <GenericNumberParameter {...childProps} />;
};

const FloatingParameter = (props: NumberParameterProps) => {
    let childProps = {...props};
    return <GenericNumberParameter {...childProps} />;
};

const GenericParameter = ({parameter, children, onRemove}: {parameter: Types.ApplicationParameter, children: any, onRemove: () => void}) => (
    <>
        <Label fontSize={1} htmlFor={parameter.name}>
            <Flex>
                <Flex>{parameter.title}{parameter.optional ? "" : <Text ml="4px" bold color="red">*</Text>}</Flex>
                {parameter.optional ? <><Box ml="auto" /><Text cursor="pointer" mb="4px" onClick={onRemove}>Remove<Icon ml="6px" size={16} name="close"/></Text></> : null}
            </Flex>
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
        this.state = {results: props.parameters};
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
            this.setState({results: this.props.parameters});
        } else {
            this.currentTimeout = setTimeout(() => {
                const results = this.fuse.search(searchTerm);
                this.setState({results});
            }, 300);
        }
    }

    render() {
        const {onUse} = this.props;
        const {results} = this.state;
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

export class OptionalParameter extends React.Component<{parameter: Types.ApplicationParameter, onUse: () => void}, {open: boolean}> {
    state = {open: false};

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
        const {parameter, onUse} = this.props;
        const {open} = this.state;

        return (
            <Box mb={8}>
                <OptionalParameter.Base onClick={(e) => (e.preventDefault(), this.setState({open: !open}))}>
                    <strong>{parameter.title}</strong>
                    {!open ?
                        <EllipsedText>
                            <Markdown
                                source={parameter.description}
                                allowedTypes={["text", "root", "paragraph"]} />
                        </EllipsedText>
                        : <Box flexGrow={1} />}

                    <Button
                        type="button"
                        lineHeight={"16px"}
                        onClick={e => {
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