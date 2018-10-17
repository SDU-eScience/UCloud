import styled from 'styled-components'
import theme from './theme'
import { space, width, borderColor, SpaceProps, WidthProps, BorderColorProps } from 'styled-system'

export interface DividerProps extends SpaceProps, WidthProps, BorderColorProps {} ;

const Divider = styled<DividerProps, "hr">("hr")`
  border: 0;
  border-bottom-style: solid;
  border-bottom-width: 1px;
  ${space} ${width} ${borderColor};
`;

Divider.displayName = 'Divider';

Divider.defaultProps = {
  borderColor: 'borderGray',
  theme: theme,
  ml: 0,
  mr: 0
};

export default Divider;
