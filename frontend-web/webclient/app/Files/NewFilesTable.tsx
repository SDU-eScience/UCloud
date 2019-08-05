import * as React from "react";
import FileSelector from "Files/FileSelector";
import {useCallback, useMemo, useState} from "react";
import {defaultVirtualFolders, VirtualFilesTable, VirtualFilesTableProps} from "Files/VirtualFilesTable";
import {RouteComponentProps, withRouter} from "react-router";
import {fileTablePage} from "Utilities/FileUtilities";

interface ResolveHolder<T> {
    resolve: (T) => void
}

// The files table that most other clients should use as a base. This includes an embedded file selector for copy and
// move operations.
export const NewFilesTable: React.FunctionComponent<VirtualFilesTableProps> = props => {
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
        return <VirtualFilesTable {...modifiedProps}/>
    }, [props]);

    return <>
        <FileSelector
            onFileSelect={handleFileSelect}
            trigger={null}
            canSelectFolders={allowFolders}
            onlyAllowFolders={canOnlySelectFolders}
            visible={isVisible}/>
        {table}
    </>;
};

const EmbeddedFileTable_: React.FunctionComponent<Omit<VirtualFilesTableProps, "onFileNavigation"> & RouteComponentProps> = props => {
    const mergedProps: VirtualFilesTableProps = {
        ...props,
        ...defaultVirtualFolders(),
        onFileNavigation: path => props.history.push(fileTablePage(path))
    };
    return <NewFilesTable {...mergedProps}/>;
};

export const EmbeddedFileTable = withRouter(EmbeddedFileTable_);
