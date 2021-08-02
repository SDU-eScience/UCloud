import * as React from "react";
import {InvokeCommand} from "Authentication/DataHook";
import {file} from "UCloud";
import UFile = file.orchestrator.UFile;
import {FileType} from "Files/index";
import * as H from 'history';
import FileMetadataAttached = file.orchestrator.FileMetadataAttached;
import * as UCloud from "UCloud";

interface FileBrowserProps {
    initialPath?: string;
    embedded?: boolean;
    onSelect?: (file: UFile) => void;
    selectFileRequirement?: FileType;
}

export interface CommonFileProps {
    path: string;
    reload: () => void;
    loadMore: () => void;
    history: H.History;
    navigateTo: (path: string) => void;
    generation: number;
    embedded: boolean;
    invokeCommand: InvokeCommand;
    onSelect?: (file: UFile) => void;
    selectFileRequirement?: FileType;
    metadata: Record<string, FileMetadataAttached[]>;
    sorting: UseSorting<UCloud.file.orchestrator.FilesSortBy>;
}

interface UseSorting<T extends string> {
    sortBy: T;
    setSortBy: (val: T) => void;
    sortOrder: UCloud.file.orchestrator.SortOrder;
    setSortOrder: (val: UCloud.file.orchestrator.SortOrder) => void;
}

function useSorting<T extends string>(key: "files" | "jobs", defaultSortBy: T): UseSorting<T> {
    const sortByKey = `${key}-sortBy`;
    const sortOrderKey = `${key}-sortOrder`

    const [_sortBy, _setSortBy] = React.useState(localStorage.getItem(sortByKey) as T ?? defaultSortBy);
    const [_sortOrder, _setSortOrder] = React.useState((localStorage.getItem(sortOrderKey) ?? UCloud.file.orchestrator.SortOrder.DESCENDING) as UCloud.file.orchestrator.SortOrder);

    return {sortBy: _sortBy, setSortBy, sortOrder: _sortOrder, setSortOrder};

    function setSortBy(sortBy: T): void {
        localStorage.setItem(sortByKey, sortBy)
        _setSortBy(sortBy);
    }

    function setSortOrder(sortOrder: UCloud.file.orchestrator.SortOrder): void {
        localStorage.setItem(sortOrderKey, sortOrder)
        _setSortOrder(sortOrder);
    }
}

const FileBrowser: React.FunctionComponent<FileBrowserProps> = props => {
    return null;
    /*
    const projectId = useProjectId();
    const params = useLocation();
    const pathFromQuery = getQueryParam(params.search, "path");
    const history = useHistory();
    const [pathFromProps, setPath] = useState(props.initialPath);
    const path = pathFromProps ?? pathFromQuery ?? "/";
    const [commandLoading, invokeCommand] = useCloudCommand();
    const [uploadPath, setUploadPath] = useGlobal("uploadPath", "/");

    const sorting = useSorting("files", UCloud.file.orchestrator.FilesSortBy.PATH);


    const [files, fetchFiles] = useCloudAPI<PageV2<UFile>>({noop: true}, emptyPageV2);
    const [collections, fetchCollections] = useCloudAPI<PageV2<FileCollection>>({noop: true}, emptyPageV2);
    const [metadata, fetchMetadata] = useCloudAPI<FileMetadataRetrieveAllResponse>({noop: true}, {metadata: []});
    const [generation, setGeneration] = useState(0);

    const groupedMetadata = useMemo(() => {
        return groupBy(metadata.data.metadata, it => it.path);
    }, [metadata.data]);

    const reload = useCallback((): void => {
        fetchMetadata(metadataApi.retrieveAll({parentPath: path}));
        if (path === "/") {
            fetchCollections(collectionsApi.browse({provider: UCLOUD_PROVIDER, itemsPerPage: 50}));
        } else {
            fetchFiles(filesApi.browse({itemsPerPage: 50, path, sortBy: sorting.sortBy, sortOrder: sorting.sortOrder}));
        }
        setGeneration(gen => gen + 1);
    }, [path, sorting.sortBy, sorting.sortOrder]);

    const loadMore = useCallback(() => {
        if (path === "/") {
            fetchCollections(collectionsApi.browse({
                provider: UCLOUD_PROVIDER,
                itemsPerPage: 50,
                next: collections.data.next
            }));
        } else {
            fetchFiles(filesApi.browse({itemsPerPage: 50, next: files.data.next, path, sortBy: UCloud.file.orchestrator.FilesSortBy.PATH, sortOrder: UCloud.file.orchestrator.SortOrder.DESCENDING}));
        }
    }, [path, files, collections]);

    const navigateTo = useCallback((newPath: string) => {
        if (props.initialPath) {
            setPath(newPath);
        } else {
            history.push(buildQueryString("/files", {path: newPath}));
        }
    }, [props.initialPath]);

    useEffect(() => reload(), [path, projectId, sorting.sortBy, sorting.sortOrder]);

    useEffect(() => {
        setUploadPath(path);
    }, [path]);

    if (props.embedded !== true) { // NOTE(Dan): I know, we are breaking rules of hooks
        // NOTE(Jonas): Can't we just move this to a separate function called `useOnNotEmbedded(embedded?: boolean)`?
        useTitle("Files");
        useSidebarPage(SidebarPages.Files);
        useLoading(files.loading || collections.loading || commandLoading);
        useRefreshFunction(reload);
    }

    const commonProps: CommonFileProps = {
        reload,
        loadMore,
        generation,
        navigateTo,
        embedded: props.embedded === true,
        invokeCommand,
        path,
        onSelect: props.onSelect,
        selectFileRequirement: props.selectFileRequirement,
        history,
        metadata: groupedMetadata,
        sorting
    };

    if (path === "/") {
        return <FileCollections provider={UCLOUD_PROVIDER} collections={collections} {...commonProps} />;
    } else {
        return <Files files={files} {...commonProps} />;
    }
     */
};

export default FileBrowser;