import {BaseParameter, ParameterProps} from "Applications/Widgets/BaseParameter";
import {FileInputSelector} from "Files/FileInputSelector";
import {addTrailingSlash} from "UtilityFunctions";
import {replaceHomeFolder, resolvePath} from "Utilities/FileUtilities";
import {Cloud} from "Authentication/SDUCloudObject";
import * as React from "react";

interface InputFileParameterProps extends ParameterProps {
    onRemove?: () => void
    defaultValue?: string
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
            remove={props.onRemove}
        />
    </BaseParameter>
);