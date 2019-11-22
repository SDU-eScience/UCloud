import {Client} from "Authentication/HttpClientInstance";
import {FilePreviewReduxState, ReduxObject} from "DefaultObjects";
import {File} from "Files";
import LoadingIcon from "LoadingIcon/LoadingIcon";
import {MainContainer} from "MainContainer/MainContainer";
import * as React from "react";
import {connect} from "react-redux";
import {match} from "react-router";
import {Dispatch} from "redux";
import {snackbarStore} from "Snackbar/SnackbarStore";
import styled from "styled-components";
import {Icon} from "ui-components";
import {Spacer} from "ui-components/Spacer";
import {statFileOrNull} from "Utilities/FileUtilities";
import {extensionTypeFromPath, removeTrailingSlash, requestFullScreen} from "UtilityFunctions";
import {fetchPreviewFile, setFilePreviewError} from "./Redux/FilePreviewAction";

interface FilePreviewStateProps {
    file: File;
    match: match;
    isEmbedded?: boolean;
}

interface FilePreviewOperations {
    fetchFile: (p: string) => void;
    setError: (error?: string) => void;
}

interface FilePreviewProps extends FilePreviewOperations, FilePreviewStateProps {
    location: {
        pathname: string;
        search: string;
    };
}

const FilePreview = (props: FilePreviewProps) => {
    const [fileContent, setFileContent] = React.useState<string>("");
    const [error, setError] = React.useState("");
    const fileType = extensionTypeFromPath(filepath());

    React.useEffect(() => {
        const path = filepath();
        statFileOrNull(path).then(stat => {
            if (stat === null) {
                snackbarStore.addFailure("File not found");
            } else if (stat.size! > 30_000_000) {
                snackbarStore.addFailure("File size too large. Download instead.");
            } else {
                Client.createOneTimeTokenWithPermission("files.download:read").then((token: string) => {
                    fetch(Client.computeURL(
                        "/api",
                        `/files/download?path=${encodeURIComponent(path)}&token=${encodeURIComponent(token)}`
                    )).then(async content => {
                        switch (fileType) {
                            case "image":
                            case "video":
                            case "pdf":
                                setFileContent(URL.createObjectURL(await content.blob()));
                                break;
                            case "code":
                            case "text":
                                setFileContent(await content.text());
                                break;
                        }
                    }).catch(it => typeof it === "string" ? setError(it) : setError("An error occurred fetching file content"));
                });
            }
        });
    }, []);

    function queryParams(): URLSearchParams {
        return new URLSearchParams(props.location.search);
    }

    function showContent() {
        if (!fileContent) return (<LoadingIcon size={36} />);
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
                return <img src={fileContent} />;
            case "audio":
            case "video":
                return <video style={{maxWidth: "100%", maxHeight: "100%", verticalAlign: "middle"}} src={fileContent} controls />;
            case "pdf":
                return (
                    <>
                        <Spacer left={<div />} right={<ExpandingIcon name="fullscreen" mb="5px" onClick={onTryFullScreen} />} />
                        <embed className="fullscreen" width="999999" height="1080" style={{maxWidth: "100%", maxHeight: "100%"}} src={fileContent} />
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
            main={showContent()}
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
