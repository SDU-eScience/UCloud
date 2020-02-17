import styled from "styled-components";
import theme from "ui-components/theme";
import Flex from "./Flex";
import Text from "./Text";

const SelectableTextWrapper = styled(Flex)`
    border-bottom: ${theme.borderWidth} solid ${p => p.theme.colors.borderGray};
    cursor: pointer;
`;

const SelectableText = styled(Text) <{selected: boolean}>`
    border-bottom: ${props => props.selected ? `3px solid ${theme.colors.blue}` : ""};
`;

SelectableText.displayName = "SelectableText";

export {SelectableTextWrapper, SelectableText};
