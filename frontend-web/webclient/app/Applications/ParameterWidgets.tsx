import * as React from "react";
import { File } from "Files";
import FileSelector from "Files/FileSelector";
import { getFilenameFromPath } from "Utilities/FileUtilities";
import { ParameterTypes, ApplicationParameter } from ".";
import { Box, Flex, Label, Text, Select, Markdown } from "ui-components";
import Input from "ui-components/Input";

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
        props.onChange(props.parameter.name, {
            source: file.path,
            destination: getFilenameFromPath(file.path)
        });
    };
    const path = props.value ? props.value.source : "";
    return (
        <GenericParameter parameter={props.parameter}>
            <FileSelector
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
    const internalOnChange = event => {
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
        <OptionalText optional={parameter.optional} />
        <Markdown source={parameter.description} />
    </>
);

const OptionalText = ({ optional }) =>
    optional ? (<span><b>Optional</b></span>) : null;
