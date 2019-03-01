import * as React from "react";
import { Cloud } from "Authentication/SDUCloudObject";
import { capitalized, removeTrailingSlash, addTrailingSlash } from "UtilityFunctions";
import { sizeToString, getParentPath, replaceHomeFolder, isDirectory, favoriteFile, reclassifyFile } from "Utilities/FileUtilities";
import LoadingIcon from "LoadingIcon/LoadingIcon";
import { SensitivityLevel, ReduxObject, SensitivityLevelMap } from "DefaultObjects";
import { dateToString } from "Utilities/DateUtilities"
import { connect } from "react-redux";
import { updatePageTitle, setActivePage } from "Navigation/Redux/StatusActions";
import ShareList from "Shares/List";
import { File } from "Files";
import { ActivityFeed } from "Activity/Activity";
import { Dispatch } from "redux";
import { fetchFileStat, setLoading, fetchFileActivity, receiveFileStat, fileInfoError } from "./Redux/FileInfoActions";
import { Flex, Box, Icon, Card, Error } from "ui-components";
import List from "ui-components/List";
import * as Heading from "ui-components/Heading"
import ClickableDropdown from "ui-components/ClickableDropdown";
import { FileInfoReduxObject } from "DefaultObjects";
import { MainContainer } from "MainContainer/MainContainer";
import { SidebarPages } from "ui-components/Sidebar";

interface FileInfoOperations {
    updatePageTitle: () => void
    setLoading: (loading: boolean) => void
    fetchFileStat: (path: string) => void
    fetchFileActivity: (path: string) => void
    receiveFileStat: (file: File) => void
    setError: (err?: string) => void
    setActivePage: () => void
}

interface FileInfo extends FileInfoReduxObject, FileInfoOperations {
    location: { pathname: string, search: string }
}

class FileInfo extends React.Component<FileInfo> {
    constructor(props: Readonly<FileInfo>) {
        super(props);
        props.setActivePage();
    }

    get queryParams(): URLSearchParams {
        return new URLSearchParams(this.props.location.search);
    }

    get path(): string {
        const param = this.queryParams.get("path");
        return param ? removeTrailingSlash(param) : "";
    }

    componentDidMount() {
        const { loading, file, ...props } = this.props;
        props.updatePageTitle();
        if (!file || getParentPath(this.path) !== file.path) {
            props.setLoading(true);
            props.fetchFileStat(this.path);
            props.fetchFileActivity(this.path);
        }
    }

    render() {
        const { loading, file, activity, ...props } = this.props;
        if (loading) return (<LoadingIcon size={18} />);
        if (!file) return <MainContainer main={<Error error={this.props.error} clearError={() => this.props.setError()} />} />;
        const fileName = replaceHomeFolder(isDirectory(file) ? addTrailingSlash(file.path) : file.path, Cloud.homeFolder);
        return (
            <MainContainer
                header={
                    <>
                        <Heading.h2>{fileName}</Heading.h2>
                        <Heading.h5 color="gray">{capitalized(file.fileType)}</Heading.h5>
                    </>}
                main={
                    <>
                        <FileView
                            file={file}
                            onFavorite={async () => props.receiveFileStat(await favoriteFile(file, Cloud))}
                            onReclassify={async level => {
                                props.receiveFileStat(await reclassifyFile(file, level, Cloud))
                                props.fetchFileStat(this.path)
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
    };
}

const Attribute: React.FunctionComponent<{ name: string, value?: string }> = props => (
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
const FileView = ({ file, onFavorite, onReclassify }: FileViewProps) =>
    !file ? null : (
        <Flex flexDirection="row" justifyContent="center" flexWrap="wrap">
            <AttributeBox>
                <Attribute name="Created at" value={dateToString(file.createdAt)} />
                <Attribute name="Modified at" value={dateToString(file.modifiedAt)} />
                <Attribute name="Favorite">
                    <Icon name={file.favorited ? "starFilled" : "starEmpty"}
                        onClick={() => onFavorite()}
                        color="blue"
                    />
                </Attribute>
                <Attribute name="Shared with" value={`${file.acl !== undefined ? file.acl.length : 0} people`} />
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
                <Attribute name="Computed sensitivity" value={SensitivityLevel[file.sensitivityLevel]} />
                <Attribute name="Size" value={sizeToString(file.size)} />
            </AttributeBox>
        </Flex >
    );

const mapStateToProps = ({ fileInfo }: ReduxObject): FileInfoReduxObject & { favorite: boolean } => ({
    loading: fileInfo.loading,
    file: fileInfo.file,
    favorite: !!(fileInfo.file && fileInfo.file.favorited),
    activity: fileInfo.activity,
    error: fileInfo.error
});

const mapDispatchToProps = (dispatch: Dispatch): FileInfoOperations => ({
    updatePageTitle: () => dispatch(updatePageTitle("File Info")),
    setLoading: loading => dispatch(setLoading(loading)),
    setError: err => dispatch(fileInfoError(err)),
    fetchFileStat: async path => dispatch(await fetchFileStat(path)),
    fetchFileActivity: async path => dispatch(await fetchFileActivity(path)),
    receiveFileStat: file => dispatch(receiveFileStat(file)),
    setActivePage: () => dispatch(setActivePage(SidebarPages.Files))
});

export default connect<FileInfoReduxObject, FileInfoOperations>(mapStateToProps, mapDispatchToProps)(FileInfo);