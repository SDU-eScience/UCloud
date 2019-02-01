import * as React from "react";
import { List as PaginationList } from "Pagination/List";
import { Cloud } from "Authentication/SDUCloudObject";
import { BreadCrumbs } from "ui-components/Breadcrumbs";
import { replaceHomeFolder, isDirectory, newMockFolder, resolvePath } from "Utilities/FileUtilities";
import PromiseKeeper from "PromiseKeeper";
import { emptyPage } from "DefaultObjects";
import { FileSelectorProps, FileSelectorState, FileSelectorModalProps, FileSelectorBodyProps, File, SortOrder, SortBy, FileOperation } from ".";
import { filepathQuery } from "Utilities/FileUtilities";
import { Input, Icon, Button, Divider, Flex, OutlineButton } from "ui-components";
import * as ReactModal from "react-modal";
import * as Heading from "ui-components/Heading";
import { Spacer } from "ui-components/Spacer";
import { FilesTable } from "./FilesTable";
import SDUCloud from "Authentication/lib";
import { addTrailingSlash } from "UtilityFunctions";
import styled from "styled-components";
import { SpaceProps } from "styled-system";
import { Refresh } from "Navigation/Header";

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
                path: resolvePath(path),
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
            <Flex>
                <FileSelectorInput
                    readOnly
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
            </Flex>)
    }
}

const FileSelectorInput = styled(Input)`
    cursor: pointer;
`;

const FileSelectorModalStyle = {
    content: {
        top: "80px",
        left: "25%",
        right: "25%"
    }
}

export const FileSelectorModal = ({ canSelectFolders, ...props }: FileSelectorModalProps) => (
    <ReactModal isOpen={props.show} shouldCloseOnEsc ariaHideApp={false} onRequestClose={props.onHide}
        style={FileSelectorModalStyle}
    >
        <Spacer alignItems="center"
            left={<Heading.h3>File selector</Heading.h3>}
            right={<><FavoritesButton mr="15px" toFavorites={props.toFavorites} /><Icon name="close" onClick={props.onHide} /></>}
        />
        <Divider />
        <Spacer height={"3em"} alignItems="center"
            left={<BreadCrumbs
                homeFolder={Cloud.homeFolder}
                currentPath={props.path}
                navigate={path => props.fetchFiles(path, props.page.pageNumber, props.page.itemsPerPage)} />}
            right={<Refresh spin={props.loading} onClick={() => props.fetchFiles(props.path, props.page.pageNumber, props.page.itemsPerPage)} />}
        />
        <PaginationList
            customEntriesPerPage
            errorMessage={props.errorMessage}
            onErrorDismiss={props.onErrorDismiss}
            pageRenderer={page =>
                <FileSelectorBody
                    canSelectFolders={!!canSelectFolders}
                    {...props}
                    page={page}
                    fetchFiles={path => props.fetchFiles(path, page.pageNumber, page.itemsPerPage)}
                />
            }
            page={props.page}
            onPageChanged={pageNumber => props.fetchFiles(props.path, pageNumber, props.page.itemsPerPage)}
            loading={props.loading}
        />
    </ReactModal>
);

type FavoritesButton = { toFavorites?: () => void } & SpaceProps;
const FavoritesButton = ({ toFavorites, ...props }: FavoritesButton) => toFavorites != null ? (
    <OutlineButton {...props} onClick={() => toFavorites()}>View Favorites</OutlineButton>
) : null;

const FileSelectorBody = ({ disallowedPaths = [], onlyAllowFolders = false, canSelectFolders = false, ...props }: FileSelectorBodyProps) => {
    let f = onlyAllowFolders ? props.page.items.filter(f => isDirectory(f)) : props.page.items;
    const files = f.filter(({ path }) => !disallowedPaths.some(d => d === path));
    const relativeFolders: File[] = [];

    const p = props.path.startsWith("/") ? addTrailingSlash(props.path) : `/${addTrailingSlash(props.path)}`
    if (p !== Cloud.homeFolder) relativeFolders.push(newMockFolder(`${addTrailingSlash(props.path)}..`, false));
    if (canSelectFolders) relativeFolders.push(newMockFolder(`${addTrailingSlash(props.path)}/.`, false));
    const ops: FileOperation[] = [];
    if (canSelectFolders) {
        ops.push({
            text: "Select", onClick: (files: File[], cloud: SDUCloud) => props.setSelectedFile(files[0]),
            disabled: (files: File[], cloud: SDUCloud) => false
        })
    }
    else {
        ops.push({
            text: "Select", onClick: (files: File[], cloud: SDUCloud) => props.setSelectedFile(files[0]),
            disabled: (files: File[], cloud: SDUCloud) => isDirectory(files[0])
        })
    }
    return (
        <FilesTable
            notStickyHeader
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
const UploadButton = ({ onClick }: FileSelectorButton) => (<Button ml="5px" type="button" onClick={onClick}>Upload File</Button>);
const RemoveButton = ({ onClick }: FileSelectorButton) => (<Button ml="5px" type="button" onClick={onClick}>✗</Button>);

export default FileSelector;