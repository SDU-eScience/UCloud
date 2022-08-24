const fs = require('fs')
const path = require('path')

const dirname = path.join(__dirname, '../app/ui-components/icons/components/')
const fileNames = fs
    .readdirSync(dirname)
    .filter(filename => /\.tsx$/.test(filename));
//.map(filename => path.basename(filename, '.tsx'))

const filename = path.join(__dirname, '../app/ui-components/icons/index.tsx')
fs.writeFileSync(filename, "import * as React from \"react\";\n");

for (var f of fileNames) {
    const content = fs.readFileSync(path.join(dirname, f));
    fs.appendFileSync(filename, Buffer.from(content, "base64").toString("utf-8"));
}

for (var f of fileNames) {
    fs.rm(path.join(dirname, f), (err) => {
        if (err != null) {
            console.warn(err);
        } 
    });
}