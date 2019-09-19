import {BaseParameter, ParameterProps} from "Applications/Widgets/BaseParameter";
import {Cloud} from "Authentication/SDUCloudObject";
import {FileInputSelector} from "Files/FileInputSelector";
import * as React from "react";
import {replaceHomeFolder, resolvePath} from "Utilities/FileUtilities";
import {addTrailingSlash} from "UtilityFunctions";

interface InputFileParameterProps extends ParameterProps {
    onRemove?: () => void;
    defaultValue?: string;
    unitWidth?: string | number;
    parameterRef: React.RefObject<HTMLInputElement>;
}

export const InputFileParameter = (props: InputFileParameterProps) => (
    <BaseParameter parameter={props.parameter} onRemove={props.onParamRemove}>
        <FileInputSelector
            showError={props.initialSubmit || props.parameter.optional}
            key={props.parameter.name}
            path={props.parameterRef.current && props.parameterRef.current.value || ""}
            onFileSelect={file => {
                props.parameterRef.current!.value = resolvePath(replaceHomeFolder(file.path, Cloud.homeFolder))
            }}
            inputRef={props.parameterRef as React.RefObject<HTMLInputElement>}
            isRequired={!props.parameter.optional}
            unitName={props.parameter.unitName}
            unitWidth={props.unitWidth}
        />
    </BaseParameter>
);
export const InputDirectoryParameter = (props: InputFileParameterProps) => (
    <BaseParameter parameter={props.parameter} onRemove={props.onParamRemove}>
        <FileInputSelector
            defaultValue={props.defaultValue}
            showError={props.initialSubmit || props.parameter.optional}
            key={props.parameter.name}
            path={props.parameterRef.current && props.parameterRef.current.value || ""}
            onFileSelect={file => {
                props.parameterRef.current!.value = addTrailingSlash(resolvePath(replaceHomeFolder(file.path, Cloud.homeFolder)))
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