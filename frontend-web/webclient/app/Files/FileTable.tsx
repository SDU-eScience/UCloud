import FileSelector from "Files/FileSelector";
import {defaultVirtualFolders, VirtualFileTable, VirtualFileTableProps} from "Files/VirtualFileTable";
import * as React from "react";
import {useCallback, useMemo, useState} from "react";
import {useHistory} from "react-router";
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

    const requestFileSelector = useCallback((doesAllowFolders: boolean, selectFoldersOnly: boolean) => {
        setAllowFolders(doesAllowFolders);
        setCanOnlySelectFolders(selectFoldersOnly);
        setIsVisible(true);
        return new Promise<string | null>((fn) => setResolve({resolve: fn}));
    }, []);

    const modifiedProps = {...props, requestFileSelector};

    function handleFileSelect(f: {path: string} | null) {
        setIsVisible(false);
        resolve.resolve(f ? f.path : null);
    }

    const table = useMemo(() => {
        return <VirtualFileTable {...modifiedProps} />;
    }, [props]);

    return (
        <>
            <FileSelector
                initialPath={props.path}
                onFileSelect={handleFileSelect}
                trigger={null}
                canSelectFolders={allowFolders}
                onlyAllowFolders={canOnlySelectFolders}
                visible={isVisible}
            />
            {table}
        </>
    );
};

export const EmbeddedFileTable: React.FunctionComponent<
    Omit<VirtualFileTableProps, "onFileNavigation"> &
    {includeVirtualFolders?: boolean}
> = props => {
    const history = useHistory();
    const mergedProps: VirtualFileTableProps = {
        ...props,
        ...(props.includeVirtualFolders !== false ? defaultVirtualFolders() : {}),
        onFileNavigation: path => history.push(fileTablePage(path)),
        embedded: true
    };
    return <FileTable {...mergedProps} />;
};
