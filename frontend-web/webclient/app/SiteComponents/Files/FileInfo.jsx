import React from "react";
import { Cloud } from "../../../authentication/SDUCloudObject";
import { getParentPath, updateSharingOfFile, shareFile, favorite, fileSizeToString, toLowerCaseAndCapitalize } from "../../UtilityFunctions";
import { fetchFiles, updatePath, updateFiles, setLoading } from "../../Actions/Files";
import { DefaultLoading } from "../LoadingIcon/LoadingIcon";
import { SensitivityLevel, RightsNameMap } from "../../DefaultObjects"
import { Container, Header, List, Button, Card, Icon, Rating } from "semantic-ui-react";
import swal from "sweetalert2";
import PropTypes from "prop-types";
import { connect } from "react-redux";
import { updatePageTitle } from "../../Actions/Status";
import { List as ShareList } from "../Shares/List";


const FileInfo = ({ dispatch, files, loading, ...props }) => {
    dispatch(updatePageTitle("File Info"));
    let file;
    const path = props.match.params[0];
    const parentPath = getParentPath(path);
    if (parentPath === props.filesPath) {
        const filePath = path.endsWith("/") ? path.slice(0, path.length - 1) : path;
        file = files.find(file => file.path === filePath);
    } else {
        dispatch(setLoading(true));
        dispatch(fetchFiles(parentPath));
        dispatch(updatePath(parentPath));
    }

    if (!file) { return (<DefaultLoading loading={true} />) }

    return (
        <Container className="container-margin">
            <Header as='h2' icon textAlign='center'>
                <Header.Content>
                    {file.path}
                </Header.Content>
                <Header.Subheader>
                    {toLowerCaseAndCapitalize(file.type)}
                </Header.Subheader>
            </Header>
            <FileView file={file} favorite={() => dispatch(updateFiles(favorite(files, file.path, Cloud)))} />
            {/* FIXME shares list by path does not work correctly, as it filters the retrieved list  */}
            <ShareList byPath={file.path} />
            <DefaultLoading loading={loading} />
        </Container>
    );
};

const FileView = ({ file, favorite }) =>
    !file ? null : (
        <Card.Group>
            <Card>
                <Card.Content>
                    <List divided>
                        <List.Item className="itemPadding">
                            <List.Content floated="right">
                                {new Date(file.createdAt).toLocaleString()}
                            </List.Content>
                            Created at:
                            </List.Item>
                        <List.Item className="itemPadding">
                            <List.Content floated="right">
                                {new Date(file.modifiedAt).toLocaleString()}
                            </List.Content>
                            Modified at:
                            </List.Item>
                        <List.Item className="itemPadding">
                            <List.Content floated="right">
                                <Rating rating={file.favorited ? 1 : 0} onClick={() => favorite(file.path)}
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
        </Card.Group>
    );

FileInfo.propTypes = {
    loading: PropTypes.bool.isRequired,
    files: PropTypes.array.isRequired,
    filesPath: PropTypes.string.isRequired,
    favoriteCount: PropTypes.number.isRequired,
};

const mapStateToProps = (state) => {
    const { loading, files, path } = state.files;
    return {
        loading,
        files,
        filesPath: path,
        favoriteCount: files.filter(file => file.favorited).length // Hack to ensure rerender
    }
}

export default connect(mapStateToProps)(FileInfo);