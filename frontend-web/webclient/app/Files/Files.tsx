import {Client} from "Authentication/HttpClientInstance";
import {defaultFileOperations} from "Files/FileOperations";
import {FileTable} from "Files/FileTable";
import {defaultVirtualFolders} from "Files/VirtualFileTable";
import {setPrioritizedSearch, setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {setLoading, useTitle} from "Navigation/Redux/StatusActions";
import * as React from "react";
import {connect} from "react-redux";
import {useHistory, useLocation} from "react-router";
import {Dispatch} from "redux";
import {SidebarPages, useSidebarPage} from "ui-components/Sidebar";
import {fileTablePage, pathComponents} from "Utilities/FileUtilities";
import {getQueryParamOrElse} from "Utilities/URIUtilities";
import {dispatchSetProjectAction} from "Project/Redux";

interface FilesOperations {
    onInit: () => void;
    refreshHook: (register: boolean, fn: () => void) => void;
    setLoading: (loading: boolean) => void;
    setActiveProject: (project?: string) => void;
}

const Files: React.FunctionComponent<FilesOperations> = props => {
    const history = useHistory();
    const location = useLocation();
    const urlPath = getQueryParamOrElse({history, location}, "path", Client.homeFolder);
    React.useEffect(() => {
        props.onInit();
    }, []);
    useTitle("Files");
    useSidebarPage(SidebarPages.Files);
    const components = pathComponents(urlPath);
    if (components.length >= 2 && components[0] === "projects" && components[1] !== Client.projectId) {
        props.setActiveProject(components[1]);
    } else if (components.length >= 2 && components[0] === "home" && Client.hasActiveProject) {
        props.setActiveProject(undefined);
    }
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
    onInit: () => dispatch(setPrioritizedSearch("files")),
    refreshHook: (register, fn) => {
        if (register) {
            dispatch(setRefreshFunction(fn));
        } else {
            dispatch(setRefreshFunction());
        }
    },
    setActiveProject: project => dispatchSetProjectAction(dispatch, project),
    setLoading: loading => dispatch(setLoading(loading))
});

export default connect(null, mapDispatchToProps)(Files);

