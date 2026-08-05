import * as React from "react";
import {useCallback, useEffect, useState} from "react";
import {useCloudCommand} from "@/Authentication/DataHook";
import {Icon} from "@/ui-components";
import {useDispatch} from "react-redux";
import {Application} from "@/Applications/AppStoreApi";
import * as AppStore from "@/Applications/AppStoreApi";
import {toggleAppFavorite} from "./Redux/Reducer";

export const FavoriteToggle: React.FunctionComponent<{
    application: Application
}> = ({application}) => {
    const [loading, invokeCommand] = useCloudCommand();
    const [favorite, setFavorite] = useState(application.favorite);
    const dispatch = useDispatch();
    useEffect(() => {
        setFavorite(application.favorite);
    }, [application]);

    const toggle = useCallback(async () => {
        if (!loading) {
            setFavorite(!favorite);
            dispatch(toggleAppFavorite({app: application, favorite: !favorite}));
            invokeCommand(AppStore.toggleStar({
                name: application.metadata.name
            }));
        }
    }, [loading, favorite]);

    return <Icon
        ml="4px"
        size={20}
        cursor="pointer"
        name={favorite ? "starFilled" : "starEmpty"}
        mb="2px"
        color={favorite ? "favoriteColor" : "favoriteColorEmpty"}
        onClick={toggle}
    />
}
