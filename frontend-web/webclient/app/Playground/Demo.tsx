import * as React from "react";
import {useState} from "react";
import {MainContainer} from "@/ui-components";

const Demo: React.FunctionComponent = () => {
    const [page, setPage] = useState(0);

    return <MainContainer main={<>Testing 28</>} />;
};

export default Demo;
