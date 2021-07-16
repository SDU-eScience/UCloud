import * as React from "react";
import {
    default as metadataApi,
    FileMetadataHistory,
    FileMetadataDocumentOrDeleted, FileMetadataDocumentApproval,
} from "UCloud/MetadataDocumentApi";
import {FileMetadataTemplate} from "UCloud/MetadataNamespaceApi";
import {useCallback, useEffect, useState} from "react";
import {useCloudCommand} from "Authentication/DataHook";
import {bulkRequestOf} from "DefaultObjects";
import {Box, Button, Flex, Icon, List} from "ui-components";
import * as Heading from "ui-components/Heading";
import {dateToString} from "Utilities/DateUtilities";
import {ConfirmationButton} from "ui-components/ConfirmationAction";
import {ConfirmCancelButtons} from "UtilityComponents";
import {JsonSchemaForm} from "Files/Metadata/JsonSchemaForm";
import styled from "styled-components";
import {ListRow, ListRowStat, ListStatContainer} from "ui-components/List";
import {deviceBreakpoint} from "ui-components/Hide";

export const History: React.FunctionComponent<{
    path: string;
    template: FileMetadataTemplate;
    metadata: FileMetadataHistory;
    reload: () => void;
    close: () => void;
}> = ({path, metadata, reload, close, template}) => {
    // Contains the new version currently being edited
    const [editingDocument, setEditingDocument] = useState<Record<string, any>>({});
    // Contains the document we are currently inspecting
    // We will display on the left hand side `documentInspection` if it is not null otherwise `editingDocument`.
    const [documentInspection, setDocumentInspection] = useState<FileMetadataDocumentOrDeleted | null>(null);
    const [commandLoading, invokeCommand] = useCloudCommand();

    const hasActivity = metadata.metadata[template.namespaceId] != null;
    const activity = metadata.metadata[template.namespaceId] ?? [];

    useEffect(() => {
        if (!hasActivity) {
            setEditingDocument({});
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
        if (activeDocument) setDocumentInspection(activeDocument);
    }, []);

    const deleteData = useCallback(async () => {
        if (commandLoading) return;
        await invokeCommand(
            metadataApi.delete(bulkRequestOf({
                id: path,
                templateId: template.namespaceId
            }))
        );

        reload();
    }, [path, commandLoading, template])

    const submitNewVersion = useCallback(async () => {
        if (commandLoading) return;
        if (!editingDocument) return;
        await invokeCommand(
            metadataApi.create(bulkRequestOf({
                id: path,
                metadata: {
                    templateId: template.namespaceId,
                    version: template.version,
                    document: editingDocument,
                    changeLog: "",
                }
            }))
        );

        reload();
        setEditingDocument({});
    }, [path, reload, commandLoading, editingDocument, template]);

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
                <Flex mb={16} height={40} alignItems={"center"}>
                    {documentInspection && documentInspection !== activeDocument ?
                        <>
                            <Heading.h4 flexGrow={1}>
                                <b>{dateToString(documentInspection.createdAt)}: </b>
                                Document updated by
                                {" "}
                                <b>{documentInspection.createdBy}</b>
                            </Heading.h4>
                            <Button ml={8} onClick={() => setDocumentInspection(null)}>
                                <Icon name={"close"} mr={8}/>Clear
                            </Button>
                        </> :
                        documentInspection && documentInspection === activeDocument ?
                            <>
                                <Heading.h4 flexGrow={1}>Current document</Heading.h4>
                                {activeDocument.type !== "metadata" ? null :
                                    <>
                                        <Button mx={8} onClick={() => {
                                            setEditingDocument(({...activeDocument.specification.document}));
                                            setDocumentInspection(null);
                                        }}>
                                            <Icon name={"upload"} mr={8}/>
                                            New version
                                        </Button>
                                        <ConfirmationButton color={"red"} actionText={"Delete"} icon={"trash"}
                                                            onAction={deleteData}/>
                                    </>
                                }
                            </> :
                            <>
                                <Heading.h4 flexGrow={1}>Editing document</Heading.h4>
                                <ConfirmCancelButtons
                                    onConfirm={submitNewVersion}
                                    onCancel={() => {
                                        if (activeDocument) setDocumentInspection(activeDocument);
                                        setEditingDocument({});
                                    }}
                                    showCancelButton={!!activeDocument}
                                />
                            </>
                    }
                </Flex>
                <div className="scroll-area">
                    {documentInspection ? <>
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
                    <Heading.h4 mb={16}>Activity</Heading.h4>
                    <div className="scroll-area">
                        <List>
                            {activity.map((entry, idx) =>
                                <ListRow
                                    key={idx}
                                    icon={
                                        <Icon name={"upload"} size={"42px"}/>
                                    }
                                    navigate={() => {
                                        setDocumentInspection(entry);
                                    }}
                                    select={() => {
                                        setDocumentInspection(entry);
                                    }}
                                    isSelected={documentInspection === entry}
                                    left={<>
                                        <b>{dateToString(entry.createdAt)}: </b>
                                        Document updated by
                                        {" "}
                                        <b>
                                            {entry.type === "metadata" ? entry.createdBy :
                                                entry.type === "deleted" ? entry.createdBy : null}
                                        </b>
                                    </>}
                                    leftSub={<ListStatContainer>
                                        {entry.type !== "metadata" ? null : <>
                                            <ApprovalStatusStat approval={entry.status.approval}/>
                                            <ListRowStat icon={"chat"}>
                                                {!entry.specification.changeLog.length ?
                                                    "No message" : entry.specification.changeLog.substring(0, 120)
                                                }
                                            </ListRowStat>
                                        </>}
                                    </ListStatContainer>}
                                    right={<></>}
                                />
                            )}
                        </List>
                    </div>
                </div> : null
            }
        </Layout>
    </Box>;
};

const Layout = styled.div`
  display: grid;
  grid-gap: 16px;

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
