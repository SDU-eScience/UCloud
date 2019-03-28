import * as React from "react";
import { List as PaginationList } from "Pagination/List";
import { Cloud } from "Authentication/SDUCloudObject";
import { BreadCrumbs } from "ui-components/Breadcrumbs";
import { replaceHomeFolder, isDirectory, newMockFolder, resolvePath, favoritesQuery } from "Utilities/FileUtilities";
import PromiseKeeper from "PromiseKeeper";
import { emptyPage } from "DefaultObjects";
import { FileSelectorProps, FileSelectorState, FileSelectorModalProps, FileSelectorBodyProps, File, SortOrder, SortBy, FileOperation } from ".";
import { filepathQuery } from "Utilities/FileUtilities";
import { Input, Icon, Button, Flex, Box } from "ui-components";
import * as ReactModal from "react-modal";
import { Spacer } from "ui-components/Spacer";
import FilesTable from "./FilesTable";
import SDUCloud from "Authentication/lib";
import { addTrailingSlash, errorMessageOrDefault } from "UtilityFunctions";
import styled from "styled-components";
import { Refresh } from "Navigation/Header";
import { Page } from "Types";
import { SelectableText, SearchOptions } from "Search/Search";
import { InputLabel } from "ui-components/Input";

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
            isFavorites: false
        };
    }

    componentWillUnmount = () => this.state.promises.cancelPromises();

    setSelectedFile = (file: File) => {
        let fileCopy = { path: file.path };
        this.setState(() => ({ modalShown: false }));
        this.props.onFileSelect(fileCopy);
    }

    private fetchFiles = (path: string, pageNumber: number, itemsPerPage: number) => {
        this.setState(() => ({ loading: true }));
        this.state.promises.makeCancelable(Cloud.get(filepathQuery(path, pageNumber, itemsPerPage))).promise.then(({ response }) =>
            this.setState(() => ({
                page: response,
                path: resolvePath(path),
                error: undefined,
                isFavorites: false
            }))
        ).catch((_) => this.setState(() => ({ error: "An error occurred fetching files" })))
            .finally(() => this.setState(() => ({ loading: false })))
    }

    private async fetchFavorites(pageNumber: number, itemsPerPage: number) {
        this.setState(() => ({ loading: true }));
        try {
            const result = await this.state.promises.makeCancelable(Cloud.get<Page<File>>(favoritesQuery(pageNumber, itemsPerPage))).promise;
            this.setState(() => ({
                page: result.response,
                error: undefined,
                isFavorites: true,
                path: "Favorites"
            }));
        } catch (e) {
            this.setState(() => ({ error: errorMessageOrDefault(e, "An error occurred fetching favorites") }));
        } finally {
            this.setState(() => ({ loading: false }));
        }
    }

    render() {
        const onUpload = () => { if (!this.props.allowUpload) return; };
        const path = this.props.path ? this.props.path : "";
        const uploadButton = this.props.allowUpload ? (<UploadButton onClick={onUpload} />) : null;
        const removeButton = this.props.remove ? (<RemoveButton onClick={() => this.props.remove!()} />) : null;
        const inputRefValueOrNull = this.props.inputRef && this.props.inputRef.current && this.props.inputRef.current.value;
        const inputValue = inputRefValueOrNull || replaceHomeFolder(path, Cloud.homeFolder);
        return (
            <Flex>
                <FileSelectorInput
                    ref={this.props.inputRef}
                    readOnly
                    required={this.props.isRequired}
                    placeholder="No file selected"
                    value={inputValue}
                    rightLabel={!!this.props.unitName}
                    onChange={() => undefined}
                    onClick={() => this.setState(() => ({ modalShown: true }))}
                />
                {this.props.unitName ? <InputLabel rightLabel>{this.props.unitName}</InputLabel> : null}
                {uploadButton}
                {removeButton}
                <FileSelectorModal
                    isFavorites={this.state.isFavorites}
                    fetchFavorites={(pageNumber, itemsPerPage) => this.fetchFavorites(pageNumber, itemsPerPage)}
                    errorMessage={this.state.error}
                    onErrorDismiss={() => this.setState(() => ({ error: undefined }))}
                    show={this.state.modalShown}
                    onHide={() => this.setState(() => ({ modalShown: false }))}
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

export const FileSelectorModal = ({ canSelectFolders, ...props }: FileSelectorModalProps) => {
    const fetchFiles = (settings: { path?: string, pageNumber?: number, itemsPerPage?: number }) => {
        const path = !!settings.path ? settings.path : props.path;
        const pageNumber = settings.pageNumber !== undefined ? settings.pageNumber : props.page.pageNumber;
        const itemsPerPage = settings.itemsPerPage !== undefined ? settings.itemsPerPage : props.page.itemsPerPage;

        props.fetchFiles(path, pageNumber, itemsPerPage);
    }

    return (
        <ReactModal
            isOpen={props.show}
            shouldCloseOnEsc
            ariaHideApp={false}
            onRequestClose={props.onHide}
            onAfterOpen={() => fetchFiles({})}
            style={FileSelectorModalStyle}
        >
            <SearchOptions>
                <SelectableText
                    cursor="pointer"
                    mr="1em"
                    selected={!props.isFavorites}
                    onClick={() => fetchFiles({ path: Cloud.homeFolder, pageNumber: 0 })}
                >Browse</SelectableText>

                <SelectableText
                    cursor="pointer"
                    onClick={() => props.fetchFavorites(props.page.pageNumber, props.page.itemsPerPage)}
                    selected={props.isFavorites}
                >Favorites</SelectableText>

                <Box mr="auto" />
                <Icon name="close" onClick={props.onHide} />
            </SearchOptions>

            <Spacer
                height={"3em"}
                alignItems="center"
                left={
                    <BreadCrumbs
                        homeFolder={Cloud.homeFolder}
                        currentPath={props.path}
                        navigate={path => fetchFiles({ path })}
                    />
                }
                right={
                    <Refresh
                        spin={props.loading}
                        onClick={() => fetchFiles({})}
                    />
                }
            />

            <PaginationList
                errorMessage={props.errorMessage}
                onErrorDismiss={props.onErrorDismiss}
                pageRenderer={page =>
                    <FileSelectorBody
                        omitRelativeFolders={props.isFavorites}
                        canSelectFolders={!!canSelectFolders}
                        {...props}
                        page={page}
                        fetchFiles={path => fetchFiles({ path })}
                    />
                }
                loading={props.loading}
                page={props.page}
                onPageChanged={pageNumber => {
                    if (props.isFavorites) {
                        props.fetchFavorites(pageNumber, props.page.itemsPerPage);
                    } else {
                        fetchFiles({});
                    }
                }}
            />
        </ReactModal>
    );
}

const FileSelectorBody = ({ disallowedPaths = [], onlyAllowFolders = false, canSelectFolders = false, ...props }: FileSelectorBodyProps) => {
    let f = onlyAllowFolders ? props.page.items.filter(f => isDirectory(f)) : props.page.items;
    const files = f.filter(({ path }) => !disallowedPaths.some(d => d === path));
    const relativeFolders: File[] = [];

    const p = props.path.startsWith("/") ? addTrailingSlash(props.path) : `/${addTrailingSlash(props.path)}`
    if (p !== Cloud.homeFolder && !props.omitRelativeFolders) relativeFolders.push(newMockFolder(`${addTrailingSlash(props.path)}..`, false));
    if (canSelectFolders && !props.omitRelativeFolders) relativeFolders.push(newMockFolder(`${addTrailingSlash(props.path)}/.`, false));
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