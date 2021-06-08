import * as React from "react";
import * as UCloud from "UCloud";
import templateApi = UCloud.file.orchestrator.metadata_template;
import {useHistory} from "react-router";
import {getQueryParam} from "Utilities/URIUtilities";
import {useCloudAPI, useCloudCommand} from "Authentication/DataHook";
import {file} from "UCloud";
import FileMetadataTemplate = file.orchestrator.FileMetadataTemplate;
import {useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState} from "react";
import {useLoading, useTitle} from "Navigation/Redux/StatusActions";
import {entityName} from "Files/Metadata/Templates/Browse";
import {useRefreshFunction} from "Navigation/Redux/HeaderActions";
import {SidebarPages, useSidebarPage} from "ui-components/Sidebar";
import MainContainer from "MainContainer/MainContainer";
import {FormBuilder} from "@ginkgo-bioworks/react-json-schema-form-builder";
import {Text, TextArea, Box, Input, Label, Select, SelectableText, SelectableTextWrapper, Grid, theme} from "ui-components";
import * as Heading from "ui-components/Heading";
import {Operation, Operations} from "ui-components/Operation";
import {Section} from "ui-components/Section";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {bulkRequestOf} from "DefaultObjects";
import {JsonSchemaForm} from "../JsonSchemaForm";
import styled from "styled-components";

enum Stage {
    INFO,
    SCHEMA,
    PREVIEW,
}

const Create: React.FunctionComponent = props => {
    const history = useHistory();
    const id = getQueryParam(history.location.search, "id");
    const [schema, setSchema] = useState<string>("{}");
    const [uiSchema, setUiSchema] = useState<string>("{}");
    const [template, fetchTemplate] = useCloudAPI<FileMetadataTemplate | null>({noop: true}, null);
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
        if (id) fetchTemplate(templateApi.retrieve({id}));
    }, [id]);

    const saveVersion = useCallback(async () => {
        const id = idRef.current?.value;
        const title = titleRef.current?.value;
        const description = descriptionRef.current?.value;
        const version = versionRef.current?.value;
        const changeLog = changeLogRef.current?.value;
        const namespace = namespaceRef.current?.value;
        const requireApproval = requireApprovalRef.current?.value === "true";
        const inheritable = inheritRef.current?.value === "true";

        if (!id) {
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
            snackbarStore.addFailure("Missing or bad description", false);
            return;
        }

        if (!changeLog) {
            snackbarStore.addFailure("Missing or bad description", false);
            return;
        }

        if (!namespaceRef) {
            snackbarStore.addFailure("Missing or bad description", false);
            return;
        }

        const success = await invokeCommand(
            templateApi.create(bulkRequestOf({
                id,
                title,
                version,
                description,
                changeLog,
                inheritable,
                requireApproval,
                uiSchema: JSON.parse(uiSchema),
                schema: JSON.parse(schema),
                namespaceType: namespace as ("COLLABORATORS" | "PER_USER"),
                product: undefined
            }))
        ) != null;

        if (success) {
            history.push("/files/metadata/templates/");
        }
    }, [schema, uiSchema]);

    const callbacks: Callbacks = useMemo(() => ({
        stage,
        setStage,
        saveVersion
    }), [stage, setStage, saveVersion]);

    useEffect(reload, [reload]);

    useLayoutEffect(() => {
        if (template.data) {
            setSchema(JSON.stringify(template.data?.specification.schema));
            if (template.data?.specification.uiSchema) {
                setUiSchema(JSON.stringify(template.data?.specification.uiSchema));
            }

            idRef.current!.value = template.data.id;
            titleRef.current!.value = template.data.specification.title;
            descriptionRef.current!.value = template.data.specification.description;
            versionRef.current!.value = template.data.specification.version;
            changeLogRef.current!.value = template.data.specification.changeLog;
            namespaceRef.current!.value = template.data.specification.namespaceType;
            requireApprovalRef.current!.value = template.data.specification.requireApproval.toString();
            inheritRef.current!.value = template.data.specification.inheritable.toString();
        }
    }, [template]);

    {
        let title = entityName;
        if (template?.data) {
            title += ` (${template.data.specification.title})`;
        }
        useTitle(title);
        useLoading(template.loading);
        useRefreshFunction(reload);
        useSidebarPage(SidebarPages.Files);
    }

    return <MainContainer
        header={
            <SelectableTextWrapper mb={"16px"}>
                <SelectableText onClick={() => setStage(Stage.INFO)} selected={stage === Stage.INFO} mr="1em">
                    1. Info
                </SelectableText>
                <SelectableText onClick={() => setStage(Stage.SCHEMA)} selected={stage === Stage.SCHEMA} mr="1em">
                    2. Schema
                </SelectableText>
                <SelectableText onClick={() => setStage(Stage.PREVIEW)} selected={stage === Stage.PREVIEW} mr="1em">
                    3. Preview and save
                </SelectableText>
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
                                <Input ref={idRef} placeholder={"schema-xyz"} />
                            </Label>
                            <Label>
                                Title
                                <Input ref={titleRef} placeholder={"Metadata Schema for XYZ"} />
                            </Label>
                            <Label>
                                Description <br />
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
                                <Input ref={versionRef} placeholder={"1.0.0"} />
                            </Label>

                            <Label>
                                Changes since last version <br />
                                <TextArea ref={changeLogRef} rows={2} width={"100%"}
                                    placeholder={"Version 1.1.0 has made the following changes..."} />
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
                    <BootstrapReplacement>
                        <FormBuilder
                            schema={schema}
                            uiSchema={uiSchema}
                            onChange={(newSchema: any, newUiSchema: any) => {
                                setSchema(newSchema);
                                setUiSchema(newUiSchema ?? uiSchema);
                            }}
                        />
                    </BootstrapReplacement>
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
                            <JsonSchemaFormBootstrapReplacement>
                                <JsonSchemaForm
                                    schema={JSON.parse(schema)}
                                    uiSchema={JSON.parse(uiSchema)}
                                />
                            </JsonSchemaFormBootstrapReplacement>
                        </Section>
                    </Grid>
                }
            </Box>
        }
        sidebar={
            <Operations location={"SIDEBAR"} operations={operations} selected={[]} extra={callbacks}
                entityNameSingular={entityName} />
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

const BootstrapReplacement = styled.div`
    & > div.formBuilder-0-2-1 {
        div.formHead-0-2-2 {
            border: 1px solid transparent;
            background-color: var(--lightGray, #f00);

            div > {
                h5.form-name-label {
                    color: var(--text, #f00);
                }

                input.form-control:focus-visible {
                    outline: none;
                }
            }
        }
        
        div.card-select {
            background-color: var(--lightGray, #f00);
        }

        span.label {
            color: var(--text);
        }

        div.form-body {

            div.collapse-element.card-container {
                background-color: var(--lightGray, #f00);
                border: 2px solid transparent;
            }

            div.collapse-element.card-container:hover {
                border: 2px solid var(--blue, #f00);
            }

            div.section-container {
                background-color: var(--lightGray, #f00);
                border: 1px solid transparent;
            }

            div.section-container:hover {
                border: 1px solid var(--blue, #f00);
            }
        }

        div > span > svg.svg-inline--fa.fa-plus-square.fa-w-14.fa, span > span > svg.svg-inline--fa.fa-arrow-up.fa-w-14.fa,
        span > span > svg.svg-inline--fa.fa-arrow-down.fa-w-14.fa {
            color: var(--blue, #f00);
        }

        span > span > svg.svg-inline--fa.fa-arrow-up.fa-w-14.fa, span > span > svg.svg-inline--fa.fa-arrow-down.fa-w-14.fa {
            border: none;
        }
        
        span > svg.svg-inline--fa.fa-pencil-alt.fa-w-16.fa {
            color: var(--blue, #f00);
            border: 2px solid var(--blue, #f00);
        }

        span > svg.svg-inline--fa.fa-trash.fa-w-14.fa {
            color: var(--red, #f00);
            border: 2px solid var(--red, #f00);
        }

        input.form-control {
            border: 2px solid var(--midGray);
            display: block;
            font-family: inherit;
            width: 100%;
            color: var(--black, #f00);
            background-color: transparent;
        
            margin: 0;
            &:invalid {
                border-color: var(--red, #f00);
            }
        
            border-radius: 5px;
            padding: 7px 12px 7px 12px;
        
            ::placeholder {
                color: var(--gray, #f00);
            }
        
            &:focus {
                outline: none;
                background-color: transparent;
            }
        
            &:disabled {
                background-color: var(--lightGray, #f00);
            }
        }

        input.form-control:active, input.form-control:focus {
            border: 2px solid var(--blue);            
        }

        div[class*="-ValueContainer"], div[class*="-IndicatorsContainer"] {
            background-color: var(--lightGray);
        }

        div.delete-button {
            color: var(--red);
            margin-left: 6px;
        }

        svg.svg-inline--fa.fa-plus.fa-w-14.fa {
            margin-left: auto;
            margin-right: auto;
            color: var(--blue);            
        }

    & form > button {
        font-smoothing: antialiased;
        display: inline-flex;
        justify-content: center;
        align-items: center;
        text-align: center;
        text-decoration: none;
        font-family: inherit;
        font-weight: ${theme.bold};
        cursor: pointer;
        border-radius: ${theme.radius};
        background-color: var(--blue, #f00);
        color: var(--white, #f00);
        border-width: 0;
        border-style: solid;
        line-height: 1.5;
        width: 100px;
        height: 40px;
    }

    & div.d-flex {
        display: flex;
        border-bottom: none;
    }

    & div.collapse, & div.collapsing {
        display: none;
    }

    & div.collapse.show {
        display: block;
    }

    & div.cardEntries-0-2-7 {
        border-bottom: none;
    }

    & div.section-interactions { 
        border-top: none; 
    }

    & div.section-head { 
        border-bottom: none;
    }
`;

const JsonSchemaFormBootstrapReplacement = styled.div`

    & button {
        font-smoothing: antialiased;
        display: inline-flex;
        justify-content: center;
        align-items: center;
        text-align: center;
        text-decoration: none;
        font-family: inherit;
        font-weight: ${theme.bold};
        cursor: pointer;
        border-radius: ${theme.radius};
        background-color: var(--blue, #f00);
        color: var(--white, #f00);
        border-width: 0;
        border-style: solid;
        line-height: 1.5;
        width: 100px;
        height: 40px;
    }

    & form.rjsf > div > button:hover {
        transform: translateY(-2px);
    }

    & form.rjsf > div > fieldset > div > input {
        margin-top: 2px;
        margin-bottom: 4px;
    }

    & button:hover {
        transform: translateY(-2px);
    }

    & i.glyphicon.glyphicon-remove::before {
        content: "❌";
    }

    & button.btn-add {
        width: 45px;
        color: var(--white, #f00);
        background-color: var(--green, #f00);
    }

    & i {
        font-style: normal;
    }



    & .glyphicon-arrow-up::before {
        content: '↑';
    }

    & .glyphicon-arrow-down::before {
        content: '↓';
    }

    & button.btn-danger {
        width: 45px;
        color: var(--white, #f00);
        background-color: var(--red, #f00);
    }

    & div.array-item > div.col.xs-9 { 
        width: 100%;
    }
    
    & div.array-item {
        display: flex;
    }

    & button.btn.btn-default.array-item-move-up, & button.btn.btn-default.array-item-move-down, & button.btn.btn-default.array-item-remove { 
        width: 45px;
    }

    & i.glyphicon.glyphicon-plus::before {
        content: "+";
    }

    & div.array-item > div.col.xs-9 {
        width: 100%;
    }
`;

export default Create;
