import * as AppStore from "@/Applications/AppStoreApi";
import React, {useEffect, useState} from "react";
import Box from "@/ui-components/Box";
import {useIsLightThemeStored} from "@/ui-components/theme";

export const LogoWithText: React.FunctionComponent<{
    id: string | number;
    title: string;
    size: number;
    forceUnder?: boolean;
    hasText?: boolean;
}> = ({title, id, size, forceUnder, hasText}) => {
    const isLight = useIsLightThemeStored();
    const [element, setElement] = useState<string | null>(null);

    useEffect(() => {
        const newElement = typeof id === "string" ? AppStore.retrieveAppLogo({
            name: id,
            darkMode: !isLight, includeText: true, placeTextUnderLogo: forceUnder === true
        }) : AppStore.retrieveGroupLogo({
            id: id,
            darkMode: !isLight,
            includeText: true,
            placeTextUnderLogo: forceUnder === true,
        });

        setElement(newElement);
    }, [id, hasText, isLight]);

    if (element === null) {
        return <Box height={size}/>;
    } else {
        return <img
            src={element}
            alt={title}
            style={{
                maxHeight: forceUnder ? "calc(100% - 32px)" : hasText ? `${size * 1.50}px` : `${size}px`,
                maxWidth: "calc(100% - 32px)",
            }}
        />
    }
}
