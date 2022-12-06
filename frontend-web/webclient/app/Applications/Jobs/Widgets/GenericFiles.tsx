import * as React from "react";
import * as UCloud from "@/UCloud";
import {findElement, widgetId, WidgetProps, WidgetSetProvider, WidgetSetter, WidgetValidator} from "./index";
import {Input} from "@/ui-components";
import {useCallback, useLayoutEffect} from "react";
import styled from "styled-components";
import {compute} from "@/UCloud";
import AppParameterValueNS = compute.AppParameterValueNS;
import {doNothing, removeTrailingSlash} from "@/UtilityFunctions";
import {dialogStore} from "@/Dialog/DialogStore";
import {FilesBrowse} from "@/Files/Files";
import {api as FilesApi} from "@/UCloud/FilesApi";
import {prettyFilePath} from "@/Files/FilePath";
import {BrowseType} from "@/Resource/BrowseType";
import {FolderResourceNS} from "../Resources";
import {getProviderField} from "../Resources/Ingress";

type GenericFileParam =
    UCloud.compute.ApplicationParameterNS.InputFile |
    UCloud.compute.ApplicationParameterNS.InputDirectory;

interface FilesProps extends WidgetProps {
    parameter: GenericFileParam;
}

export const FilesParameter: React.FunctionComponent<FilesProps> = props => {
    const isDirectoryInput = props.parameter.type === "input_directory";

    const valueInput = () =>
        document.getElementById(widgetId(props.parameter)) as HTMLInputElement | null;
    const visualInput = () =>
        document.getElementById(widgetId(props.parameter) + "visual") as HTMLInputElement | null;

    useLayoutEffect(() => {
        const value = valueInput();
        const visual = visualInput();
        const listener = async () => {
            if (value && visual) {
                const path = await (value.value ? prettyFilePath(value.value) : "");
                const visual2 = visualInput();
                if (visual2) {
                    visual2.value = path;
                }
            }
        };
        value?.addEventListener("change", listener);
        return () => {
            value?.removeEventListener("change", listener);
        }
    }, []);

    const onActivate = useCallback(() => {
        const pathRef = {current: ""};
        const provider = getProviderField();
        dialogStore.addDialog(
            <FilesBrowse
                browseType={BrowseType.Embedded}
                pathRef={pathRef}
                additionalFilters={provider ? {
                    filterProvider: provider
                } : undefined}
                onSelectRestriction={file =>
                    (isDirectoryInput && file.status.type === "DIRECTORY") ||
                    (!isDirectoryInput && file.status.type === "FILE")
                }
                onSelect={async (res) => {
                    const target = removeTrailingSlash(res.id === "" ? pathRef.current : res.id);
                    if (props.errors[props.parameter.name]) {
                        delete props.errors[props.parameter.name];
                        props.setErrors?.({...props.errors});
                    }
                    FilesSetter(props.parameter, {path: target, readOnly: false, type: "file"});
                    WidgetSetProvider(props.parameter, res.specification.product.provider);
                    dialogStore.success();
                    if (anyFolderDuplicates()) {
                        props.setWarning?.("Duplicate folders selected. This is not always supported.");
                    }
                }}
            />,
            doNothing,
            true,
            FilesApi.fileSelectorModalStyle
        );
    }, []);

    const error = props.errors[props.parameter.name] != null;
    return <>
        <input type={"hidden"} id={widgetId(props.parameter)} />
        <FileSelectorInput
            id={widgetId(props.parameter) + "visual"}
            placeholder={`No ${isDirectoryInput ? "directory" : "file"} selected`}
            onClick={onActivate}
            error={error}
        />
    </>;
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
    if (param.type !== "input_directory" && param.type !== "input_file") return;
    const file = value as AppParameterValueNS.File;

    const selector = findElement(param);
    if (!selector) return;
    if (file.path.length === 0) {
        selector.removeAttribute("value");
    } else {
        selector.value = file.path;
    }
    selector.dispatchEvent(new Event("change"));
};

function findAllFolderNames(): string[] {
    const result: string[] = [];
    let count = 0;
    while (true) {
        const name: `${FolderResourceNS}${number}` = `resourceFolder${count++}`;
        const element = findElement({name});
        if (!element) break;
        result.push(element.value);
    }
    return result;
}

export function anyFolderDuplicates(): boolean {
    const dirs = findAllFolderNames();
    return new Set(dirs).size !== dirs.length;
}