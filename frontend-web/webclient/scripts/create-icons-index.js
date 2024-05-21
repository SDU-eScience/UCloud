import fs from "fs";
import path from "path";

const __dirname = path.resolve(path.dirname(""));
console.log(__dirname);
const dirname = path.join(__dirname, './app/ui-components/icons/components/')
const fileNames = fs
    .readdirSync(dirname)
    .filter(filename => /\.tsx$/.test(filename));
//.map(filename => path.basename(filename, '.tsx'))

const filename = path.join(__dirname, './app/ui-components/icons/index.tsx')
fs.writeFileSync(filename, "import * as React from \"react\";\n");

for (const f of fileNames) {
    const content = fs.readFileSync(path.join(dirname, f));
    const text = Buffer.from(content, "base64").toString("utf-8")
        .split("\n")
        .filter(it => it.indexOf("import ") === -1 && it.indexOf("export ") === -1)
        .map(line => {
            const newLine = line
                .replace("SVGProps<SVGSVGElement>", "any")
                .replace("default Svg", "default ")
                .replace("\"#53657d\"", "\"currentcolor\"")
                .replace("\"#001833\"", "\"currentcolor\"")
                .replace("\"#8393a7\"", "props.color2 ? props.color2 : \"currentcolor\"");

            if (newLine.indexOf("const") !== -1) {
                const words = newLine.split(" ");
                words[1] = words[1].replace("Svg", "")
                words[1] = words[1][0].toLowerCase() + words[1].substring(1);
                return "export " + words.join(" ");
            }

            return newLine;
        })
        .join("\n")

    fs.appendFileSync(filename, text);
}

for (const f of fileNames) {
    fs.rm(path.join(dirname, f), (err) => {
        if (err != null) {
            console.warn(err);
        } 
    });
}