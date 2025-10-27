import {produce, WritableDraft} from "immer";
import {useCallback, useState} from "react";

export function useImmerState<S>(defaultValue: S): [S, (old: (mutator: WritableDraft<S>) => void) => void] {
    const [state, rawSetState] = useState<S>(defaultValue);
    const updateState = useCallback((mutator: (state: WritableDraft<S>) => void) => {
        rawSetState(prev => produce(prev, (draft: WritableDraft<S>) => {
            mutator(draft);
        }));
    }, []);
    return [state, updateState];
}
