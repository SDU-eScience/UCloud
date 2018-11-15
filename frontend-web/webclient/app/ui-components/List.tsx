import styled from "styled-components";
import Box from "./Box";

const List = styled(Box)`
    & > * {
        border-bottom: 1px solid lightGrey;
    }

    & > *:last-child {
        border-bottom: 0px;
    }
`

export default List;