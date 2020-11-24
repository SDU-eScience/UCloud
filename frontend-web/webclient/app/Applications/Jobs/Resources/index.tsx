import {useCallback, useRef, useState} from "react";

export interface ResourceHook {
    onAdd: () => void;
    onRemove: (id: string) => void;
    ids: string[];
}

export function useResource(ns: string): ResourceHook {
    const counter = useRef<number>(0);
    const [ids, setIds] = useState<string[]>([]);

    const onAdd = useCallback(() => {
        setIds([...ids, `${ns}${counter.current++}`]);
    }, [ids]);

    const onRemove = useCallback((id: string) => {
        setIds(ids.filter(it => it !== id));
    }, [ids]);

    return {onAdd, onRemove, ids};
}
