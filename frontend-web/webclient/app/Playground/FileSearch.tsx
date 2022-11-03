import * as React from "react";
import {WSFactory} from "@/Authentication/HttpClientInstance";
import {useCallback, useEffect, useState} from "react";
import {Box} from "@/ui-components";

export const FileSearch: React.FunctionComponent = () => {
    const [searchResults, setSearchResults] = useState<string[]>([]);
    useEffect(() => {
        let counter = 0;
        setInterval(() => {
            setSearchResults(prev => [...prev, `${counter++}`]);
        }, 250);
    }, []);

    return <Box>
        {searchResults.map(it =>
            <MyRowMemo key={it} data={it} />
        )}
    </Box>;
};


const MyRow: React.FunctionComponent<{data: string}> = (props) => {
    console.log("Rendering my row", props.data);
    const [myState, setMyState] = useState<string>("")
    const [selected, setSelected] = useState<boolean>(false)

    useEffect(() => {
        setMyState(`Hello ${props.data}`);
    }, [props.data]);

    const toggleSelected = useCallback((() => {
        setSelected(prev => !prev);
    }), []);

    return <Box backgroundColor={selected ? "blue" : "white"} onClick={toggleSelected}>{myState}</Box>;
};

const MyRowMemo = React.memo(MyRow);
