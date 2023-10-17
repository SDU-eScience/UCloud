import * as React from "react";
import * as UCloud from "@/UCloud";
import {findElement, widgetId, WidgetProps, WidgetSetProvider, WidgetSetter, WidgetValidator} from "./index";
import {Input} from "@/ui-components";
import {useCallback, useLayoutEffect} from "react";
import {compute} from "@/UCloud";
import AppParameterValueNS = compute.AppParameterValueNS;
import {doNothing, removeTrailingSlash} from "@/UtilityFunctions";
import {dialogStore} from "@/Dialog/DialogStore";
import {api as FilesApi} from "@/UCloud/FilesApi";
import {prettyFilePath} from "@/Files/FilePath";
import {FolderResourceNS} from "../Resources";
import {getProviderField, providerMismatchError} from "../Create";
import {injectStyleSimple} from "@/Unstyled";
import FileBrowse from "@/Files/FileBrowse";

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
        const additionalFilters: {filterProvider: string} | {} = provider ? {filterProvider: provider} : {};
        dialogStore.addDialog(
            <FileBrowse
                opts={{
                    additionalFilters: additionalFilters,
                    embedded: true,
                    isModal: true,
                    initialPath: "",
                    selection: {
                        onSelect: async res => {
                            const target = removeTrailingSlash(res.id === "" ? pathRef.current : res.id);
                            if (props.errors[props.parameter.name]) {
                                delete props.errors[props.parameter.name];
                                props.setErrors({...props.errors});
                            }
                            FilesSetter(props.parameter, {path: target, readOnly: false, type: "file"});
                            WidgetSetProvider(props.parameter, res.specification.product.provider);
                            dialogStore.success();
                            if (anyFolderDuplicates()) {
                                props.setWarning?.("Duplicate folders selected. This is not always supported.");
                            }
                        },
                        onSelectRestriction: file => {
                            const fileProvider = file.specification.product.provider;
                            const isCorrectlyDir = isDirectoryInput && file.status.type === "DIRECTORY";
                            const isCorrectlyFile = !isDirectoryInput && file.status.type === "FILE";
                            if (provider && provider !== fileProvider) {
                                if (isCorrectlyDir) {
                                    return providerMismatchError("Folders", fileProvider);
                                } else if (isCorrectlyFile) {
                                    return providerMismatchError("Files", fileProvider)
                                }
                            }
                            return isCorrectlyDir || isCorrectlyFile;
                        }
                    }
                }} />,
            doNothing,
            true,
            FilesApi.fileSelectorModalStyle
        );
    }, [props.errors]);

    const error = props.errors[props.parameter.name] != null;
    return <>
        <input type={"hidden"} id={widgetId(props.parameter)} />
        <Input
            id={widgetId(props.parameter) + "visual"}
            className={FileInputClass}
            placeholder={`No ${isDirectoryInput ? "directory" : "file"} selected`}
            onClick={onActivate}
            error={error}
        />
    </>;
};

const FileInputClass = injectStyleSimple("file-input", `
    cursor: pointer;
`);

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