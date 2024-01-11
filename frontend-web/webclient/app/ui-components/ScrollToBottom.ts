import * as React from "react";
import {useEffect, useRef} from "react";

export function useScrollToBottom(ref: React.RefObject<HTMLDivElement>) {
    const wasAtBottom = useRef(true);
    useEffect(() => {
        const current = ref.current;
        if (!current) return;

        if (wasAtBottom.current) {
            current.scrollTop = current.scrollHeight;
        }

        const scrollHandler = () => {
            const newPos = Math.round(current.scrollTop + current.clientHeight);
            wasAtBottom.current = newPos === Math.round(current.scrollHeight);
        };

        current.addEventListener("scroll", scrollHandler, { passive: true });
        return () => {
            current.removeEventListener("scroll", scrollHandler);
        };
    });
}
