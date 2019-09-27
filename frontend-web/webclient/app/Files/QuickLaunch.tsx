import {File} from "Files/index";
import { ApplicationMetadata } from "Applications";

export interface QuickLaunchCallback {
    foo: string
}

export interface QuickLaunchApp {
    extensions: string[];
    metadata: ApplicationMetadata;
    //onClick: (file: File, cb: QuickLaunchCallback) => void;
    //disabled: (selectedFiles: File[]) => boolean;
    //icon?: string;
    //color?: string;
    //outline?: boolean;
    //currentDirectoryMode?: boolean;
}

/*export const defaultFileQuickLaunchApps: FileQuickLaunchApp[] = [
    {
        text: "Coder",
        onClick: files => UF.copyToClipboard({
            value: files[0].path,
            message: `${replaceHomeFolder(files[0].path, Cloud.homeFolder)} copied to clipboard`
        }),
        disabled: files => !UF.inDevEnvironment() || files.length !== 1 || isAnyMockFile(files) || isAnySharedFs(files),
        icon: "chat"
    },
    {
        text: "Rename",
        onClick: (files, cb) => cb.startRenaming(files[0]),
        disabled: (files: File[]) => files.length === 1 && !allFilesHasAccessRight(AccessRight.WRITE, files) ||
            isAnyMockFile(files) || isAnySharedFs(files),
        icon: "rename"
    }
];*/
