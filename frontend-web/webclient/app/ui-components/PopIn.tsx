import * as React from "react";
import {useDispatch, useSelector} from "react-redux";
import Flex from "./Flex";
import Icon from "./Icon";
import theme from "./theme";
import {injectStyle} from "@/Unstyled";

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
        padding: 4px 4px 4px 4px;
        top: 0;
        z-index: 120;
        box-shadow: ${theme.shadows.sm};
        right: 0;
        background-color: var(--white);
        width: 0;
    }

    ${k}[data-has-content="true"] {
        width: var(--popInWidth);
    }
`);

export function RightPopIn(): JSX.Element {
    const dispatch = useDispatch();

    const content = useSelector<ReduxObject, JSX.Element | null>(it => it.popinChild);
    /* Alternatively, use React.portal */
    return <PopIn hasContent={content != null} >
        <Icon color="var(--black)" cursor="pointer" pt="4px" pl="4px" hoverColor="black" name="close" onClick={() => dispatch(setPopInChild(null))} />
        <Flex flexDirection="column" mx="4px" my="4px">{content}</Flex>
    </PopIn>
}

type SetPopInChildAction = PayloadAction<"SET_POP_IN_CHILD", JSX.Element | null>;
export function setPopInChild(el: JSX.Element | null): SetPopInChildAction {
    return {
        type: "SET_POP_IN_CHILD",
        payload: el
    };
}

export const popInReducer = (state: JSX.Element | null = null, action: SetPopInChildAction): JSX.Element | null => {
    switch (action.type) {
        case "SET_POP_IN_CHILD":
            return action.payload;
        default:
            return state;
    }
};