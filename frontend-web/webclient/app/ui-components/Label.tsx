import styled from 'styled-components';
import { space, fontSize, fontWeight, color, FontStyleProps, SpaceProps, ColorProps, FontWeightProps, FontSizeProps } from 'styled-system';
import theme from './theme';

const nowrap = props =>
  props.nowrap
    ? {
      whiteSpace: 'nowrap'
    }
    : null

const accessiblyHide = props =>
  props.hidden
    ? {
      position: 'absolute',
      width: '1px',
      height: '1px',
      clip: 'rect(1px, 1px, 1px, 1px)'
    }
    : null

export type LabelProps = 
  SpaceProps & FontSizeProps & FontStyleProps & ColorProps & FontWeightProps 
  & { nowrap?: boolean, hidden?: boolean };

const Label = styled("label") <LabelProps>`
  font-size: 10px;
  letter-spacing: 0.2px;
  display: block;
  width: 100%;
  margin: 0;

  ${space} ${fontSize} ${color} ${fontWeight};
  ${nowrap}
  ${accessiblyHide}
`;

Label.defaultProps = {
  fontSize: '10px',
  fontWeight: 'bold',
  color: 'gray',
  theme: theme
};

Label.displayName = 'Label';

export default Label;
