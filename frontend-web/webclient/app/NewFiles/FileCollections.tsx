import * as React from "react";
import {APICallState, useCloudAPI} from "Authentication/DataHook";
import {accounting, file, PageV2} from "UCloud";
import {useToggleSet} from "Utilities/ToggleSet";
import {MutableRefObject, useCallback, useEffect, useMemo, useRef, useState} from "react";
import {Box, Button, Divider, Flex, FtIcon, Icon, Input, Label, List, Select} from "ui-components";
import {ListRow, ListRowStat, ListStatContainer} from "ui-components/List";
import {creditFormatter} from "Project/ProjectUsage";
import {Operation, Operations} from "ui-components/Operation";
import MainContainer from "MainContainer/MainContainer";
import {IconName} from "ui-components/Icon";
import FileCollection = file.orchestrator.FileCollection;
import {CommonFileProps} from "NewFiles/FileBrowser";
import collectionsApi = file.orchestrator.collections;
import FileCollectionsProviderRetrieveManifestResponse = file.orchestrator.FileCollectionsProviderRetrieveManifestResponse;
import ProductReference = accounting.ProductReference;
import FSSupport = file.orchestrator.FSSupport;
import * as Heading from "ui-components/Heading";
import {dialogStore} from "Dialog/DialogStore";
import {doNothing} from "UtilityFunctions";
import {bulkRequestOf} from "DefaultObjects";
import {NamingField} from "UtilityComponents";

const entityName = "Drive";

const aclOptions: { icon: IconName; name: string, title?: string }[] = [
    {icon: "search", name: "READ", title: "Read"},
    {icon: "edit", name: "WRITE", title: "Write"},
];

function productRefKey(ref: ProductReference): string {
    return `${ref.id}/${ref.category}/${ref.category}`;
}

function pathToCollection(collection: FileCollection): string {
    return `/${collection.specification.product.provider}/` +
        `${collection.specification.product.category}/` +
        `${collection.specification.product.id}/${collection.id}`;
}

type Manifest = FileCollectionsProviderRetrieveManifestResponse;
type CollectionWithSupport = { collection: FileCollection, support: FSSupport };

export const FileCollections: React.FunctionComponent<CommonFileProps & {
    provider: string;
    collections: APICallState<PageV2<FileCollection>>;
}> = props => {
    const renameRef = useRef<HTMLInputElement>(null);
    const [renaming, setRenaming] = useState<FileCollection | null>(null);

    const [manifest, fetchManifest] = useCloudAPI<Manifest>(
        {noop: true},
        {support: []}
    );

    const collectionsByProduct: Record<string, CollectionWithSupport[]> = {};
    for (const collection of props.collections.data.items) {
        const key = productRefKey(collection.specification.product);
        const existing = collectionsByProduct[key] ?? [];
        const support = manifest.data?.support?.find((it: FSSupport) => productRefKey(it.product) === key);
        if (!support) continue;
        existing.push({collection, support});
        collectionsByProduct[key] = existing;
    }

    const shouldShowProductTitle = Object.keys(collectionsByProduct).length > 1;

    const all = Object.values(collectionsByProduct).reduce((acc, val) => acc.concat(val), []);
    const toggleSet = useToggleSet(all);
    const reload = useCallback(() => {
        toggleSet.uncheckAll();
        props.reload();
    }, [props.reload]);

    const startRenaming = useCallback((collection: FileCollection) => {
        setRenaming(collection);
    }, []);

    const renameCollection = useCallback(async () => {
        if (!renaming) return;
        const value = renameRef.current?.value;
        if (!value || value.length === 0) return;

        await props.invokeCommand(collectionsApi.rename(bulkRequestOf(
            {
                provider: renaming.specification.product.provider,
                id: renaming.id,
                newTitle: value,
            }
        )));

        reload();
    }, [reload, renaming, renameRef]);


    const callbacks: CollectionsCallbacks = useMemo(() => ({
        ...props,
        reload,
        manifest: manifest.data.support,
        startRenaming,
    }), [reload, manifest, startRenaming, props]);

    useEffect(() => {
        fetchManifest(collectionsApi.retrieveManifest({provider: props.provider}));
    }, [props.provider]);

    const main = <>
        {Object.entries(collectionsByProduct).map(([key, group]) => {
                const product = group[0].collection.specification.product;
                return <React.Fragment key={key}>
                    {!shouldShowProductTitle ? null : <Heading.h3>{product.category} / {product.id}</Heading.h3>}
                    <List childPadding={"8px"} bordered={true}>
                        {group.map(collectionWithSupport => {
                            const {collection, support} = collectionWithSupport;
                            return <React.Fragment key={collection.id}>
                                <ListRow
                                    icon={<Icon name={"hdd"} size={"42px"}/>}
                                    left={
                                        renaming === collection ?
                                            <NamingField
                                                confirmText="Rename"
                                                defaultValue={collection.specification.title}
                                                onCancel={() => setRenaming(null)}
                                                onSubmit={renameCollection}
                                                inputRef={renameRef}
                                            /> : collection.specification.title
                                    }
                                    isSelected={toggleSet.checked.has(collectionWithSupport)}
                                    select={() => toggleSet.toggle(collectionWithSupport)}
                                    leftSub={
                                        <ListStatContainer>
                                            <ListRowStat>
                                                {collection.specification.product.category}
                                                {" "}
                                                ({collection.specification.product.provider})
                                            </ListRowStat>
                                            <ListRowStat>
                                                {creditFormatter(collection.billing.pricePerUnit)}
                                            </ListRowStat>
                                        </ListStatContainer>
                                    }
                                    right={
                                        <Operations
                                            selected={toggleSet.checked.items}
                                            location={"IN_ROW"}
                                            entityNameSingular={entityName}
                                            extra={callbacks}
                                            operations={collectionOperations}
                                            row={collectionWithSupport}
                                            all={all}
                                        />
                                    }
                                    navigate={() => {
                                        props.navigateTo(pathToCollection(collection));
                                    }}
                                />
                            </React.Fragment>;
                        })}
                    </List>
                </React.Fragment>
            }
        )}
    </>;

    if (!props.embedded) {
        return <MainContainer
            main={main}
            sidebar={<>
                <Operations
                    location={"SIDEBAR"}
                    operations={collectionOperations}
                    selected={toggleSet.checked.items}
                    extra={callbacks}
                    entityNameSingular={entityName}
                    all={all}
                />
            </>}
        />;
    }
    return null;
};

// eslint-disable-next-line
interface CollectionsCallbacks extends CommonFileProps {
    manifest: FSSupport[];
    startRenaming: (collection: FileCollection) => void;
}

const collectionOperations: Operation<CollectionWithSupport, CollectionsCallbacks>[] = [
    {
        text: `New ${entityName.toLowerCase()}`,
        icon: "newFolder",
        primary: true,
        enabled: (selected, cb) => {
            return cb.manifest.some(it => it.collection.usersCanCreate) && selected.length === 0;
        },
        onClick: (selected, cb) => {
            const options = cb.manifest.filter(it => it.collection.usersCanCreate);
            const productRef: MutableRefObject<HTMLSelectElement | null> = {current: null};
            const nameRef: MutableRefObject<HTMLInputElement | null> = {current: null};
            const onCreate = async (e?: React.SyntheticEvent) => {
                e?.preventDefault();
                dialogStore.success();

                const name = nameRef.current!.value;

                const productValue = productRef?.current?.value;
                let product: ProductReference | undefined = undefined;

                if (productValue) product = options.find(it => productRefKey(it.product) === productValue)?.product;
                if (!product && options.length > 0) product = options[0].product;
                if (!product) return;

                await cb.invokeCommand(collectionsApi.create(bulkRequestOf({
                    title: name,
                    product
                })));

                cb.reload();
            };

            dialogStore.addDialog(
                <Box width={"600px"}>
                    <div>
                        <Heading.h3>New collection</Heading.h3>
                        <Divider/>

                        <form onSubmit={onCreate}>
                        {options.length === 1 ? null :
                            <Label>
                                Product
                                <Select selectRef={productRef}>
                                    {options.map(it => {
                                        if (!it.collection.usersCanCreate) return null;
                                        return (
                                            <option key={productRefKey(it.product)}>
                                                {it.product.category} / {it.product.id}
                                            </option>
                                        );
                                    })}
                                </Select>
                            </Label>
                        }

                        <Label>
                            Name
                            <Input ref={nameRef} autoFocus={true}/>
                        </Label>
                        </form>
                    </div>
                    <Flex mt="20px">
                        <Button onClick={dialogStore.failure} color={"red"} mr="5px">Cancel</Button>
                        <Button onClick={onCreate} color={"green"}>
                            Create collection
                        </Button>
                    </Flex>
                </Box>,
                doNothing,
                true
            );
        }
    },
    {
        text: "Rename",
        icon: "rename",
        primary: false,
        onClick: (selected, cb) => cb.startRenaming(selected[0].collection),
        enabled: selected => selected.length === 1 && selected.every(it => it.support.collection.usersCanRename),
    },
    {
        text: "Delete",
        icon: "trash",
        confirm: true,
        color: "red",
        primary: false,
        onClick: async (selected, cb) => {
            await cb.invokeCommand(
                collectionsApi.remove(bulkRequestOf(...selected.map(it => ({
                    id: it.collection.id,
                    provider: it.collection.specification.product.provider
                }))))
            );

            cb.reload();
        },
        enabled: selected => selected.length > 0 && selected.every(it => it.support.collection.usersCanDelete),
    },
    {
        text: "Properties",
        icon: "properties",
        primary: false,
        onClick: () => 42,
        enabled: selected => selected.length > 0,
    },
];

