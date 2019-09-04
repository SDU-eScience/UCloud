import styled from "styled-components";
import theme from "ui-components/theme";
import Flex from "./Flex";
import Text from "./Text";

const SelectableTextWrapper = styled(Flex)`
    border-bottom: 1px solid ${p => p.theme.colors.lightGray};
    cursor: pointer;
`;

SelectableTextWrapper.defaultProps = {
    theme
};

const SelectableText = styled(Text) <{selected: boolean}>`
    border-bottom: ${props => props.selected ? `2px solid ${theme.colors.blue}` : ""};
`;

SelectableText.defaultProps = {
    theme
};

SelectableText.displayName = "SelectableText";

export {SelectableTextWrapper, SelectableText};
