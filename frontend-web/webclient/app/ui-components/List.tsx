import styled from "styled-components";
import Box from "./Box";

const childPadding = ({childPadding}: {childPadding?: string | number}) =>
    childPadding ? {marginBottom: childPadding, marginTop: childPadding} : null;

const List = styled(Box) <{fontSize?: string, childPadding?: string | number, bordered?: boolean}>`
    font-size: ${props => props.fontSize};
    & > * {
        ${props => props.bordered ? "border-bottom: 1px solid lightGrey;" : null}
        ${childPadding};
    }

    & > *:last-child {
        ${props => props.bordered ? "border-bottom: 0px;" : null}
    }
`;

List.defaultProps = {
    fontSize: "large",
    bordered: true
};

List.displayName = "List";

export default List;