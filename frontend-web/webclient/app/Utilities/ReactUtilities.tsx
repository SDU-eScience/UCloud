import * as React from "react";
import { useCallback, useState, useEffect, useRef } from "react";

export function useForcedRender(): () => void {
    const [generation, setGeneration] = useState(0);
    const cb = useCallback(() => setGeneration(prev => prev + 1), []);
    return cb;
}

export function useDidUnmount(): React.RefObject<boolean> {
    const didUnmount = useRef(false);
    useEffect(() => {
        return () => {
            didUnmount.current = true;
        };
    }, []);
    return didUnmount;
}

