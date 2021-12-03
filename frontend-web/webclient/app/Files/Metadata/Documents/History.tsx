import * as React from "react";
import {
    default as metadataApi,
    FileMetadataHistory,
    FileMetadataDocumentOrDeleted, FileMetadataDocumentApproval,
} from "@/UCloud/MetadataDocumentApi";
import {FileMetadataTemplate} from "@/UCloud/MetadataNamespaceApi";
import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {noopCall, useCloudCommand} from "@/Authentication/DataHook";
import {bulkRequestOf} from "@/DefaultObjects";
import {TextArea, Box, Button, Flex, Grid, Icon, Label} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {dateToString} from "@/Utilities/DateUtilities";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";
import {JsonSchemaForm} from "@/Files/Metadata/JsonSchemaForm";
import styled from "styled-components";
import {ListRowStat} from "@/ui-components/List";
import {deviceBreakpoint} from "@/ui-components/Hide";
import {UFile} from "@/UCloud/FilesApi";
import {ItemRenderer, StandardCallbacks, StandardList} from "@/ui-components/Browse";
import {Operation} from "@/ui-components/Operation";
import {getCssVar} from "@/Utilities/StyledComponentsUtilities";
import {SvgFt} from "@/ui-components/FtIcon";

export const History: React.FunctionComponent<{
    file: UFile;
    template: FileMetadataTemplate;
    metadata: FileMetadataHistory;
    reload: () => void;
    close: () => void;
}> = ({file, metadata, reload, close, template}) => {
    // Contains the new version currently being edited
    const [editingDocument, setEditingDocument] = useState<Record<string, any> | null>(null);
    // Contains the document we are currently inspecting
    // We will display on the left hand side `documentInspection` if it is not null otherwise `editingDocument`.
    const [documentInspection, setDocumentInspection] = useState<FileMetadataDocumentOrDeleted | null>(null);
    const [commandLoading, invokeCommand] = useCloudCommand();

    const hasActivity = metadata.metadata[template.namespaceId] != null;
    const activity = useMemo(() => metadata.metadata[template.namespaceId] ?? [], [metadata]);
    const changeLogRef = useRef<HTMLTextAreaElement>(null);

    useEffect(() => {
        if (!hasActivity) {
            setEditingDocument(null);
        }
    }, [hasActivity]);

    // Most recent approved document
    const activeDocument = activity.find(it =>
        it.type === "deleted" ||
        (
            it.type === "metadata" &&
            (it.status.approval.type === "not_required" || it.status.approval.type === "approved")
        )
    );

    useEffect(() => {
        // NOTE(Dan): Automatically switch to the latest document which is valid.
        if (activeDocument && !editingDocument) {
            setDocumentInspection(activeDocument);
        }
    }, [activeDocument, editingDocument]);

    const submitNewVersion = useCallback(async () => {
        if (commandLoading) return;
        if (!editingDocument) return;
        await invokeCommand(
            metadataApi.create(bulkRequestOf({
                fileId: file.id,
                metadata: {
                    templateId: template.namespaceId,
                    version: template.version,
                    document: editingDocument,
                    changeLog: changeLogRef?.current?.value ?? "",
                }
            }))
        );

        const change = changeLogRef?.current;
        if (change) change.value = "";

        reload();
        setDocumentInspection(activeDocument ?? null);
        setEditingDocument(null);
    }, [file, reload, commandLoading, editingDocument, template]);

    const activityRows = useMemo((): DocumentRow[] => {
        return activity.map(entry => ({
            isActive: entry === activeDocument,
            doc: entry
        }));
    }, [activeDocument, activity]);

    const activityNavigate = useCallback((row: DocumentRow) => {
        setDocumentInspection(row.doc);
    }, []);

    const activityCallbacks = useMemo((): ActivityCallbacks => ({
        file, setEditingDocument, setDocumentInspection, documentInspection, reloadFile: reload
    }), [file, setEditingDocument, setDocumentInspection, documentInspection, reload]);

    const approveChange = useCallback(async () => {
        if (!documentInspection) return;
        await invokeCommand(metadataApi.approve(bulkRequestOf({id: documentInspection.id})));
    }, [documentInspection]);

    const rejectChange = useCallback(async () => {
        if (!documentInspection) return;
        await invokeCommand(metadataApi.reject(bulkRequestOf({id: documentInspection.id})));
    }, [documentInspection]);

    return <Box>
        <Flex mb={16} height={40}>
            <Box flexGrow={1}>
                <Flex alignItems={"center"}>
                    <Heading.h3>{template.title}</Heading.h3>
                </Flex>
            </Box>
            <Button onClick={close} mr={"34px"}>
                <Icon name={"backward"} mr={16}/>
                Back to overview
            </Button>
        </Flex>

        <Layout>
            <div className={"doc-viewer"}>
                <Box mb={"16px"}>
                    {documentInspection && documentInspection !== activeDocument ?
                        <>
                            <Heading.h3 flexGrow={1}>
                                Viewing old document
                            </Heading.h3>

                        </> :
                        documentInspection && documentInspection === activeDocument ?
                            <>
                                <Heading.h3 flexGrow={1}>Current document</Heading.h3>
                            </> :
                            <>
                                <Heading.h3 flexGrow={1}>New document</Heading.h3>
                                <Label>
                                    Changes<br/>
                                    <TextArea width={"100%"} rows={4} ref={changeLogRef}/>
                                </Label>
                                <ConfirmationButton mt={"8px"} color={"green"} icon={"check"} actionText={"Save"}
                                                    onAction={submitNewVersion} fullWidth/>
                            </>
                    }
                </Box>
                <div className="scroll-area">
                    {documentInspection ? <>
                        <div><b>Change by:</b> {documentInspection.createdBy}</div>
                        <div><b>Created at:</b> {dateToString(documentInspection.createdAt)}</div>
                        {documentInspection.status.approval.type !== "rejected" ? null :
                            <div><b>Rejected by:</b> {documentInspection.status.approval.rejectedBy}</div>
                        }
                        {documentInspection.status.approval.type !== "approved" ? null :
                            <div><b>Approved by:</b> {documentInspection.status.approval.approvedBy}</div>
                        }

                        {documentInspection.status.approval.type === "pending" &&
                        file.permissions.myself.some(it => it === "ADMIN") ?
                            <Grid gridTemplateColumns={"1fr 1fr"} gridGap={"8px"} mt={"8px"}>
                                <ConfirmationButton actionText={"Approve change"} icon={"check"} color={"green"}
                                                    onAction={approveChange}/>
                                <ConfirmationButton actionText={"Reject change"} icon={"trash"} color={"red"}
                                                    onAction={rejectChange}/>
                            </Grid> : null
                        }

                        {documentInspection.type === "deleted" ? "The metadata has been deleted" : null}
                        {documentInspection.type !== "metadata" ? null : <FormWrapper>
                            <JsonSchemaForm
                                disabled={true}
                                schema={template.schema}
                                uiSchema={template.uiSchema}
                                formData={documentInspection.specification.document}
                            />
                        </FormWrapper>
                        }
                    </> : <FormWrapper>
                        <JsonSchemaForm
                            schema={template.schema}
                            uiSchema={template.uiSchema}
                            formData={editingDocument}
                            onChange={(e) => setEditingDocument(e.formData)}
                        />
                    </FormWrapper>}
                </div>
            </div>

            {hasActivity ?
                <div className={"activity"}>
                    <div className="scroll-area" style={{paddingTop: "5px"}}>
                        <StandardList
                            generateCall={noopCall} renderer={entryRenderer} embedded={"inline"}
                            title={"Activity entry"} titlePlural={"Activity"} preloadedResources={activityRows}
                            navigate={activityNavigate} operations={entryOperations}
                            extraCallbacks={activityCallbacks}/>
                    </div>
                </div> : null
            }
        </Layout>
    </Box>;
};

interface DocumentRow {
    isActive: boolean;
    doc: FileMetadataDocumentOrDeleted;
}

interface ActivityCallbacks {
    file: UFile;
    documentInspection: FileMetadataDocumentOrDeleted | null;
    setDocumentInspection: (doc: FileMetadataDocumentOrDeleted | null) => void;
    setEditingDocument: (doc: Record<string, any> | null) => void;
    reloadFile: () => void;
}

const entryRenderer: ItemRenderer<DocumentRow> = {
    Icon: props => {
        if (!props.resource) return null;
        const isActive = props.resource.isActive;
        const status = props.resource.doc.status.approval;
        const hasBeenApproved = status.type === "approved" || status.type === "not_required";
        if (isActive && hasBeenApproved) return <Icon name={"check"} color={"green"} size={props.size}/>;
        else if (status.type === "pending") return <Icon name={"warning"} color={"orange"} size={props.size}/>;
        else if (status.type === "rejected") return <Icon name={"trash"} color={"red"} size={props.size}/>;
        return <SvgFt width={props.size} height={props.size} type={"text"} ext={"meta"}
                      color={getCssVar("FtIconColor")} color2={getCssVar("FtIconColor2")}
                      hasExt={true}/>
    },
    MainTitle: props => {
        if (!props.resource) return null;
        const entry = props.resource.doc;
        return <>
            {entry.type !== "metadata" ? null : <>
                {!entry.specification.changeLog.length ?
                    "No message" : entry.specification.changeLog.substring(0, 120)
                }
            </>}
            {entry.type !== "deleted" ? null : <>
                {!entry.changeLog.length ?
                    "No message" : entry.changeLog.substring(0, 120)
                }
            </>}
        </>
    },
    Stats: props => {
        if (!props.resource) return null;
        const entry = props.resource.doc;
        return <>
            <ApprovalStatusStat approval={entry.status.approval}/>
            <ListRowStat icon={"calendar"}>{dateToString(entry.createdAt)}</ListRowStat>
            <ListRowStat icon={"user"}>{entry.createdBy}</ListRowStat>
            <ListRowStat icon={entry.type === "metadata" ? "upload" : "trash"}
                         color={"iconColor"} color2={"iconColor2"}>
                Document was {entry.type === "metadata" ? "updated" : "deleted"}
            </ListRowStat>
        </>;
    }
};

const entryOperations: Operation<DocumentRow, StandardCallbacks<DocumentRow> & ActivityCallbacks>[] = [
    {
        icon: "close",
        text: "Close new document",
        primary: true,
        enabled: (selected, cb) => selected.length === 0 && cb.documentInspection === null,
        onClick: (selected, cb) => {
            cb.setEditingDocument(null);
        }
    },
    {
        icon: "upload",
        text: "New document",
        primary: true,
        enabled: (selected, cb) => selected.length === 0 && cb.documentInspection !== null,
        onClick: (selected, cb) => {
            cb.setDocumentInspection(null);
        }
    },
    {
        icon: "check",
        text: "Approve",
        color: "green",
        confirm: true,
        enabled: (selected, cb) => {
            return selected.length >= 1 &&
                selected.every(it => it.doc.status.approval.type === "pending") &&
                cb.file.permissions.myself.some(it => it === "ADMIN");
        },
        onClick: async (selected, cb) => {
            await cb.invokeCommand(metadataApi.approve(bulkRequestOf(...selected.map(it => ({id: it.doc.id})))));
            cb.reloadFile();
        }
    },
    {
        icon: "trash",
        text: "Reject",
        color: "red",
        confirm: true,
        enabled: (selected, cb) => {
            return selected.length >= 1 &&
                selected.every(it => it.doc.status.approval.type === "pending") &&
                cb.file.permissions.myself.some(it => it === "ADMIN");
        },
        onClick: async (selected, cb) => {
            await cb.invokeCommand(metadataApi.reject(bulkRequestOf(...selected.map(it => ({id: it.doc.id})))));
            cb.reloadFile();
        }
    },
    {
        icon: "copy",
        text: "Clone",
        enabled: (selected, cb) => selected.length === 1 && selected[0].doc.type === "metadata",
        onClick: (selected, cb) => {
            const row = selected[0];
            if (row.doc.type === "metadata") {
                cb.setDocumentInspection(null);
                cb.setEditingDocument(row.doc.specification.document);
            }
        }
    },
    {
        icon: "trash",
        text: "Delete",
        color: "red",
        confirm: true,
        enabled: (selected) => selected.length === 1 && selected[0].isActive,
        onClick: async (selected, cb) => {
            await cb.invokeCommand(metadataApi.delete(bulkRequestOf({id: selected[0].doc.id, changeLog: "Deleting document"})));
            cb.reloadFile();
        }
    },
    {
        icon: "properties",
        text: "Properties",
        enabled: (selected, cb) => selected.length === 1,
        onClick: (selected, cb) => {
            cb.setDocumentInspection(selected[0].doc);
        }
    },
];

const Layout = styled.div`
  display: grid;
  grid-gap: 16px;
  margin-bottom: 16px;

  ${deviceBreakpoint({maxWidth: "1000px"})} {
    grid-template-columns: 1fr;
    grid-template-areas:
      "doc-viewer"
      "activity";
  }

  ${deviceBreakpoint({minWidth: "1000px"})} {
    grid-template-columns: 1fr 1fr;
    grid-template-areas: "doc-viewer activity"
  }

  .doc-viewer {
    grid-area: doc-viewer;
  }

  .activity {
    grid-area: activity;
  }

  .scroll-area {
    max-height: 700px;
    overflow-y: auto;
  }
`;

const FormWrapper = styled.div`
  button[type=submit] {
    display: none;
  }
`;

const ApprovalStatusStat: React.FunctionComponent<{ approval: FileMetadataDocumentApproval }> = ({approval}) => {
    switch (approval.type) {
        case "approved":
            return <ListRowStat icon={"verified"} color={"green"} textColor={"green"}
                                children={"Approved by " + approval.approvedBy}/>;
        case "not_required":
            return null;
        case "pending":
            return <ListRowStat icon={"notchedCircle"} children={"Approval pending"}/>;
        case "rejected":
            return <ListRowStat icon={"verified"} color={"red"} textColor={"red"}
                                children={"Rejected by " + approval.rejectedBy}/>;
    }
};
