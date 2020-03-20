import {Client} from "Authentication/HttpClientInstance";
import {defaultFileOperations} from "Files/FileOperations";
import {FileTable} from "Files/FileTable";
import {defaultVirtualFolders} from "Files/VirtualFileTable";
import {setPrioritizedSearch, setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {setActivePage, setLoading, updatePageTitle} from "Navigation/Redux/StatusActions";
import * as React from "react";
import {useEffect} from "react";
import {connect} from "react-redux";
import {useHistory, useLocation} from "react-router";
import {Dispatch} from "redux";
import {SidebarPages} from "ui-components/Sidebar";
import {fileTablePage} from "Utilities/FileUtilities";
import {getQueryParamOrElse} from "Utilities/URIUtilities";

interface FilesOperations {
    onInit: () => void;
    refreshHook: (register: boolean, fn: () => void) => void;
    setLoading: (loading: boolean) => void;
}

const Files: React.FunctionComponent<FilesOperations> = props => {
    const history = useHistory();
    const location = useLocation();
    const urlPath = getQueryParamOrElse({history, location}, "path", Client.homeFolder);
    useEffect(() => props.onInit(), []);
    return (
        <FileTable
            {...defaultVirtualFolders()}
            fileOperations={defaultFileOperations.filter(it => it.text !== "View Parent")}
            embedded={false}
            onFileNavigation={navigation}
            path={urlPath}
            previewEnabled
            permissionAlertEnabled
            onLoadingState={props.setLoading}
            refreshHook={props.refreshHook}
        />
    );

    function navigation(path: string): void {
        history.push(fileTablePage(path));
    }
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

