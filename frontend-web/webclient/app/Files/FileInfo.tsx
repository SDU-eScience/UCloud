import * as React from "react";
import { Cloud } from "Authentication/SDUCloudObject";
import { toLowerCaseAndCapitalize, removeTrailingSlash, addTrailingSlash } from "UtilityFunctions";
import { favoriteFileFromPage, fileSizeToString, getParentPath, replaceHomeFolder, isDirectory } from "Utilities/FileUtilities";
import { updatePath, updateFiles, setLoading, fetchPageFromPath } from "./Redux/FilesActions";
import { DefaultLoading } from "LoadingIcon/LoadingIcon";
import { SensitivityLevel, emptyPage } from "DefaultObjects";
import { Container, Header, List, Card, Icon, Segment } from "semantic-ui-react";
import { dateToString } from "Utilities/DateUtilities"
import { connect } from "react-redux";
import { updatePageTitle } from "Navigation/Redux/StatusActions";
import { List as ShareList } from "Shares/List";
import { File, SortOrder, SortBy, FileInfoProps, FileInfoState } from "Files";
import { ActivityFeed } from "Activity/Activity";
import { Dispatch } from "redux";
import { Page } from "Types";

interface FileInfoOperations {
    updatePageTitle: () => void
    setLoading: (loading: boolean) => void
    updatePath: (path: string) => void
    fetchPageFromPath: (path: string, itemsPerPage: number, sortOrder: SortOrder, sortBy: SortBy) => void
    updateFiles: (page: Page<File>) => void
}

class FileInfo extends React.Component<FileInfoProps & FileInfoOperations, FileInfoState> {
    constructor(props) {
        super(props);
        this.state = { activity: emptyPage };
    }

    get queryParams(): URLSearchParams {
        return new URLSearchParams(this.props.location.search);
    }

    get path(): string { 
        const param = this.queryParams.get("path"); 
        console.log(param);
        return param ? removeTrailingSlash(param) : "";
    }

    componentDidMount() {
        const { filesPath, loading, page } = this.props;
        this.props.updatePageTitle();
        // FIXME: Either move to promiseKeeper, or redux store
        Cloud.get(`/activity/stream/by-path?path=${encodeURI(this.path)}`).then(({ response }) => this.setState({ activity: response }));

        if (!(getParentPath(this.path) === filesPath)) {
            this.props.setLoading(true);
            if (loading) return;
            this.props.fetchPageFromPath(this.path, page.itemsPerPage, SortOrder.ASCENDING, SortBy.PATH);
            this.props.updatePath(this.path);
        }
    }

    render() {
        const { page, loading } = this.props;
        const file = page.items.find(file => file.path === removeTrailingSlash(this.path));
        if (!file) { return (<DefaultLoading loading={true} />) }
        const fileName = replaceHomeFolder(isDirectory(file) ? addTrailingSlash(file.path) : file.path, Cloud.homeFolder);
        return (
            <Container className="container-margin">
                <Header as="h2" icon textAlign="center">
                    <Header.Content content={fileName} />
                    <Header.Subheader content={toLowerCaseAndCapitalize(file.fileType)} />
                </Header>
                <FileView file={file} favorite={() => this.props.updateFiles(favoriteFileFromPage(page, [file], Cloud))} />
                {this.state.activity.items.length ? (<Segment><ActivityFeed activity={this.state.activity.items} /></Segment>) : null}
                {/* FIXME shares list by path does not work correctly, as it filters the retrieved list  */}
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

const mapStateToProps = ({ files }) => ({
    loading: files.loading,
    page: files.page,
    sortBy: files.sortBy,
    sortOrder: files.sortOrder,
    filesPath: files.path,
    favoriteCount: files.page.items.filter(file => file.favorited).length // Hack to ensure rerender
});

const mapDispatchToProps = (dispatch: Dispatch): FileInfoOperations => ({
    updatePageTitle: () => dispatch(updatePageTitle("File Info")),
    setLoading: (loading: boolean) => setLoading(loading),
    updatePath: (path: string) => dispatch(updatePath(path)),
    fetchPageFromPath: async (path: string, itemsPerPage: number, sortOrder: SortOrder, sortBy: SortBy) =>
        dispatch(await fetchPageFromPath(path, itemsPerPage, sortOrder, sortBy)),
    updateFiles: (page: Page<File>) => dispatch(updateFiles(page))
});

export default connect(mapStateToProps, mapDispatchToProps)(FileInfo);