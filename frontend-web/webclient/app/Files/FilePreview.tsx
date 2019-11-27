import {Client} from "Authentication/HttpClientInstance";
import {FilePreviewReduxState, ReduxObject} from "DefaultObjects";
import {File} from "Files";
import LoadingIcon from "LoadingIcon/LoadingIcon";
import {MainContainer} from "MainContainer/MainContainer";
import * as React from "react";
import {connect} from "react-redux";
import {useLocation} from "react-router";
import {Dispatch} from "redux";
import {snackbarStore} from "Snackbar/SnackbarStore";
import styled from "styled-components";
import {Button, Icon} from "ui-components";
import Error from "ui-components/Error";
import {Spacer} from "ui-components/Spacer";
import {downloadFiles, isDirectory, statFileOrNull} from "Utilities/FileUtilities";
import {extensionFromPath, extensionTypeFromPath, removeTrailingSlash, requestFullScreen} from "UtilityFunctions";
import {fetchPreviewFile, setFilePreviewError} from "./Redux/FilePreviewAction";

interface FilePreviewStateProps {
    file: File;
    isEmbedded?: boolean;
}

interface FilePreviewOperations {
    fetchFile: (p: string) => void;
    setError: (error?: string) => void;
}

type FilePreviewProps = FilePreviewOperations & FilePreviewStateProps;

const FilePreview = (props: FilePreviewProps) => {
    const location = useLocation();
    const [fileContent, setFileContent] = React.useState("");
    const [error, setError] = React.useState("");
    const [showDownloadButton, setDownloadButton] = React.useState(false);
    const fileType = extensionTypeFromPath(filepath());

    React.useEffect(() => {
        const path = filepath();
        statFileOrNull(path).then(stat => {
            if (stat === null) {
                snackbarStore.addFailure("File not found");
                setError("File not found");
            } else if (isDirectory({fileType: stat.fileType})) {
                snackbarStore.addFailure("Directories cannot be previewed.");
                setError("Preview for folders not supported");
                setDownloadButton(true);
            } else if (stat.size! > 30_000_000) {
                snackbarStore.addFailure("File size too large. Download instead.");
                setError("File size too large to preview.");
                setDownloadButton(true);
            } else {
                Client.createOneTimeTokenWithPermission("files.download:read").then((token: string) => {
                    fetch(Client.computeURL(
                        "/api",
                        `/files/download?path=${encodeURIComponent(path)}&token=${encodeURIComponent(token)}`
                    )).then(async content => {
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
                    }).catch(it => typeof it === "string" ? setError(it) : setError("An error occurred fetching file content"));
                });
            }
        });
    }, []);

    function queryParams(): URLSearchParams {
        return new URLSearchParams(location.search);
    }

    function showContent() {
        if (error) return null;
        else if (showDownloadButton) {
            return (
                <Button mt="10px" onClick={() => downloadFiles([{path: filepath()}], () => undefined, Client)}>
                    Download file
                </Button>
            );
        } else if (!fileContent) return (<LoadingIcon size={36} />);
        switch (fileType) {
            case "text":
            case "code":
                /* TODO: Syntax highlighting (Google Prettify?) */
                return (
                    <>
                        <Spacer left={<div />} right={<ExpandingIcon name="fullscreen" onClick={onTryFullScreen} />} />
                        <code className="fullscreen" style={{whiteSpace: "pre-wrap"}}>{fileContent}</code>
                    </>
                );
            case "image":
                return (
                    <>
                        <Spacer left={<div />} right={<ExpandingIcon name="fullscreen" onClick={onTryFullScreen} />} />
                        <img src={fileContent} className="fullscreen" />
                    </>
                );
            case "audio":
                return <audio controls src={fileContent} />;
            case "video":
                return (
                    <video
                        style={{maxWidth: "100%", maxHeight: "100%", height: "calc(100vh - 48px)"}}
                        src={fileContent}
                        controls
                    />
                );
            case "pdf":
                return (
                    <>
                        <Spacer
                            left={<div />}
                            right={<ExpandingIcon name="fullscreen" mb="5px" onClick={onTryFullScreen} />}
                        />
                        <embed
                            className="fullscreen"
                            width="999999"
                            height="1080"
                            style={{maxWidth: "100%", maxHeight: "100%"}}
                            src={fileContent}
                        />
                    </>
                );
            default:
                return (<div>Can't render content</div>);
        }
    }

    function onTryFullScreen() {
        requestFullScreen(document.getElementsByClassName("fullscreen")[0]!, () => snackbarStore.addFailure("Failed to enter fullscreen."));
    }

    function filepath() {
        const param = queryParams().get("path");
        return param ? removeTrailingSlash(param) : "";
    }

    if (props.isEmbedded) {
        return showContent();
    }

    return (
        <MainContainer
            main={(
                <>
                    <Error error={error} />
                    {showContent()}
                </>
            )}
        />
    );
};

const ExpandingIcon = styled(Icon)`
    &:hover {
        transform: scale(1.05);
        cursor: pointer;
    }
`;

const mapStateToProps = ({filePreview}: ReduxObject): FilePreviewReduxState => ({
    file: filePreview.file
});

const mapDispatchToProps = (dispatch: Dispatch): FilePreviewOperations => ({
    fetchFile: async path => dispatch(await fetchPreviewFile(path)),
    setError: error => dispatch(setFilePreviewError(error))
});

export default connect(mapStateToProps, mapDispatchToProps)(FilePreview);
