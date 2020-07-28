import {Client} from "Authentication/HttpClientInstance";
import LoadingIcon from "LoadingIcon/LoadingIcon";
import {MainContainer} from "MainContainer/MainContainer";
import * as React from "react";
import {useHistory, useLocation} from "react-router";
import {snackbarStore} from "Snackbar/SnackbarStore";
import styled from "styled-components";
import {Box, Button, Markdown} from "ui-components";
import Error from "ui-components/Error";
import {downloadFiles, fileTablePage, isDirectory} from "Utilities/FileUtilities";
import {
    extensionFromPath,
    extensionTypeFromPath,
    isExtPreviewSupported,
    ExtensionType,
} from "UtilityFunctions";
import {PREVIEW_MAX_SIZE} from "../../site.config.json";
import SyntaxHighlighter from "react-syntax-highlighter";
import {BreadCrumbs} from "ui-components/Breadcrumbs";
import {useAsyncCommand, useAsyncWork} from "Authentication/DataHook";
import {buildQueryString, getQueryParamOrElse} from "Utilities/URIUtilities";
import {useEffect, useState} from "react";
import {statFile} from "Files/LowLevelFileTable";
import {quickLaunchFromParametersFile} from "Files/QuickLaunch";
import {useTitle} from "Navigation/Redux/StatusActions";
import {useSidebarPage, SidebarPages} from "ui-components/Sidebar";

interface FilePreviewStateProps {
    isEmbedded?: boolean;
}

function useFileContent(): {
    path: string; fileContent: string | null; hasDownloadButton: boolean; error: string | null, fileType: ExtensionType
} {
    const [, runCommand] = useAsyncCommand();
    const [, , runWork] = useAsyncWork();
    const location = useLocation();
    const path = getQueryParamOrElse(location.search, "path", "");
    const [error, setError] = useState<string | null>(null);
    const [hasDownloadButton, setDownloadButton] = useState<boolean>(false);
    const fileType = extensionTypeFromPath(path);
    const [fileContent, setFileContent] = useState<string | null>("");

    useEffect(() => {
        async function fetchData(): Promise<void> {
            if (path === null) return;

            if (!isExtPreviewSupported(extensionFromPath(path))) {
                setError("Preview is not supported for file type.");
                setDownloadButton(true);
                return;
            }

            const stat = await runCommand(statFile({path}));
            if (stat === null) {
                snackbarStore.addFailure("File not found", false);
                setError("File not found");
            } else if (isDirectory({fileType: stat.fileType})) {
                snackbarStore.addFailure("Directories cannot be previewed.", false);
                setError("Preview for folders not supported");
                setDownloadButton(true);
            } else if (stat.size! > PREVIEW_MAX_SIZE) {
                snackbarStore.addFailure("File size too large. Download instead.", false);
                setError("File size too large to preview.");
                setDownloadButton(true);
            } else {
                let token: string = "";
                await runWork(async () => {
                    token = await Client.createOneTimeTokenWithPermission("files.download:read");
                });

                const content = await fetch(Client.computeURL(
                    "/api",
                    buildQueryString("/files/download", {path, token})
                ));
                switch (fileType) {
                    case "image":
                    case "audio":
                    case "video":
                    case "pdf":
                        setFileContent(URL.createObjectURL(await content.blob()));
                        break;
                    case "code":
                    case "text":
                    case "application":
                    case "markdown":
                        setFileContent(await content.text());
                        break;
                    default:
                        setError(`Preview not support for '${extensionFromPath(path)}'.`);
                        setDownloadButton(true);
                        break;
                }
            }
        }

        setFileContent(null);
        fetchData();
    }, [path]);
    return {path, fileContent, hasDownloadButton, error, fileType};
}

const FilePreview = (props: FilePreviewStateProps): JSX.Element => {
    const {hasDownloadButton, fileContent, fileType, error, path} = useFileContent();
    const history = useHistory();
    useTitle("File Preview");
    useSidebarPage(SidebarPages.Files);


    function showContent(): JSX.Element | null {
        if (hasDownloadButton) {
            return (
                <Button mt="10px" onClick={() => downloadFiles([{path}], Client)}>
                    Download file
                </Button>
            );
        } else if (error) return null;
        else if (!fileContent) return (<LoadingIcon size={36} />);
        switch (fileType) {
            case "application":
                quickLaunchFromParametersFile(fileContent, path, history);
                return <></>;
            case "text":
            case "code":
                return <SyntaxHighlighter className="fullscreen">{fileContent}</SyntaxHighlighter>;
            case "image":
                return <Img src={fileContent} className="fullscreen" />;
            case "audio":
                return <audio controls src={fileContent} />;
            case "video":
                return <Video src={fileContent} controls />;
            case "pdf":
                return <div><Embed className="fullscreen" src={fileContent} /></div>;
            case "markdown":
                return <Box maxWidth={"1200px"} m={"0 auto"}><Markdown>{fileContent}</Markdown></Box>;
            default:
                return <div>Cant render content</div>;
        }
    }

    function onFileNavigation(newPath: string): void {
        history.push(fileTablePage(newPath));
    }

    if (props.isEmbedded) {
        return (
            <>
                <Error error={error ?? undefined} />
                {showContent()}
            </>
        );
    }

    return (
        <MainContainer
            main={(
                <>
                    <BreadCrumbs
                        embedded
                        currentPath={path}
                        navigate={onFileNavigation}
                        client={Client}
                    />

                    <Error error={error ?? undefined} />
                    {showContent()}
                </>
            )}
        />
    );
};

const Embed = styled.embed`
    width: 85vw;
    height: 89vh;
`;

const Video = styled.video`
    max-height: 90vh;
    max-width: 90vw;
`;

const Img = styled.img`
    max-height: 90vh;
    max-width: 90vw;
`;

export default FilePreview;

