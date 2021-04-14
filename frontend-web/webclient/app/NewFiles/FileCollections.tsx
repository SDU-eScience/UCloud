import * as React from "react";
import {APICallState} from "Authentication/DataHook";
import {file, PageV2} from "UCloud";
import {useToggleSet} from "Utilities/ToggleSet";
import {useCallback, useMemo} from "react";
import {FtIcon, List} from "ui-components";
import {ListRow, ListRowStat, ListStatContainer} from "ui-components/List";
import {creditFormatter} from "Project/ProjectUsage";
import {Operation, Operations} from "ui-components/Operation";
import MainContainer from "MainContainer/MainContainer";
import {IconName} from "ui-components/Icon";
import FileCollection = file.orchestrator.FileCollection;
import {CommonFileProps} from "NewFiles/FileBrowser";

export const FileCollections: React.FunctionComponent<CommonFileProps & {
    collections: APICallState<PageV2<FileCollection>>;
}> = props => {
    const toggleSet = useToggleSet(props.collections.data.items);
    const reload = useCallback(() => {
        toggleSet.uncheckAll();
        props.reload();
    }, [props.reload]);

    const callbacks: CollectionsCallbacks = useMemo(() => ({...props, reload}), [reload, props]);

    const main = <>
        <List childPadding={"8px"} bordered={false}>
            {props.collections.data.items.map(it =>
                <ListRow
                    key={it.id}
                    icon={<FtIcon fileIcon={{type: "DIRECTORY"}} size={"42px"}/>}
                    left={it.specification.title}
                    isSelected={toggleSet.checked.has(it)}
                    select={() => toggleSet.toggle(it)}
                    leftSub={
                        <ListStatContainer>
                            <ListRowStat>
                                {it.specification.product.category} ({it.specification.product.provider})
                            </ListRowStat>
                            <ListRowStat>
                                {creditFormatter(it.billing.pricePerUnit)}
                            </ListRowStat>
                        </ListStatContainer>
                    }
                    right={
                        <Operations
                            selected={toggleSet.checked.items}
                            location={"IN_ROW"}
                            entityNameSingular={collectionsEntityName}
                            extra={callbacks}
                            operations={collectionOperations}
                            row={it}
                        />
                    }
                    navigate={() => {
                        const path = `/${it.specification.product.provider}/${it.specification.product.category}/` +
                            `${it.specification.product.id}/${it.id}`;
                        props.navigateTo(path);
                    }}
                />
            )}
        </List>
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
                    entityNameSingular={collectionsEntityName}
                />
            </>}
        />;
    }
    return null;
};

// eslint-disable-next-line
interface CollectionsCallbacks extends CommonFileProps {
}

const collectionOperations: Operation<FileCollection, CollectionsCallbacks>[] = [
    {
        text: "Delete",
        icon: "trash",
        confirm: true,
        color: "red",
        primary: false,
        onClick: () => 42,
        enabled: selected => selected.length > 0,
    },
    {
        text: "Properties",
        icon: "properties",
        primary: false,
        onClick: () => 42,
        enabled: selected => selected.length > 0,
    },
];

const collectionsEntityName = "Collections";

const collectionAclOptions: { icon: IconName; name: string, title?: string }[] = [
    {icon: "search", name: "READ", title: "Read"},
    {icon: "edit", name: "WRITE", title: "Write"},
];
