import * as React from "react";
import {file} from "UCloud";
import {Box, Flex, List} from "ui-components";
import * as Heading from "ui-components/Heading";
import {Operation, Operations} from "ui-components/Operation";
import {useCallback, useEffect, useMemo, useState} from "react";
import {useToggleSet} from "Utilities/ToggleSet";
import {ListRow, ListRowStat, ListStatContainer} from "ui-components/List";
import FileMetadataHistory = file.orchestrator.FileMetadataHistory;
import {AppLogo, hashF} from "Applications/Card";
import {History as MetadataHistory} from "./History";
import ReactModal from "react-modal";
import {defaultModalStyle, largeModalStyle} from "Utilities/ModalUtilities";
import {default as TemplateBrowse} from "../Templates/Browse";
import FileMetadataTemplate = file.orchestrator.FileMetadataTemplate;

export const entityName = "Metadata";

export const Browse: React.FunctionComponent<{
    path: string;
    metadata: FileMetadataHistory;
    reload: () => void;
}> = ({path, metadata, reload}) => {
    const [lookingForTemplate, setLookingForTemplate] = useState<boolean>(false);
    const [inspecting, setInspecting] = useState<string | null>(null);
    const [creatingForTemplate, setCreatingForTemplate] = useState<FileMetadataTemplate | null>(null);
    const toggleSet = useToggleSet(Object.keys(metadata));

    const callbacks: Callbacks = useMemo(() => ({
        metadata,
        inspecting,
        setInspecting,
        setLookingForTemplate
    }), [inspecting, setInspecting, setLookingForTemplate, metadata]);

    const selectTemplate = useCallback((template: FileMetadataTemplate) => {
        setCreatingForTemplate(template);
        setLookingForTemplate(false);
    }, []);

    useEffect(() => {
        toggleSet.uncheckAll();
    }, [metadata]);

    if (inspecting) {
        return <MetadataHistory metadata={metadata} reload={reload} template={metadata.templates[inspecting]}
                                path={path} close={() => setInspecting(null)}/>;
    } else if (creatingForTemplate != null) {
        return <MetadataHistory metadata={metadata} reload={reload} template={creatingForTemplate} path={path}
                                close={() => setCreatingForTemplate(null)}/>;
    }

    return <Box>
        <Flex mb={16} height={40}>
            <Box flexGrow={1}><Heading.h3>Metadata</Heading.h3></Box>
            <Operations location={"TOPBAR"} operations={operations} selected={toggleSet.checked.items}
                        extra={callbacks}
                        entityNameSingular={entityName} displayTitle={false}/>
        </Flex>

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
                            <Operations location={"IN_ROW"} operations={operations}
                                        selected={toggleSet.checked.items}
                                        extra={callbacks} entityNameSingular={entityName} row={k}/>
                        }
                    />;
                }
            )}
        </List>

        <ReactModal
            isOpen={lookingForTemplate}
            ariaHideApp={false}
            shouldCloseOnEsc={true}
            onRequestClose={() => setLookingForTemplate(false)}
            style={largeModalStyle}
        >
            <TemplateBrowse embedded={true} onSelect={selectTemplate}/>
        </ReactModal>
    </Box>;
};

interface Callbacks {
    metadata: FileMetadataHistory;
    inspecting: string | null;
    setInspecting: (inspecting: string | null) => void;
    setLookingForTemplate: (looking: boolean) => void;
}

const operations: Operation<string, Callbacks>[] = [
    {
        text: `New ${entityName.toLowerCase()}`,
        primary: true,
        icon: "docs",
        enabled: (selected, cb) => selected.length === 0 && cb.inspecting == null,
        onClick: (_, cb) => {
            cb.setLookingForTemplate(true);
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