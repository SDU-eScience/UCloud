import * as React from "react";
import {File, FileResource, SortBy, SortOrder} from "Files/index";
import * as UF from "UtilityFunctions"
import {APICallParameters, useAsyncCommand, useCloudAPI} from "Authentication/DataHook";
import {buildQueryString} from "Utilities/URIUtilities";
import {Page} from "Types";
import Table, {TableBody, TableCell, TableHeader, TableHeaderCell, TableRow} from "ui-components/Table";
import styled from "styled-components";
import Flex from "ui-components/Flex";
import Box from "ui-components/Box";
import {Arrow, FileIcon} from "UtilityComponents";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {TextSpan} from "ui-components/Text";
import Divider from "ui-components/Divider";
import {emptyPage, KeyCode, SensitivityLevelMap} from "DefaultObjects";
import {Cloud} from "Authentication/SDUCloudObject";
import {MainContainer} from "MainContainer/MainContainer";
import Checkbox from "ui-components/Checkbox";
import {Button, Icon, Input, Label, OutlineButton, Tooltip, Truncate} from "ui-components";
import {
    createFolder,
    getFilenameFromPath, getParentPath,
    isDirectory,
    isInvalidPathName, moveFile,
    replaceHomeFolder
} from "Utilities/FileUtilities";
import BaseLink from "ui-components/BaseLink";
import Theme from "ui-components/theme";
import {defaultFileOperations, FileOperation, FileOperationCallback} from "Files/NewFileOperations";
import {SpaceProps} from "styled-system";
import {useEffect, useState} from "react";

interface NewFilesTableProps {
    page?: Page<File>
    path?: string
    embedded?: boolean
    onFileNavigation: (file: File) => void
    sortBy: SortBy[] // This might be removed
    fileOperations?: FileOperation[]
    onReloadRequested?: () => void
}

export interface ListDirectoryRequest {
    path: string
    page: number
    itemsPerPage: number
    order: SortOrder
    sortBy: SortBy
    attrs: FileResource[]
}

export const listDirectory = ({path, page, itemsPerPage, order, sortBy, attrs}: ListDirectoryRequest): APICallParameters<ListDirectoryRequest> => ({
    method: "GET",
    path: buildQueryString(
        "/files",
        {
            path,
            page,
            itemsPerPage,
            order,
            sortBy,
            attrs: attrs.join(",")
        }
    ),
    parameters: {path, page, itemsPerPage, order, sortBy, attrs},
    reloadId: Math.random()
});

const invertSortOrder = (order: SortOrder): SortOrder => {
    switch (order) {
        case SortOrder.ASCENDING:
            return SortOrder.DESCENDING;
        case SortOrder.DESCENDING:
            return SortOrder.ASCENDING;
    }
};

export const NewFilesTable: React.FunctionComponent<NewFilesTableProps> = props => {
    if (props.page !== undefined && props.path !== undefined) {
        throw Error("FilesTable contain both page and path properties, these are mutually exclusive.");
    }

    if (props.page === undefined && props.path === undefined) {
        throw Error("FilesTable must set either path or page property");
    }

    if (props.page !== undefined) {
        throw Error("Not yet implemented");
    }

    const isEmbedded = props.embedded !== false;
    const sortingSupported = props.path !== undefined;

    const [checkedFiles, setCheckedFiles] = useState<Set<string>>(new Set());
    const [fileBeingRenamed, setFileBeingRenamed] = useState<string | null>(null);

    const [page, setPageParams, pageParams] = useCloudAPI<Page<File>>(listDirectory({
        path: props.path!,
        itemsPerPage: 25,
        page: 0,
        sortBy: SortBy.PATH,
        order: SortOrder.ASCENDING,
        attrs: []
    }), emptyPage);

    const pageParameters: ListDirectoryRequest = pageParams.parameters!;

    useEffect(() => {
        setPageParams(listDirectory({...pageParameters, path: props.path!}));
    }, [props.path]);

    const setSorting = (sortBy: SortBy, order: SortOrder) => {
        if (!sortingSupported) return;
        setPageParams(
            listDirectory({
                ...pageParameters,
                sortBy,
                order,
                page: 0
            })
        );
    };

    const [commandState, invokeCommand] = useAsyncCommand();

    const sortingIconFor = (other: SortBy): "arrowUp" | "arrowDown" | undefined => {
        if (other === pageParameters.sortBy) {
            if (pageParameters.order === SortOrder.ASCENDING) {
                return "arrowUp";
            } else {
                return "arrowDown";
            }
        }

        return undefined;
    };

    const callbacks: FileOperationCallback = {
        invokeCommand,
        requestReload: () => {
            setFileBeingRenamed(null);
            setCheckedFiles(new Set());
            if (props.path !== undefined) {
                setPageParams(listDirectory(pageParameters));
            } else if (props.onReloadRequested !== undefined) {
                props.onReloadRequested();
            }
        },
        injectFiles: files => 42, // TODO
        startRenaming: file => setFileBeingRenamed(file.fileId!)
    };

    const fileOperations = props.fileOperations !== undefined ? props.fileOperations : defaultFileOperations;

    const setChecked = (updatedFiles: File[], checkStatus?: boolean) => {
        const checked = new Set(checkedFiles);
        if (checkStatus === false) {
            checked.clear();
        } else {
            updatedFiles.forEach(file => {
                const fileId = file.fileId!;
                if (checkStatus === true) {
                    checked.add(fileId);
                } else {
                    if (checked.has(fileId)) checked.delete(fileId);
                    else checked.add(fileId);
                }
            });
        }

        setCheckedFiles(checked);
    };

    const isMasterChecked = page.data.items.length > 0 && page.data.items.every(f => checkedFiles.has(f.fileId!));

    const onRenameFile = (key: number, file: File, name: string) => {
        if (key === KeyCode.ESC) {
            setFileBeingRenamed(null);
        } else if (key === KeyCode.ENTER) {
            const file = page.data.items.find(f => f.fileId == fileBeingRenamed);
            if (file === undefined) return;
            const fileNames = page.data.items.map(file => getFilenameFromPath(file.path));
            if (isInvalidPathName({path: name, filePaths: fileNames})) return;

            const fullPath = `${UF.addTrailingSlash(getParentPath(file.path))}${name}`;
            if (file.isMockFolder) {
                createFolder({
                    path: fullPath,
                    cloud: Cloud,
                    onSuccess: () => callbacks.requestReload()
                })
            } else {
                moveFile({
                    oldPath: file.path,
                    newPath: fullPath,
                    cloud: Cloud,
                    setLoading: () => 42, // TODO
                    onSuccess: () => callbacks.requestReload()
                });
            }
        }
    };

    // TODO We should always paginate here.
    return <Table>
        <TableHeader>
            <TableRow>
                <FileTableHeaderCell notSticky={isEmbedded} textAlign="left" width="99%">
                    <Flex
                        backgroundColor="white"
                        alignItems="center"
                        onClick={() => setSorting(SortBy.PATH, invertSortOrder(pageParameters.order))}
                    >
                        <Box mx="9px" onClick={e => e.stopPropagation()}>
                            {isEmbedded ? null :
                                <Label>
                                    <Checkbox
                                        data-tag="masterCheckbox"
                                        onClick={e => setChecked(page.data.items, !isMasterChecked)}
                                        checked={isMasterChecked}
                                        onChange={(e: React.SyntheticEvent) => e.stopPropagation()}
                                    />
                                </Label>
                            }
                        </Box>
                        <Arrow name={sortingIconFor(SortBy.PATH)}/>
                        <Box cursor="pointer">Filename</Box>
                    </Flex>
                </FileTableHeaderCell>
                <FileTableHeaderCell notSticky={isEmbedded} width={"3em"}>
                    <Flex backgroundColor="white"/>
                </FileTableHeaderCell>

                {props.sortBy.filter(it => it != null).map((column, i) => {
                    const isSortedBy = pageParameters.sortBy === column;

                    return <FileTableHeaderCell notSticky={isEmbedded} width="10rem">
                        <Flex backgroundColor="white" alignItems="center" cursor="pointer" justifyContent="left">
                            <Box onClick={() => setSorting(column, invertSortOrder(pageParameters.order))}
                            >
                                <Arrow name={sortingIconFor(column)}/>
                            </Box>
                            {!sortingSupported ?
                                <>UF.sortByToPrettierString(column)</>
                                :
                                <ClickableDropdown
                                    trigger={<TextSpan>{UF.sortByToPrettierString(column)}</TextSpan>}
                                    chevron>
                                    <Box ml="-16px" mr="-16px" pl="15px"
                                         hidden={pageParameters.order === SortOrder.ASCENDING && isSortedBy}
                                         onClick={() => setSorting(column, SortOrder.ASCENDING)}
                                    >
                                        {UF.prettierString(SortOrder.ASCENDING)}
                                    </Box>
                                    <Box ml="-16px" mr="-16px" pl="15px"
                                         onClick={() => setSorting(column, SortOrder.DESCENDING)}
                                         hidden={pageParameters.order === SortOrder.DESCENDING && isSortedBy}
                                    >
                                        {UF.prettierString(SortOrder.DESCENDING)}
                                    </Box>
                                    <Divider ml="-16px" mr="-16px"/>
                                    {Object.values(SortBy).map((sortByKey: SortBy, i) => (
                                        <Box ml="-16px" mr="-16px" pl="15px" key={i}
                                             onClick={() => setSorting(sortByKey, pageParameters.order)}
                                             hidden={sortByKey === pageParameters.sortBy || sortByKey === SortBy.PATH}
                                        >
                                            {UF.sortByToPrettierString(sortByKey)}
                                        </Box>
                                    ))}
                                </ClickableDropdown>
                            }
                        </Flex>
                    </FileTableHeaderCell>
                })}

                <FileTableHeaderCell
                    notSticky={isEmbedded}

                    // TODO This is not correct. We had some custom code before. This should be ported.
                    width={"7em"}
                >
                    <Flex/>
                </FileTableHeaderCell>
            </TableRow>
        </TableHeader>

        <TableBody>
            {page.data.items.map(file => (
                <TableRow highlighted={file.isChecked} key={file.fileId!} data-tag={"fileRow"}>
                    <TableCell>
                        <Flex flexDirection="row" alignItems="center" mx="9px">
                            {isEmbedded ? null :
                                <Box>
                                    <Label>
                                        <Checkbox
                                            checked={checkedFiles.has(file.fileId!)}
                                            onChange={e => e.stopPropagation()}
                                            onClick={() => setChecked([file])}/>
                                    </Label>
                                </Box>
                            }
                            <Box ml="5px" pr="5px"/>
                            <NameBox file={file} onRenameFile={onRenameFile} onNavigate={props.onFileNavigation}
                                     invokeCommand={invokeCommand} fileBeingRenamed={fileBeingRenamed}/>
                        </Flex>
                    </TableCell>

                    <TableCell>
                        <SensitivityIcon sensitivity={file.sensitivityLevel}/>
                    </TableCell>

                    {props.sortBy.filter(it => it != null).map((sC, i) => (
                        <TableCell key={i}>{sC ? UF.sortingColumnToValue(sC, file) : null}</TableCell>
                    ))}

                    <TableCell textAlign="center">
                        {
                            checkedFiles.size > 0 ? null :
                                fileOperations.length > 1 ?
                                    <ClickableDropdown
                                        width="175px"
                                        left="-160px"
                                        trigger={<Icon name="ellipsis" size="1em" rotation="90"/>}
                                    >
                                        <FileOperations
                                            files={[file]}
                                            fileOperations={fileOperations}
                                            As={Box}
                                            ml="-17px"
                                            mr="-17px"
                                            pl="15px"
                                            callback={callbacks}
                                        />
                                    </ClickableDropdown> :
                                    <FileOperations files={[file]} fileOperations={fileOperations} As={OutlineButton}
                                                    callback={callbacks}/>
                        }
                    </TableCell>
                </TableRow>)
            )}
        </TableBody>
    </Table>;
};

interface NameBoxProps {
    file: File
    onRenameFile: (keycode: number, file: File, value: string) => void
    onNavigate: (file: File) => void
    invokeCommand: (call: APICallParameters) => void
    fileBeingRenamed: string | null
}

const NameBox: React.FunctionComponent<NameBoxProps> = props => {
    const canNavigate = isDirectory({fileType: props.file.fileType});

    const icon = <Box mr="10px" cursor="inherit">
        <FileIcon
            fileIcon={UF.iconFromFilePath(props.file.path, props.file.fileType, Cloud.homeFolder)}
            size={38} link={props.file.link} shared={(props.file.acl != null ? props.file.acl.length : 0) > 0}
        />
    </Box>;

    if (props.file.fileId !== null && props.file.fileId === props.fileBeingRenamed) {
        return <>
            {icon}

            <Input
                placeholder={getFilenameFromPath(props.file.path)}
                defaultValue={getFilenameFromPath(props.file.path)}
                p="0"
                noBorder
                maxLength={1024}
                borderRadius="0px"
                type="text"
                width="100%"
                autoFocus
                data-tag="renameField"
                onKeyDown={e => {
                    if (!!props.onRenameFile) props.onRenameFile(e.keyCode, props.file, (e.target as HTMLInputElement).value)
                }}
            />

            <Icon
                size={"1em"}
                color="red"
                ml="9px"
                name="close"
                onClick={() => {
                    if (!!props.onRenameFile) props.onRenameFile(KeyCode.ESC, props.file, "")
                }}
            />
        </>;
    } else {
        return <>
            <Flex data-tag={"fileName"} flex="0 1 auto" minWidth="0"> {/* Prevent name overflow */}
                <Box title={replaceHomeFolder(props.file.path, Cloud.homeFolder)} width="100%">
                    <BaseLink href={"#"} onClick={e => {
                        e.preventDefault();
                        props.onNavigate(props.file);
                    }}>
                        <Flex alignItems="center">
                            {icon}

                            <Truncate
                                cursor={canNavigate ? "pointer" : undefined}
                                mr="5px"
                            >
                                {getFilenameFromPath(props.file.path)}
                            </Truncate>
                        </Flex>
                    </BaseLink>
                </Box>
            </Flex>

            <Icon
                data-tag="fileFavorite"
                size="1em" ml=".7em"
                color={props.file.favorited ? "blue" : "gray"}
                name={props.file.favorited ? "starFilled" : "starEmpty"}
                // onClick={() => props.onFavorite(props.file)}
                // TODO Handle on favorite
                hoverColor="blue"
            />
        </>;
    }
};

const notSticky = ({notSticky}: { notSticky?: boolean }): { position: "sticky" } | null =>
    notSticky ? null : {position: "sticky"};

const FileTableHeaderCell = styled(TableHeaderCell) <{ notSticky?: boolean }>`
    background-color: ${({theme}) => theme.colors.white};
    top: 144px; //topmenu + header size
    z-index: 10;
    ${notSticky}
`;

const SensitivityIcon = (props: { sensitivity: SensitivityLevelMap | null }) => {
    type IconDef = { color: string, text: string, shortText: string };
    let def: IconDef;

    switch (props.sensitivity) {
        case SensitivityLevelMap.CONFIDENTIAL:
            def = {color: Theme.colors.purple, text: "Confidential", shortText: "C"};
            break;
        case SensitivityLevelMap.SENSITIVE:
            def = {color: "#ff0004", text: "Sensitive", shortText: "S"};
            break;
        case SensitivityLevelMap.PRIVATE:
            def = {color: Theme.colors.midGray, text: "Private", shortText: "P"};
            break;
        default:
            def = {color: Theme.colors.midGray, text: "", shortText: ""};
            break;
    }

    const badge = <SensitivityBadge data-tag={"sensitivityBadge"} bg={def.color}>{def.shortText}</SensitivityBadge>;
    return <Tooltip right={"0"} top={"1"} mb="50px" trigger={badge}>{def.text}</Tooltip>;
};

const SensitivityBadge = styled.div<{ bg: string }>`
    content: '';
    height: 2em;
    width: 2em;
    display: flex;
    align-items: center;
    justify-content: center;
    border: 0.2em solid ${props => props.bg};
    border-radius: 100%;
`;

interface FileOperations extends SpaceProps {
    files: File[],
    fileOperations: FileOperation[],
    As: typeof OutlineButton | typeof Box | typeof Button
    callback: FileOperationCallback
}

const FileOperations = ({files, fileOperations, As, ...props}: FileOperations) => {
    if (files.length === 0 || fileOperations.length === 0) return null;

    return <>
        {fileOperations.map((fileOp: FileOperation, i: number) => {
            if (fileOp.disabled(files)) return null;

            return <As cursor="pointer" key={i} color={fileOp.color} alignItems="center"
                       onClick={() => fileOp.onClick(files, props.callback)} {...props}>
                {fileOp.icon ? <Icon size={16} mr="1em" name={fileOp.icon}/> : null}
                <span>{fileOp.text}</span>
            </As>;
        })}
    </>
};


export const NewFilesTableDemo: React.FunctionComponent = props => {
    const [path, setPath] = useState(Cloud.homeFolder);
    return <MainContainer main={
        <>
            <Box height={144} width={"100%"} backgroundColor={"white"}/>
            <NewFilesTable
                embedded={false}
                path={path}
                sortBy={[SortBy.FILE_TYPE, SortBy.SIZE]}
                onFileNavigation={file => {
                    console.log("Navigating to ", file);
                    setPath(file.path);
                }}
            />
        </>
    }/>;
};
