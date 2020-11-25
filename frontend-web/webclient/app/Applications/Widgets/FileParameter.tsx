import {BaseParameter, ParameterProps} from "Applications/Widgets/BaseParameter";
import {Client} from "Authentication/HttpClientInstance";
import {FileInputSelector} from "Files/FileInputSelector";
import * as React from "react";
import {replaceHomeOrProjectFolder, resolvePath} from "Utilities/FileUtilities";
import {addTrailingSlash} from "UtilityFunctions";
import {useProjectStatus} from "Project/cache";
import {getProjectNames} from "Utilities/ProjectUtilities";
import {AdditionalMountedFolder} from "Applications";
import {snackbarStore} from "Snackbar/SnackbarStore";

interface InputFileParameterProps extends ParameterProps {
    onRemove?: () => void;
    defaultValue?: string;
    unitWidth?: string | number;
    parameterRef: React.RefObject<HTMLInputElement>;
}

interface InputFolderParameterProps extends InputFileParameterProps {
    mountedFolders: AdditionalMountedFolder[];
}

export const InputFileParameter = (props: InputFileParameterProps): JSX.Element => {
    const projectNames = getProjectNames(useProjectStatus());
    return (
        <BaseParameter parameter={props.parameter} onRemove={props.onParamRemove}>
            <FileInputSelector
                key={props.parameter.name}
                path={props.parameterRef.current?.value ?? ""}
                onFileSelect={file => {
                    props.parameterRef.current!.dataset.path = file.path;
                    props.parameterRef.current!.value = resolvePath(replaceHomeOrProjectFolder(file.path, Client, projectNames));
                }}
                inputRef={props.parameterRef as React.RefObject<HTMLInputElement>}
                isRequired={!props.parameter.optional}
                unitName={props.parameter.unitName}
                unitWidth={props.unitWidth}
            />
        </BaseParameter>
    );
};

export const InputDirectoryParameter = (props: InputFolderParameterProps): JSX.Element => {
    const projectNames = getProjectNames(useProjectStatus());
    return (
        <BaseParameter parameter={props.parameter} onRemove={props.onParamRemove}>
            <FileInputSelector
                defaultValue={props.defaultValue}
                key={props.parameter.name}
                path={props.parameterRef.current?.value ?? ""}
                onFileSelect={file => {
                    const paths = props.mountedFolders.map(it => it.ref.current?.value ?? "").filter(it => it);
                    if (paths.includes(`/${replaceHomeOrProjectFolder(file.path, Client, projectNames)}/`)) {
                        snackbarStore.addFailure("Folder already added.", false);
                        return;
                    }
                    props.parameterRef.current!.dataset.path = file.path;
                    props.parameterRef.current!.value = addTrailingSlash(resolvePath(replaceHomeOrProjectFolder(file.path, Client, projectNames)));
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
};
