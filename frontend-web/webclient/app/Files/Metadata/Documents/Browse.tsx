import * as React from "react";
import metadataApi = file.orchestrator.metadata;
import {file} from "UCloud";
import FileMetadataOrDeleted = file.orchestrator.FileMetadataOrDeleted;
import {Box, Button, Flex, Icon, List} from "ui-components";
import * as Heading from "ui-components/Heading";
import {Operation, Operations} from "ui-components/Operation";
import {useCallback, useEffect, useMemo, useState} from "react";
import {useToggleSet} from "Utilities/ToggleSet";
import {ListRow, ListRowStat, ListStatContainer} from "ui-components/List";
import FileMetadataHistory = file.orchestrator.FileMetadataHistory;
import {AppLogo, AppLogoRaw, hashF} from "Applications/Card";
import {dateToString} from "Utilities/DateUtilities";
import ApprovalStatus = file.orchestrator.FileMetadataDocumentNS.ApprovalStatus;
import styled from "styled-components";
import {ConfirmCancelButtons} from "UtilityComponents";
import {useCloudCommand} from "Authentication/DataHook";
import {bulkRequestOf} from "DefaultObjects";
import {ConfirmationButton} from "ui-components/ConfirmationAction";
import {JsonSchemaForm} from "../JsonSchemaForm";

export const entityName = "Metadata";

export const Browse: React.FunctionComponent<{
    path: string;
    metadata: FileMetadataHistory;
    reload: () => void;
}> = ({path, metadata, reload}) => {
    const [inspecting, setInspecting] = useState<string | null>(null);
    const [editingDocument, setEditingDocument] = useState<Record<string, any> | null>(null);
    const [documentInspection, setDocumentInspection] = useState<FileMetadataOrDeleted | null>(null);
    const toggleSet = useToggleSet(Object.keys(metadata));
    const [commandLoading, invokeCommand] = useCloudCommand();

    const callbacks: Callbacks = useMemo(() => ({
        metadata,
        inspecting,
        setInspecting
    }), [inspecting, setInspecting, metadata]);

    const activeDocument = !inspecting ? null :
        documentInspection ? documentInspection :
            metadata.metadata[inspecting].find(it =>
                it.type === "deleted" ||
                (
                    it.type === "metadata" &&
                    (it.status.approval.type === "not_required" || it.status.approval.type === "approved")
                )
            )

    const deleteData = useCallback(async () => {
        if (commandLoading) return;
        if (!inspecting) return;
        await invokeCommand(
            metadataApi.remove(bulkRequestOf({
                path,
                templateId: inspecting
            }))
        );

        reload();
    }, [path, commandLoading, inspecting])

    const submitNewVersion = useCallback(async () => {
        console.log(commandLoading, inspecting, editingDocument);
        if (commandLoading) return;
        if (!inspecting) return;
        if (!editingDocument) return;

        await invokeCommand(
            metadataApi.create(bulkRequestOf({
                path,
                metadata: {
                    templateId: inspecting,
                    document: editingDocument,
                    changeLog: "",
                    product: undefined
                }
            }))
        );

        reload();
        setEditingDocument(null);
    }, [path, reload, commandLoading, editingDocument, inspecting]);

    useEffect(() => {
        toggleSet.uncheckAll();
    }, [metadata]);

    return <Box>
        <Flex mb={16} height={40}>
            <Box flexGrow={1}>
                {inspecting ? <>
                    <Flex alignItems={"center"}>
                        <AppLogo hash={hashF(inspecting)} size={"42px"}/>
                        <Heading.h3 ml={8}>{metadata.templates[inspecting].specification.title}</Heading.h3>
                    </Flex>

                </> : <Heading.h3>Metadata</Heading.h3>
                }
            </Box>
            <Operations location={"TOPBAR"} operations={operations} selected={toggleSet.checked.items} extra={callbacks}
                        entityNameSingular={entityName} displayTitle={false}/>
        </Flex>

        {inspecting == null ? null :
            <>
                {!activeDocument ? null :
                    <Box maxWidth={"800px"} mb={"32px"}>
                        <Flex mb={16} height={40} alignItems={"center"}>
                            {documentInspection ?
                                <>
                                    <Heading.h4 flexGrow={1}>
                                        Document created at {dateToString(documentInspection.createdAt)}
                                    </Heading.h4>
                                    <Button ml={8} onClick={() => setDocumentInspection(null)}>
                                        <Icon name={"close"} mr={8}/>Clear
                                    </Button>
                                </> :
                                !editingDocument ?
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
                        {editingDocument ? <FormWrapper>
                            <JsonSchemaForm
                                schema={metadata.templates[inspecting].specification.schema}
                                uiSchema={metadata.templates[inspecting].specification.uiSchema}
                                formData={editingDocument}
                                onChange={(e) => setEditingDocument(e.formData)}
                            />
                        </FormWrapper> : <>
                            {activeDocument.type === "deleted" ? "The metadata has been deleted" : null}
                            {activeDocument.type !== "metadata" ? null : <FormWrapper>
                                <JsonSchemaForm
                                    disabled={true}
                                    schema={metadata.templates[inspecting].specification.schema}
                                    uiSchema={metadata.templates[inspecting].specification.uiSchema}
                                    formData={activeDocument.specification.document}
                                />
                            </FormWrapper>}
                        </>}
                    </Box>
                }

                <Box>
                    <Heading.h4 mb={16}>Activity</Heading.h4>
                    <List>
                        {metadata.metadata[inspecting].map((entry, idx) =>
                            <ListRow
                                key={idx}
                                icon={
                                    <Icon name={"upload"} size={"42px"}/>
                                }
                                navigate={() => {
                                    if (!editingDocument) {
                                        setDocumentInspection(entry);
                                    }
                                }}
                                left={<>
                                    {entry.type === "deleted" ? "Deleted" : null}
                                    {entry.type !== "metadata" ? null : <>
                                        <b>{dateToString(entry.createdAt)}: </b>
                                        Document updated by
                                        {" "}
                                        <b>{entry.owner.createdBy}</b>
                                    </>}
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
                </Box>
            </>
        }

        {inspecting != null ? null :
            <List>
                {Object.entries(metadata.metadata).map(([k, v]) => {
                        const allApproved = v.every(it =>
                            it.type === "deleted" ||
                            (
                                it.type === "metadata" &&
                                (it.status.approval.type === "not_required" || it.status.approval.type === "approved")
                            )
                        );

                        return <ListRow
                            key={k}
                            isSelected={toggleSet.checked.has(k)}
                            select={() => toggleSet.toggle(k)}
                            navigate={() => setInspecting(k)}
                            icon={<AppLogo hash={hashF(k)} size={"42px"}/>}
                            left={metadata.templates[k].specification.title}
                            leftSub={
                                <ListStatContainer>
                                    <ListRowStat icon={"hourglass"}
                                                 children={`${v.length} version` + (v.length > 1 ? "s" : "")}/>
                                    {allApproved ?
                                        <ListRowStat textColor={"green"} color={"green"} icon={"verified"}
                                                     children={"All entries approved"}/>
                                        :
                                        <ListRowStat textColor={"red"} color={"red"} icon={"verified"}
                                                     children={"Updates are pending approval"}/>
                                    }
                                </ListStatContainer>
                            }
                            right={
                                <Operations location={"IN_ROW"} operations={operations} selected={toggleSet.checked.items}
                                            extra={callbacks} entityNameSingular={entityName} row={k}/>
                            }
                        />;
                    }
                )}
            </List>
        }
    </Box>;
};

const FormWrapper = styled.div`
  max-width: 800px;

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

interface Callbacks {
    metadata: FileMetadataHistory;
    inspecting: string | null;
    setInspecting: (inspecting: string | null) => void;
}

const operations: Operation<string, Callbacks>[] = [
    {
        text: `New ${entityName.toLowerCase()}`,
        primary: true,
        icon: "docs",
        enabled: (selected, cb) => selected.length === 0 && cb.inspecting == null,
        onClick: () => {

        }
    },
    {
        text: "Back to overview",
        primary: true,
        icon: "backward",
        enabled: (selected, cb) => cb.inspecting != null,
        onClick: (selected, cb) => {
            cb.setInspecting(null);
        }
    },
    {
        text: "View activity",
        icon: "search",
        enabled: (selected, cb) => selected.length === 1,
        onClick: () => {

        }
    },
    {
        text: "Delete",
        icon: "trash",
        confirm: true,
        color: "red",
        enabled: (selected, cb) => selected.length > 0,
        onClick: () => {

        }
    },
];