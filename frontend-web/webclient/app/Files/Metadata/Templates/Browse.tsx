import * as React from "react";
import * as UCloud from "UCloud";
import {Operation, Operations} from "ui-components/Operation";
import templateApi = UCloud.file.orchestrator.metadata_template;
import {file, PageV2} from "UCloud";
import FileMetadataTemplate = file.orchestrator.FileMetadataTemplate;
import {InvokeCommand, useCloudAPI, useCloudCommand} from "Authentication/DataHook";
import {bulkRequestOf} from "DefaultObjects";
import {useCallback, useEffect, useMemo, useState} from "react";
import * as Pagination from "Pagination";
import HexSpin from "LoadingIcon/LoadingIcon";
import {PageRenderer} from "Pagination/PaginationV2";
import {useLoading, useTitle} from "Navigation/Redux/StatusActions";
import {SidebarPages, useSidebarPage} from "ui-components/Sidebar";
import {useRefreshFunction} from "Navigation/Redux/HeaderActions";
import MainContainer from "MainContainer/MainContainer";
import {useToggleSet} from "Utilities/ToggleSet";
import {useHistory} from "react-router";
import {useProjectId} from "Project";
import {buildQueryString} from "Utilities/URIUtilities";
import {List} from "ui-components";
import {ListRow, ListRowStat, ListStatContainer} from "ui-components/List";
import {IconName} from "ui-components/Icon";

export const entityName = "Template";
export const aclOptions: { icon: IconName; name: string, title?: string }[] = [
    {icon: "search", name: "READ", title: "Read"},
    {icon: "edit", name: "WRITE", title: "Write"},
];


const Browse: React.FunctionComponent<{
    embedded?: boolean;
    onSelect?: (selected: FileMetadataTemplate) => void;
}> = props => {
    const [commandLoading, invokeCommand] = useCloudCommand();
    const [templates, fetchTemplates] = useCloudAPI<PageV2<FileMetadataTemplate> | null>({noop: true}, null);
    const [scrollGeneration, setScrollGeneration] = useState(0);
    const selected = useToggleSet(templates?.data?.items ?? []);
    const history = useHistory();
    const projectId = useProjectId();

    const reload = useCallback(() => {
        fetchTemplates(templateApi.browse({itemsPerPage: 100}));
        setScrollGeneration(prev => prev + 1);
    }, [projectId]);
    useEffect(reload, [reload]);

    const loadMore = useCallback(() => {
        fetchTemplates(templateApi.browse({itemsPerPage: 100, next: templates?.data?.next}));
    }, [templates]);

    const pageRenderer: PageRenderer<FileMetadataTemplate> = useCallback((items) => {
        return <List bordered childPadding={8}>
            {items.map(row =>
                <ListRow
                    key={row.id}
                    select={() => selected.toggle(row)}
                    isSelected={selected.checked.has(row)}
                    left={row.specification.title}
                    leftSub={
                        <ListStatContainer>
                            <ListRowStat
                                icon={"info"}
                                color={"iconColor"}
                                color2={"iconColor2"}
                                children={row.specification.version}
                            />
                        </ListStatContainer>
                    }
                    right={
                        <Operations
                            location={"IN_ROW"}
                            row={row}
                            operations={operations}
                            selected={selected.checked.items}
                            extra={callbacks}
                            entityNameSingular={entityName}
                        />
                    }
                />
            )}
        </List>
    }, []);

    const callbacks: Callbacks = useMemo(() => {
        return {
            commandLoading,
            invokeCommand,
            reload,
            onSelect: props.onSelect,
            viewProperties: (selected) => {
                history.push(buildQueryString("/files/metadata/templates/properties/", {id: selected.id}));
            },
            createNewVersion: (selected) => {
                history.push(buildQueryString("/files/metadata/templates/create/", {id: selected.id}));
            },
            create: () => {
                history.push("/files/metadata/templates/create/");
            },
        }
    }, [history, props.onSelect]);

    let main: JSX.Element;
    if (templates.error) {
        main = <>{templates.error.statusCode}: {templates.error.why}</>
    } else if (templates.data == null) {
        main = <HexSpin/>;
    } else {
        main = <Pagination.ListV2
            page={templates.data}
            pageRenderer={pageRenderer}
            loading={templates.loading}
            onLoadMore={loadMore}
            infiniteScrollGeneration={scrollGeneration}
        />;
    }

    if (props.embedded !== true) {
        useTitle(entityName);
        useLoading(templates.loading);
        useSidebarPage(SidebarPages.Files);
        useRefreshFunction(reload);

        return <MainContainer
            main={main}
            sidebar={
                <Operations
                    location={"SIDEBAR"}
                    operations={operations}
                    selected={selected.checked.items}
                    extra={callbacks}
                    entityNameSingular={entityName}
                />
            }
        />;
    }

    return <>
        <Operations
            location={"TOPBAR"}
            operations={operations}
            selected={selected.checked.items}
            extra={callbacks}
            entityNameSingular={entityName}
        />

        {main}
    </>;
};

interface Callbacks {
    commandLoading: boolean;
    invokeCommand: InvokeCommand;

    reload: () => void;
    createNewVersion: (selected: FileMetadataTemplate) => void;
    create: () => void;
    viewProperties: (selected: FileMetadataTemplate) => void;
    onSelect?: (selected: FileMetadataTemplate) => void;
}

const operations: Operation<FileMetadataTemplate, Callbacks>[] = [
    {
        text: "Use",
        icon: "check",
        primary: true,
        enabled: (selected, cb) => selected.length === 1 && cb.onSelect !== undefined,
        onClick: (selected, cb) => {
            cb.onSelect?.(selected[0]);
        }
    },
    {
        text: `New ${entityName.toLowerCase()}`,
        icon: "upload",
        primary: true,
        enabled: (selected, cb) => selected.length === 0,
        onClick: (selected, cb) => {
            cb.create();
        }
    },
    {
        text: "New version...",
        icon: "newFolder",
        enabled: (selected, cb) => selected.length === 1,
        onClick: (selected, cb) => {
            cb.createNewVersion(selected[0]);
        }
    },
    {
        text: "Deprecate",
        confirm: true,
        icon: "close",
        color: "red",
        enabled: (selected, cb) => selected.length >= 1,
        onClick: async (selected, cb) => {
            if (cb.commandLoading) return;
            await cb.invokeCommand(templateApi.deprecate(bulkRequestOf(
                ...selected.map(it => ({id: it.id}))
            )));
            cb.reload();
        }
    },
    {
        text: "Properties",
        icon: "properties",
        enabled: (selected, cb) => selected.length === 1,
        onClick: (selected, cb) => {
            cb.viewProperties(selected[0]);
        }
    },
];

export default Browse;