import * as React from "react";
import { useCallback, useState } from "react";

export function useForcedRender(): () => void {
    const [generation, setGeneration] = useState(0);
    const cb = useCallback(() => setGeneration(prev => prev + 1), []);
    return cb;
}
