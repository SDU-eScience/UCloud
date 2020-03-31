import {BaseParameter, ParameterProps} from "Applications/Widgets/BaseParameter";
import {Client} from "Authentication/HttpClientInstance";
import {FileInputSelector} from "Files/FileInputSelector";
import * as React from "react";
import {replaceHomeOrProjectFolder, resolvePath} from "Utilities/FileUtilities";
import {addTrailingSlash} from "UtilityFunctions";

interface InputFileParameterProps extends ParameterProps {
    onRemove?: () => void;
    defaultValue?: string;
    unitWidth?: string | number;
    parameterRef: React.RefObject<HTMLInputElement>;
}

export const InputFileParameter = (props: InputFileParameterProps): JSX.Element => (
    <BaseParameter parameter={props.parameter} onRemove={props.onParamRemove}>
        <FileInputSelector
            showError={props.initialSubmit || props.parameter.optional}
            key={props.parameter.name}
            path={props.parameterRef.current?.value ?? ""}
            onFileSelect={file => {
                props.parameterRef.current!.value = resolvePath(replaceHomeOrProjectFolder(file.path, Client));
            }}
            inputRef={props.parameterRef as React.RefObject<HTMLInputElement>}
            isRequired={!props.parameter.optional}
            unitName={props.parameter.unitName}
            unitWidth={props.unitWidth}
        />
    </BaseParameter>
);
export const InputDirectoryParameter = (props: InputFileParameterProps): JSX.Element => (
    <BaseParameter parameter={props.parameter} onRemove={props.onParamRemove}>
        <FileInputSelector
            defaultValue={props.defaultValue}
            showError={props.initialSubmit || props.parameter.optional}
            key={props.parameter.name}
            path={props.parameterRef.current?.value ?? ""}
            onFileSelect={file => {
                props.parameterRef.current!.value = addTrailingSlash(resolvePath(replaceHomeOrProjectFolder(file.path, Client)));
            }}
            inputRef={props.parameterRef as React.RefObject<HTMLInputElement>}
            canSelectFolders
            onlyAllowFolders
            isRequired={!props.parameter.optional}
            unitName={props.parameter.unitName}
            unitWidth={props.unitWidth}
            remove={props.onRemove}
        />
    </BaseParameter>
);
