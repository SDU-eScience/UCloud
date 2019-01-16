import * as React from "react";
import { File, FileOperation, PredicatedOperation } from "Files";
import { Table, TableBody, TableRow, TableCell, TableHeaderCell, TableHeader } from "ui-components/Table";
import { FilesTableProps, SortOrder, SortBy, ResponsiveTableColumnProps, FilesTableHeaderProps, SortByDropdownProps, ContextBarProps, ContextButtonsProps, Operation, FileOptionsProps, FilenameAndIconsProps } from "Files";
import ClickableDropdown from "ui-components/ClickableDropdown";
import { Icon, Box, OutlineButton, Flex, Divider, VerticalButtonGroup, Button, Label, Checkbox, Link, Input, Truncate } from "ui-components";
import * as UF from "UtilityFunctions"
import { Arrow, FileIcon } from "UtilityComponents";
import { TextSpan } from "ui-components/Text";
import { clearTrash, isDirectory, fileTablePage, previewSupportedExtension, getFilenameFromPath, isProject, toFileText, filePreviewPage } from "Utilities/FileUtilities";
import { Cloud } from "Authentication/SDUCloudObject";
import * as Heading from "ui-components/Heading"
import { KeyCode } from "DefaultObjects";
import styled from "styled-components";
import { SpaceProps } from "styled-system";

export const FilesTable = ({
    files, masterCheckbox, sortingIcon, sortFiles, onRenameFile, onCheckFile, sortingColumns, onDropdownSelect,
    fileOperations, sortOrder, onFavoriteFile, sortBy, customEntriesPerPage, onNavigationClick, notStickyHeader
}: FilesTableProps) => {
    const checkedFiles = files.filter(it => it.isChecked);
    const checkedCount = checkedFiles.length;
    return (
        <Table>
            <FilesTableHeader
                notStickyHeader={notStickyHeader}
                onDropdownSelect={onDropdownSelect}
                sortOrder={sortOrder}
                sortingColumns={sortingColumns}
                masterCheckbox={masterCheckbox}
                toSortingIcon={sortingIcon}
                sortFiles={sortFiles}
                sortBy={sortBy}
                customEntriesPerPage={customEntriesPerPage}
                customEntriesWidth={fileOperations.length > 1 ? "4em" : "7em"} //on modal thi is lenght=1
            >
                {/* FIXME: Figure out how to handle responsiveness for FileOperations */}
                {checkedCount === -1 ? <FileOperationsWrapper fileOperations={fileOperations} files={checkedFiles} /> : null}
            </FilesTableHeader>

            <TableBody>
                {files.map((file, i) => (
                    <TableRow highlighted={file.isChecked} key={i}>
                        <FilenameAndIcons
                            onNavigationClick={onNavigationClick}
                            file={file}
                            onFavoriteFile={onFavoriteFile}
                            hasCheckbox={masterCheckbox != null}
                            onRenameFile={onRenameFile}
                            onCheckFile={checked => onCheckFile(checked, file)}
                        />
                        {sortingColumns.filter(it => it != null).map((sC, i) => (
                            <TableCell key={i} >{sC ? UF.sortingColumnToValue(sC, file) : null}</TableCell>
                        ))}
                        <TableCell textAlign="center">
                            {checkedCount === 0 && !file.isMockFolder ? (<FileOperationsWrapper
                                files={[file]}
                                fileOperations={fileOperations}
                            />) : null}
                        </TableCell>
                    </TableRow>)
                )}
            </TableBody>
        </Table >
    );
}

interface FileOperationWrapper { files: File[], fileOperations: FileOperation[] }
const FileOperationsWrapper = ({ files, fileOperations }: FileOperationWrapper) => fileOperations.length > 1 ?
    <ClickableDropdown width="175px" trigger={<Icon name="ellipsis" size="1em" rotation="90" />}>
        <FileOperations files={files} fileOperations={fileOperations} As={Box} ml="-17px" mr="-17px" pl="15px" />
    </ClickableDropdown> :
    <FileOperations files={files} fileOperations={fileOperations} As={OutlineButton} />;

const notSticky = ({ notSticky }: { notSticky?: boolean }): { position: "sticky" } | null =>
    notSticky ? null : { position: "sticky" };

const FileTableHeaderCell = styled(TableHeaderCell) <{ notSticky?: boolean }>`
    top: 144px; //topmenu + header size
    z-index: 10;
    background-color: white;
    ${notSticky}
`;

const ResponsiveTableColumn = ({
    asDropdown,
    iconName,
    onSelect = (_1: SortOrder, _2: SortBy) => null,
    isSortedBy,
    currentSelection,
    sortOrder,
    notSticky
}: ResponsiveTableColumnProps) => (
        <FileTableHeaderCell notSticky={notSticky} width="10rem" >
            <Flex alignItems="center" justifyContent="left">
                <Arrow name={iconName} />
                <SortByDropdown
                    isSortedBy={isSortedBy}
                    onSelect={onSelect}
                    asDropdown={asDropdown}
                    currentSelection={currentSelection}
                    sortOrder={sortOrder} />
            </Flex>
        </FileTableHeaderCell>
    );

const toSortOrder = (sortBy: SortBy, lastSort: SortBy, sortOrder: SortOrder) =>
    sortBy === lastSort ? (sortOrder === SortOrder.ASCENDING ? SortOrder.DESCENDING : SortOrder.ASCENDING) : SortOrder.ASCENDING;

const FilesTableHeader = ({
    toSortingIcon = () => undefined,
    sortFiles = () => null,
    sortOrder,
    masterCheckbox,
    sortingColumns,
    onDropdownSelect,
    sortBy,
    customEntriesPerPage,
    customEntriesWidth,
    notStickyHeader,
    children
}: FilesTableHeaderProps) => (
        <TableHeader>
            <TableRow>
                <FileTableHeaderCell notSticky={notStickyHeader} textAlign="left" width="99%">
                    <Flex
                        alignItems="center"
                        onClick={() => sortFiles(toSortOrder(SortBy.PATH, sortBy, sortOrder), SortBy.PATH)}>
                        <Box mx="9px" onClick={e => e.stopPropagation()}>{masterCheckbox}</Box>
                        <Arrow name={toSortingIcon(SortBy.PATH)} />
                        <Box>Filename</Box>
                    </Flex>
                </FileTableHeaderCell>
                {sortingColumns.filter(it => it != null).map((sC, i) => (
                    <ResponsiveTableColumn
                        key={i}
                        isSortedBy={sC === sortBy}
                        onSelect={(sortOrder: SortOrder, sortBy: SortBy) => { if (!!onDropdownSelect) onDropdownSelect(sortOrder, sortBy, i) }}
                        currentSelection={sC!}
                        sortOrder={sortOrder}
                        asDropdown={!!onDropdownSelect}
                        iconName={toSortingIcon(sC!)}
                    />
                ))}
                <FileTableHeaderCell notSticky={notStickyHeader} width={customEntriesWidth}>
                    <Flex>{customEntriesPerPage}{children}</Flex>
                </FileTableHeaderCell>
            </TableRow>
        </TableHeader>
    );

const SortByDropdown = ({ currentSelection, sortOrder, onSelect, asDropdown, isSortedBy }: SortByDropdownProps) => asDropdown ? (
    <ClickableDropdown trigger={<TextSpan>{UF.sortByToPrettierString(currentSelection)}</TextSpan>} chevron>
        <Box ml="-16px" mr="-16px" pl="15px"
            hidden={sortOrder === SortOrder.ASCENDING && isSortedBy}
            onClick={() => onSelect(SortOrder.ASCENDING, currentSelection)}
        >
            {UF.prettierString(SortOrder.ASCENDING)}
        </Box>
        <Box ml="-16px" mr="-16px" pl="15px"
            onClick={() => onSelect(SortOrder.DESCENDING, currentSelection)}
            hidden={sortOrder === SortOrder.DESCENDING && isSortedBy}
        >
            {UF.prettierString(SortOrder.DESCENDING)}
        </Box>
        <Divider ml="-16px" mr="-16px" />
        {Object.keys(SortBy).map((sortByKey: SortBy, i) => (
            <Box ml="-16px" mr="-16px" pl="15px" key={i}
                onClick={() => onSelect(sortOrder, sortByKey)}
                hidden={sortByKey === currentSelection || sortByKey === SortBy.PATH}
            >
                {UF.sortByToPrettierString(sortByKey)}
            </Box>
        ))}
    </ClickableDropdown>) : <>{UF.prettierString(currentSelection)}</>;

export const ContextBar = ({ files, ...props }: ContextBarProps) => (
    <Box>
        <ContextButtons toHome={props.toHome} inTrashFolder={props.inTrashFolder} showUploader={props.showUploader} createFolder={props.createFolder} />
        <FileOptions files={files} {...props} />
    </Box>
);

export const ContextButtons = ({ createFolder, showUploader, inTrashFolder, toHome }: ContextButtonsProps) => (
    <VerticalButtonGroup>
        {!inTrashFolder ?
            <><Button color="blue" onClick={showUploader}>Upload Files</Button>
                <OutlineButton color="blue" onClick={createFolder}>New folder</OutlineButton></> :
            <Button color="red" onClick={() => clearTrash(Cloud, () => toHome())}>
                Empty trash
            </Button>}
    </VerticalButtonGroup>
);

interface PredicatedCheckbox { predicate: boolean, checked: boolean, onClick: (e: any) => void }
const PredicatedCheckbox = ({ predicate, checked, onClick }: PredicatedCheckbox) => predicate ? (
    <Box><Label><Checkbox checked={checked} onClick={onClick} onChange={e => e.stopPropagation()} /></Label></Box>
) : null;

const PredicatedFavorite = ({ predicate, item, onClick }) => predicate ? (
    <Icon
        size="1em" ml=".7em"
        color={item.favorited ? "blue" : "gray"}
        name={item.favorited ? "starFilled" : "starEmpty"}
        onClick={() => onClick([item])}
        hoverColor="blue"
    />
) : null;


interface Groupicon { isProject: boolean }
const GroupIcon = ({ isProject }: Groupicon) => isProject ? (<Icon name="projects" ml=".7em" size="1em" />) : null;

const FileLink = ({ file, children }: { file: File, children: any }) => {
    if (isDirectory(file)) {
        return (<Link to={fileTablePage(file.path)}>{children}</Link>);
    } else if (previewSupportedExtension(file.path)) {
        return (<Link to={filePreviewPage(file.path)}>{children}</Link>);
    } else {
        return (<>{children}</>);
    }
}

function FilenameAndIcons({ file, size = "big", onRenameFile = () => null, onCheckFile = () => null, hasCheckbox = false, onFavoriteFile, ...props }: FilenameAndIconsProps) {
    const fileName = getFilenameFromPath(file.path);
    const checkbox = <PredicatedCheckbox predicate={hasCheckbox} checked={!!file.isChecked} onClick={e => onCheckFile(e.target.checked)} />
    const iconType = UF.iconFromFilePath(file.path, file.fileType, Cloud.homeFolder);
    const cursor = isDirectory(file) && !file.path.endsWith("/.") ? "pointer" : undefined;
    const icon = (
        <Box mr="10px" cursor="inherit">
            <FileIcon
                fileIcon={iconType}
                size={size} link={file.link} shared={(file.acl !== undefined ? file.acl.length : 0) > 0}
            />
        </Box>
    );
    const renameBox = (<>
        {icon}
        <Input
            placeholder={getFilenameFromPath(file.path)}
            defaultValue={getFilenameFromPath(file.path)}
            p="0"
            noBorder
            maxLength={1024}
            borderRadius="0px"
            type="text"
            width="100%"
            autoFocus
            onKeyDown={e => { if (!!onRenameFile) onRenameFile(e.keyCode, file, (e.target as any).value) }}
        />
        <Icon size={"1em"} color="red" ml="9px" name="close" onClick={() => onRenameFile(KeyCode.ESC, file, "")} />
    </>);

    const nameLink = !!props.onNavigationClick ?
        <Flex onClick={() => isDirectory(file) ? props.onNavigationClick!(file.path) : null}
            alignItems="center"
            width="100%"
        >
            {icon}
            <Truncate cursor={cursor} mr="5px">{fileName}</Truncate>
        </Flex>
        :
        <Box width="100%" >
            <FileLink file={file}>
                <Flex alignItems="center">
                    {icon}
                    <Truncate cursor={cursor} mr="5px">{fileName}</Truncate>
                </Flex>
            </FileLink>
        </Box>;

    const fileBox = (<>
        <Flex flex="0 1 auto" minWidth="0"> {/* Prevent name overflow */}
            {nameLink}
        </Flex>
        <GroupIcon isProject={isProject(file)} />
        <PredicatedFavorite predicate={!!onFavoriteFile} item={file} onClick={onFavoriteFile} />
    </>);

    return <TableCell>
        <Flex flexDirection="row" alignItems="center" mx="9px">
            {checkbox}
            <Box ml="5px" pr="5px" />
            {file.beingRenamed ? renameBox : fileBox}
        </Flex>
    </TableCell>
};

const FileOptions = ({ files, fileOperations }: FileOptionsProps) => files.length ? (
    <Box mb="13px">
        <Heading.h5 pl="20px" pt="5px" pb="8px">{toFileText(files)}</Heading.h5>
        <FileOperations files={files} fileOperations={fileOperations} As={Box} pl="20px" />
    </Box>
) : null;

interface FileOperations extends SpaceProps { files: File[], fileOperations: FileOperation[], As: any }
export const FileOperations = ({ files, fileOperations, As, ...props }/* :FileOperations */) => files.length && fileOperations.length ?
    fileOperations.map((fileOp, i) => {
        let operation = fileOp;
        if ("predicate" in operation) {
            operation = (fileOp as PredicatedOperation).predicate(files, Cloud) ? operation.onTrue : operation.onFalse;
        }
        operation = operation as Operation;
        return !operation.disabled(files, Cloud) ? (
            <As cursor="pointer" key={i} onClick={() => (operation as Operation).onClick(files, Cloud)} {...props}>
                {operation.icon ? <Icon size={16} mr="1em" color={operation.color} name={operation.icon} /> : null}
                <span>{operation.text}</span>
            </As>
        ) : null;
    }) : null;