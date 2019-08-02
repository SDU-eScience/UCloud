import * as React from "react";
import {LowLevelFilesTable, LowLevelFilesTableProps} from "Files/LowLevelFilesTable";
import {Page} from "Types";
import {resolvePath} from "Utilities/FileUtilities";

interface VirtualFilesTableProps extends LowLevelFilesTableProps {
    fakeFolders: string[]
    loadFolder: (folder: string, page: number, itemsPerPage: number) => Page<File>
}

export const VirtualFilesTable: React.FunctionComponent<VirtualFilesTableProps> = props => {
    const mergedProperties = {...props};

    return <LowLevelFilesTable {...mergedProperties}/>;
};