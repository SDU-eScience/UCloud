import styled from "styled-components";
import Box from "./Box";

const childPadding = ({ childPadding }: { childPadding?: string | number }) =>
    childPadding ? { marginBottom: childPadding, marginTop: childPadding } : null;

const List = styled(Box) <{ fontSize?: string, childPadding?: string | number }>`
    font-size: ${props => props.fontSize};
    

    & > * {
        border-bottom: 1px solid lightGrey;
        ${childPadding};
    }

    & > *:last-child {
        border-bottom: 0px;
    }
`;

List.defaultProps = {
    fontSize: "large"
}

export default List;