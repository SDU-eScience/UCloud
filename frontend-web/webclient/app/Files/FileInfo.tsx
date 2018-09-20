import * as React from "react";
import { Cloud } from "Authentication/SDUCloudObject";
import { toLowerCaseAndCapitalize, removeTrailingSlash } from "UtilityFunctions";
import { favoriteFileFromPage, fileSizeToString, getParentPath, replaceHomeFolder } from "Utilities/FileUtilities";
import { updatePath, updateFiles, setLoading, fetchPageFromPath } from "./Redux/FilesActions";
import { DefaultLoading } from "LoadingIcon/LoadingIcon";
import { SensitivityLevel, emptyPage } from "DefaultObjects";
import { Container, Header, List, Card, Icon, Segment } from "semantic-ui-react";
import { dateToString } from "Utilities/DateUtilities"
import { connect } from "react-redux";
import { updatePageTitle } from "Navigation/Redux/StatusActions";
import { List as ShareList } from "Shares/List";
import { File, Annotation, SortOrder, SortBy, FileInfoProps } from "Files";
import { annotationToString } from "Utilities/FileUtilities";
import { ActivityFeed } from "Activity/Activity";

class FileInfo extends React.Component<FileInfoProps, any> {
    constructor(props) {
        super(props);
        this.state = { activity: emptyPage };
    }

    componentDidMount() {
        const { match, filesPath, dispatch, loading, page } = this.props;
        dispatch(updatePageTitle("File Info"));
        const path = match.params[0];
        // FIXME: Either move to promiseKeeper, 
        Cloud.get(`/activity/stream/by-path?path=${path}`).then(({ response }) => this.setState({ activity: response }));
        if (!(getParentPath(path) === filesPath)) {
            dispatch(setLoading(true));
            if (loading) return;
            dispatch(fetchPageFromPath(path, page.itemsPerPage, SortOrder.ASCENDING, SortBy.PATH));
            dispatch(updatePath(path));
        }
    }

    render() {
        const { match, page, dispatch, loading } = this.props;
        const file = page.items.find(file => file.path === removeTrailingSlash(match.params[0]));
        if (!file) { return (<DefaultLoading loading={true} />) }
        return (
            <Container className="container-margin" >
                <Header as="h2" icon textAlign="center">
                    <Header.Content content={replaceHomeFolder(file.path, Cloud.homeFolder)} />
                    <Header.Subheader content={toLowerCaseAndCapitalize(file.type)} />
                </Header>                               {/* MapDispatchToProps */}
                <FileView file={file} favorite={() => dispatch(updateFiles(favoriteFileFromPage(page, [file], Cloud)))} />
                <Segment><ActivityFeed activity={this.state.activity.items} /></Segment>
                {/* FIXME shares list by path does not work correctly, as it filters the retrieved list  */}
                <ShareList byPath={file.path} />
                <DefaultLoading loading={loading} />
            </Container >
        );
    };
}

const FileView = ({ file, favorite }: { file: File, favorite: () => void }) =>
    !file ? null : (
        <Card.Group>
            <Card>
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
            <Card>
                <Card.Content>
                    <List divided>
                        <List.Item className="itemPadding">
                            Sensitivity: <List.Content floated="right" content={SensitivityLevel[file.sensitivityLevel]} />
                        </List.Item>
                        <List.Item className="itemPadding">
                            Size: <List.Content floated="right" content={fileSizeToString(file.size)} />
                        </List.Item>
                        <List.Item className="itemPadding">
                            Shared with:
                                <List.Content floated="right">
                                {file.acl.length} {file.acl.length === 1 ? "person" : "people"}.
                                </List.Content>
                        </List.Item>
                    </List>
                </Card.Content>
            </Card>
            <Card>
                <Card.Content>
                    <Card.Header content="Annotations" />
                    <List divided>
                        {file.annotations.map((it, i) => (
                            <List.Item key={i} floated="right">
                                {annotationToString(it as Annotation)}
                            </List.Item>
                        ))}
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

export default connect(mapStateToProps)(FileInfo);