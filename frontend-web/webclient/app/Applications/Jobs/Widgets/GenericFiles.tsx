import * as React from "react";
import {findElement, widgetId, WidgetProps, WidgetSetProvider, WidgetSetter, WidgetValidator} from "./index";
import {Input} from "@/ui-components";
import {useCallback, useLayoutEffect, useState} from "react";
import {compute} from "@/UCloud";
import AppParameterValueNS = compute.AppParameterValueNS;
import {doNothing, removeTrailingSlash} from "@/UtilityFunctions";
import {dialogStore} from "@/Dialog/DialogStore";
import {api as FilesApi} from "@/UCloud/FilesApi";
import {prettyFilePath} from "@/Files/FilePath";
import {getProviderField, providerMismatchError} from "../Create";
import {injectStyleSimple} from "@/Unstyled";
import FileBrowse from "@/Files/FileBrowse";
import {ApplicationParameterNS} from "@/Applications/AppStoreApi";
import {UFile} from "@/UCloud/UFile";
import {Selection} from "@/ui-components/ResourceBrowser";
import {getParentPath, pathComponents} from "@/Utilities/FileUtilities";
import {Toggle} from "@/ui-components/Toggle";

type GenericFileParam =
    ApplicationParameterNS.InputFile |
    ApplicationParameterNS.InputDirectory;

interface FilesProps extends WidgetProps {
    parameter: GenericFileParam;
}

export const FilesParameter: React.FunctionComponent<FilesProps> = props => {
    const isDirectoryInput = props.parameter.type === "input_directory";
    const [hasValue, setHasValue] = useState(false);

    const valueInput = () =>
        document.getElementById(widgetId(props.parameter)) as HTMLInputElement | null;
    const visualInput = () =>
        document.getElementById(widgetId(props.parameter) + "visual") as HTMLInputElement | null;

    useLayoutEffect(() => {
        const value = valueInput();
        const visual = visualInput();
        const listener = async () => {
            if (value && visual) {
                setHasValue(value.value !== "");
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
        // Note(Jonas): Not meaningfully in use?
        const provider = getProviderField();
        const additionalFilters: {filterProvider: string} | {} = provider ? {filterProvider: provider} : {};
        additionalFilters["filterMemberFiles"] == "all";

        async function onClick(res: UFile) {
            const target = removeTrailingSlash(res.id);
            if (props.errors[props.parameter.name]) {
                delete props.errors[props.parameter.name];
                props.setErrors({...props.errors});
            }
            FilesSetter(props.parameter, {path: target, readOnly: false, type: "file"});
            WidgetSetProvider(props.parameter, res.specification.product.provider);
            props.onValueChange?.();
            dialogStore.success();

            setLastActivePath(
                res.status.type === "DIRECTORY" && pathComponents(res.id).length === 1 ? res.id : getParentPath(res.id)
            );
            if (anyFolderDuplicates()) {
                props.setWarning?.("Duplicate folders selected. This is not always supported.");
            }
        }

        function providerRestriction(file: UFile): boolean | string {
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

        const selection: Selection<UFile> = {
            text: "Use",
            onClick,
            show: providerRestriction
        };

        dialogStore.addDialog(
            <FileBrowse
                opts={{
                    additionalFilters,
                    isModal: true,
                    managesLocalProject: true,
                    initialPath: getLastActivePath(),
                    selection,
                }} />,
            doNothing,
            true,
            FilesApi.fileSelectorModalStyle
        );
    }, [props.errors, props.onValueChange]);

    const error = props.errors[props.parameter.name] != null;
    return <>
        <input type={"hidden"} id={widgetId(props.parameter)} />
        {!props.initScriptCache ? null : (
            <input
                type="hidden"
                id={widgetId(props.initScriptCache.parameter)}
                value={props.initScriptCache.enabled ? "true" : "false"}
                readOnly
            />
        )}
        <Input
            id={widgetId(props.parameter) + "visual"}
            className={FileInputClass}
            placeholder={`No ${isDirectoryInput ? "directory" : "file"} selected`}
            onClick={onActivate}
            readOnly
            data-field-activator
            error={error}
        />
        {!hasValue || !props.initScriptCache ? null : (
            <div className={InitScriptCacheClass}>
                <Toggle
                    height={20}
                    checked={props.initScriptCache.enabled}
                    onChange={previous => props.initScriptCache?.onChange(!previous)}
                />
                <div>
                    <strong>Cache installed dependencies</strong>
                    <span>Run this script once to prepare the image. It will not run again when the cached image is reused.</span>
                </div>
            </div>
        )}
    </>;
};

const FileInputClass = injectStyleSimple("file-input", `
    cursor: pointer;
`);

const InitScriptCacheClass = injectStyleSimple("init-script-cache", `
    display: flex;
    align-items: flex-start;
    gap: 10px;
    margin-top: 10px;
    
    > div:first-child {
        flex-shrink: 0;
    }

    > div:last-child {
        display: flex;
        flex-direction: column;
        gap: 2px;
    }

    strong {
        font-size: 14px;
        line-height: 20px;
    }

    span {
        color: var(--textSecondary);
        font-size: 13px;
        line-height: 18px;
    }
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
    return Array.from(document.querySelectorAll<HTMLInputElement>("input[type=hidden][id^='app-param-resourceFolder']"))
        .map(element => element.value);
}

export function anyFolderDuplicates(): boolean {
    const dirs = findAllFolderNames();
    return new Set(dirs).size !== dirs.length;
}

export function getLastActivePath(): string {
    return document.querySelector<HTMLDivElement>("[data-last-used-file-path]")?.innerText ?? "";
}

function setLastActivePath(path: string) {
    const pathNode = document.querySelector<HTMLDivElement>("[data-last-used-file-path]")
    if (pathNode) pathNode.innerText = path;
}
