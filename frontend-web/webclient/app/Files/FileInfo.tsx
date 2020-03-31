import {ActivityFeed} from "Activity/Feed";
import {Client} from "Authentication/HttpClientInstance";
import {FileInfoReduxObject} from "DefaultObjects";
import {ReduxObject, SensitivityLevel, SensitivityLevelMap} from "DefaultObjects";
import {File} from "Files";
import {History} from "history";
import LoadingIcon from "LoadingIcon/LoadingIcon";
import {MainContainer} from "MainContainer/MainContainer";
import {setActivePage, updatePageTitle} from "Navigation/Redux/StatusActions";
import * as React from "react";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import ShareList from "Shares/List";
import {Box, Button, Card, Flex, Icon} from "ui-components";
import {BreadCrumbs} from "ui-components/Breadcrumbs";
import ClickableDropdown from "ui-components/ClickableDropdown";
import * as Heading from "ui-components/Heading";
import List from "ui-components/List";
import {SidebarPages} from "ui-components/Sidebar";
import {dateToString} from "Utilities/DateUtilities";
import {
    directorySizeQuery,
    expandHomeOrProjectFolder,
    favoriteFile,
    fileTablePage,
    reclassifyFile,
    sizeToString,
    isFilePreviewSupported,
    isDirectory,
    statFileQuery
} from "Utilities/FileUtilities";
import {capitalized, removeTrailingSlash, errorMessageOrDefault} from "UtilityFunctions";
import FilePreview from "./FilePreview";
import {fetchFileActivity, setLoading} from "./Redux/FileInfoActions";
import {snackbarStore} from "Snackbar/SnackbarStore";

interface FileInfoOperations {
    updatePageTitle: () => void;
    setLoading: (loading: boolean) => void;
    fetchFileActivity: (path: string) => void;
    setActivePage: () => void;
}

interface FileInfo extends FileInfoReduxObject, FileInfoOperations {
    location: {pathname: string; search: string};
    history: History;
}

function FileInfo(props: Readonly<FileInfo>): JSX.Element | null {
    const [previewShown, setPreviewShown] = React.useState(false);
    const [file, setFile] = React.useState<File | undefined>(undefined);

    React.useEffect(() => {
        props.setActivePage();
        props.updatePageTitle();
    }, []);

    React.useEffect(() => {
        fetchFile();
    }, [path()]);

    const {loading, activity} = props;

    if (loading) return (<LoadingIcon size={18} />);
    if (!file) return null;
    return (
        <MainContainer
            header={(
                <Box>
                    <Flex>
                        <BreadCrumbs
                            currentPath={file.path}
                            navigate={p => props.history.push(fileTablePage(expandHomeOrProjectFolder(p, Client)))}
                            homeFolder={Client.homeFolder}
                            projectFolder={Client.projectFolder}
                        />
                    </Flex>
                    <Heading.h5 color="gray">{capitalized(file.fileType)}</Heading.h5>
                </Box>
            )}
            headerSize={140}
            main={(
                <>
                    <FileView file={file} />
                    <ShowFilePreview />

                    {activity.items.length ? (
                        <Flex flexDirection="row" justifyContent="center">
                            <Card mt="1em" maxWidth="75%" mb="1em" p="1em 1em 1em 1em" width="100%" height="auto">
                                <ActivityFeed activity={activity.items} />
                            </Card>
                        </Flex>
                    ) : null}
                    <Box width={0.88}><ShareList innerComponent byPath={file.path} /></Box>
                    {loading ? <LoadingIcon size={18} /> : null}
                </>
            )}
        />
    );

    function queryParams(): URLSearchParams {
        return new URLSearchParams(props.location.search);
    }

    function ShowFilePreview(): JSX.Element | null {
        if (file == null || !isFilePreviewSupported(file)) return null;
        if (previewShown) return <FilePreview isEmbedded />;
        return (
            <Flex justifyContent="center">
                <Button my="10px" onClick={() => setPreviewShown(true)}>Show preview</Button>
            </Flex>
        );
    }

    function path(): string {
        const param = queryParams().get("path");
        return param ? removeTrailingSlash(param) : "";
    }

    async function fetchFile(): Promise<void> {
        const filePath = path();
        props.setLoading(true);
        props.fetchFileActivity(filePath);
        try {
            const {response} = await Client.get<File>(statFileQuery(filePath));
            setFile(response);
            const fileType = response.fileType;
            if (isDirectory({fileType})) {
                const res = await Client.post<{size: number}>(directorySizeQuery, {paths: [filePath]});
                setFile({...response!, size: res.response.size});
            }
        } catch (e) {
            errorMessageOrDefault(e, "An error ocurred fetching file info.");
        } finally {
            props.setLoading(false);
        }
    }
}

const Attribute: React.FunctionComponent<{name: string; value?: string}> = props => (
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
    file: File;
}

function FileView({file}: FileViewProps): JSX.Element | null {
    const [favorite, setFavorite] = React.useState(file.favorited);
    const [sensitivity, setSensitivity] = React.useState(file.ownSensitivityLevel);

    return !file ? null : (
        <Flex flexDirection="row" justifyContent="center" flexWrap="wrap">
            <AttributeBox>
                <Attribute name="Modified at" value={dateToString(file.modifiedAt!)} />
                <Attribute name="Favorite">
                    <Icon
                        name={favorite ? "starFilled" : "starEmpty"}
                        onClick={toggleFavorite}
                        color="blue"
                    />
                </Attribute>
            </AttributeBox>
            <AttributeBox>
                <Attribute name="Sensitivity">
                    <ClickableDropdown
                        chevron
                        trigger={SensitivityLevel[sensitivity ?? SensitivityLevelMap.INHERIT]}
                        onChange={changeSensitivity}
                        options={
                            Object.keys(SensitivityLevel).map(key => ({
                                text: SensitivityLevel[key],
                                value: key
                            }))
                        }
                    />
                </Attribute>
                <Attribute name="Computed sensitivity" value={SensitivityLevel[file.sensitivityLevel!]} />
                <Attribute name="Size" value={sizeToString(file.size!)} />
            </AttributeBox>
        </Flex>
    );

    async function toggleFavorite(): Promise<void> {
        try {
            await favoriteFile(file, Client);
            setFavorite(fav => !fav);
        } catch (e) {
            snackbarStore.addFailure(errorMessageOrDefault(e, "Failed to toggle favorite status."));
        }
    }

    async function changeSensitivity(val: SensitivityLevelMap): Promise<void> {
        try {
            await reclassifyFile({file, sensitivity: val, client: Client});
            setSensitivity(val);
        } catch (e) {
            snackbarStore.addFailure(errorMessageOrDefault(e, "Failed to change sensitivity."));
        }
    }
}

const mapStateToProps = ({fileInfo}: ReduxObject): FileInfoReduxObject => ({
    loading: fileInfo.loading,
    activity: fileInfo.activity,
    error: fileInfo.error
});

const mapDispatchToProps = (dispatch: Dispatch): FileInfoOperations => ({
    updatePageTitle: () => dispatch(updatePageTitle("File Info")),
    setLoading: loading => dispatch(setLoading(loading)),
    fetchFileActivity: async path => dispatch(await fetchFileActivity(path)),
    setActivePage: () => dispatch(setActivePage(SidebarPages.Files))
});

export default connect(mapStateToProps, mapDispatchToProps)(FileInfo);
