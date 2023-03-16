import * as React from "react";
import {useDispatch, useSelector} from "react-redux";
import styled from "styled-components";

const PopIn = styled.div<{right: string;}>`
    transition: right 0.4s;
    height: 100vh;
    position: absolute;
    width: var(--popInWidth);
    top: 0;
    z-index: 120;
    right: ${p => p.right};
    background-color: var(--red);
`;

export function RightPopIn(): JSX.Element {
    const dispatch = useDispatch();

    const content = useSelector<ReduxObject, JSX.Element | null>(it => null);

    return <PopIn
        right={content != null ? "0" : "calc(0px - var(--popInWidth))"}
    >
        {content}
    </PopIn>
}