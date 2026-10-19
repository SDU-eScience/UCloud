import * as React from "react";
import {useCallback, useEffect, useState} from "react";
import {Box, Button, Flex, Icon, Input, Label, Text, TextArea, Truncate} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {dialogStore} from "@/Dialog/DialogStore";
import {callAPI} from "@/Authentication/DataHook";
import {bulkRequestOf, displayErrorMessageOrDefault, doNothing, stopPropagation} from "@/UtilityFunctions";
import {MandatoryField} from "@/UtilityComponents";
import {sendFailureNotification, sendSuccessNotification} from "@/Notifications";
import {accounting, PageV2} from "@/UCloud";
import DatasetsApi, {Dataset, DatasetEntry} from "@/UCloud/DatasetsApi";
import {fileName} from "@/Utilities/FileUtilities";
import {fetchAll} from "@/Utilities/PageUtilities";
import {injectStyleSimple} from "@/Unstyled";
import ProductReference = accounting.ProductReference;

export function addDatasetArchiveDialog(
    entries: DatasetEntry[],
    product: ProductReference | undefined,
    reload?: () => void,
): void {
    dialogStore.addDialog(
        <DatasetArchiveDialog entries={entries} product={product} reload={reload} />,
        doNothing,
        true,
    );
}

const DatasetArchiveClass = injectStyleSimple("dataset-archive", `
    display: flex;
    flex-direction: column;
    gap: 16px;
    width: 100%;
    min-width: min(100%, 560px);
`);

const EntryListClass = injectStyleSimple("dataset-archive-entries", `
    display: flex;
    flex-direction: column;
    gap: 4px;
    max-height: 116px;
    overflow-y: auto;
`);

const DatasetListClass = injectStyleSimple("dataset-archive-datasets", `
    display: flex;
    flex-direction: column;
    gap: 8px;
    max-height: 240px;
    overflow-y: auto;
`);

function DatasetArchiveDialog({entries, product, reload}: {
    entries: DatasetEntry[];
    product?: ProductReference;
    reload?: () => void;
}): React.ReactNode {
    const [mode, setMode] = useState<"new" | "existing">("new");
    const [title, setTitle] = useState("");
    const [description, setDescription] = useState("");
    const [keywords, setKeywords] = useState("");

    const [existingDatasets, setExistingDatasets] = useState<Dataset[] | null>(null);
    const [selectedDataset, setSelectedDataset] = useState<Dataset | null>(null);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        if (mode !== "existing" || existingDatasets != null || loading) return;
        setLoading(true);
        fetchAll(next => callAPI<PageV2<Dataset>>(DatasetsApi.browse({itemsPerPage: 250, next})))
            .then(items => {
                // Entries can only be added while a dataset has not been published yet.
                setExistingDatasets(items.filter(it =>
                    it.status.state === "DRAFT" || it.status.state === "READY"
                ));
            })
            .catch(e => {
                displayErrorMessageOrDefault(e, "Failed to fetch datasets.");
                setExistingDatasets([]);
            })
            .finally(() => setLoading(false));
    }, [mode]);

    const onConfirm = useCallback(async () => {
        try {
            if (mode === "new") {
                if (!product) {
                    sendFailureNotification("Unable to determine the storage product for the dataset.");
                    return;
                }

                await callAPI(DatasetsApi.create(bulkRequestOf({
                    product,
                    entries,
                    title,
                    description: description === "" ? undefined : description,
                    keywords: keywords === "" ? undefined :
                        keywords.split(",").map(it => it.trim()).filter(it => it !== ""),
                })));
                sendSuccessNotification(`Dataset "${title}" is being created. The selected entries will be copied into it.`);
            } else {
                if (!selectedDataset) return;

                await callAPI(DatasetsApi.updateEntries(bulkRequestOf({
                    id: selectedDataset.id,
                    addedEntries: entries,
                })));
                sendSuccessNotification(`The selected entries are being added to "${selectedDataset.specification.title}".`);
            }

            reload?.();
            dialogStore.success();
        } catch (e) {
            displayErrorMessageOrDefault(e, "Failed to archive data.");
        }
    }, [mode, title, description, keywords, entries, product, selectedDataset, reload]);

    const canConfirm = !loading && (mode === "new" ? (title !== "" && product != null) : selectedDataset != null);

    return <form
        className={DatasetArchiveClass}
        onSubmit={e => {
            e.preventDefault();
            e.stopPropagation();
            if (canConfirm) onConfirm().then(doNothing);
        }}
    >
        <Heading.h3>Archive to dataset</Heading.h3>
        <Box>
            Datasets archive your files for later publication. The selected entries are copied (snapshotted)
            into the dataset and can be published once the dataset is ready.
        </Box>

        <Box>
            <Text mb="4px"><b>{entries.length === 1 ? "Selected item" : `Selected items (${entries.length})`}</b></Text>
            <div className={EntryListClass}>
                {entries.map(entry => (
                    <Truncate key={entry.id} title={entry.id}>{fileName(entry.id) || entry.id}</Truncate>
                ))}
            </div>
        </Box>

        <Flex gap="8px">
            <Button
                fullWidth
                type="button"
                color={mode === "new" ? "primaryMain" : "secondaryMain"}
                onClick={() => setMode("new")}
            >
                New dataset
            </Button>
            <Button
                fullWidth
                type="button"
                color={mode === "existing" ? "primaryMain" : "secondaryMain"}
                onClick={() => setMode("existing")}
            >
                Existing dataset
            </Button>
        </Flex>

        {mode === "new" ? <>
            <Label>
                Dataset title<MandatoryField />
                <Input
                    autoFocus
                    required
                    placeholder="Title of the new dataset..."
                    value={title}
                    onKeyDown={stopPropagation}
                    onChange={e => setTitle(e.target.value)}
                />
            </Label>
            <Label>
                Description
                <TextArea
                    width="100%"
                    rows={4}
                    placeholder="Describe the contents of the dataset..."
                    value={description}
                    onKeyDown={stopPropagation}
                    onChange={e => setDescription(e.target.value)}
                />
            </Label>
            <Label>
                Keywords
                <Input
                    placeholder="Comma separated, e.g. biology, survey, 2024"
                    value={keywords}
                    onKeyDown={stopPropagation}
                    onChange={e => setKeywords(e.target.value)}
                />
            </Label>
        </> : <Box>
            <Text mb="8px"><b>Choose a dataset</b></Text>
            {existingDatasets == null ? <Text>Fetching datasets...</Text> : null}
            {existingDatasets?.length === 0 ?
                <Text>
                    You have no datasets which can receive new entries. Datasets become immutable once they
                    have been published. Create a new dataset instead.
                </Text> : null}
            <div className={DatasetListClass}>
                {existingDatasets?.map(it => (
                    <Flex
                        key={it.id}
                        alignItems="center"
                        gap="8px"
                        padding="8px"
                        style={{
                            border: "1px solid var(--borderColor)",
                            borderRadius: "6px",
                            cursor: "pointer",
                        }}
                        onClick={() => setSelectedDataset(it)}
                    >
                        <Icon name={selectedDataset?.id === it.id ? "radioChecked" : "radioEmpty"} size={18} />
                        <Truncate flexGrow={1} title={it.specification.title}>{it.specification.title}</Truncate>
                        <Text fontSize={12}>{it.status.state}</Text>
                    </Flex>
                ))}
            </div>
        </Box>}

        <Flex justifyContent="end" gap="8px">
            <Button color="errorMain" type="button" onClick={() => dialogStore.failure()}>Cancel</Button>
            <Button color="successMain" type="submit" disabled={!canConfirm}>
                {mode === "new" ? "Archive" : "Add to dataset"}
            </Button>
        </Flex>
    </form>;
}