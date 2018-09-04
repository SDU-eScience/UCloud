import * as React from "react";
import { Cloud } from "Authentication/SDUCloudObject";
import { toLowerCaseAndCapitalize, removeTrailingSlash } from "UtilityFunctions";
import { favoriteFileFromPage, fileSizeToString, getParentPath } from "Utilities/FileUtilities";
import { updatePath, updateFiles, setLoading, fetchPageFromPath } from "./Redux/FilesActions";
import { DefaultLoading } from "LoadingIcon/LoadingIcon";
import { SensitivityLevel } from "DefaultObjects";
import { Container, Header, List, Card, Icon } from "semantic-ui-react";
import { dateToString } from "Utilities/DateUtilities"
import { connect } from "react-redux";
import { updatePageTitle } from "Navigation/Redux/StatusActions";
import { List as ShareList } from "Shares/List";
import { File, Annotation, SortOrder, SortBy, FileInfoProps } from "Files";
import { annotationToString } from "Utilities/FileUtilities";

class FileInfo extends React.Component<FileInfoProps> {

    componentDidMount() {
        const { match, filesPath, dispatch, loading, page } = this.props;
        dispatch(updatePageTitle("File Info"));
        const path = match.params[0];
        console.log(getParentPath(path), filesPath);
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
                    <Header.Content>
                        {file.path}
                    </Header.Content>
                    <Header.Subheader>
                        {toLowerCaseAndCapitalize(file.type)}
                    </Header.Subheader>
                </Header>                               {/* MapDispatchToProps */}
                <FileView file={file} favorite={() => dispatch(updateFiles(favoriteFileFromPage(page, [file], Cloud)))} />
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
                            Sensitivity:
                                <List.Content floated="right">
                                {SensitivityLevel[file.sensitivityLevel]}
                            </List.Content>
                        </List.Item>
                        <List.Item className="itemPadding">
                            Size:
                                <List.Content floated="right">
                                {fileSizeToString(file.size)}
                            </List.Content>
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

const mapStateToProps = (state) => {
    const { loading, page, path, sortOrder, sortBy } = state.files;
    return {
        loading,
        page,
        sortBy,
        sortOrder,
        filesPath: path,
        favoriteCount: page.items.filter(file => file.favorited).length // Hack to ensure rerender
    }
}

export default connect(mapStateToProps)(FileInfo);