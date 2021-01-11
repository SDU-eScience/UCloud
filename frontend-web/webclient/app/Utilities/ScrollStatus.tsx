import * as React from "react";
import {useLayoutEffect, useMemo, useState} from "react";

interface ScrollStatusHook {
    isAtTheTop: boolean;
}

export function useScrollStatus<T extends HTMLElement>(
    containerRef: React.RefObject<T>,
    useParent = false,
    scrollThreshold = 20
): ScrollStatusHook {
    const [isAtTheTop, setIsAtTheTop] = useState(true);

    useLayoutEffect(() => {
        let container: HTMLElement | null = containerRef.current;
        if (container && useParent) container = container.parentElement;
        if (!container) return;

        const listener = () => {
            if (container!.scrollTop < scrollThreshold) setIsAtTheTop(true);
            else setIsAtTheTop(false);
        };

        container.addEventListener("scroll", listener)

        return () => {
            let newlyCaptured: HTMLElement | null = containerRef.current;
            if (newlyCaptured && useParent) newlyCaptured = newlyCaptured.parentElement;
            if (newlyCaptured) newlyCaptured.removeEventListener("scroll", listener);
        };
    }, [containerRef, useParent, scrollThreshold]);

    return useMemo(() => ({isAtTheTop}), [isAtTheTop]);
}