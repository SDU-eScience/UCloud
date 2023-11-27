import path from "path";
const __dirname = path.resolve(path.dirname(""));

const appVersionPath = path.join(__dirname, "./public/AppVersion.txt");

const {version} = JSON.parse(fs.readFileSync(path.join(__dirname, "./package.json"),));

const data = fs.readFileSync(appVersionPath, {encoding:'utf8', flag:'r'});

if (data !== version) {
    console.warn("AppVersion.txt is outdated. Updating...");
    fs.writeFileSync(appVersionPath, version);
}
