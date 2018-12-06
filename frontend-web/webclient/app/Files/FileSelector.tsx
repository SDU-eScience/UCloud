import * as React from "react";
import { List as PaginationList } from "Pagination/List";
import { Cloud } from "Authentication/SDUCloudObject";
import { BreadCrumbs } from "ui-components/Breadcrumbs";
import * as PropTypes from "prop-types";
import { replaceHomeFolder, getFilenameFromPath, getParentPath, isDirectory, createFolder } from "Utilities/FileUtilities";
import * as uf from "UtilityFunctions";
import PromiseKeeper from "PromiseKeeper";
import { KeyCode } from "DefaultObjects";
import { FileIcon, RefreshButton } from "UtilityComponents";
import { emptyPage } from "DefaultObjects";
import { FileSelectorProps, FileSelectorState, FileListProps, FileSelectorModalProps, FileSelectorBodyProps, File } from ".";
import { filepathQuery, isInvalidPathName } from "Utilities/FileUtilities";
import { Input, Flex, Icon, Box, Button, Divider, List, OutlineButton } from "ui-components";
import * as ReactModal from "react-modal";
import * as Heading from "ui-components/Heading";
import { TextSpan } from "ui-components/Text";
import { Spacer } from "ui-components/Spacer";
import { EntriesPerPageSelector } from "Pagination";

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
    const files = f.filter((it) => !disallowedPaths.some((d) => d === it.path));
    const { path } = props;
    return (
        <List childPadding="10px">
            <Flex>
                Filename
                <Box ml="auto" />
                {props.entriesPerPageSelector}
            </Flex>
            <CreatingFolder
                creatingFolder={props.creatingFolder}
                handleKeyDown={props.handleKeyDown}
            />
            <MockFolder // Return folder
                predicate={uf.removeTrailingSlash(path) !== uf.removeTrailingSlash(Cloud.homeFolder) && !(disallowedPaths.some(it => it === getParentPath(path)))}
                folderName=".."
                path={getParentPath(path)}
                canSelectFolders={canSelectFolders}
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
            <FileList files={files} setSelectedFile={props.setSelectedFile} fetchFiles={props.fetchFiles} canSelectFolders={canSelectFolders} />
        </List>)
};

type CreateFolderButton = { createFolder?: () => void }
const CreateFolderButton = ({ createFolder }: CreateFolderButton) =>
    !!createFolder ? (<Button onClick={() => createFolder()}>Create new folder</Button>) : null;

interface MockFolderProps {
    predicate: boolean
    path: string
    folderName: string
    canSelectFolders: boolean
    setSelectedFile: Function
    fetchFiles: (p: string) => void
}

function MockFolder({ predicate, path, folderName, fetchFiles, setSelectedFile, canSelectFolders }: MockFolderProps) {
    const folderSelection = canSelectFolders ? (
        <>
            <Box ml="auto" />
            <Button onClick={() => setSelectedFile({ path })}>Select</Button>
        </>
    ) : null;
    return predicate ? (
        <Flex onClick={() => fetchFiles(path)}>
            <Icon name="folder" color="blue" />
            {folderName}
            {folderSelection}
        </Flex>
    ) : null;
}

const CreatingFolder = ({ creatingFolder, handleKeyDown }) =>
    !creatingFolder ? null : (
        <Flex>
            <Input
                onKeyDown={(e: any) => handleKeyDown(e.keyCode, e.target.value)}
                placeholder="Folder name..."
                autoFocus
            />
            <Icon name="folder" color="blue" />
            <OutlineButton color="red" onClick={() => handleKeyDown(KeyCode.ESC)}>Cancel</OutlineButton>
        </Flex>
    );

const UploadButton = ({ onClick }) => (<Button type="button" onClick={onClick}>Upload File</Button>);
const RemoveButton = ({ onClick }) => (<Button type="button" onClick={onClick}>✗</Button>);
const FolderSelection = ({ canSelectFolders, setSelectedFile }) => canSelectFolders ?
    (<Button onClick={setSelectedFile}>Select</Button>) : null;

const FileList = ({ files, fetchFiles, setSelectedFile, canSelectFolders }: FileListProps) =>
    !files.length ? null :
        (<>
            {files.map((file, index) =>
                file.fileType === "FILE" ? (
                    <Flex key={index} onClick={() => setSelectedFile(file)}>
                        <Icon name="ftFile" color="blue" />{getFilenameFromPath(file.path)}
                        {/* <SList.Icon name={uf.iconFromFilePath(file.path, file.fileType, Cloud.homeFolder)} /> */}
                    </Flex>
                ) : (
                        <Flex key={index}>
                            <TextSpan onClick={() => fetchFiles(file.path)}>
                                <FileIcon name="ftFolder" link={file.link} color="blue" />
                                {getFilenameFromPath(file.path)}
                            </TextSpan>
                            <Box ml="auto" />
                            <FolderSelection canSelectFolders={canSelectFolders} setSelectedFile={() => setSelectedFile(file)} />
                        </Flex>
                    ))}
        </>);

export default FileSelector;