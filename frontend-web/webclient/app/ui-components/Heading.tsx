import Text from './Text'
import theme from './theme'

const Heading = Text.withComponent('h3')

Heading.displayName = 'Heading'
Heading.defaultProps = {
  regular: true,
  fontSize: 4,
  m: 0,
  theme
};

export const h1 = Heading.withComponent('h1')
h1.defaultProps = {
  bold: true,
  fontSize: 6,
  m: 0
};

export const h2 = Heading.withComponent('h2')
h2.defaultProps = {
  bold: true,
  fontSize: 5,
  m: 0
};

export const h3 = Heading.withComponent('h3')
h3.defaultProps = {
  regular: true,
  fontSize: 4,
  m: 0
};

export const h4 = Heading.withComponent('h4')
h4.defaultProps = {
  regular: true,
  fontSize: 3,
  m: 0
};

export const h5 = Heading.withComponent('h5')
h5.defaultProps = {
  bold: true,
  fontSize: 2,
  m: 0
};

export const h6 = Heading.withComponent('h6')
h6.defaultProps = {
  bold: true,
  caps: true,
  fontSize: 0,
  m: 0
};

export default Heading;