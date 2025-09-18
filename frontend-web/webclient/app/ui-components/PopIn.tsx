import * as React from "react";
import {useDispatch, useSelector} from "react-redux";
import Flex from "./Flex";
import Icon from "./Icon";
import {injectStyle} from "@/Unstyled";
import {Spacer} from "./Spacer";
import {PayloadAction} from "@reduxjs/toolkit";

function PopIn({hasContent, children}: React.PropsWithChildren<{hasContent: boolean}>): React.ReactNode {
    return <div className={PopInClass} data-has-content={hasContent}>
        {hasContent ? children : null}
    </div >
}

const PopInClass = injectStyle("popin-class", k => `
    ${k} {
        transition: width 0.2s;
        height: 100vh;
        overflow-y: auto;
        position: absolute;
        top: 0;
        z-index: 120;
        box-shadow: var(--defaultShadow);
        right: 0;
        background-color: var(--backgroundDefault);
        width: 0;
        max-width: var(--popInWidth);
    }

    ${k}[data-has-content="true"] {
        width: var(--popInWidth);
        max-width: var(--popInWidth);
        padding: 4px 4px 4px 4px;
    }
`);

export function RightPopIn(): React.ReactNode {
    const dispatch = useDispatch();

    const content = useSelector<ReduxObject, PopInArgs | null>(it => it.popinChild);
    /* Alternatively, use React.portal */
    return <PopIn hasContent={content != null} >
        <Spacer
            mt="16px"
            left={<Icon color="textPrimary" cursor="pointer" pt="4px" pl="4px" hoverColor="textPrimary" name="close" onClick={() => dispatch(setPopInChild(null))} />}
            right={content?.onFullScreen ? <Icon color="textPrimary" cursor="pointer" pt="4px" pr="4px" hoverColor="textPrimary" name="heroArrowsPointingOut" onClick={() => {
                content?.onFullScreen?.();
                dispatch(setPopInChild(null));
            }} /> : null}

        />
        <Flex flexDirection="column" m="4px">{content?.el}</Flex>
    </PopIn>
}

export interface PopInArgs {
    el: React.ReactNode;
    onFullScreen?: () => void;
}

type SetPopInChildAction = PayloadAction<PopInArgs | null, "SET_POP_IN_CHILD">;
export function setPopInChild(args: PopInArgs | null): SetPopInChildAction {
    return {
        type: "SET_POP_IN_CHILD",
        payload: args
    };
}

export const popInReducer = (state: PopInArgs | null = null, action: SetPopInChildAction): PopInArgs | null => {
    switch (action.type) {
        case "SET_POP_IN_CHILD":
            return action.payload;
        default:
            return state;
    }
};