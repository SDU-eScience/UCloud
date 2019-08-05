import {RouteComponentProps} from "react-router";
import {useEffect} from "react";
import {getQueryParamOrElse} from "Utilities/URIUtilities";
import {Cloud} from "Authentication/SDUCloudObject";
import {Dispatch} from "redux";
import {setPrioritizedSearch, setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {setActivePage, setLoading, updatePageTitle} from "Navigation/Redux/StatusActions";
import {SidebarPages} from "ui-components/Sidebar";
import {connect} from "react-redux";
import {NewFilesTable} from "Files/NewFilesTable";
import * as React from "react";
import {fileTablePage} from "Utilities/FileUtilities";
import {defaultVirtualFolders} from "Files/VirtualFilesTable";

interface FilesProps extends RouteComponentProps {
    onInit: () => void
    refreshHook: (register: boolean, fn: () => void) => void,
    setLoading: (loading: boolean) => void
}

const Files: React.FunctionComponent<FilesProps> = props => {
    const urlPath = getQueryParamOrElse(props, "path", Cloud.homeFolder);
    useEffect(() => props.onInit(), []);

    return <NewFilesTable
        {...defaultVirtualFolders()}
        embedded={false}
        onFileNavigation={path => props.history.push(fileTablePage(path))}
        path={urlPath}
        onLoadingState={props.setLoading}
        refreshHook={props.refreshHook}
    />;
};

const mapDispatchToProps = (dispatch: Dispatch) => ({
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

