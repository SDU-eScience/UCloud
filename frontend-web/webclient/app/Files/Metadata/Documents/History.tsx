import * as React from "react";
import {file} from "UCloud";
import metadataApi = file.orchestrator.metadata;
import FileMetadataHistory = file.orchestrator.FileMetadataHistory;
import {useCallback, useEffect, useState} from "react";
import {useCloudCommand} from "Authentication/DataHook";
import FileMetadataOrDeleted = file.orchestrator.FileMetadataOrDeleted;
import {bulkRequestOf} from "DefaultObjects";
import {Box, Button, Flex, Icon, List} from "ui-components";
import {AppLogo, hashF} from "Applications/Card";
import * as Heading from "ui-components/Heading";
import {dateToString} from "Utilities/DateUtilities";
import {ConfirmationButton} from "ui-components/ConfirmationAction";
import {ConfirmCancelButtons} from "UtilityComponents";
import {JsonSchemaForm} from "Files/Metadata/JsonSchemaForm";
import styled from "styled-components";
import {ListRow, ListRowStat, ListStatContainer} from "ui-components/List";
import ApprovalStatus = file.orchestrator.FileMetadataDocumentNS.ApprovalStatus;
import {deviceBreakpoint} from "ui-components/Hide";
import FileMetadataTemplate = file.orchestrator.FileMetadataTemplate;

export const History: React.FunctionComponent<{
    path: string;
    template: FileMetadataTemplate;
    metadata: FileMetadataHistory;
    reload: () => void;
    close: () => void;
}> = ({path, metadata, reload, close, template}) => {
    const [editingDocument, setEditingDocument] = useState<Record<string, any> | null>(null);
    const [documentInspection, setDocumentInspection] = useState<FileMetadataOrDeleted | null>(null);
    const [commandLoading, invokeCommand] = useCloudCommand();

    const hasActivity = metadata.metadata[template.id] != null;
    const activity = metadata.metadata[template.id] ?? [];

    useEffect(() => {
        if (!hasActivity) {
            setEditingDocument({});
        }
    }, [hasActivity]);

    const activeDocument = documentInspection ? documentInspection :
        activity.find(it =>
            it.type === "deleted" ||
            (
                it.type === "metadata" &&
                (it.status.approval.type === "not_required" || it.status.approval.type === "approved")
            )
        );

    const deleteData = useCallback(async () => {
        if (commandLoading) return;
        await invokeCommand(
            metadataApi.remove(bulkRequestOf({
                path,
                templateId: template.id
            }))
        );

        reload();
    }, [path, commandLoading, template])

    const submitNewVersion = useCallback(async () => {
        if (commandLoading) return;
        if (!editingDocument) return;

        await invokeCommand(
            metadataApi.create(bulkRequestOf({
                path,
                metadata: {
                    templateId: template.id,
                    document: editingDocument,
                    changeLog: "",
                    product: undefined
                }
            }))
        );

        reload();
        setEditingDocument(null);
    }, [path, reload, commandLoading, editingDocument, template]);

    return <Box>
        <Flex mb={16} height={40}>
            <Box flexGrow={1}>
                <Flex alignItems={"center"}>
                    <AppLogo hash={hashF(template.id)} size={"42px"}/>
                    <Heading.h3 ml={8}>{template.specification.title}</Heading.h3>
                </Flex>
            </Box>
            <Button onClick={close}>
                <Icon name={"backward"} mr={16}/>
                Back to overview
            </Button>
        </Flex>

        <Layout>
            <div className={"doc-viewer"}>
                <Flex mb={16} height={40} alignItems={"center"}>
                    {documentInspection ?
                        <>
                            <Heading.h4 flexGrow={1}>
                                <b>{dateToString(documentInspection.createdAt)}: </b>
                                Document updated by
                                {" "}
                                <b>
                                    {documentInspection.type === "metadata" ? documentInspection.owner.createdBy :
                                        documentInspection.type === "deleted" ? documentInspection.createdBy : null}
                                </b>
                            </Heading.h4>
                            <Button ml={8} onClick={() => setDocumentInspection(null)}>
                                <Icon name={"close"} mr={8}/>Clear
                            </Button>
                        </> :
                        !editingDocument && activeDocument ?
                            <>
                                <Heading.h4 flexGrow={1}>Current document</Heading.h4>
                                {activeDocument.type !== "metadata" ? null :
                                    <>
                                        <Button mx={8} onClick={() => {
                                            setEditingDocument(({...activeDocument.specification.document}));
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
                                    onCancel={() => setEditingDocument(null)}
                                />
                            </>
                    }
                </Flex>
                <div className="scroll-area">
                    {editingDocument || !activeDocument ? <FormWrapper>
                        <JsonSchemaForm
                            schema={template.specification.schema}
                            uiSchema={template.specification.uiSchema}
                            formData={editingDocument}
                            onChange={(e) => setEditingDocument(e.formData)}
                        />
                    </FormWrapper> : activeDocument ? <>
                        {activeDocument.type === "deleted" ? "The metadata has been deleted" : null}
                        {activeDocument.type !== "metadata" ? null : <FormWrapper>
                            <JsonSchemaForm
                                disabled={true}
                                schema={template.specification.schema}
                                uiSchema={template.specification.uiSchema}
                                formData={activeDocument.specification.document}
                            />
                        </FormWrapper>}
                    </> : null}
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
                                        if (!editingDocument) setDocumentInspection(entry);
                                    }}
                                    select={() => {
                                        if (!editingDocument) setDocumentInspection(entry);
                                    }}
                                    isSelected={documentInspection === entry || activeDocument === entry}
                                    left={<>
                                        <b>{dateToString(entry.createdAt)}: </b>
                                        Document updated by
                                        {" "}
                                        <b>
                                            {entry.type === "metadata" ? entry.owner.createdBy :
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

const ApprovalStatusStat: React.FunctionComponent<{ approval: ApprovalStatus }> = ({approval}) => {
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
