import * as React from "react";
import {useDispatch, useSelector} from "react-redux";
import Flex from "./Flex";
import Icon from "./Icon";
import theme from "./theme";
import {injectStyle} from "@/Unstyled";
import {Spacer} from "./Spacer";

function PopIn({hasContent, children}: React.PropsWithChildren<{hasContent: boolean}>): JSX.Element {
    return <div className={PopInClass} data-has-content={hasContent}>
        {hasContent ? children : null}
    </div >
}

const PopInClass = injectStyle("popin-class", k => `
    ${k} {
        transition: width 0.2s;
        height: 100vh;
        overflow-y: scroll;
        position: absolute;
        top: 0;
        z-index: 120;
        box-shadow: ${theme.shadows.sm};
        right: 0;
        background-color: var(--white);
        width: 0;
        max-width: var(--popInWidth);
    }

    ${k}[data-has-content="true"] {
        width: var(--popInWidth);
        max-width: var(--popInWidth);
        padding: 4px 4px 4px 4px;
    }
`);

export function RightPopIn(): JSX.Element {
    const dispatch = useDispatch();

    const content = useSelector<ReduxObject, PopInArgs | null>(it => it.popinChild);
    /* Alternatively, use React.portal */
    return <PopIn hasContent={content != null} >
        <Spacer
            left={<Icon color="var(--black)" cursor="pointer" pt="4px" pl="4px" hoverColor="black" name="close" onClick={() => dispatch(setPopInChild(null))} />}

            right={<Icon color="var(--black)" cursor="pointer" pt="4px" pr="4px" hoverColor="black" name="fullscreen" onClick={() => {
                content?.onFullScreen?.();
                dispatch(setPopInChild(null));
            }} />}

        />
        <Flex flexDirection="column" mx="4px" my="4px">{content?.el}</Flex>
    </PopIn>
}

export interface PopInArgs {
    el: JSX.Element;
    onFullScreen?: () => void;
}

type SetPopInChildAction = PayloadAction<"SET_POP_IN_CHILD", PopInArgs | null>;
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