import * as React from "react";
import { Container } from "semantic-ui-react";
import { connect } from "react-redux";
import { getParentPath, fetchFileContent } from "Utilities/FileUtilities";
import { fetchPageFromPath, updateFiles } from "Files/Redux/FilesActions";
import { DefaultLoading } from "LoadingIcon/LoadingIcon";
import { Page } from "Types";
import { File } from "Files";
import { match } from "react-router";
import { FilesReduxObject } from "DefaultObjects";
import { Cloud } from "Authentication/SDUCloudObject";
import { removeTrailingSlash, getExtensionFromPath } from "UtilityFunctions";

interface FilePreviewStateProps {
    page: Page<File>
    contentCount: number
    match: match<{ params: string[] }>
}

interface FilePreviewOperations {
    fetchPage: (p) => void
    updatePage: (p) => void
}

interface FilePreviewProps extends FilePreviewOperations, FilePreviewStateProps { }

class FilePreview extends React.Component<FilePreviewProps> {
    componentDidMount() {
        if (this.file) {
            console.log("No file")
            if (this.file.size < 32_000 && this.file.content == null)
                this.fetchFileContent()
            else {
                console.log(this.file.size);
            }
        } else {
            this.props.fetchPage(this.filepath);
        }
    }

    renderContent() {
        const extension = getExtensionFromPath(this.filepath);
    }

    fetchFileContent() {
        if (this.file && this.file.size < 32_000_000) {
            fetchFileContent(this.filepath, Cloud)
                .then(it => it.text().then(it => {
                    const { page } = this.props;
                    page.items.find(it => removeTrailingSlash(it.path) === this.filepath).content = it;
                    this.props.updatePage(page);
                }));
        }
        else {
            // SET ERROR AS FILE IS LARGER THAN 32 GB
        }
    }


    shouldComponentUpdate(nextProps, _nextState) {
        if (this.props.page.items.length) {
            if (getParentPath(this.props.page.items[0].path) !== getParentPath(nextProps.match.params[0])) {
                this.props.fetchPage(this.filepath);
            } else if (!this.file || this.file.content == null) {
                this.fetchFileContent();
            }
        } else {
            this.props.fetchPage(this.filepath);
        }
        return true;
    }

    get filepath() {
        return removeTrailingSlash(this.props.match.params[0]);
    }

    get file() {
        if (this.props.page.items.length) {
            return this.props.page.items.find(it => it.path === this.filepath);
        }
        return null;
    }

    render() {
        const loading = !this.file || !this.file.content;
        return (
            <Container>
                <DefaultLoading loading={loading} />
                {loading ? null : <span>{this.file.content}</span>}
            </Container>
        );
    }
}

const mapStateToProps = ({ files }: { files: FilesReduxObject }) => ({
    page: files.page,
    contentCount: files.page.items.filter(it => it.content !== undefined).length
});
const mapDispatchToProps = (dispatch): FilePreviewOperations => ({
    fetchPage: (path: string) => dispatch(fetchPageFromPath(path, 10)),
    updatePage: (page: Page<File>) => dispatch(updateFiles(page))
});


export default connect(mapStateToProps, mapDispatchToProps)(FilePreview);