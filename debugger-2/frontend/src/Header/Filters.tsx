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
    const joinedFilters = filters.size === 0 ? "None" : (filters.size === 1 ? DebugContextType[[...filters][0]] : `${filters.size} selected`);
    const toggleFilter = React.useCallback((type: DebugContextType) => {
        setFilters(f => {
            if (f.has(type)) f.delete(type);
            else f.add(type);
            return new Set([...f]);
        });
    }, [setFilters]);
    console.log(filters)
    return <div style={{marginRight: "8px"}}>
        <Dropdown trigger={
            <div className="header-dropdown flex vertically-centered">
                <span className="vertically-centered" style={{marginLeft: "12px"}}>Filters: {joinedFilters}</span>
            </div>
        }>
            <div>
                <div><label><input type="checkbox" onChange={e => toggleFilter(DebugContextType.BACKGROUND_TASK)} checked={filters.has(DebugContextType.BACKGROUND_TASK)} />Background Tasks</label></div>
                <div><label><input type="checkbox" onChange={() => toggleFilter(DebugContextType.CLIENT_REQUEST)} checked={filters.has(DebugContextType.CLIENT_REQUEST)}/>Client Request</label></div>
                <div><label><input type="checkbox" onChange={() => toggleFilter(DebugContextType.SERVER_REQUEST)} checked={filters.has(DebugContextType.SERVER_REQUEST)}/>Server Request</label></div>
                <div><label><input type="checkbox" onChange={() => toggleFilter(DebugContextType.DATABASE_TRANSACTION)} checked={filters.has(DebugContextType.DATABASE_TRANSACTION)}/>Database Transaction</label></div>
                <div><label><input type="checkbox" onChange={() => toggleFilter(DebugContextType.OTHER)} checked={filters.has(DebugContextType.OTHER)}/>Other</label></div>
            </div>
        </Dropdown>
    </div>
}