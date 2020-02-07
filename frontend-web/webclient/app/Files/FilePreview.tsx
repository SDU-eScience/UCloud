import {Client} from "Authentication/HttpClientInstance";
import LoadingIcon from "LoadingIcon/LoadingIcon";
import {MainContainer} from "MainContainer/MainContainer";
import {usePromiseKeeper} from "PromiseKeeper";
import * as React from "react";
import {useLocation} from "react-router";
import {snackbarStore} from "Snackbar/SnackbarStore";
import styled from "styled-components";
import {Box, Button, Icon} from "ui-components";
import Error from "ui-components/Error";
import {Spacer} from "ui-components/Spacer";
import {downloadFiles, isDirectory, statFileOrNull} from "Utilities/FileUtilities";
import {
    extensionFromPath,
    extensionTypeFromPath,
    isExtPreviewSupported,
    removeTrailingSlash,
    requestFullScreen
} from "UtilityFunctions";
import {PREVIEW_MAX_SIZE} from "../../site.config.json";

interface FilePreviewStateProps {
    isEmbedded?: boolean;
}

const FilePreview = (props: FilePreviewStateProps): JSX.Element => {
    const location = useLocation();
    const [fileContent, setFileContent] = React.useState("");
    const [error, setError] = React.useState("");
    const [showDownloadButton, setDownloadButton] = React.useState(false);
    const fileType = extensionTypeFromPath(filepath());
    const promises = usePromiseKeeper();

    React.useEffect(() => {
        const path = filepath();
        if (!isExtPreviewSupported(extensionFromPath(path))) {
            setError("Preview is not supported for file type.");
            setDownloadButton(true);
            return;
        }
        promises.makeCancelable(statFileOrNull(path)).promise.then(stat => {
            if (stat === null) {
                snackbarStore.addFailure("File not found");
                setError("File not found");
            } else if (isDirectory({fileType: stat.fileType})) {
                snackbarStore.addFailure("Directories cannot be previewed.");
                setError("Preview for folders not supported");
                setDownloadButton(true);
            } else if (stat.size! > PREVIEW_MAX_SIZE) {
                snackbarStore.addFailure("File size too large. Download instead.");
                setError("File size too large to preview.");
                setDownloadButton(true);
            } else {
                promises.makeCancelable(
                    Client.createOneTimeTokenWithPermission("files.download:read")
                ).promise.then((token: string) => {
                    fetch(Client.computeURL(
                        "/api", `/files/download?path=${encodeURIComponent(path)}&token=${encodeURIComponent(token)}`
                    )).then(async content => {
                        if (promises.canceledKeeper) return;
                        switch (fileType) {
                            case "image":
                            case "audio":
                            case "video":
                            case "pdf":
                                setFileContent(URL.createObjectURL(await content.blob()));
                                break;
                            case "code":
                            case "text":
                                setFileContent(await content.text());
                                break;
                            default:
                                setError(`Preview not support for '${extensionFromPath(path)}'.`);
                                setDownloadButton(true);
                                break;
                        }
                    }).catch(it => {
                        /* Must be solveable more elegantly */
                        if (!it.isCanceled)
                            setError(typeof it === "string" ? it : "An error occurred fetching file content");
                    });
                }).catch(it => {
                    if (!it.isCanceled)
                        setError(typeof it === "string" ? it : "An error occurred fetching permission");
                });
            }
        }).catch(it => {
            if (!it.isCanceled)
                setError(typeof it === "string" ? it : "An error occurred fetching info");
        });
    }, []);

    function queryParams(): URLSearchParams {
        return new URLSearchParams(location.search);
    }

    function showContent(): JSX.Element | null {
        if (showDownloadButton) {
            return (
                <Button mt="10px" onClick={() => downloadFiles([{path: filepath()}], Client)}>
                    Download file
                </Button>
            );
        } else if (error) return null;
        else if (!fileContent) return (<LoadingIcon size={36} />);
        switch (fileType) {
            case "text":
            case "code":
                /* TODO: Syntax highlighting (Google Prettify?) */
                return <Code className="fullscreen">{fileContent}</Code>;
            case "image":
                return <Img src={fileContent} className="fullscreen" />;
            case "audio":
                return <audio controls src={fileContent} />;
            case "video":
                return <Video src={fileContent} controls />;
            case "pdf":
                return <div><Embed className="fullscreen" src={fileContent} /></div>;
            default:
                return <div>Can't render content</div>;
        }
    }

    function FullScreenIcon(): JSX.Element | null {
        if (!fileContent) return null;
        if (!["pdf", "text", "code", "image"].includes(fileType as string)) return null;
        return (
            <Spacer
                left={<div />}
                right={<ExpandingIcon mt="-60px" name="fullscreen" mb="5px" onClick={onTryFullScreen} />}
            />
        );
    }

    function onTryFullScreen(): void {
        requestFullScreen(
            document.getElementsByClassName("fullscreen")[0]!,
            () => snackbarStore.addFailure("Failed to enter fullscreen.")
        );
    }

    function filepath(): string {
        const param = queryParams().get("path");
        return param ? removeTrailingSlash(param) : "";
    }

    if (props.isEmbedded) {
        return (
            <>
                <Error error={error} />
                <FullScreenIcon />
                {showContent()}
            </>
        );
    }

    return (
        <MainContainer
            main={(
                <>
                    <Error error={error} />
                    <Box height="50px" />
                    <FullScreenIcon />
                    <ContentWrapper>
                        {showContent()}
                    </ContentWrapper>
                </>
            )}
        />
    );
};

const ContentWrapper = styled.div`
    display: flex;
    width: 100%;
    height: 80vh;
    justify-items: center;
    justify-content: center;
    align-items: center;
`;

const ExpandingIcon = styled(Icon)`
    &:hover {
        transform: scale(1.05);
        cursor: pointer;
    }
`;

const Code = styled.code`
    max-height: 90vh;
    maxWidth: 90vw;
    overflow-y: scroll;
    white-space: pre-wrap;
`;

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

