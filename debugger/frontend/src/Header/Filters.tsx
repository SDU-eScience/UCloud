import * as React from "react";
import {prettierString} from "../MainContent/MainContent";
import {DebugContextType} from "../WebSockets/Schema";
import {Dropdown} from "./Levels";

export function Filters({filters, setFilters}: {
    filters: Set<DebugContextType>;
    setFilters: React.Dispatch<React.SetStateAction<Set<DebugContextType>>>;
}): JSX.Element {
    const [localFilters, setLocalFilters] = React.useState<Set<DebugContextType>>(new Set());
    const joinedFilters = localFilters.size === 0 ? "None" : (localFilters.size === 1 ? prettierString(DebugContextType[[...localFilters][0]]) : `${localFilters.size} selected`);
    const toggleFilter = React.useCallback((type: DebugContextType) => {
        setLocalFilters(f => {
            if (f.has(type)) f.delete(type);
            else f.add(type);
            return new Set([...f]);
        });
    }, [setLocalFilters]);

    const applyFilters = React.useCallback(() => {
        setFilters(new Set(localFilters));
    }, [localFilters, setFilters]);

    let isDirty = false;
    if (localFilters.size !== filters.size) isDirty = true;
    else {
        for (const v of localFilters) {
            if (!filters.has(v)) {
                isDirty = true;
                break;
            }
        }
    }

    return <div className="mr-8px">
        <Dropdown trigger={
            <div className="header-dropdown flex vertically-centered">
                <span className="vertically-centered ml-12px">Filters: {joinedFilters}</span>
            </div>
        }>
            <div>
                <div><label><input type="checkbox" onChange={() => toggleFilter(DebugContextType.BACKGROUND_TASK)} checked={localFilters.has(DebugContextType.BACKGROUND_TASK)} />Background Tasks</label></div>
                <div><label><input type="checkbox" onChange={() => toggleFilter(DebugContextType.CLIENT_REQUEST)} checked={localFilters.has(DebugContextType.CLIENT_REQUEST)} />Client Request</label></div>
                <div><label><input type="checkbox" onChange={() => toggleFilter(DebugContextType.SERVER_REQUEST)} checked={localFilters.has(DebugContextType.SERVER_REQUEST)} />Server Request</label></div>
                <div><label><input type="checkbox" onChange={() => toggleFilter(DebugContextType.DATABASE_TRANSACTION)} checked={localFilters.has(DebugContextType.DATABASE_TRANSACTION)} />Database Transaction</label></div>
                <div><label><input type="checkbox" onChange={() => toggleFilter(DebugContextType.OTHER)} checked={localFilters.has(DebugContextType.OTHER)} />Other</label></div>
                <div className="flex mb-8px">
                    <button disabled={!isDirty} className="button filter-button" onClick={applyFilters}>
                        Apply filters
                    </button>
                </div>
            </div>
        </Dropdown>
    </div>
}
