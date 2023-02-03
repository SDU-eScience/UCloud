import * as React from "react";

export function SearchBar({setQuery}: {setQuery: (val: string) => void;}): JSX.Element {
    const ref = React.useRef<HTMLInputElement>(null);
    const onSubmit = React.useCallback((e: React.SyntheticEvent) => {
        e.preventDefault();
        e.stopPropagation();
        const value = ref.current?.value ?? "";
        setQuery(value);
    }, [setQuery]);
    return <form onSubmit={onSubmit}>
        <input ref={ref} placeholder="Search bar" className="header-input header-search" />
    </form>;
}