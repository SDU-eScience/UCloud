import * as React from 'react'
import IconButton from './IconButton'
import theme from './theme'

const CloseButton = props => <IconButton {...props} name="close" />

CloseButton.defaultProps = {
  size: 24,
  title: 'close'
}


CloseButton.displayName = 'CloseButton'

export default CloseButton
