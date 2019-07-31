import * as React from "react";
import {List as PaginationList} from "Pagination/List";
import {Cloud} from "Authentication/SDUCloudObject";
import {BreadCrumbs} from "ui-components/Breadcrumbs";
import {
    favoritesQuery,
    filepathQuery,
    isDirectory,
    newMockFolder,
    resolvePath
} from "Utilities/FileUtilities";
import PromiseKeeper from "PromiseKeeper";
import {emptyPage} from "DefaultObjects";
import {
    File,
    FileOperation,
    FileResource,
    FileSelectorProps,
    FileSource,
    SortBy,
    SortOrder
} from ".";
import {Box, Button, Flex, Icon, Input, SelectableText, SelectableTextWrapper} from "ui-components";
import * as ReactModal from "react-modal";
import {Spacer} from "ui-components/Spacer";
import FilesTable from "./FilesTable";
import SDUCloud from "Authentication/lib";
import {addTrailingSlash, errorMessageOrDefault} from "UtilityFunctions";
import styled from "styled-components";
import {Refresh} from "Navigation/Header";
import {Page} from "Types";
import {useState} from "react";
import {APICallParameters, useCloudAPI} from "Authentication/DataHook";
import {buildQueryString} from "Utilities/URIUtilities";

interface FileSelectorState {
    promises: PromiseKeeper
    path: string
    error?: string
    loading: boolean
    page: Page<File>
    fileSource: FileSource
}

class FileSelector extends React.Component<FileSelectorProps, FileSelectorState> {
    constructor(props: Readonly<FileSelectorProps>) {
        super(props);
        this.state = {
            promises: new PromiseKeeper(),
            path: `${Cloud.homeFolder}`,
            loading: false,
            error: undefined,
            page: emptyPage,
            fileSource: FileSource.HOME
        };
    }

    componentWillUnmount = () => this.state.promises.cancelPromises();

    setSelectedFile = (file: File | null) => {
        if (file === null) {
            this.props.onFileSelect(null);
        } else {
            let fileCopy = {path: file.path};
            this.props.onFileSelect(fileCopy);
        }
    };

    private fetchFiles = async (source: FileSource, path: string, pageNumber: number, itemsPerPage: number) => {
        this.setState(() => ({loading: true}));
        const {onlyAllowFolders} = this.props;

        try {
            let filePageFuture: Promise<Page<File>>;

            switch (source) {
                case FileSource.HOME:
                    filePageFuture = this.state.promises.makeCancelable(
                        Cloud.get<Page<File>>(filepathQuery(
                            path,
                            pageNumber,
                            itemsPerPage,
                            SortOrder.DESCENDING,
                            onlyAllowFolders ? SortBy.FILE_TYPE : SortBy.PATH,
                            [FileResource.PATH, FileResource.FILE_ID, FileResource.FILE_TYPE, FileResource.SENSITIVITY_LEVEL]
                        ))).promise.then(it => it.response);
                    break;

                case FileSource.FAVORITES:
                    filePageFuture = this.state.promises.makeCancelable(
                        Cloud.get<Page<File>>(favoritesQuery(pageNumber, itemsPerPage))
                    ).promise.then(it => it.response);
                    break;

                case FileSource.SHARES:
                    filePageFuture = this.state.promises.makeCancelable(
                        Cloud.get<Page<File>>(buildQueryString("/shares/list-files", {page: pageNumber, itemsPerPage}))
                    ).promise.then(it => it.response);
                    break;

                default:
                    throw "Unknown file source";
            }

            let page = await filePageFuture;
            this.setState(() => ({
                page,
                path: resolvePath(path),
                error: undefined,
                fileSource: source
            }));
        } catch (e) {
            this.setState(() => ({error: errorMessageOrDefault(e, "An error occurred fetching files")}));
        } finally {
            this.setState(() => ({loading: false}))
        }
    };

    render() {
        return (
            <Flex backgroundColor="white">
                {this.props.trigger}

                <FileSelectorModal
                    fileSource={this.state.fileSource}
                    errorMessage={this.state.error}
                    onErrorDismiss={() => this.setState(() => ({error: undefined}))}
                    show={this.props.visible}
                    onHide={() => this.setSelectedFile(null)}
                    path={this.state.path}
                    navigate={(path, pageNumber, itemsPerPage) => this.fetchFiles(this.state.fileSource, path, pageNumber, itemsPerPage)}
                    page={this.state.page}
                    loading={this.state.loading}
                    setSelectedFile={this.setSelectedFile}
                    fetchFiles={this.fetchFiles}
                    canSelectFolders={this.props.canSelectFolders}
                    onlyAllowFolders={this.props.onlyAllowFolders}
                    disallowedPaths={this.props.disallowedPaths}
                />
            </Flex>
        )
    }
}

const FileSelectorModalStyle = {
    content: {
        top: "80px",
        left: "25%",
        right: "25%",
        background: ""
    }
};

interface FileSelectorModalProps {
    show: boolean
    loading: boolean
    path: string
    onHide: () => void
    page: Page<File>
    setSelectedFile: Function
    fetchFiles: (source: FileSource, path: string, pageNumber: number, itemsPerPage: number) => void
    disallowedPaths?: string[]
    onlyAllowFolders?: boolean
    canSelectFolders?: boolean
    fileSource: FileSource
    errorMessage?: string
    onErrorDismiss?: () => void
    navigate?: (path: string, pageNumber: number, itemsPerPage: number) => void
}

const FileSelectorModal = ({canSelectFolders, ...props}: FileSelectorModalProps) => {
    const fetchFiles = (settings: { source: FileSource, path?: string, pageNumber?: number, itemsPerPage?: number }) => {
        const path = !!settings.path ? settings.path : props.path;
        const pageNumber = settings.pageNumber !== undefined ? settings.pageNumber : props.page.pageNumber;
        const itemsPerPage = settings.itemsPerPage !== undefined ? settings.itemsPerPage : props.page.itemsPerPage;

        props.fetchFiles(settings.source, path, pageNumber, itemsPerPage);
    };

    const FileSourceTab = (tabProps: { source: FileSource, text: string }) => (
        <SelectableText
            cursor="pointer"
            mr="1em"
            selected={tabProps.source == props.fileSource}
            onClick={() => fetchFiles({source: tabProps.source, path: Cloud.homeFolder, pageNumber: 0})}
        >{tabProps.text}</SelectableText>
    );

    return (
        <ReactModal
            isOpen={props.show}
            shouldCloseOnEsc
            ariaHideApp={false}
            onRequestClose={props.onHide}
            onAfterOpen={() => fetchFiles({source: FileSource.HOME})}
            style={FileSelectorModalStyle}
        >
            <SelectableTextWrapper>
                <FileSourceTab source={FileSource.HOME} text={"Browse"}/>
                <FileSourceTab source={FileSource.FAVORITES} text={"Favorites"}/>
                <FileSourceTab source={FileSource.SHARES} text={"Shares"}/>

                <Box mr="auto"/>
                <Icon name="close" onClick={props.onHide}/>
            </SelectableTextWrapper>

            <Spacer
                height={"3em"}
                alignItems="center"
                left={
                    <Box mt="48px">
                        <BreadCrumbs
                            homeFolder={Cloud.homeFolder}
                            currentPath={props.path}
                            navigate={path => fetchFiles({source: FileSource.HOME, path})}
                        />
                    </Box>
                }
                right={
                    <Refresh
                        spin={props.loading}
                        onClick={() => fetchFiles({source: props.fileSource})}
                    />
                }
            />

            <PaginationList
                pageRenderer={page =>
                    <FileSelectorBody
                        omitRelativeFolders={props.fileSource != FileSource.HOME}
                        canSelectFolders={!!canSelectFolders}
                        {...props}
                        page={page}
                        fetchFiles={path => fetchFiles({source: FileSource.HOME, path})}
                    />
                }
                loading={props.loading}
                page={props.page}
                onPageChanged={pageNumber => {
                    fetchFiles({source: props.fileSource, itemsPerPage: props.page.itemsPerPage, pageNumber})
                }}
            />
        </ReactModal>
    );
};

interface FileSelectorBodyProps {
    entriesPerPageSelector?: React.ReactNode
    disallowedPaths?: string[]
    onlyAllowFolders?: boolean
    creatingFolder?: boolean
    canSelectFolders: boolean
    page: Page<File>
    fetchFiles: (path: string) => void
    setSelectedFile: Function
    createFolder?: () => void
    path: string
    omitRelativeFolders: boolean
}

const FileSelectorBody = ({disallowedPaths = [], onlyAllowFolders = false, canSelectFolders = false, ...props}: FileSelectorBodyProps) => {
    let f = onlyAllowFolders ? props.page.items.filter(f => isDirectory(f)) : props.page.items;
    const files = f.filter(({path}) => !disallowedPaths.some(d => d === path));
    const relativeFolders: File[] = [];

    const p = props.path.startsWith("/") ? addTrailingSlash(props.path) : `/${addTrailingSlash(props.path)}`;
    if (p !== Cloud.homeFolder && !props.omitRelativeFolders) relativeFolders.push(newMockFolder(`${addTrailingSlash(props.path)}..`, false));
    if (canSelectFolders && !props.omitRelativeFolders) relativeFolders.push(newMockFolder(`${addTrailingSlash(props.path)}/.`, false));
    const ops: FileOperation[] = [];
    if (canSelectFolders) {
        ops.push({
            text: "Select", onClick: (files: File[], cloud: SDUCloud) => props.setSelectedFile(files[0]),
            disabled: (files: File[], cloud: SDUCloud) => false
        })
    } else {
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

export default FileSelector;