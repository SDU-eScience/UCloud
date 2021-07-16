import * as React from "react";
import {useHistory} from "react-router";
import {getQueryParam} from "Utilities/URIUtilities";
import {useCloudAPI, useCloudCommand} from "Authentication/DataHook";
import {useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState} from "react";
import {useLoading, useTitle} from "Navigation/Redux/StatusActions";
import {useRefreshFunction} from "Navigation/Redux/HeaderActions";
import {SidebarPages, useSidebarPage} from "ui-components/Sidebar";
import MainContainer from "MainContainer/MainContainer";
import {FormBuilder} from "@ginkgo-bioworks/react-json-schema-form-builder";
import {Text, TextArea, Box, Input, Label, Select, SelectableText, SelectableTextWrapper, Grid} from "ui-components";
import * as Heading from "ui-components/Heading";
import {Operation, Operations} from "ui-components/Operation";
import {Section} from "ui-components/Section";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {bulkRequestOf, placeholderProduct} from "DefaultObjects";
import {JsonSchemaForm} from "../JsonSchemaForm";
import {default as templateApi, FileMetadataTemplate, FileMetadataTemplateNamespace} from "UCloud/MetadataNamespaceApi";
import {BulkResponse, FindByStringId} from "UCloud";

enum Stage {
    INFO,
    SCHEMA,
    PREVIEW,
}

const Create: React.FunctionComponent = props => {
    const history = useHistory();
    const id = getQueryParam(history.location.search, "namespace");
    const [schema, setSchema] = useState<string>("{}");
    const [uiSchema, setUiSchema] = useState<string>("{}");
    const [namespace, fetchNamespace] = useCloudAPI<FileMetadataTemplateNamespace | null>({noop: true}, null);
    const [latestTemplate, fetchLatestTemplate] = useCloudAPI<FileMetadataTemplate | null>({noop: true}, null);
    const [stage, setStage] = useState(Stage.INFO);
    const [commandLoading, invokeCommand] = useCloudCommand();

    const idRef = useRef<HTMLInputElement>(null);
    const titleRef = useRef<HTMLInputElement>(null);
    const descriptionRef = useRef<HTMLTextAreaElement>(null);
    const versionRef = useRef<HTMLInputElement>(null);
    const changeLogRef = useRef<HTMLTextAreaElement>(null);
    const namespaceRef = useRef<HTMLSelectElement>(null);
    const requireApprovalRef = useRef<HTMLSelectElement>(null);
    const inheritRef = useRef<HTMLSelectElement>(null);

    const reload = useCallback(() => {
        if (id) {
            fetchNamespace(templateApi.retrieve({id}));
            fetchLatestTemplate(templateApi.retrieveLatest({id}));
        }
    }, [id]);

    const saveVersion = useCallback(async () => {
        let name = idRef.current?.value;
        const title = titleRef.current?.value;
        const description = descriptionRef.current?.value;
        const version = versionRef.current?.value;
        const changeLog = changeLogRef.current?.value;
        const namespaceType = namespaceRef.current?.value;
        const requireApproval = requireApprovalRef.current?.value === "true";
        const inheritable = inheritRef.current?.value === "true";

        if (!name) {
            snackbarStore.addFailure("Missing or bad ID", false);
            return;
        }

        if (!title) {
            snackbarStore.addFailure("Missing or bad title", false);
            return;
        }

        if (!description) {
            snackbarStore.addFailure("Missing or bad description", false);
            return;
        }

        if (!version) {
            snackbarStore.addFailure("Missing or bad version", false);
            return;
        }

        if (!changeLog) {
            snackbarStore.addFailure("Missing or bad change log", false);
            return;
        }

        if (!namespaceRef) {
            snackbarStore.addFailure("Missing or bad namespace type", false);
            return;
        }

        if (id == null) {
            // NOTE(Dan): We must register the namespace if this is not a new version of an existing template
            try {
                name = (await invokeCommand<BulkResponse<FindByStringId>>(
                    templateApi.create(bulkRequestOf({
                        name,
                        namespaceType: namespaceType as ("COLLABORATORS" | "PER_USER"),
                        product: placeholderProduct()
                    })),
                    {defaultErrorHandler: false}
                ))?.responses?.[0]!.id;
            } catch (e) {
                snackbarStore.addFailure(
                    "Unable to use this ID. It might already be in use, try a different ID",
                    false
                );
                return;
            }
        } else {
            name = namespace.data!.id;
        }

        const success = await invokeCommand(
            templateApi.createTemplate(bulkRequestOf({
                namespaceId: name!,
                title,
                version,
                description,
                changeLog,
                inheritable,
                requireApproval,
                uiSchema: JSON.parse(uiSchema),
                schema: JSON.parse(schema),
                namespaceType: namespaceType as ("COLLABORATORS" | "PER_USER"),
            }))
        ) != null;

        if (success) {
            history.push("/" + templateApi.routingNamespace);
        }
    }, [schema, uiSchema]);

    const callbacks: Callbacks = useMemo(() => ({
        stage,
        setStage,
        saveVersion
    }), [stage, setStage, saveVersion]);

    useEffect(reload, [reload]);

    useLayoutEffect(() => {
        if (namespace.data) {
            idRef.current!.value = namespace.data.specification.name;
        }

        if (latestTemplate.data) {
            setSchema(JSON.stringify(latestTemplate.data?.schema));
            if (latestTemplate.data?.uiSchema) {
                setUiSchema(JSON.stringify(latestTemplate.data?.uiSchema));
            }

            titleRef.current!.value = latestTemplate.data.title;
            descriptionRef.current!.value = latestTemplate.data.description;
            versionRef.current!.value = latestTemplate.data.version;
            changeLogRef.current!.value = latestTemplate.data.changeLog;
            namespaceRef.current!.value = latestTemplate.data.namespaceType;
            requireApprovalRef.current!.value = latestTemplate.data.requireApproval.toString();
            inheritRef.current!.value = latestTemplate.data.inheritable.toString();
        }
    }, [latestTemplate.data, namespace.data]);

    {
        let title = templateApi.title;
        if (latestTemplate?.data) {
            title += ` (${latestTemplate.data.title})`;
        }
        useTitle(title);
        useLoading(latestTemplate.loading || namespace.loading);
        useRefreshFunction(reload);
        useSidebarPage(SidebarPages.Files);
    }

    return <MainContainer
        header={
            <SelectableTextWrapper mb={"16px"}>
                <SelectableText onClick={() => setStage(Stage.INFO)} selected={stage === Stage.INFO}
                                children={"1. Info"} mr={"1em"}/>
                <SelectableText onClick={() => setStage(Stage.SCHEMA)} selected={stage === Stage.SCHEMA}
                                children={"2. Schema"} mr={"1em"}/>
                <SelectableText onClick={() => setStage(Stage.PREVIEW)} selected={stage === Stage.PREVIEW}
                                children={"3. Preview and save"} mr={"1em"}/>
            </SelectableTextWrapper>
        }
        headerSize={45}
        main={
            <Box minHeight={"calc(100vh - 76px - 47px)"} mt={"16px"}>
                <Box style={{display: stage !== Stage.INFO ? "none" : "block"}}>
                    <Grid maxWidth={"800px"} margin={"0 auto"} gridGap={"32px"}>
                        <Section gap={"16px"}>
                            <Heading.h3>Information</Heading.h3>
                            <Label>
                                ID
                                <Input ref={idRef} placeholder={"schema-xyz"} disabled={id != null}/>
                                {id == null ? null :
                                    <Text color={"gray"}>
                                        <b>NOTE:</b>{" "}
                                        You cannot change to ID when you are creating a new version of a template.
                                    </Text>
                                }
                            </Label>
                            <Label>
                                Title
                                <Input ref={titleRef} placeholder={"Metadata Schema for XYZ"}/>
                            </Label>
                            <Label>
                                Description <br/>
                                <TextArea
                                    ref={descriptionRef}
                                    rows={5}
                                    width={"100%"}
                                    placeholder={"This metadata schema contains information about..."}
                                />
                            </Label>
                        </Section>

                        <Section>
                            <Heading.h3>Versioning</Heading.h3>
                            <Label>
                                Version
                                <Input ref={versionRef} placeholder={"1.0.0"}/>
                            </Label>

                            <Label>
                                Changes since last version <br/>
                                <TextArea ref={changeLogRef} rows={2} width={"100%"}
                                          placeholder={"Version 1.1.0 has made the following changes..."}/>
                            </Label>
                        </Section>

                        <Section>
                            <Heading.h3>Behavior</Heading.h3>

                            <Label>
                                Namespace type
                                <Select selectRef={namespaceRef}>
                                    <option value={"COLLABORATORS"}>Collaborators</option>
                                    <option value={"PER_USER"}>Per user</option>
                                </Select>
                                <Text color={"gray"}>
                                    <Box my={"8px"}>
                                        <b>Collaborators: </b> Metadata documents will be shared among all collaborators
                                        of a file.
                                    </Box>
                                    <Box>
                                        <b>Per user: </b> Every collaborator of a file will have their own copy of the
                                        metadata document. Users cannot view the metadata document of other
                                        collaborators.
                                    </Box>
                                </Text>
                            </Label>

                            <Label>
                                Changes require approval
                                <Select selectRef={requireApprovalRef}>
                                    <option value={"true"}>Yes</option>
                                    <option value={"false"}>No</option>
                                </Select>
                                <Text color={"gray"}>
                                    <Box my={"8px"}>
                                        <b>Yes: </b> A workspace administrator, e.g. a project admin or PI, must approve
                                        all changes to the metadata document.
                                    </Box>
                                    <Box>
                                        <b>No: </b> Metadata documents can be modified by any user who is allowed to
                                        edit
                                        the file.
                                    </Box>
                                </Text>
                            </Label>

                            <Label>
                                Metadata should be inherited from ancestor directories
                                <Select selectRef={inheritRef}>
                                    <option value={"true"}>Yes</option>
                                    <option value={"false"}>No</option>
                                </Select>
                                <Text color={"gray"}>
                                    <Box my={"8px"}>
                                        <b>Yes: </b> Metadata will be present on all files that are a descendant,
                                        i.e. placed in the folder or any sub-folder, of this file. The metadata can be
                                        overridden by placing a different copy on a descendant.
                                    </Box>
                                    <Box>
                                        <b>No: </b> The metadata will only be present on the file it is directly
                                        associated
                                        with.
                                    </Box>
                                </Text>
                            </Label>
                        </Section>
                    </Grid>
                </Box>
                {stage !== Stage.SCHEMA ? null :
                    <FormBuilder
                        schema={schema}
                        uiSchema={uiSchema}
                        onChange={(newSchema: any, newUiSchema: any) => {
                            setSchema(newSchema);
                            setUiSchema(newUiSchema);
                        }}
                    />
                }
                {stage !== Stage.PREVIEW ? null :
                    <Grid gridGap={"32px"} width={"800px"} margin={"0 auto"}>
                        <Section>
                            <Heading.h3>Information</Heading.h3>
                            <ul>
                                <li><b>ID: </b>{idRef.current?.value}</li>
                                <li><b>Title: </b>{titleRef.current?.value}</li>
                                <li><b>Description: </b>{descriptionRef.current?.value}</li>
                            </ul>
                        </Section>
                        <Section>
                            <Heading.h3>Versioning</Heading.h3>
                            <ul>
                                <li><b>Version: </b>{versionRef.current?.value}</li>
                                <li><b>Changes since last version: </b>{changeLogRef.current?.value}</li>
                            </ul>
                        </Section>
                        <Section>
                            <Heading.h3>Behavior</Heading.h3>
                            <ul>
                                <li><b>Namespace type: </b>{prettySelectOption(namespaceRef.current)}</li>
                                <li><b>Changes require approval: </b>{prettySelectOption(requireApprovalRef.current)}
                                </li>
                                <li>
                                    <b>Metadata should be inherited from ancestor directories: </b>
                                    {prettySelectOption(inheritRef.current)}
                                </li>
                            </ul>
                        </Section>
                        <Section>
                            <Heading.h3>Form preview</Heading.h3>
                            <JsonSchemaForm
                                schema={JSON.parse(schema)}
                                uiSchema={JSON.parse(uiSchema)}
                            />
                        </Section>
                    </Grid>
                }
            </Box>
        }
        sidebar={
            <Operations location={"SIDEBAR"} operations={operations} selected={[]} extra={callbacks}
                        entityNameSingular={templateApi.title}/>
        }
    />;
};

function prettySelectOption(element?: HTMLSelectElement | null): string {
    if (element == null) return "N/A";
    return element.options[element.selectedIndex].text;
}

interface Callbacks {
    stage: Stage;
    setStage: (newStage: Stage) => void;
    saveVersion: () => void;
}

const operations: Operation<void, Callbacks>[] = [
    {
        text: "Previous",
        primary: true,
        icon: "backward",
        enabled: (_, cb) => cb.stage !== Stage.INFO ? true : "Already at first step",
        onClick: (_, cb) => {
            if (cb.stage === Stage.PREVIEW) {
                cb.setStage(Stage.SCHEMA);
            } else if (cb.stage === Stage.SCHEMA) {
                cb.setStage(Stage.INFO);
            }
        }
    },
    {
        text: "Next",
        icon: "forward",
        primary: true,
        enabled: (_, cb) => cb.stage !== Stage.PREVIEW,
        onClick: (_, cb) => {
            if (cb.stage === Stage.INFO) {
                cb.setStage(Stage.SCHEMA);
            } else if (cb.stage === Stage.SCHEMA) {
                cb.setStage(Stage.PREVIEW);
            }
        }
    },
    {
        text: "Save",
        icon: "upload",
        confirm: true,
        color: "green",
        primary: true,
        enabled: (_, cb) => cb.stage === Stage.PREVIEW,
        onClick: (_, cb) => {
            cb.saveVersion();
        }
    }
];

export default Create;
