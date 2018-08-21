import * as React from "react";
import { Modal, Button, List, Input } from "semantic-ui-react";
import { List as PaginationList } from "Pagination/List";
import { Cloud } from "Authentication/SDUCloudObject";
import { BreadCrumbs } from "Breadcrumbs/Breadcrumbs";
import * as PropTypes from "prop-types";
import { replaceHomeFolder, getFilenameFromPath, getParentPath, isDirectory, createFolder } from "Utilities/FileUtilities";
import * as uf from "UtilityFunctions";
import PromiseKeeper from "PromiseKeeper";
import { changeUppyRunAppOpen } from "Uppy/Redux/UppyActions";
import { KeyCode } from "DefaultObjects";
import { FileIcon } from "UtilityComponents";
import "./Files.scss";
import "Styling/Shared.scss";
import { emptyPage } from "DefaultObjects";
import { FileSelectorProps, FileSelectorState, FileListProps, FileSelectorModalProps, FileSelectorBodyProps } from ".";
import { filepathQuery, isInvalidPathName } from "Utilities/FileUtilities";

class FileSelector extends React.Component<FileSelectorProps, FileSelectorState> {
    constructor(props, context) {
        super(props, context);
        this.state = {
            promises: new PromiseKeeper(),
            path: `${Cloud.homeFolder}`,
            loading: false,
            page: emptyPage,
            modalShown: false,
            uppyOnUploadSuccess: null,
            creatingFolder: false
        };
    }

    static contextTypes = {
        store: PropTypes.object.isRequired
    }

    handleKeyDown = (key, name) => {
        if (key === KeyCode.ESC) {
            this.setState(() => ({ creatingFolder: false }));
        } else if (key === KeyCode.ENTER) {
            const { path, page } = this.state;
            const fileNames = page.items.map((it) => getFilenameFromPath(it.path));
            if (isInvalidPathName(name, fileNames)) { return }
            const newPath = `${path.endsWith("/") ? path + name : path + "/" + name}`;
            createFolder(newPath, Cloud, () => {
                this.setState(() => ({ creatingFolder: false }));
                this.fetchFiles(path, page.pageNumber, page.itemsPerPage);
            });
        }
    }

    uppyOnUploadSuccess = (file, resp, uploadURL) => {
        if (!this.props.allowUpload) return;
        // TODO This is a hack.
        let apiIndex = uploadURL.indexOf("/api/");
        if (apiIndex === -1) throw "Did not expect upload URL to not contain /api/";

        let apiEndpoint = uploadURL.substring(apiIndex + 5);

        Cloud.head(apiEndpoint).then(it => {
            console.log("Got a response back!");
            let path = it.request.getResponseHeader("File-Location");
            path.lastSlash
            let lastSlash = path.lastIndexOf("/");
            if (lastSlash === -1) throw `Could not parse name of path: ${path}`;
            let name = path.substring(lastSlash + 1);
            let fileObject = {
                path: path,
                name: name,
            };
            this.props.onFileSelect(fileObject);
        });
    };

    componentDidMount() {
        const { page } = this.state;
        this.fetchFiles(Cloud.homeFolder, page.pageNumber, page.itemsPerPage);
    }

    componentWillUnmount() {
        this.state.promises.cancelPromises();
    }

    onUppyClose = (): void => {
        this.props.uppy.off("upload-success", this.state.uppyOnUploadSuccess);
        this.setState(() => ({
            uppyOnUploadSuccess: null,
        }));
    }

    setSelectedFile = (file) => {
        let fileCopy = { path: file.path };
        this.setState(() => ({
            modalShown: false,
            creatingFolder: false
        }));
        this.props.onFileSelect(fileCopy);
    }

    fetchFiles = (path: string, pageNumber: number, itemsPerPage: number) => {
        this.setState(() => ({ loading: true, creatingFolder: false }));
        this.state.promises.makeCancelable(Cloud.get(filepathQuery(path, pageNumber, itemsPerPage))).promise.then(({ response }) =>
            this.setState(() => ({
                page: response,
                loading: false,
                path
            }))
        );
    }

    render() {
        const onUpload = () => {
            if (!this.props.allowUpload) return;
            this.context.store.dispatch(changeUppyRunAppOpen(true));
            let uppy = this.props.uppy;
            uppy.reset();
            uppy.once("upload-success", this.uppyOnUploadSuccess);
        };
        const path = this.props.path ? this.props.path : "";
        const uploadButton = this.props.allowUpload ? (<UploadButton onClick={onUpload} />) : null;
        const removeButton = this.props.remove ? (<RemoveButton onClick={this.props.remove} />) : null;
        return (
            <React.StrictMode>
                <Input
                    className="readonly mobile-padding"
                    required={this.props.isRequired}
                    placeholder={"No file selected"}
                    value={path}
                    action
                >
                    <input onClick={() => this.setState(() => ({ modalShown: true }))} />
                    {uploadButton}
                    {removeButton}
                </Input>
                <FileSelectorModal
                    show={this.state.modalShown}
                    onHide={() => this.setState(() => ({ modalShown: false }))}
                    path={this.state.path}
                    navigate={this.fetchFiles}
                    page={this.state.page}
                    loading={this.state.loading}
                    creatingFolder={this.state.creatingFolder}
                    setSelectedFile={this.setSelectedFile}
                    fetchFiles={this.fetchFiles}
                    handleKeyDown={this.handleKeyDown}
                    createFolder={() => this.setState(() => ({ creatingFolder: true }))}
                    canSelectFolders={this.props.canSelectFolders}
                    onlyAllowFolders={this.props.onlyAllowFolders}
                />
            </React.StrictMode>)
    }
}

export const FileSelectorModal = (props: FileSelectorModalProps) => (
    <Modal open={props.show} onClose={props.onHide} closeOnDimmerClick size="large">
        <Modal.Header>
            File selector
            <Button circular floated="right" icon="cancel" type="button" onClick={props.onHide} />
            <Button icon="redo" loading={props.loading} floated="right" circular onClick={() => props.fetchFiles(props.path, props.page.pageNumber, props.page.itemsPerPage)} />
            <CreateFolderButton createFolder={props.createFolder} />
        </Modal.Header>
        <Modal.Content scrolling>
            <BreadCrumbs currentPath={props.path} navigate={(path) => props.fetchFiles(path, props.page.pageNumber, props.page.itemsPerPage)} />
            <PaginationList
                errorMessage={props.errorMessage}
                onErrorDismiss={props.onErrorDismiss}
                pageRenderer={(page) =>
                    <FileSelectorBody
                        {...props}
                        page={page}
                        fetchFiles={(path) => props.fetchFiles(path, page.pageNumber, page.itemsPerPage)}
                    />
                }
                page={props.page}
                onPageChanged={(pageNumber) => props.fetchFiles(props.path, pageNumber, props.page.itemsPerPage)}
                onItemsPerPageChanged={(itemsPerPage) => props.fetchFiles(props.path, 0, itemsPerPage)}
                loading={props.loading}
            />
        </Modal.Content>
    </Modal>
);

const FileSelectorBody = ({ disallowedPaths = [] as string[], onlyAllowFolders = false, ...props }: FileSelectorBodyProps) => {
    let f = onlyAllowFolders ? props.page.items.filter(f => isDirectory(f)) : props.page.items;
    const files = f.filter((it) => !disallowedPaths.some((d) => d === it.path));
    const { path } = props;
    return (
        <React.Fragment>
            <List divided size="large">
                <List.Header>
                    Filename
                </List.Header>
                <CreatingFolder
                    creatingFolder={props.creatingFolder}
                    handleKeyDown={props.handleKeyDown}
                />
                <MockFolder // Return folder
                    predicate={uf.removeTrailingSlash(path) !== uf.removeTrailingSlash(Cloud.homeFolder) && !(disallowedPaths.some(it => it === getParentPath(path)))}
                    folderName=".."
                    path={getParentPath(path)}
                    canSelectFolders={props.canSelectFolders}
                    setSelectedFile={props.setSelectedFile}
                    fetchFiles={props.fetchFiles}
                />
                <MockFolder // Current folder
                    predicate={onlyAllowFolders && !disallowedPaths.some(dP => uf.addTrailingSlash(dP) === uf.addTrailingSlash(path))}
                    path={path}
                    setSelectedFile={props.setSelectedFile}
                    canSelectFolders
                    folderName={`${getFilenameFromPath(replaceHomeFolder(path, Cloud.homeFolder))} (Current folder)`}
                    fetchFiles={() => null}
                />
                <FileList files={files} setSelectedFile={props.setSelectedFile} fetchFiles={props.fetchFiles} canSelectFolders={props.canSelectFolders} />
            </List>
        </React.Fragment>)
};

type CreateFolderButton = { createFolder: () => void }
const CreateFolderButton = ({ createFolder }: CreateFolderButton) =>
    !!createFolder ?
        (<Button onClick={() => createFolder()} basic className="float-right" content="Create new folder" />) : null;


function MockFolder({ predicate, path, folderName, fetchFiles, setSelectedFile, canSelectFolders }) {
    const folderSelection = canSelectFolders ? (
        <List.Content floated="right">
            <Button onClick={() => setSelectedFile({ path })} content="Select" />
        </List.Content>
    ) : null;
    return predicate ? (
        <List.Item className="pointer-cursor itemPadding" onClick={() => fetchFiles(path)}>
            <List.Content>
                {folderSelection}
                <List.Icon name="folder" color="blue" />
                {folderName}
            </List.Content>
        </List.Item>
    ) : null;
}

const CreatingFolder = ({ creatingFolder, handleKeyDown }) =>
    !creatingFolder ? null : (
        <List.Item className="itemPadding">
            <List.Content>
                <List.Icon name="folder" color="blue" />
                <Input
                    onKeyDown={(e) => handleKeyDown(e.keyCode, e.target.value)}
                    placeholder="Folder name..."
                    autoFocus
                    transparent
                />
                <Button floated="right" onClick={() => handleKeyDown(KeyCode.ESC)}>✗</Button>
            </List.Content>
        </List.Item>
    );

const UploadButton = ({ onClick }) => (<Button type="button" content="Upload File" onClick={onClick} />);
const RemoveButton = ({ onClick }) => (<Button type="button" content="✗" onClick={onClick} />);
const FolderSelection = ({ canSelectFolders, setSelectedFile }) => canSelectFolders ?
    (<Button onClick={setSelectedFile} floated="right" content="Select" />) : null;

const FileList = ({ files, fetchFiles, setSelectedFile, canSelectFolders }: FileListProps) =>
    !files.length ? null :
        (<React.Fragment>
            {files.map((file, index) =>
                file.type === "FILE" ? (
                    <List.Item key={index} className="itemPadding pointer-cursor">
                        <List.Content onClick={() => setSelectedFile(file)}>
                            <List.Icon name={uf.iconFromFilePath(file.path, file.type, Cloud.homeFolder)} />
                            {getFilenameFromPath(file.path)}
                        </List.Content>
                    </List.Item>
                ) : (
                        <List.Item key={index} className="itemPadding pointer-cursor">
                            <List.Content floated="right">
                                <FolderSelection canSelectFolders={canSelectFolders} setSelectedFile={() => setSelectedFile(file)} />
                            </List.Content>
                            <List.Content onClick={() => fetchFiles(file.path)}>
                                <FileIcon size={null} name="folder" link={file.link} color="blue" />
                                {getFilenameFromPath(file.path)}
                            </List.Content>
                        </List.Item>
                    ))}
        </React.Fragment>);

export default FileSelector;