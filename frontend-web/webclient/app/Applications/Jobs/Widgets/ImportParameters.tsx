import * as React from "react";
import * as UCloud from "@/UCloud";
import {default as ReactModal} from "react-modal";
import {defaultModalStyle} from "@/Utilities/ModalUtilities";
import {Box, Button, Flex, Icon, Label} from "@/ui-components";
import {HiddenInputField} from "@/ui-components/Input";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import CONF from "../../../../site.config.json";
import {useCallback, useState} from "react";
import {errorMessageOrDefault} from "@/UtilityFunctions";
import {compute, PageV2} from "@/UCloud";
import JobSpecification = compute.JobSpecification;
import AppParameterValue = compute.AppParameterValue;
import styled from "styled-components";
import {TextP} from "@/ui-components/Text";
import {useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {default as JobsApi, Job} from "@/UCloud/JobsApi";
import {bulkRequestOf, emptyPageV2} from "@/DefaultObjects";
import {BrowseType} from "@/Resource/BrowseType";
import {dialogStore} from "@/Dialog/DialogStore";
import {FilesBrowse} from "@/Files/Files";
import {api as FilesApi} from "@/UCloud/FilesApi";
import {FilesCreateDownloadResponseItem, UFile} from "@/UCloud/FilesApi";
import {JobBrowse} from "../Browse";

export const ImportParameters: React.FunctionComponent<{
    application: UCloud.compute.Application;
    onImport: (parameters: Partial<UCloud.compute.JobSpecification>) => void;
    importDialogOpen: boolean;
    onImportDialogClose: () => void;
}> = ({application, onImport, importDialogOpen, onImportDialogClose}) => {

    const [previousRuns] = useCloudAPI<PageV2<Job>>(
        JobsApi.browse({
            itemsPerPage: 50,
            filterApplication: application.metadata.name,
        }),
        emptyPageV2
    );

    const [messages, setMessages] = useState<ImportMessage[]>([]);

    const readParsedJSON = useCallback(async (parsedJson: any) => {
        if (typeof parsedJson === "object") {
            const version = parsedJson["siteVersion"];

            let result: ImportResult;
            if (version === 1) {
                result = await importVersion1(application, parsedJson);
            } else if (version === 2) {
                result = await importVersion2(application, parsedJson);
            } else if (version === 3) {
                result = await importVersion2(application, parsedJson);
            } else {
                result = {messages: [{type: "error", message: "Corrupt or invalid import file"}]};
            }

            result = await cleanupImportResult(application, result)
            setMessages(result.messages);

            /* Is this correct? Is the typeof an undefined value not a string? */
            if (typeof result.output === undefined) {
                // Do nothing
            } else {
                onImport(result.output!);
            }
        }
    }, [])

    const importParameters = useCallback((file: File) => {
        const fileReader = new FileReader();
        fileReader.onload = async (): Promise<void> => {
            const rawInputFile = fileReader.result as string;
            try {
                const parsedJson = JSON.parse(rawInputFile);
                readParsedJSON(parsedJson);
            } catch (e) {
                console.warn(e);
                if (!file.name.endsWith(".json")) {
                    snackbarStore.addFailure(
                        errorMessageOrDefault(e, "An error occurred. The file format must be JSON."), false
                    );
                } else {
                    snackbarStore.addFailure(errorMessageOrDefault(e, "An error occurred"), false);
                }
            }
        };
        fileReader.readAsText(file);
    }, []);

    const [, invokeCommand] = useCloudCommand();

    const fetchAndImportParameters = useCallback(async (file: UFile) => {
        const download = await invokeCommand<UCloud.BulkResponse<FilesCreateDownloadResponseItem>>(
            FilesApi.createDownload(bulkRequestOf({id: file.id})),
            {defaultErrorHandler: false}
        );
        const downloadEndpoint = download?.responses[0]?.endpoint;
        if (!downloadEndpoint) {
            return;
        }
        const content = await fetch(downloadEndpoint);
        if (content.ok) importParameters(new File([await content.blob()], "params"));
    }, []);

    const previousRunsValid: Job[] = [];
    for (const run of previousRuns.data.items) {
        if (previousRunsValid.length >= 10) break;
        if (run.status.jobParametersJson != null && run.status.jobParametersJson["siteVersion"] != null) {
            previousRunsValid.push(run);
        }
    }

    return <Box>
        <Label>Load parameters from a previous run:</Label>
        <Flex flexDirection="row" flexWrap="wrap">
            {previousRunsValid.map((run, idx) => (
                <Box cursor="pointer" onClick={() => readParsedJSON(run.status.jobParametersJson)} mr="0.8em" key={idx}>
                    {run.specification.name ?? run.id}
                </Box>
            ))}
        </Flex>

        {messages.length === 0 ? null : (
            <Box>
                <TextP bold>We have attempted to your import your previous job</TextP>
                <MessageBox>
                    {messages.map((it, i) =>
                        <li key={i}>
                            {it.type === "error" ? <Icon name={"warning"} color={"red"} /> : null}
                            {it.type === "warning" ? <Icon name={"warning"} color={"yellow"} /> : null}
                            {it.type === "info" ? <Icon name={"info"} /> : null}
                            {it.message}
                        </li>
                    )}
                </MessageBox>
            </Box>
        )}

        <ReactModal
            isOpen={importDialogOpen}
            shouldCloseOnEsc
            onRequestClose={onImportDialogClose}
            style={defaultModalStyle}
            ariaHideApp={false}
        >
            <div>
                <div>
                    <Button fullWidth as="label">
                        Upload file
                        <HiddenInputField
                            type="file"
                            accept="application/json"
                            onChange={e => {
                                onImportDialogClose();
                                if (e.target.files) {
                                    const file = e.target.files[0];
                                    if (file.size > 10_000_000) {
                                        snackbarStore.addFailure("File exceeds 10 MB. Not allowed.", false);
                                    } else {
                                        importParameters(file);
                                    }
                                }
                            }}
                        />
                    </Button>
                    <Button mt="6px" fullWidth onClick={() => {
                        onImportDialogClose();
                        dialogStore.addDialog(
                            /* TODO(Jonas): Only allow files */
                            <FilesBrowse
                                browseType={BrowseType.Embedded}
                                onSelect={res => {
                                    fetchAndImportParameters(res);
                                    dialogStore.success();
                                }}
                                pathRef={{current: ""}}
                                onSelectRestriction={res => res.status.type === "FILE" && res.id.endsWith(".json")}
                            />,
                            () => undefined,
                            true
                        );
                    }}>
                        Select file from {CONF.PRODUCT_NAME}
                    </Button>
                    <Button mt="6px" fullWidth onClick={() => {
                        onImportDialogClose();
                        dialogStore.addDialog(
                            <JobBrowse
                                browseType={BrowseType.Embedded}
                                onSelect={res => {
                                    readParsedJSON(res.status.jobParametersJson);
                                    dialogStore.success();
                                }}
                                additionalFilters={{filterApplication: application.metadata.name}}
                                onSelectRestriction={res =>
                                    res.specification.application.name === application.metadata.name
                                }
                            />,
                            () => undefined,
                            true
                        );
                    }}>
                        Select parameters from jobs
                    </Button>
                </div>
                <Flex mt="20px">
                    <Button onClick={onImportDialogClose} color="red" mr="5px">Close</Button>
                </Flex>
            </div>
        </ReactModal>
    </Box>;
};

type ImportMessage =
    {type: "info", message: string} |
    {type: "warning", message: string} |
    {type: "error", message: string};

interface ImportResult {
    output?: Partial<JobSpecification>;
    messages: ImportMessage[];
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
async function importVersion1(application: UCloud.compute.Application, json: any): Promise<ImportResult> {
    if (typeof json !== "object") return {messages: [{type: "error", message: "Invalid job parameters file"}]};

    const output: Partial<JobSpecification> = {};
    const messages: ImportMessage[] = [];

    const {parameters, numberOfNodes, mountedFolders, maxTime, machineType, jobName} = json;
    const applicationFromFile = json.application;

    // Verify metadata
    if (applicationFromFile.name !== application.metadata.name) {
        return {messages: [{type: "error", message: "Failed because application names do not match"}]};
    } else if (applicationFromFile.version !== application.metadata.version) {
        messages.push({
            type: "info",
            message: "Application version does not match. Some parameters may not be filled out correctly.",
        });
    }

    const userInputValues: Record<string, AppParameterValue> = {};
    output.parameters = userInputValues;
    for (const param of application.invocation.parameters) {
        const valueFromFile = parameters[param.name];
        if (!valueFromFile) continue;
        switch (param.type) {
            case "integer":
                userInputValues[param.name] = {type: "integer", value: parseInt(valueFromFile.toString(), 10)};
                break;
            case "boolean":
                userInputValues[param.name] = {
                    type: "boolean",
                    value: valueFromFile === "Yes" || valueFromFile === true
                };
                break;
            case "peer":
                userInputValues[param.name] = {
                    type: "peer",
                    jobId: valueFromFile["jobId"]?.toString() ?? valueFromFile,
                    hostname: valueFromFile["hostname"]?.toString() ?? valueFromFile["name"]?.toString() ?? param.name
                };
                break;
            case "license_server":
                userInputValues[param.name] = {
                    type: "license_server",
                    id: valueFromFile["id"] ?? valueFromFile,
                };
                break;
            case "enumeration":
                userInputValues[param.name] = {type: "text", value: valueFromFile};
                break;
            case "floating_point":
                userInputValues[param.name] = {type: "floating_point", value: parseFloat(valueFromFile.toString())};
                break;
            case "text":
                userInputValues[param.name] = {type: "text", value: valueFromFile};
                break;
            case "textarea":
                userInputValues[param.name] = {type: "textarea", value: valueFromFile};
                break;
            case "input_directory":
            case "input_file": {
                userInputValues[param.name] = {
                    type: "file",
                    path: valueFromFile?.["source"]?.toString() ?? valueFromFile?.["ref"]?.toString() ?? "",
                    readOnly: false
                }
                break;
            }
        }
    }

    const resources: AppParameterValue[] = [];
    output.resources = resources;
    for (const mountedFolder of mountedFolders) {
        const path = mountedFolder["ref"]?.toString();
        if (path) {
            resources.push({type: "file", path, readOnly: false});
        }
    }

    const parametersFromUser = Object.keys(userInputValues);

    const unknownParameters = Object.keys(parameters).filter(it => !parametersFromUser.includes(it));
    for (const unknown of unknownParameters) {
        messages.push({type: "warning", message: "Parameter was removed from new version: " + unknown});
    }

    output.name = jobName;
    output.timeAllocation = maxTime;
    output.replicas = numberOfNodes;
    if (machineType !== undefined) {
        try {
            output.product = {
                id: machineType.id,
                category: machineType.category.id,
                provider: machineType.category.provider
            }
        } catch (ignored) {
            // Ignored
        }
    }

    return {output, messages};
}

async function importVersion2(application: UCloud.compute.Application, json: any): Promise<ImportResult> {
    const output = "request" in json ? json["request"] : {};
    return {output, messages: []};
}

// noinspection SuspiciousTypeOfGuard
async function cleanupImportResult(
    application: UCloud.compute.Application,
    result: ImportResult
): Promise<ImportResult> {
    const output = result.output;
    if (output === undefined) return result;

    if (typeof output.parameters !== "object" || !Array.isArray(output.resources)) {
        result.messages.push({type: "error", message: "Corrupt import file (resources)"});
        return result;
    }

    const parameters = output.parameters ?? {};
    const resources = output.resources ?? [];

    const badParam: (paramName: string) => void = (paramName) => {
        result.messages.push({type: "warning", message: "Corrupt parameter: " + paramName});
        delete parameters[paramName];
    }

    for (const paramName of Object.keys(parameters)) {
        const param = parameters[paramName];
        if (typeof param !== "object") {
            badParam(paramName);
            continue;
        }

        const type = application.invocation.parameters.find(it => it.name === paramName)?.type;
        if (type == null) {
            badParam(paramName);
            continue;
        }

        // noinspection SuspiciousTypeOfGuard
        if ((type === "input_file" || type === "input_directory") &&
            (typeof param["path"] !== "string" || typeof param["readOnly"] !== "boolean")) {
            badParam(paramName);
            continue;
        }

        // noinspection SuspiciousTypeOfGuard
        if (type === "boolean" && (typeof param["value"] != "boolean")) {
            badParam(paramName);
            continue;
        }

        // noinspection SuspiciousTypeOfGuard
        if (type === "integer" && (typeof param["value"] !== "number")) {
            badParam(paramName);
            continue;
        }

        // noinspection SuspiciousTypeOfGuard
        if (type === "floating_point" && (typeof param["value"] !== "number")) {
            badParam(paramName);
            continue;
        }

        // noinspection SuspiciousTypeOfGuard
        if (type === "license_server" && (typeof param["id"] !== "string")) {
            badParam(paramName);
            continue;
        }

        if (type === "input_file" || type === "input_directory") {
            if (!param["path"]) delete parameters[paramName];
            /*
            TODO
            else if (!await checkIfFileExists(param["path"] as string, Client)) {
                result.messages.push({type: "warning", message: "File no longer exists: " + param["path"]});
                delete parameters[paramName];
            }
             */
        }
    }

    let i = resources.length;
    while (i--) {
        const param = resources[i];
        if (param.type === "file") {
            // noinspection SuspiciousTypeOfGuard
            if (typeof param.path !== "string") {
                result.messages.push({type: "warning", message: "Corrupt mounted folder"});
                resources.splice(i, 1);
                continue;
            }

            /*
            TODO
            if (!await checkIfFileExists(param.path, Client)) {
                result.messages.push({type: "warning", message: "File no longer exists: " + param.path});
                resources.splice(i, 1);
            }
             */
        }
    }

    if (output.product !== undefined) {
        if (typeof output.product !== "object") {
            result.messages.push({type: "warning", message: "Corrupt machine type"})
            output.product = undefined;
        } else {
            // noinspection SuspiciousTypeOfGuard
            if (typeof output.product.provider !== "string") {
                result.messages.push({type: "warning", message: "Corrupt machine type"})
                output.product = undefined;
            }

            // noinspection SuspiciousTypeOfGuard
            else if (typeof output.product.id !== "string") {
                result.messages.push({type: "warning", message: "Corrupt machine type"})
                output.product = undefined;
            }

            // noinspection SuspiciousTypeOfGuard
            else if (typeof output.product.category !== "string") {
                result.messages.push({type: "warning", message: "Corrupt machine type"})
                output.product = undefined;
            }
        }
    }

    if (output.replicas !== undefined) {
        // noinspection SuspiciousTypeOfGuard
        if (typeof output.replicas !== "number") {
            result.messages.push({type: "warning", message: "Corrupt number of nodes"});
            output.replicas = undefined;
        }
    }

    if (output.timeAllocation !== undefined) {
        if (typeof output.timeAllocation !== "object") {
            result.messages.push({type: "warning", message: "Corrupt time allocation"});
            output.timeAllocation = undefined;
        } else {
            // noinspection SuspiciousTypeOfGuard
            if (typeof output.timeAllocation.hours !== "number") {
                result.messages.push({type: "warning", message: "Corrupt time allocation"});
                output.timeAllocation = undefined;
            }

            // noinspection SuspiciousTypeOfGuard
            else if (typeof output.timeAllocation.minutes !== "number") {
                result.messages.push({type: "warning", message: "Corrupt time allocation"});
                output.timeAllocation = undefined;
            }

            // noinspection SuspiciousTypeOfGuard
            else if (typeof output.timeAllocation.seconds !== "number") {
                result.messages.push({type: "warning", message: "Corrupt time allocation"});
                output.timeAllocation = undefined;
            }
        }
    }

    // noinspection SuspiciousTypeOfGuard
    if (output.name != null && typeof output.name !== "string") {
        result.messages.push({type: "warning", message: "Corrupt job name"});
        output.name = undefined;
    }

    return result;
}

const MessageBox = styled.ul`
    list-style-type: none;
    padding-left: 0;
    ${Icon} {
        margin-right: 10px;
    }
`;
