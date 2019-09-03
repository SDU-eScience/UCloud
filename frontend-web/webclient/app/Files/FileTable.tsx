import FileSelector from "Files/FileSelector";
import {defaultVirtualFolders, VirtualFileTable, VirtualFileTableProps} from "Files/VirtualFileTable";
import * as React from "react";
import {useCallback, useMemo, useState} from "react";
import {RouteComponentProps, withRouter} from "react-router";
import {fileTablePage} from "Utilities/FileUtilities";

interface ResolveHolder<T> {
    resolve: (arg: T) => void;
}

// The files table that most other clients should use as a base. This includes an embedded file selector for copy and
// move operations.
export const FileTable: React.FunctionComponent<VirtualFileTableProps> = props => {
    const [resolve, setResolve] = useState<ResolveHolder<string | null>>({resolve: () => 0});
    const [isVisible, setIsVisible] = useState(false);
    const [allowFolders, setAllowFolders] = useState<boolean>(false);
    const [canOnlySelectFolders, setCanOnlySelectFolders] = useState<boolean>(false);

    const requestFileSelector = useCallback((allowFolders: boolean, canOnlySelectFolders: boolean) => {
        setAllowFolders(allowFolders);
        setCanOnlySelectFolders(canOnlySelectFolders);
        setIsVisible(true);
        return new Promise<string | null>((fn) => setResolve({resolve: fn}));
    }, []);

    const modifiedProps = {...props, requestFileSelector};

    function handleFileSelect(f: { path: string } | null) {
        setIsVisible(false);
        resolve.resolve(f ? f.path : null);
    }

    const table = useMemo(() => {
        return <VirtualFileTable {...modifiedProps}/>;
    }, [props]);

    return <>
        <FileSelector
            initialPath={props.path}
            onFileSelect={handleFileSelect}
            trigger={null}
            canSelectFolders={allowFolders}
            onlyAllowFolders={canOnlySelectFolders}
            visible={isVisible}/>
        {table}
    </>;
};

const EmbeddedFileTable_: React.FunctionComponent<Omit<VirtualFileTableProps, "onFileNavigation"> & RouteComponentProps & { includeVirtualFolders?: boolean }> = props => {
    const mergedProps: VirtualFileTableProps = {
        ...props,
        ...(props.includeVirtualFolders !== false ? defaultVirtualFolders() : {}),
        onFileNavigation: path => props.history.push(fileTablePage(path)),
        embedded: true
    };
    return <FileTable {...mergedProps}/>;
};

export const EmbeddedFileTable = withRouter(EmbeddedFileTable_);
