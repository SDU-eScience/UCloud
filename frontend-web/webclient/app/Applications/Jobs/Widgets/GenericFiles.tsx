import * as React from "react";
import * as UCloud from "UCloud";
import {widgetId, WidgetProps, WidgetSetter, WidgetValidator} from "./index";
import {Input} from "ui-components";
import FileSelector from "Files/FileSelector";
import {useEffect, useRef, useState} from "react";
import styled from "styled-components";
import {getProjectNames} from "Utilities/ProjectUtilities";
import {useProjectStatus} from "Project/cache";
import {replaceHomeOrProjectFolder} from "Utilities/FileUtilities";
import {Client} from "Authentication/HttpClientInstance";

type GenericFileParam =
    UCloud.compute.ApplicationParameterNS.InputFile |
    UCloud.compute.ApplicationParameterNS.InputDirectory;

interface FilesProps extends WidgetProps {
    parameter: GenericFileParam;
}

export const FilesParameter: React.FunctionComponent<FilesProps> = props => {
    const isDirectoryInput = props.parameter.type === "input_directory";
    const valueRef = useRef<HTMLInputElement>(null);
    const visualRef = useRef<HTMLInputElement>(null);
    const [isOpen, setOpen] = useState(false);

    const valueInput = valueRef.current;
    const visualInput = visualRef.current;

    const projects = getProjectNames(useProjectStatus());

    useEffect(() => {
        let listener: EventListener | null = null;
        if (valueInput && visualInput) {
            listener = () => {
                visualInput.value = replaceHomeOrProjectFolder(valueInput.value, Client, projects);
            };
            valueInput.addEventListener("change", listener);
        }

        return () => {
            if (valueInput && listener) valueInput.removeEventListener("change", listener);
        };
    }, [valueInput?.id, visualInput?.id, projects.length]); // TODO getProjectNames returns a new copy every time

    const error = props.errors[props.parameter.name] != null;
    return <FileSelector
        visible={isOpen}

        canSelectFolders={isDirectoryInput}
        onlyAllowFolders={isDirectoryInput}

        onFileSelect={file => {
            valueInput!.value = file?.path ?? "";
            valueInput!.dispatchEvent(new Event("change"));
            setOpen(false);
        }}

        trigger={
            <>
                <input type={"hidden"} ref={valueRef} id={widgetId(props.parameter)}/>
                <FileSelectorInput
                    ref={visualRef}
                    placeholder={`No ${isDirectoryInput ? "directory" : "file"} selected`}
                    onClick={() => setOpen(true)}
                    error={error}
                />
            </>
        }
    />;
};

const FileSelectorInput = styled(Input)`
    cursor: pointer;
`;

export const FilesValidator: WidgetValidator = (param) => {
    if (param.type === "input_directory" || param.type === "input_file") {
        const elem = findElement(param);
        if (elem === null) return {valid: true};

        const value = elem.value;
        if (value === "") return {valid: true};
        return {valid: true, value: {type: "file", path: value, readOnly: false}};
    }

    return {valid: true};
};

export const FilesSetter: WidgetSetter = (param, value) => {
    if (param.type !== "input_directory") return;
    if (value.type !== "file") return;

    const selector = findElement(param);
    selector.value = value.path;
};

function findElement(param: GenericFileParam): HTMLSelectElement {
    return document.getElementById(widgetId(param)) as HTMLSelectElement;
}
