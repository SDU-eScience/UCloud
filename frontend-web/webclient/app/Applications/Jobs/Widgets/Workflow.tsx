import * as React from "react";
import {useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState} from "react";
import {default as ReactModal} from "react-modal";
import {Box, Button, Flex, Icon, List, Markdown} from "@/ui-components";
import {fullScreenModalStyle} from "@/Utilities/ModalUtilities";
import {findElement, widgetId, WidgetProps, WidgetSetter, WidgetValidator} from "@/Applications/Jobs/Widgets/index";
import {callAPI, useCloudAPI} from "@/Authentication/DataHook";
import {CardClass} from "@/ui-components/Card";
import {ApplicationParameterNS} from "@/Applications/AppStoreApi";
import WorkflowEditor from "@/Applications/Workflows/Editor";
import * as WorkflowApi from "@/Applications/Workflows";
import {Workflow, WorkflowSpecification} from "@/Applications/Workflows";
import {AppLogo, hashF} from "@/Applications/AppToolLogo";
import {emptyPageV2} from "@/Utilities/PageUtilities";
import {bulkRequestOf, doNothing, timestampUnixMs} from "@/UtilityFunctions";
import {SingleLineMarkdown} from "@/ui-components/Markdown";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";
import {TooltipV2} from "@/ui-components/Tooltip";
import {RichSelect, RichSelectChildComponent} from "@/ui-components/RichSelect";
import ScrollableBox from "@/ui-components/ScrollableBox";
import {compute} from "@/UCloud";
import AppParameterValueNS = compute.AppParameterValueNS;

interface WorkflowProps extends WidgetProps {
    parameter: ApplicationParameterNS.Workflow;
}

interface WorkflowParameterInputFormat {
    id: string | null;
    path: string | null;
    specification: WorkflowSpecification;
}

const WorkflowSelectedRow: RichSelectChildComponent<SearchableWorkflow> = ({element, dataProps, onSelect}) => {
    const [extractedDescription, extractedBody] = useMemo(() => {
        if (!element || element.type !== "workflow") return ["", ""];
        return extractWorkflowDescriptionAndBody(element.specification);
    }, [element]);

    if (!element) {
        return <Flex height={40} alignItems={"center"} pl={12}>No workflow selected</Flex>
    }

    if (element.type === "create") {
        return <Flex gap={"16px"} {...dataProps} alignItems={"center"} p={8} onClick={onSelect}>
            <Icon name={"heroPlus"} size={24} color={"successMain"} />
            <b>Create a new workflow</b>
        </Flex>;
    } else {
        return <Flex gap={"16px"} {...dataProps} alignItems={"center"} p={8} onClick={onSelect}>
            <AppLogo size={"24px"} hash={hashF(element.status.path)}/>
            <b>{element.status.path}</b>
            <div style={{color: "var(--textSecondary)"}}>
                <SingleLineMarkdown width={"600px"} children={extractedDescription}/>
            </div>
        </Flex>;
    }
}

type SearchableWorkflow = (Workflow & { type: "workflow", searchString: string; }) | { type: "create", searchString: string; };

export const WorkflowParameter: React.FunctionComponent<WorkflowProps> = props => {
    const error = props.errors[props.parameter.name];

    const [selectedWorkflow, setSelectedWorkflow] = useState<{
        id: string | null;
        path: string | null;
        specification: WorkflowSpecification;
    } | null>(null);

    const doClose = useCallback(() => {
        setSelectedWorkflow(null);
    }, [selectedWorkflow]);

    const [existingWorkflows, fetchExistingWorkflows] = useCloudAPI<PageV2<Workflow>>({noop: true}, emptyPageV2);
    const [activeInput, setActiveInput] = useState<WorkflowParameterInputFormat | null>(null);
    const [activeWorkflow, setActiveWorkflow] = useState<SearchableWorkflow | undefined>(undefined);

    const workflows: SearchableWorkflow[] = useMemo(() => {
        let result: SearchableWorkflow[] = [];
        const defaultSpec = props.parameter.defaultValue as WorkflowSpecification;
        result.push({
            id: "default",
            createdAt: timestampUnixMs(),
            owner: {
                createdBy: "_ucloud",
                project: null
            },
            specification: defaultSpec,
            status: {
                path: "Default"
            },
            type: "workflow",
            searchString: "Default",
            permissions: {
                openToWorkspace: true,
                myself: ["READ", "WRITE", "ADMIN"],
                others: [],
            }
        });

        for (const wf of existingWorkflows.data.items) {
            result.push({...wf, type: "workflow", searchString: wf.status.path});
        }

        if (activeInput) {
            if (!result.some(it => it.type === "workflow" && it.id === activeInput.id)) {
                result = [
                    {
                        id: "unsaved",
                        createdAt: timestampUnixMs(),
                        owner: {
                            createdBy: "_ucloud",
                            project: null
                        },
                        specification: activeInput.specification,
                        status: {
                            path: "Unsaved workflow based on " + (activeInput.path ?? "default"),
                        },
                        type: "workflow",
                        searchString: (activeInput.path ?? "Unsaved workflow"),
                        permissions: {
                            openToWorkspace: true,
                            myself: ["READ", "WRITE", "ADMIN"],
                            others: [],
                        }
                    },
                    ...result
                ];
            }
        }

        result.push({ type: "create", searchString: "Create" });

        return result;
    }, [existingWorkflows.data.items, props.parameter.defaultValue, activeInput]);

    useEffect(() => {
        if (!activeInput) {
            setActiveWorkflow(undefined);
        } else {
            let existingWorkflow = workflows.find(it => it.type === "workflow" && it.id === activeInput.id);
            if (!existingWorkflow) {
                existingWorkflow = workflows.find(it => it.type === "workflow" && it.id === "unsaved");
            }
            setActiveWorkflow(existingWorkflow ?? undefined);
        }
    }, [activeInput, workflows]);

    useEffect(() => {
        fetchExistingWorkflows(WorkflowApi.browse({
            itemsPerPage: 250,
            filterApplicationName: props.application.metadata.name
        })).then(doNothing);
    }, [props.application.metadata.name]);

    const valueInput = () => document.getElementById(widgetId(props.parameter)) as HTMLInputElement | null;
    const getActiveValue = (): WorkflowParameterInputFormat | null => {
        const input = valueInput();
        if (!input) return null;
        if (!input.value) return null;
        return JSON.parse(input.value) as WorkflowParameterInputFormat;
    }

    useEffect(() => {
        props.injectWorkflowParameters(activeInput?.specification?.inputs ?? []);
    }, [activeInput]);

    useLayoutEffect(() => {
        const listener = async () => {
            const value = valueInput();
            if (value) {
                const loadedValue = getActiveValue();
                if (loadedValue) {
                    setActiveInput(loadedValue);
                }
            }
        };

        const value = valueInput();
        value!.addEventListener("change", listener);
        return () => {
            value!.removeEventListener("change", listener);
        }
    }, []);

    const onUse = useCallback((id: string | null, path: string | null, specification: WorkflowSpecification) => {
        const input = valueInput();
        if (!input) return;
        input.value = JSON.stringify({id, path, specification});
        input.dispatchEvent(new Event("change"));
        setSelectedWorkflow(null);

        if (id != null) {
            fetchExistingWorkflows(
                WorkflowApi.browse({
                    itemsPerPage: 250,
                    filterApplicationName: props.application.metadata.name
                })
            ).then(doNothing);
        }
    }, [props.application.metadata.name]);

    const onEdit = useCallback((id: string | null, path: string | null, specification: WorkflowSpecification) => {
        setSelectedWorkflow({id, path, specification});
    }, []);

    const onDoEdit = useCallback(() => {
        if (!activeWorkflow) return;
        if (activeWorkflow.type !== "workflow") return;

        const id = activeWorkflow.id === "default" ? null : activeWorkflow.id;
        onEdit(id, activeWorkflow.status.path, activeWorkflow.specification);
    }, [onEdit, activeWorkflow]);

    const descriptionRef = useRef<HTMLDivElement>(null);
    const [expanded, setExpanded] = useState(false);
    const onExpand = useCallback(() => {
        setExpanded(p => !p);
        const box = descriptionRef.current;
        if (box) {
            window.setTimeout(() => box.scrollIntoView(true), 0);
        }
    }, []);

    const [canExpand, setCanExpand] = useState(false);
    useLayoutEffect(() => {
            const box = descriptionRef.current;
            if (!box) {
                setCanExpand(true);
            } else {
                const child = box.querySelector("div") as HTMLDivElement;
                const {scrollHeight, clientHeight} = child;
                if (scrollHeight <= clientHeight) {
                    setCanExpand(false);
                } else {
                    setCanExpand(true);
                }
            }
    }, [activeWorkflow]);

    return (<Flex flexDirection={"column"}>
        <input type="hidden" id={widgetId(props.parameter)}/>
        {!error ? null : <>
            <p style={{color: "var(--errorMain)"}}>{error}</p>
        </>}

        <Flex gap={"8px"} alignItems={"center"}>
            <Box flexGrow={1}>
                <RichSelect
                    items={workflows}
                    keys={["searchString"]}
                    RenderRow={WorkflowSelectedRow}
                    RenderSelected={WorkflowSelectedRow}
                    onSelect={wf => {
                        if (wf.type === "create") {
                            onEdit(null, "Default", props.parameter.defaultValue as WorkflowSpecification);
                        } else {
                            onUse(wf.id, wf.status.path, wf.specification);
                        }
                    }}
                    selected={activeWorkflow}
                    noResultsItem={{type: "create", searchString: ""}}
                />
            </Box>

            {!activeWorkflow || activeWorkflow.type !== "workflow" ? null : <>
                {activeWorkflow.id === "default" || activeWorkflow.id === "unsaved" ? null :
                    <TooltipV2 tooltip={"Hold to delete"} contentWidth={150}>
                        <ConfirmationButton onAction={async () => {
                            await callAPI(WorkflowApi.remove(bulkRequestOf({ id: activeWorkflow.id })));
                            fetchExistingWorkflows(WorkflowApi.browse({
                                itemsPerPage: 250,
                                filterApplicationName: props.application.metadata.name,
                            })).then(doNothing);
                            setActiveInput(null);
                        }} icon={"heroTrash"}/>
                    </TooltipV2>
                }

                <Button onClick={onDoEdit} color={"secondaryMain"}>
                    <Icon name={"heroPencil"} mr={"8px"}/>
                    Edit
                </Button>
            </>}
        </Flex>

        {!activeWorkflow || activeWorkflow.type !== "workflow" || !activeWorkflow.specification.readme ? null : <div style={{marginTop: "16px"}} ref={descriptionRef}>
            <ScrollableBox maxHeight={expanded ? "80vh" : "300px"}>
                <Markdown children={(extractWorkflowDescriptionAndBody(activeWorkflow.specification))[1]} />
            </ScrollableBox>
            {canExpand ?
                <Flex justifyContent={"center"} cursor={"pointer"} onClick={onExpand}>
                    <Icon name={expanded ? "heroChevronDoubleUp" : "heroChevronDoubleDown"} />
                </Flex> : null
            }
        </div>}

        <ReactModal
            isOpen={selectedWorkflow != null}
            ariaHideApp={false}
            style={fullScreenModalStyle}
            shouldCloseOnEsc
            shouldCloseOnOverlayClick
            onRequestClose={doClose}
            className={CardClass}
        >
            {selectedWorkflow ?
                <WorkflowEditor
                    applicationName={props.application.metadata.title}
                    initialId={selectedWorkflow.id}
                    initialExistingPath={selectedWorkflow.path}
                    workflow={selectedWorkflow.specification}
                    onUse={onUse}
                /> : null
            }
        </ReactModal>
    </Flex>);
}

function extractWorkflowDescriptionAndBody(spec: WorkflowSpecification): [string, string] {
    // NOTE(Dan): Extracts the first paragraph from the README field. This is then passed to <SingleLineMarkdown />
    // and displayed as a description of the workflow. There is no guarantee that this actually makes any sense, but
    // it is up to the users to follow this convention. If it can work for Git, then it can work for us.

    const readme = spec.readme ?? "";
    const lines = readme.split("\n");
    let builder = "";
    let body = "";
    let fillBody = false;
    for (const line of lines) {
        if (!fillBody) {
            if (!line && builder) {
                fillBody = true;
                continue;
            }
            if (builder) builder += " ";
            builder += line;
        } else {
            if (body) body += "\n";
            body += line;
        }
    }

    return [builder, body];
}

export const WorkflowValidator: WidgetValidator = (param) => {
    if (param.type === "workflow") {
        const elem = findElement(param);
        if (elem === null) return {valid: true};
        if (elem.value === "") return {valid: false, message: "You must select a workflow"};
        try {
            const parsed = JSON.parse(elem.value) as WorkflowParameterInputFormat;
            return {
                valid: true,
                value: {
                    type: "workflow",
                    specification: parsed.specification,
                }
            };
        } catch (e) {
            return {valid: false, message: "Invalid parameter specified."};
        }
    }

    return {valid: true};
};

export const WorkflowSetter: WidgetSetter = (param, value) => {
    if (param.type !== "workflow") return;
    const selector = findElement(param);
    if (selector === null) throw "Missing element for: " + param.name;
    const wf = value as AppParameterValueNS.Workflow;
    const formatted: WorkflowParameterInputFormat = {
        id: null,
        path: null,
        specification: wf.specification,
    };

    selector.value = JSON.stringify(formatted);
    selector.dispatchEvent(new Event("change"));
};
