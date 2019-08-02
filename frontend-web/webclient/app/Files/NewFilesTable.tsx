import * as React from "react";
import {LowLevelFilesTable, LowLevelFilesTableProps} from "Files/LowLevelFilesTable";
import FileSelector from "Files/FileSelector";
import {useCallback, useMemo, useState} from "react";

interface ResolveHolder<T> {
    resolve: (T) => void
}

// The files table that most other clients should use as a base. This includes an embedded file selector for copy and
// move operations.
export const NewFilesTable: React.FunctionComponent<LowLevelFilesTableProps> = props => {
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
        return <LowLevelFilesTable {...modifiedProps}/>
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