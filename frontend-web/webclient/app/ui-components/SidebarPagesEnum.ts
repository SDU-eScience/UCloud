import {setActivePage} from "@/Navigation/Redux/StatusActions";
import {useEffect} from "react";
import {useDispatch} from "react-redux";

export const enum SidebarPages {
    Files,
    Shares,
    Projects,
    AppStore,
    Resources,
    Runs,
    Publish,
    Activity,
    Admin,
    None
}

export function useSidebarPage(page: SidebarPages): void {
    const dispatch = useDispatch();
    useEffect(() => {
        dispatch(setActivePage(page));
        return () => {
            dispatch(setActivePage(SidebarPages.None));
        };
    }, [page]);
}