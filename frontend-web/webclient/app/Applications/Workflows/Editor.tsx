import * as React from "react";
import {Feature, hasFeature} from "@/Features";
import {Editor, EditorApi, Vfs, VirtualFile} from "@/Editor/Editor";
import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {bulkRequestOf, displayErrorMessageOrDefault, extractErrorCode, stopPropagation} from "@/UtilityFunctions";
import {WorkflowSpecification} from "@/Applications/Workflows/index";
import {Box, Button, Flex, Icon, Input, Label} from "@/ui-components";
import {TooltipV2} from "@/ui-components/Tooltip";
import {callAPI} from "@/Authentication/DataHook";
import * as WorkflowApi from ".";
import {useDidUnmount} from "@/Utilities/ReactUtilities";
import * as YAML from "yaml";
import * as AppStore from "@/Applications/AppStoreApi";
import {ApplicationParameter} from "@/Applications/AppStoreApi";
import EnumOption = AppStore.ApplicationParameterNS.EnumOption;
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {snackbarStore} from "@/Snackbar/SnackbarStore";

const WorkflowEditor: React.FunctionComponent<{
    initialExistingPath?: string | null;
    initialId?: string | null;
    workflow: WorkflowSpecification;
    applicationName: string;
    onUse?: (id: string | null, path: string | null, spec: WorkflowSpecification) => void;
}> = props => {
    if (!hasFeature(Feature.COPY_APP_MOCKUP)) return null;

    const editorApi = useRef<EditorApi>(null);
    const [currentPath, setCurrentPath] = useState<string | null>(props.initialExistingPath ?? null);
    const [isSaving, setIsSaving] = useState(false);
    const [isOverwriting, setIsOverwriting] = useState<string | null>(null);
    const didUnmount = useDidUnmount();
    const [error, setError] = useState<string | null>(null);
    const [savedId, setSavedId] = useState<string | null>(props.initialId ?? null);

    const vfs = useMemo(() => {
        return new WorkflowVfs(props.workflow);
    }, []);

    useEffect(() => {
        vfs.workflow = props.workflow;
    }, [props.workflow]);

    const readCurrentSpecification = useCallback((): WorkflowSpecification | null => {
        const params = vfs.dirtyFiles["/" + FILE_NAME_PARAMETERS] ?? "";
        let parsed: any;
        let error: string | null = null;

        try {
            parsed = YAML.parse(params);
        } catch (e) {
            error = `Error in ${FILE_NAME_PARAMETERS}:\nInvalid YAML supplied.`;
        }

        const inputs: ApplicationParameter[] = [];

        if (parsed != null) {
            const errPrefix = `Error in ${FILE_NAME_PARAMETERS}.yaml:\n`;
            if (typeof parsed !== "object" || Array.isArray(parsed)) {
                error = errPrefix + "expected parameters to contain a dictionary of parameters"
            } else {
                for (let [name, param] of Object.entries(parsed)) {
                    if (typeof param !== "object") {
                        error = errPrefix + "error in parameter " + name + ": expected an object";
                        break;
                    }

                    const withName = {...param, name};
                    const validatedOrError = validateParameter(withName);
                    if (typeof validatedOrError === "string") {
                        error = errPrefix + "error in parameter " + name + ": " + validatedOrError;
                        break;
                    }

                    inputs.push(validatedOrError);
                }
            }
        }

        setError(error);
        if (error) {
            snackbarStore.addFailure(error, false);
            return null;
        }

        const init = vfs.dirtyFiles["/" + FILE_NAME_INIT] ?? "";
        const job = vfs.dirtyFiles["/" + FILE_NAME_JOB] ?? "";
        const readme = vfs.dirtyFiles["/" + FILE_NAME_README] ?? "";
        return {
            init,
            job,
            inputs,
            readme,
            applicationName: props.applicationName,
            language: "JINJA2",
        };
    }, []);

    const onUse = useCallback(async () => {
        const api = editorApi.current;
        if (!api) return;
        await api.notifyDirtyBuffer();
        const spec = readCurrentSpecification();
        if (spec && props.onUse) {
            props.onUse(savedId, currentPath, spec);
        }
    }, [readCurrentSpecification, currentPath, props.onUse, savedId]);

    const saveKeyDown: React.KeyboardEventHandler = useCallback(ev => {
        ev.stopPropagation();
        if (ev.code === "Escape") {
            setIsSaving(false);
        }
    }, []);

    const savingRef = useRef(false);
    const onSaveCopy: React.FormEventHandler = useCallback(ev => {
        savingRef.current = true;
        ev.preventDefault();

        const data = new FormData(ev.target as HTMLFormElement);
        const name = data.get("name")! as string;
        if (!name) return;

        setIsSaving(false);
        setCurrentPath(name);

        (async () => {
            const api = editorApi.current;
            if (!api) return;

            await api.notifyDirtyBuffer();

            const specification = readCurrentSpecification();
            if (!specification) return;

            try {
                const res = await callAPI(WorkflowApi.create(bulkRequestOf({
                    path: name,
                    allowOverwrite: false,
                    specification,
                })));

                setSavedId(res.responses[0].id);
            } catch (e) {
                if (didUnmount.current) return;

                const statusCode = extractErrorCode(e);
                if (statusCode === 409) {
                    setIsOverwriting(name);
                } else {
                    displayErrorMessageOrDefault(e, "Could not save workflow");
                }
            }
        })();
        savingRef.current = false;
    }, []);

    const saveOverwritten = useCallback(async () => {
        setIsOverwriting(null);

        try {
            const specification = readCurrentSpecification();
            if (!specification) return;

            const res = await callAPI(WorkflowApi.create(bulkRequestOf({
                path: isOverwriting ?? "",
                allowOverwrite: true,
                specification,
            })));

            setSavedId(res.responses[0].id);
        } catch (e) {
            displayErrorMessageOrDefault(e, "Could not save workflow");
        }
    }, [isOverwriting]);

    return <Editor
        vfs={vfs}
        title={props.applicationName}
        initialFolderPath={"/" + FILE_NAME_JOB}
        apiRef={editorApi}
        toolbarBeforeSettings={<>
            {!error ? null :
                <TooltipV2>
                    <ClickableDropdown
                        trigger={
                            <Icon
                                name={"heroExclamationTriangle"}
                                size={"20px"}
                                cursor={"pointer"}
                                color={"errorMain"}
                                mt={"4px"}
                            />
                        }
                        colorOnHover={false}
                        useMousePositioning
                        width={300}
                        height={300}
                        paddingControlledByContent
                    >
                        <div
                            style={{
                                cursor: "default",
                                maxWidth: "100%",
                                whiteSpace: "normal",
                                padding: "16px"
                            }}
                        >
                            <pre style={{fontFamily: "unset"}}>{error}</pre>
                        </div>
                    </ClickableDropdown>
                </TooltipV2>
            }
        </>}
        toolbar={<>
            <TooltipV2 tooltip={"Save copy"} contentWidth={100}>
                <Icon name={"floppyDisk"} size={"20px"} cursor={"pointer"} onClick={() => setIsSaving(true)} />
                {!isSaving ? null :
                    <div style={{position: "absolute"}} onMouseMove={stopPropagation}>
                        <div style={{
                            position: "relative",
                            left: -280,
                            top: 5,
                            width: 300,
                            padding: 16,
                            borderRadius: 8,
                            backgroundColor: "var(--backgroundCard)",
                            boxShadow: "var(--defaultShadow)",
                            zIndex: 1000000000,
                        }}>
                            <form onSubmit={onSaveCopy} onBlur={(ev) => {
                                if (!savingRef.current) setIsSaving(false);
                            }}>
                                <Label>
                                    What should we call this workflow?
                                    <Input
                                        name={"name"}
                                        onKeyDown={saveKeyDown}
                                        placeholder={"My workflow"}
                                        defaultValue={currentPath ?? ""}
                                        autoFocus
                                    />
                                </Label>
                                <Flex gap={"8px"} mt={"8px"}>
                                    <Box flexGrow={1} />
                                    <Button color={"errorMain"} type={"button"}
                                        onClick={() => setIsSaving(false)}>Cancel</Button>
                                    <Button color={"successMain"} type={"submit"}
                                        onMouseDown={() => savingRef.current = true}>Save</Button>
                                </Flex>
                            </form>
                        </div>
                    </div>
                }
                {!isOverwriting ? null :
                    <div style={{position: "absolute"}} onMouseMove={stopPropagation}>
                        <div style={{
                            position: "relative",
                            left: -280,
                            top: 5,
                            width: 300,
                            padding: 16,
                            borderRadius: 8,
                            backgroundColor: "var(--backgroundCard)",
                            boxShadow: "var(--defaultShadow)",
                            zIndex: 1000000000,
                        }}>
                            This workflow already exists, do you want to overwrite it?
                            <Flex gap={"8px"} mt={"8px"}>
                                <Box flexGrow={1} />
                                <Button color={"errorMain"} type={"button"}
                                    onClick={() => setIsOverwriting(null)}>No</Button>
                                <Button color={"successMain"} onMouseDown={() => savingRef.current = true}
                                    onClick={saveOverwritten}>Yes</Button>
                            </Flex>
                        </div>
                    </div>
                }
            </TooltipV2>
            <TooltipV2 tooltip={"Use"} contentWidth={100}>
                <Icon name={"heroPlay"} color={"successMain"} size={"20px"} cursor={"pointer"} onClick={onUse} />
            </TooltipV2>
        </>}
    />;
};

function validateParameter(parameter: any): ApplicationParameter | string {
    if (typeof parameter !== "object") {
        return "expected parameter to be an object"
    }

    const type = parameter["type"];
    if (typeof type !== "string") return "expected to find a 'type' property";

    const name = parameter["name"];
    if (typeof name !== "string") return "expected to find an 'name' property";

    let title = parameter["title"];
    if (title === undefined || title === null) title = name;
    if (typeof title !== "string") return "expected to find a 'title' property";

    let description = parameter["description"];
    if (description === undefined || description === null) description = "";
    if (typeof description !== "string") return "expected to find a 'description' property";

    let optional = parameter["optional"];
    if (optional === undefined || optional === null) optional = false;
    if (typeof optional !== "boolean") return "expected to find an 'optional' property";

    switch (type) {
        case "File":
            return {
                type: "input_file",
                title,
                description,
                optional,
                name,
            };

        case "Directory":
            return {
                type: "input_directory",
                title,
                description,
                optional,
                name,
            };

        case "Text": {
            const defaultValue = parameter["defaultValue"];
            if (defaultValue && typeof defaultValue !== "string") return "expected 'defaultValue' to be a string";
            return {
                type: "text",
                title,
                description,
                optional,
                name,
                defaultValue,
            };
        }

        case "TextArea": {
            const defaultValue = parameter["defaultValue"];
            if (defaultValue && typeof defaultValue !== "string") return "expected 'defaultValue' to be a string";
            return {
                type: "textarea",
                title,
                description,
                optional,
                name,
                defaultValue,
            };
        }

        case "Integer": {
            const defaultValue = parameter["defaultValue"];
            if (defaultValue && typeof defaultValue !== "number") return "expected 'defaultValue' to be an integer";

            const min = parameter["min"];
            if (min != null && typeof min !== "number") return "expected 'min' to be an integer";

            const max = parameter["max"];
            if (max != null && typeof max !== "number") return "expected 'max' to be an integer";

            const step = parameter["step"];
            if (step != null && typeof step !== "number") return "expected 'step' to be an integer";

            return {
                type: "integer",
                title,
                description,
                optional,
                name,
                defaultValue,
                min, max, step
            };
        }

        case "FloatingPoint": {
            const defaultValue = parameter["defaultValue"];
            if (defaultValue && typeof defaultValue !== "number") return "expected 'defaultValue' to be an integer";

            const min = parameter["min"];
            if (min != null && typeof min !== "number") return "expected 'min' to be an integer";

            const max = parameter["max"];
            if (max != null && typeof max !== "number") return "expected 'max' to be an integer";

            const step = parameter["step"];
            if (step != null && typeof step !== "number") return "expected 'step' to be an integer";

            return {
                type: "floating_point",
                title,
                description,
                optional,
                name,
                defaultValue,
                min, max, step
            };
        }

        case "Boolean": {
            const defaultValue = parameter["defaultValue"];
            if (defaultValue != null && typeof defaultValue !== "boolean") return "expected 'defaultValue' to be a boolean";
            return {
                type: "boolean",
                title,
                description,
                optional,
                name,
                defaultValue,
                trueValue: "true",
                falseValue: "false",
            };
        }

        case "Enumeration": {
            const defaultValue = parameter["defaultValue"];
            if (defaultValue != null && typeof defaultValue !== "string") return "expected 'defaultValue' to be a string";

            const options = parameter["options"];
            if (!Array.isArray(options)) return "expected 'options' to be an array";

            const parsedOptions: EnumOption[] = [];

            for (let i = 0; i < options.length; i++) {
                const opt = options[i];
                if (typeof opt !== "object") return `expected 'options[${i}] to be an object`;

                const title = opt["title"];
                if (typeof title !== "string") return `expected 'options[${i}] to have a 'title' property of type string`;

                const value = opt["value"];
                if (typeof value !== "string") return `expected 'options[${i}] to have a 'value' property of type string`;

                parsedOptions.push({name: title, value});
            }

            return {
                type: "enumeration",
                title,
                description,
                optional,
                name,
                defaultValue,
                options: parsedOptions,
            };
        }

        case "Job":
            return {
                type: "peer",
                title,
                description,
                optional,
                name,
            };

        case "PublicLink":
            return {
                type: "ingress",
                title,
                description,
                optional,
                name,
            };

        case "PublicIP":
            return {
                type: "network_ip",
                title,
                description,
                optional,
                name,
            };

        case "Workflow":
            return {
                type: "workflow",
                title,
                description,
                optional,
                name,
            };

        default:
            return "unknown parameter type"
    }
}

/* TODO(Jonas): Hello. Hopefully this is not in the pull-request, but fixed before, but this very much needs to be tested with the changes. */
class WorkflowVfs implements Vfs {
    workflow: WorkflowSpecification;
    dirtyFiles: Record<string, string> = {};
    isDirty: Record<string, boolean> = {};
    path: string;

    private knownFiles: VirtualFile[] = [
        {absolutePath: "/" + FILE_NAME_README, isDirectory: false, requestedSyntax: "markdown"},
        // {absolutePath: "/" + FILE_NAME_INIT, isDirectory: false, requestedSyntax: "jinja2"},
        {absolutePath: "/" + FILE_NAME_JOB, isDirectory: false, requestedSyntax: "jinja2"},
        {absolutePath: "/" + FILE_NAME_PARAMETERS, isDirectory: false, requestedSyntax: "yaml"},
    ];

    constructor(workflow: WorkflowSpecification) {
        this.workflow = workflow;

        for (const f of this.knownFiles) {
            let thisFile = f.absolutePath;
            this.readFile(thisFile).then(content => {
                this.dirtyFiles[thisFile] = content;
            });
        }
    }

    async listFiles(path: string): Promise<VirtualFile[]> {
        switch (path) {
            case "":
            case "/":
                return this.knownFiles;
        }

        return [];
    }

    async readFile(path: string): Promise<string> {
        switch (path) {
            case "/" + FILE_NAME_README:
                return this.workflow.readme ?? "";
            case "/" + FILE_NAME_INIT:
                return this.workflow.init ?? "";
            case "/" + FILE_NAME_JOB:
                return this.workflow.job ?? "";
            case "/" + FILE_NAME_PARAMETERS:
                return this.serializeParameters();
            default:
                return "";
        }
    }

    async writeFile(path: string, content: string): Promise<void> {}

    setFileAsDirty(path: string): void {
        this.isDirty[path] = true;
    }

    private serializeParameters(): string {
        let builder: Record<string, any> = {};

        const parameters = this.workflow.inputs;
        for (const param of parameters) {
            let type = "";
            const {title, description, optional, name} = param;
            let properties: Record<string, any> = {};

            switch (param.type) {
                case "input_file":
                    type = "File";
                    break;
                case "input_directory":
                    type = "Directory";
                    break;
                case "ingress":
                    type = "PublicLink";
                    break;
                case "license_server":
                    type = "License";
                    break;
                case "peer":
                    type = "Job";
                    break;
                case "network_ip":
                    type = "PublicIP";
                    break;
                case "integer":
                    type = "Integer";
                    if (param.min != null) properties["min"] = param.min;
                    if (param.max != null) properties["min"] = param.max;
                    if (param.step != null) properties["min"] = param.step;
                    if (param.defaultValue != null) properties["defaultValue"] = param.defaultValue;
                    break;
                case "floating_point":
                    type = "FloatingPoint";
                    if (param.min != null) properties["min"] = param.min;
                    if (param.max != null) properties["min"] = param.max;
                    if (param.step != null) properties["min"] = param.step;
                    if (param.defaultValue != null) properties["defaultValue"] = param.defaultValue;
                    break;
                case "boolean":
                    type = "Boolean";
                    if (param.defaultValue != null) properties["defaultValue"] = param.defaultValue;
                    break;
                case "text":
                    type = "Text";
                    if (param.defaultValue != null) properties["defaultValue"] = param.defaultValue;
                    break;
                case "textarea":
                    type = "TextArea";
                    if (param.defaultValue != null) properties["defaultValue"] = param.defaultValue;
                    break;
                case "enumeration":
                    type = "Enumeration";
                    if (param.defaultValue != null) properties["defaultValue"] = param.defaultValue;
                    properties["options"] = param.options.map(it => ({
                        title: it.name,
                        value: it.value,
                    }));
                    break;
                case "workflow":
                    type = "Workflow"
                    break;
            }

            builder[name] = {
                type,
                title,
                description,
                optional,
                ...properties
            };
        }

        return YAML.stringify(builder);
    }

    setDirtyFileContent(path: string, content: string) {
        this.dirtyFiles[path] = content;
    }
}

const FILE_NAME_INIT = "999_job_init.sh";
const FILE_NAME_README = "000_readme.md";
const FILE_NAME_JOB = "001_job.sh";
const FILE_NAME_PARAMETERS = "002_parameters.yml";

export default WorkflowEditor;
