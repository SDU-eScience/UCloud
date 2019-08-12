import * as React from "react";
import {Cloud} from "Authentication/SDUCloudObject";
import {capitalized, removeTrailingSlash} from "UtilityFunctions";
import {sizeToString, getParentPath, favoriteFile, reclassifyFile, fileTablePage, expandHomeFolder, directorySizeQuery} from "Utilities/FileUtilities";
import LoadingIcon from "LoadingIcon/LoadingIcon";
import {SensitivityLevel, ReduxObject, SensitivityLevelMap} from "DefaultObjects";
import {dateToString} from "Utilities/DateUtilities"
import {connect} from "react-redux";
import {updatePageTitle, setActivePage} from "Navigation/Redux/StatusActions";
import ShareList from "Shares/List";
import {File} from "Files";
import {ActivityFeed} from "Activity/Feed";
import {Dispatch} from "redux";
import {fetchFileStat, setLoading, fetchFileActivity, receiveFileStat, fileInfoError} from "./Redux/FileInfoActions";
import {Flex, Box, Icon, Card} from "ui-components";
import List from "ui-components/List";
import * as Heading from "ui-components/Heading"
import ClickableDropdown from "ui-components/ClickableDropdown";
import {FileInfoReduxObject} from "DefaultObjects";
import {MainContainer} from "MainContainer/MainContainer";
import {SidebarPages} from "ui-components/Sidebar";
import {BreadCrumbs} from "ui-components/Breadcrumbs";
import {History} from "history";

interface FileInfoOperations {
    updatePageTitle: () => void
    setLoading: (loading: boolean) => void
    fetchFileStat: (path: string) => void
    fetchFileActivity: (path: string) => void
    receiveFileStat: (file: File) => void
    setActivePage: () => void
}

interface FileInfo extends FileInfoReduxObject, FileInfoOperations {
    location: {pathname: string, search: string}
    history: History
}

function FileInfo(props: Readonly<FileInfo>) {
    const [size, setSize] = React.useState(0);

    React.useEffect(() => {
        props.setActivePage();
        props.updatePageTitle();
        const {file} = props;
        const filePath = path();
        if (!file || getParentPath(filePath) !== file.path) {
            props.setLoading(true);
            props.fetchFileStat(filePath);
            props.fetchFileActivity(filePath);
        }
    }, []);

    React.useEffect(() => {
        const fileType = props.file && props.file.fileType;
        if (fileType === "DIRECTORY") {
            Cloud.post<{size: number}>(directorySizeQuery, {paths: [file!.path]})
                 .then(it => setSize(it.response.size));
        }
    }, [props.file && props.file.size])

    const {loading, file, activity} = props;
    if (loading) return (<LoadingIcon size={18} />);
    if (!file) return null;
    return (
        <MainContainer
            header={
                <>
                    <Heading.h2><BreadCrumbs
                        currentPath={file.path}
                        navigate={path => props.history.push(fileTablePage(expandHomeFolder(path, Cloud.homeFolder)))}
                        homeFolder={Cloud.homeFolder}
                    /></Heading.h2>
                    <Heading.h5 color="gray">{capitalized(file.fileType)}</Heading.h5>
                </>}
            main={
                <>
                    <FileView
                        file={{...file, size}}
                        onFavorite={async () => props.receiveFileStat(await favoriteFile(file, Cloud))}
                        onReclassify={async sensitivity => {
                            props.receiveFileStat(await reclassifyFile({file, sensitivity, cloud: Cloud}));
                            props.fetchFileStat(path());
                        }} />
                    {activity.items.length ? (
                        <Flex flexDirection="row" justifyContent="center">
                            <Card mt="1em" maxWidth={"75%"} mb="1em" p="1em 1em 1em 1em" width="100%" height="auto">
                                <ActivityFeed activity={activity.items} />
                            </Card>
                        </Flex>) : null}
                    <Box width={0.88}><ShareList innerComponent byPath={file.path} /></Box>
                    {loading ? <LoadingIcon size={18} /> : null}
                </>}
        />
    );

    function queryParams(): URLSearchParams {
        return new URLSearchParams(props.location.search);
    }

    function path(): string {
        const param = queryParams().get("path");
        return param ? removeTrailingSlash(param) : "";
    }
}

const Attribute: React.FunctionComponent<{name: string, value?: string}> = props => (
    <Flex>
        <Box flexGrow={1}>{props.name}</Box>
        {props.value}{props.children}
    </Flex>
);

const AttributeBox: React.FunctionComponent = props => (
    <Box width="20em" m={16}>
        <List>
            {props.children}
        </List>
    </Box>
);

interface FileViewProps {
    file: File
    onFavorite: () => void
    onReclassify: (level: SensitivityLevelMap) => void
}
const FileView = ({file, onFavorite, onReclassify}: FileViewProps) =>
    !file ? null : (
        <Flex flexDirection="row" justifyContent="center" flexWrap="wrap">
            <AttributeBox>
                <Attribute name="Created at" value={dateToString(file.createdAt!)} />
                <Attribute name="Modified at" value={dateToString(file.modifiedAt!)} />
                <Attribute name="Favorite">
                    <Icon name={file.favorited ? "starFilled" : "starEmpty"}
                        onClick={() => onFavorite()}
                        color="blue"
                    />
                </Attribute>
                <Attribute name="Shared with" value={`${file.acl !== null ? file.acl.length : 0} people`} />
            </AttributeBox>
            <AttributeBox>
                <Attribute name="Sensitivity">
                    <ClickableDropdown
                        chevron
                        trigger={SensitivityLevel[!!file.ownSensitivityLevel ?
                            file.ownSensitivityLevel : SensitivityLevelMap.INHERIT]}
                        onChange={e => onReclassify(e as SensitivityLevelMap)}
                        options={
                            Object.keys(SensitivityLevel).map(key => ({
                                text: SensitivityLevel[key],
                                value: key
                            }))
                        } />
                </Attribute>
                <Attribute name="Computed sensitivity" value={SensitivityLevel[file.sensitivityLevel!]} />
                <Attribute name="Size" value={sizeToString(file.size!)} />
            </AttributeBox>
        </Flex >
    );

const mapStateToProps = ({fileInfo}: ReduxObject): FileInfoReduxObject & {favorite: boolean} => ({
    loading: fileInfo.loading,
    file: fileInfo.file,
    favorite: !!(fileInfo.file && fileInfo.file.favorited),
    activity: fileInfo.activity,
    error: fileInfo.error
});

const mapDispatchToProps = (dispatch: Dispatch): FileInfoOperations => ({
    updatePageTitle: () => dispatch(updatePageTitle("File Info")),
    setLoading: loading => dispatch(setLoading(loading)),
    fetchFileStat: async path => dispatch(await fetchFileStat(path)),
    fetchFileActivity: async path => dispatch(await fetchFileActivity(path)),
    receiveFileStat: file => dispatch(receiveFileStat(file)),
    setActivePage: () => dispatch(setActivePage(SidebarPages.Files))
});

export default connect<FileInfoReduxObject, FileInfoOperations>(mapStateToProps, mapDispatchToProps)(FileInfo);