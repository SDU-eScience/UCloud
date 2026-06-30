import * as React from "react";
import {IconButton} from "@/ui-components/IconButton";
import {useCallback, useState} from "react";
import {IconName} from "@/ui-components/Icon";
import {ThemeColor} from "@/ui-components/theme";

export const CopyButton: React.FunctionComponent<{
    onClick: () => void;
}> = props => {
    const [icon, setIcon] = useState<{ name: IconName, color: ThemeColor; }>(
        { name: "heroDocumentDuplicate", color: "textSecondary" }
    );
    const actualOnClick = useCallback(() => {
        let didCancel = false;
        props.onClick();
        setIcon({ name: "heroCheck", color: "successMain" });
        window.setTimeout(() => {
            if (!didCancel) setIcon({ name: "heroDocumentDuplicate", color: "textSecondary" });
        }, 1500);
        return () => {
            didCancel = true;
        }
    }, [props.onClick]);

    return <IconButton tooltip="Copy to clipboard" onClick={actualOnClick} icon={icon.name} color={icon.color}/>
}
