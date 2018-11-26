const fs = require('fs')
const path = require('path')

const dirname = path.join(__dirname, '../app/ui-components/icons/components')
const icons = fs
  .readdirSync(dirname)
  .filter(filename => /\.tsx$/.test(filename))
  .map(filename => path.basename(filename, '.tsx'))

const template = icons =>
  icons.map(name => `export { default as ${name} } from './components/${name}'`).join('\n')

const content = template(icons)
const filename = path.join(__dirname, '../app/ui-components/icons/index.ts')

fs.writeFileSync(filename, content)