import {RouteComponentProps} from "react-router";
import {useEffect} from "react";
import {getQueryParamOrElse} from "Utilities/URIUtilities";
import {Cloud} from "Authentication/SDUCloudObject";
import {Dispatch} from "redux";
import {setPrioritizedSearch, setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {setActivePage, setLoading, updatePageTitle} from "Navigation/Redux/StatusActions";
import {SidebarPages} from "ui-components/Sidebar";
import {connect} from "react-redux";
import {FileTable} from "Files/FileTable";
import * as React from "react";
import {fileTablePage} from "Utilities/FileUtilities";
import {defaultVirtualFolders} from "Files/VirtualFileTable"
import {defaultFileOperations} from "Files/FileOperations";

interface FilesOperations {
    onInit: () => void
    refreshHook: (register: boolean, fn: () => void) => void,
    setLoading: (loading: boolean) => void
}

type FilesProps = RouteComponentProps & FilesOperations;

const Files: React.FunctionComponent<FilesProps> = props => {
    const urlPath = getQueryParamOrElse(props, "path", Cloud.homeFolder);
    useEffect(() => props.onInit(), []);

    return <FileTable
        {...defaultVirtualFolders()}
        fileOperations={defaultFileOperations.filter(it => it.text !== "View Parent")}
        embedded={false}
        onFileNavigation={path => props.history.push(fileTablePage(path))}
        path={urlPath}
        onLoadingState={props.setLoading}
        refreshHook={props.refreshHook}
    />;
};

const mapDispatchToProps = (dispatch: Dispatch): FilesOperations => ({
    onInit: () => {
        dispatch(setPrioritizedSearch("files"));
        dispatch(updatePageTitle("Files"));
        dispatch(setActivePage(SidebarPages.Files));
    },
    refreshHook: (register, fn) => {
        if (register) {
            dispatch(setRefreshFunction(fn));
        } else {
            dispatch(setRefreshFunction());
        }
    },
    setLoading: loading => dispatch(setLoading(loading))
});

export default connect(null, mapDispatchToProps)(Files);

