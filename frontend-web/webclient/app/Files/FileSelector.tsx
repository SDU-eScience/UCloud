import * as React from "react";
import { List as PaginationList } from "Pagination/List";
import { Cloud } from "Authentication/SDUCloudObject";
import { BreadCrumbs } from "ui-components/Breadcrumbs";
import { replaceHomeFolder, isDirectory, newMockFolder } from "Utilities/FileUtilities";
import PromiseKeeper from "PromiseKeeper";
import { KeyCode } from "DefaultObjects";
import { RefreshButton } from "UtilityComponents";
import { emptyPage } from "DefaultObjects";
import { FileSelectorProps, FileSelectorState, FileSelectorModalProps, FileSelectorBodyProps, File, SortOrder, SortBy, FileOperation } from ".";
import { filepathQuery } from "Utilities/FileUtilities";
import { Input, Icon, Button, Divider } from "ui-components";
import * as ReactModal from "react-modal";
import * as Heading from "ui-components/Heading";
import { Spacer } from "ui-components/Spacer";
import { EntriesPerPageSelector } from "Pagination";
import { FilesTable } from "./FilesTable";
import SDUCloud from "Authentication/lib";
import { addTrailingSlash } from "UtilityFunctions";

class FileSelector extends React.Component<FileSelectorProps, FileSelectorState> {
    constructor(props: Readonly<FileSelectorProps>) {
        super(props);
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

    componentDidMount() {
        const { page } = this.state;
        this.fetchFiles(Cloud.homeFolder, page.pageNumber, page.itemsPerPage);
    }

    componentWillUnmount = () => this.state.promises.cancelPromises();

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
        ).catch((_) => this.setState(() => ({ error: "An error occurred fetching files", loading: false })));
        // FIXME: Ideally finally should be used for loading, but ts-jest doesn't allow it.
    }

    render() {
        const onUpload = () => {
            if (!this.props.allowUpload) return;
        };
        const path = this.props.path ? this.props.path : "";
        const uploadButton = this.props.allowUpload ? (<UploadButton onClick={onUpload} />) : null;
        const removeButton = this.props.remove ? (<RemoveButton onClick={() => this.props.remove!()} />) : null;
        return (
            <React.StrictMode>
                <Input
                    required={this.props.isRequired}
                    placeholder="No file selected"
                    value={replaceHomeFolder(path, Cloud.homeFolder)}
                    onChange={() => undefined}
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
                    setSelectedFile={this.setSelectedFile}
                    fetchFiles={this.fetchFiles}
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
            right={<Icon name="close" onClick={props.onHide} />}
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

const FileSelectorBody = ({ disallowedPaths = [], onlyAllowFolders = false, canSelectFolders = false, ...props }: FileSelectorBodyProps) => {
    let f = onlyAllowFolders ? props.page.items.filter(f => isDirectory(f)) : props.page.items;
    const files = f.filter(({ path }) => !disallowedPaths.some(d => d === path));
    const relativeFolders: File[] = [];

    const p = props.path.startsWith("/") ? addTrailingSlash(props.path) : `/${addTrailingSlash(props.path)}`
    if (p !== Cloud.homeFolder) relativeFolders.push(newMockFolder(`${props.path}/..`, false));
    if (canSelectFolders) relativeFolders.push(newMockFolder(`${props.path}/.`, false));
    const ops: FileOperation[] = [];
    if (canSelectFolders) {
        ops.push(
            {
                text: "Select", onClick: (files: File[], cloud: SDUCloud) => props.setSelectedFile(files[0]),
                disabled: (files: File[], cloud: SDUCloud) => false
            })
    }
    else {
        ops.push(
            {
                text: "Select", onClick: (files: File[], cloud: SDUCloud) => props.setSelectedFile(files[0]),
                disabled: (files: File[], cloud: SDUCloud) => isDirectory(files[0])
            })
    }
    return (
        <FilesTable
            onNavigationClick={props.fetchFiles}
            files={relativeFolders.concat(files)}
            sortOrder={SortOrder.ASCENDING}
            sortingColumns={[]}
            sortFiles={() => undefined}
            onCheckFile={() => undefined}
            refetchFiles={() => undefined}
            sortBy={SortBy.PATH}
            fileOperations={ops}
        />);
};

interface FileSelectorButton { onClick: () => void }
const UploadButton = ({ onClick }: FileSelectorButton) => (<Button type="button" onClick={onClick}>Upload File</Button>);
const RemoveButton = ({ onClick }: FileSelectorButton) => (<Button type="button" onClick={onClick}>✗</Button>);

export default FileSelector;