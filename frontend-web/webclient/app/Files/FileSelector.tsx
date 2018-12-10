import * as React from "react";
import { List as PaginationList } from "Pagination/List";
import { Cloud } from "Authentication/SDUCloudObject";
import { BreadCrumbs } from "ui-components/Breadcrumbs";
import * as PropTypes from "prop-types";
import { replaceHomeFolder, getFilenameFromPath, isDirectory, createFolder } from "Utilities/FileUtilities";
import PromiseKeeper from "PromiseKeeper";
import { KeyCode } from "DefaultObjects";
import { FileIcon, RefreshButton } from "UtilityComponents";
import { emptyPage } from "DefaultObjects";
import { FileSelectorProps, FileSelectorState, FileSelectorModalProps, FileSelectorBodyProps, File, SortOrder, SortBy, FileOperation } from ".";
import { filepathQuery, isInvalidPathName } from "Utilities/FileUtilities";
import { Input, Icon, Box, Button, Divider } from "ui-components";
import * as ReactModal from "react-modal";
import * as Heading from "ui-components/Heading";
import { Spacer } from "ui-components/Spacer";
import { EntriesPerPageSelector } from "Pagination";
import * as UF from "UtilityFunctions";
import { FilesTable } from "./Files";
import SDUCloud from "Authentication/lib";

class FileSelector extends React.Component<FileSelectorProps, FileSelectorState> {
    constructor(props, context) {
        super(props, context);
        this.state = {
            promises: new PromiseKeeper(),
            path: `${Cloud.homeFolder}`,
            loading: false,
            error: undefined,
            page: emptyPage,
            modalShown: false,
            creatingFolder: false
        };
    }

    static contextTypes = {
        store: PropTypes.object.isRequired
    };

    // FIXME Find better name
    handleKeyDown = (key: number, name: string) => {
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

    componentDidMount() {
        const { page } = this.state;
        this.fetchFiles(Cloud.homeFolder, page.pageNumber, page.itemsPerPage);
    }

    componentWillUnmount() {
        this.state.promises.cancelPromises();
    }

    setSelectedFile = (file: File) => {
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
                path,
                error: undefined
            }))
        ).catch(_ => this.setState(() => ({ error: "An error occurred fetching files", loading: false })));
        // FIXME: Ideally finally should be used for loading, but ts-jest doesn't allow it.
    }

    render() {
        const onUpload = () => {
            if (!this.props.allowUpload) return;
        };
        const path = this.props.path ? this.props.path : "";
        const uploadButton = this.props.allowUpload ? (<UploadButton onClick={onUpload} />) : null;
        const removeButton = this.props.remove ? (<RemoveButton onClick={this.props.remove} />) : null;
        return (
            <React.StrictMode>
                <Input
                    required={this.props.isRequired}
                    placeholder="No file selected"
                    value={replaceHomeFolder(path, Cloud.homeFolder)}
                    onClick={() => this.setState(() => ({ modalShown: true }))}
                />
                {uploadButton}
                {removeButton}
                <FileSelectorModal
                    errorMessage={this.state.error}
                    onErrorDismiss={() => this.setState(() => ({ error: undefined }))}
                    show={this.state.modalShown}
                    onHide={() => this.setState(() => ({ modalShown: false, creatingFolder: false }))}
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

export const FileSelectorModal = ({ canSelectFolders, ...props }: FileSelectorModalProps) => (
    <ReactModal isOpen={props.show} shouldCloseOnEsc ariaHideApp={false} onRequestClose={props.onHide}
        style={{
            content: {
                top: "80px",
                left: "25%",
                right: "25%"
            }
        }}
    >
        <Spacer alignItems="center"
            left={<Heading.h3>File selector</Heading.h3>}
            right={
                <>
                    <CreateFolderButton createFolder={props.createFolder} />
                    <Box mr="5px" />
                    <Icon name="close" onClick={props.onHide} />
                </>
            }
        />
        <Divider />
        <BreadCrumbs
            homeFolder={Cloud.homeFolder}
            currentPath={props.path}
            navigate={path => props.fetchFiles(path, props.page.pageNumber, props.page.itemsPerPage)}
        />
        <PaginationList
            customEntriesPerPage
            errorMessage={props.errorMessage}
            onErrorDismiss={props.onErrorDismiss}
            pageRenderer={page =>
                <FileSelectorBody
                    entriesPerPageSelector={
                        <>
                            <EntriesPerPageSelector
                                entriesPerPage={page.itemsPerPage}
                                content="Files per page"
                                onChange={itemsPerPage => props.fetchFiles(props.path, page.pageNumber, itemsPerPage)}
                            />
                            <RefreshButton loading={props.loading} onClick={() => props.fetchFiles(props.path, page.pageNumber, page.itemsPerPage)} />
                        </>}
                    canSelectFolders={!!canSelectFolders}
                    {...props}
                    page={page}
                    fetchFiles={path => props.fetchFiles(path, page.pageNumber, page.itemsPerPage)}
                />
            }
            page={props.page}
            onPageChanged={pageNumber => props.fetchFiles(props.path, pageNumber, props.page.itemsPerPage)}
            onItemsPerPageChanged={itemsPerPage => props.fetchFiles(props.path, 0, itemsPerPage)}
            loading={props.loading}
        />
    </ReactModal>
);

const FileSelectorBody = ({ disallowedPaths = [] as string[], onlyAllowFolders = false, canSelectFolders = false, ...props }: FileSelectorBodyProps) => {
    let f = onlyAllowFolders ? props.page.items.filter(f => isDirectory(f)) : props.page.items;
    const files = f.filter(({ path }) => !disallowedPaths.some((d) => d === path));
    const ops: FileOperation[] = [];
    if (canSelectFolders) {
        ops.push(
            {
                text: "Select", onClick: (files: File[], cloud: SDUCloud) => props.setSelectedFile(files[0]),
                disabled: (files: File[], cloud: SDUCloud) => false, icon: "check", color: "green"
            })
    }
    else {
        ops.push(
            {
                text: "Select", onClick: (files: File[], cloud: SDUCloud) => props.setSelectedFile(files[0]),
                disabled: (files: File[], cloud: SDUCloud) => isDirectory(files[0]), icon: "check", color: "green"
            })
    }
    return (
        <FilesTable
            onNavigationClick={props.fetchFiles}
            files={files}
            sortOrder={SortOrder.ASCENDING}
            sortingColumns={[]}
            sortFiles={() => undefined}
            onCheckFile={() => undefined}
            refetchFiles={() => undefined}
            sortBy={SortBy.PATH}
            fileOperations={ops}
        />);
};

type CreateFolderButton = { createFolder?: () => void }
const CreateFolderButton = ({ createFolder }: CreateFolderButton) =>
    !!createFolder ? (<Button onClick={() => createFolder()}>Create new folder</Button>) : null;

const UploadButton = ({ onClick }) => (<Button type="button" onClick={onClick}>Upload File</Button>);
const RemoveButton = ({ onClick }) => (<Button type="button" onClick={onClick}>✗</Button>);

export default FileSelector;