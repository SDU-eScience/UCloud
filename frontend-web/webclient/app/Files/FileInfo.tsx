import * as React from "react";
import { Cloud } from "Authentication/SDUCloudObject";
import { toLowerCaseAndCapitalize, removeTrailingSlash, addTrailingSlash } from "UtilityFunctions";
import { fileSizeToString, getParentPath, replaceHomeFolder, isDirectory, favoriteFile } from "Utilities/FileUtilities";
import { DefaultLoading } from "LoadingIcon/LoadingIcon";
import { SensitivityLevel, emptyPage, ReduxObject } from "DefaultObjects";
import { Container, Header, List, Card, Icon, Segment } from "semantic-ui-react";
import { dateToString } from "Utilities/DateUtilities"
import { connect } from "react-redux";
import { updatePageTitle } from "Navigation/Redux/StatusActions";
import { List as ShareList } from "Shares/List";
import { File, FileInfoProps } from "Files";
import { ActivityFeed } from "Activity/Activity";
import { Dispatch } from "redux";
import { fetchFileStat, setLoading, fetchFileActivity, receiveFileStat } from "./Redux/FileInfoActions";

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
            <Container className="container-margin">
                <Header as="h2" icon textAlign="center">
                    <Header.Content content={fileName} />
                    <Header.Subheader content={toLowerCaseAndCapitalize(file.fileType)} />
                </Header>
                <FileView file={file} favorite={async () => props.receiveFileStat(favoriteFile(file, Cloud))} />
                {activity.items.length ? (<Segment><ActivityFeed activity={activity.items} /></Segment>) : null}
                <ShareList byPath={file.path} />
                <DefaultLoading loading={loading} />
            </Container >
        );
    };
}

const FileView = ({ file, favorite }: { file: File, favorite: () => void }) =>
    !file ? null : (
        <Card.Group itemsPerRow={3}>
            <Card fluid>
                <Card.Content>
                    <List divided>
                        <List.Item className="itemPadding">
                            <List.Content floated="right">
                                {dateToString(file.createdAt)}
                            </List.Content>
                            Created at:
                            </List.Item>
                        <List.Item className="itemPadding">
                            <List.Content floated="right">
                                {dateToString(file.modifiedAt)}
                            </List.Content>
                            Modified at:
                        </List.Item>
                        <List.Item className="itemPadding">
                            <List.Content floated="right">
                                <Icon
                                    name={file.favorited ? "star" : "star outline"}
                                    onClick={() => favorite()}
                                    color="blue"
                                />
                            </List.Content>
                            Favorite file:
                        </List.Item>
                    </List>
                </Card.Content>
            </Card>
            <Card fluid>
                <Card.Content>
                    <List divided>
                        <List.Item className="itemPadding">
                            Sensitivity: <List.Content floated="right" content={SensitivityLevel[file.sensitivityLevel]} />
                        </List.Item>
                        <List.Item className="itemPadding">
                            Size: <List.Content floated="right" content={fileSizeToString(file.size)} />
                        </List.Item>
                        {file.acl !== undefined ? <List.Item className="itemPadding">
                            Shared with:
                                <List.Content floated="right">
                                {file.acl.length} {file.acl.length === 1 ? "person" : "people"}.
                                </List.Content>
                        </List.Item> : null}
                    </List>
                </Card.Content>
            </Card>
        </Card.Group>
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