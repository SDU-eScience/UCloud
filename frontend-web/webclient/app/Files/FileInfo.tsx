import * as React from "react";
import { Cloud } from "Authentication/SDUCloudObject";
import { toLowerCaseAndCapitalize, removeTrailingSlash, addTrailingSlash } from "UtilityFunctions";
import { fileSizeToString, getParentPath, replaceHomeFolder, isDirectory, favoriteFile } from "Utilities/FileUtilities";
import { DefaultLoading } from "LoadingIcon/LoadingIcon";
import { SensitivityLevel, ReduxObject } from "DefaultObjects";
import { dateToString } from "Utilities/DateUtilities"
import { connect } from "react-redux";
import { updatePageTitle } from "Navigation/Redux/StatusActions";
import { List as ShareList } from "Shares/List";
import { File, FileInfoProps } from "Files";
import { ActivityFeed } from "Activity/Activity";
import { Dispatch } from "redux";
import { fetchFileStat, setLoading, fetchFileActivity, receiveFileStat } from "./Redux/FileInfoActions";
import { Flex, Box, Icon, Card } from "ui-components";
import List from "ui-components/List";
import * as Heading from "ui-components/Heading"

interface FileInfoOperations {
    updatePageTitle: () => void
    setLoading: (loading: boolean) => void
    fetchFileStat: (path: string) => void
    fetchFileActivity: (path: string) => void
    receiveFileStat: (file: File) => void
}

class FileInfo extends React.Component<FileInfoProps & FileInfoOperations & { location: { pathname: string, search: string } }> {
    constructor(props) {
        super(props);
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
        if (!file) { return (<DefaultLoading loading={true} />) }
        const fileName = replaceHomeFolder(isDirectory(file) ? addTrailingSlash(file.path) : file.path, Cloud.homeFolder);
        return (
            <Flex alignItems="center" flexDirection="column">
                <Box width={0.7}>
                    <Heading.h2>{fileName}</Heading.h2>
                    <Heading.h5 color="gray">{toLowerCaseAndCapitalize(file.fileType)}</Heading.h5>
                    <FileView file={file} favorite={() => props.receiveFileStat(favoriteFile(file, Cloud))} />
                    {activity.items.length ? (
                        <Card mt="1em" mb="1em" p="1em 1em 1em 1em" width="100%" height="auto">
                            <ActivityFeed activity={activity.items} />
                        </Card>) : null}
                    <ShareList byPath={file.path} />
                    <DefaultLoading loading={loading} />
                </Box>
            </Flex>
        );
    };
}

const FileView = ({ file, favorite }: { file: File, favorite: () => void }) =>
    !file ? null : (
        <Flex flexDirection="row" flexWrap="wrap">
            <Box ml="auto" />
            <Box width="20em">
                <List>
                    <Flex>
                        Created at:
                        <Box ml="auto" />
                        {dateToString(file.createdAt)}
                    </Flex>
                    <Flex>
                        Modified at:
                        <Box ml="auto" />
                        {dateToString(file.modifiedAt)}
                    </Flex>
                    <Flex>
                        Favorite file:
                        <Box ml="auto" />
                        <Icon name={file.favorited ? "star" : "star outline"}
                            onClick={() => favorite()}
                            color="blue"
                        />
                    </Flex>
                </List>
            </Box>
            <Box ml="auto" />
            <Box ml="auto" />
            <Box width="20em">
                <List>
                    <Flex>
                        Sensitivity: <Box ml="auto" />{SensitivityLevel[file.sensitivityLevel]}
                    </Flex>
                    <Flex>
                        Size: <Box ml="auto" />{fileSizeToString(file.size)}
                    </Flex>
                    {file.acl !== undefined ?
                        <Flex>
                            Shared with:
                            <Box ml="auto" />
                            {file.acl.length} {file.acl.length === 1 ? "person" : "people"}.
                        </Flex> : null}
                </List>
            </Box>
            <Box ml="auto" />
        </Flex >
    );

const mapStateToProps = ({ fileInfo }: ReduxObject): FileInfoProps => ({
    loading: fileInfo.loading,
    file: fileInfo.file,
    favorite: !!(fileInfo.file && fileInfo.file.favorited),
    activity: fileInfo.activity
});

const mapDispatchToProps = (dispatch: Dispatch): FileInfoOperations => ({
    updatePageTitle: () => dispatch(updatePageTitle("File Info")),
    setLoading: loading => dispatch(setLoading(loading)),
    fetchFileStat: async (path) => dispatch(await fetchFileStat(path)),
    fetchFileActivity: async (path) => dispatch(await fetchFileActivity(path)),
    receiveFileStat: (file) => dispatch(receiveFileStat(file))
});

export default connect(mapStateToProps, mapDispatchToProps)(FileInfo);