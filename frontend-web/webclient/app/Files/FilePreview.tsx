import * as React from "react";
import { connect } from "react-redux";
import { File } from "Files";
import { match } from "react-router";
import { ReduxObject, FilePreviewReduxState } from "DefaultObjects";
import { removeTrailingSlash } from "UtilityFunctions";
import { Dispatch } from "redux";
import { MainContainer } from "MainContainer/MainContainer";
import { setFilePreviewError, fetchPreviewFile } from "./Redux/FilePreviewAction";
import { fetchFileContent } from "Utilities/FileUtilities";
import { Cloud } from "Authentication/SDUCloudObject";

interface FilePreviewStateProps {
    file: File
    match: match
}

interface FilePreviewOperations {
    fetchFile: (p: string) => void
    setError: (error?: string) => void
}

interface FilePreviewProps extends FilePreviewOperations, FilePreviewStateProps {
    location: { pathname: string, search: string }
}

class FilePreview extends React.Component<FilePreviewProps> {
    componentDidMount() {
        this.props.fetchFile(this.filepath);
        this.fetchContent();
    }


    get queryParams(): URLSearchParams {
        return new URLSearchParams(this.props.location.search);
    }

    async fetchContent() {
        /* if (this.file && this.file.content === null) return;
        const type = extensionTypeFromPath(this.filepath);
        if (!this.file || !this.file.content) return (<LoadingIcon size={18} />)
        switch (type) {
            case "code":
                return (<code style={{ whiteSpace: "pre-wrap" }}>{this.file.content}</code>)
            case "image":
                return (<img src={`${0}`} />)
            case "text":
            case "audio":
            case "video":
            case "archive":
            case "pdf":
            default:
                return (<div>Can't render content</div>)
        } */
    }

    get filepath() {
        const param = this.queryParams.get("path");
        return param ? removeTrailingSlash(param) : "";
    }

    render() {
        return (
            <MainContainer main={<div/>} />
        );
    }
}

const mapStateToProps = ({ filePreview }: ReduxObject): FilePreviewReduxState => ({
    file: filePreview.file
});

const mapDispatchToProps = (dispatch: Dispatch): FilePreviewOperations => ({
    fetchFile: async path => dispatch(await fetchPreviewFile(path)),
    setError: error => dispatch(setFilePreviewError(error))
});

export default connect(mapStateToProps, mapDispatchToProps)(FilePreview);