import * as React from "react";
import {IconButton} from "@/ui-components/IconButton";
import {useCallback, useState} from "react";
import {IconName} from "@/ui-components/Icon";
import {ThemeColor} from "@/ui-components/theme";

export const CopyButton: React.FunctionComponent<{
    onClick: () => void;
    tooltip?: string;
}> = props => {
    const text = props.tooltip ?? "Copy to clipboard";
    return <IconActionButton tooltip={text} onClick={props.onClick} icon={"heroDocumentDuplicate"} />;
}

export const IconActionButton: React.FunctionComponent<{
    tooltip: string;
    onClick: () => void;
    icon: IconName;
    color?: ThemeColor;
}> = props => {
    const color = props.color ?? "textSecondary";
    const [iconState, setIconState] = useState<{ name: IconName, color: ThemeColor; }>(
        { name: props.icon, color: color }
    );
    const actualOnClick = useCallback(() => {
        let didCancel = false;
        props.onClick();
        setIconState({ name: "heroCheck", color: "successMain" });
        window.setTimeout(() => {
            if (!didCancel) setIconState({ name: props.icon, color: "textSecondary" });
        }, 1500);
        return () => {
            didCancel = true;
        }
    }, [props.onClick]);

    return <IconButton tooltip={props.tooltip} onClick={actualOnClick} icon={iconState.name} color={iconState.color}/>
}
