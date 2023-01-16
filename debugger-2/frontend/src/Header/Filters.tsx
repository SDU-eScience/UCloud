import * as React from "react";
import {DebugContextType} from "../WebSockets/Schema";
import {Dropdown} from "./Levels";

export function Filters({
    filters,
    setFilters
}: {
    filters: Set<DebugContextType>;
    setFilters: React.Dispatch<React.SetStateAction<Set<DebugContextType>>>;
}): JSX.Element {
    const joinedFilters = filters.size === 0 ? "None" : [...filters].join(", ");
    const toggleFilter = React.useCallback((type: DebugContextType) => {
        setFilters(f => {
            if (f.has(type)) f.add(type);
            else f.delete(type);
            return new Set(f);
        });
    }, [setFilters]);
    return <div style={{marginRight: "8px"}}><Dropdown trigger={
        <div className="header-dropdown flex vertically-centered">
            <span className="vertically-centered" style={{marginLeft: "12px"}}>Filters: {joinedFilters}</span>
        </div>
    }>
        <div>
            <label onClick={() => toggleFilter(DebugContextType.BACKGROUND_TASK)}><input type="checkbox" />Background Tasks</label>
            <label onClick={() => toggleFilter(DebugContextType.CLIENT_REQUEST)}><input type="checkbox" />Client Request</label>
            <label onClick={() => toggleFilter(DebugContextType.SERVER_REQUEST)}><input type="checkbox" />Server Request</label>
            <label onClick={() => toggleFilter(DebugContextType.DATABASE_TRANSACTION)}><input type="checkbox" />Database Transaction</label>
            <label onClick={() => toggleFilter(DebugContextType.OTHER)}><input type="checkbox" />Other</label>
        </div>
    </Dropdown></div>
}