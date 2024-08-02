import * as React from "react";
import {Box, Button, Flex, Icon, MainContainer} from "@/ui-components";
import {usePage} from "@/Navigation/Redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {getQueryParam} from "@/Utilities/URIUtilities";
import {useLocation} from "react-router";
import * as AppStore from "@/Applications/AppStoreApi";
import {Application} from "@/Applications/AppStoreApi";
import {useState, useEffect, useRef} from "react";
import {callAPI} from "@/Authentication/DataHook";
import {SafeLogo} from "@/Applications/AppToolLogo";
import HexSpin from "@/LoadingIcon/LoadingIcon";
import Text from "../ui-components/Text";
import TabbedCard, {TabbedCardTab} from "@/ui-components/TabbedCard";
import {extensionFromPath, isLightThemeStored, languageFromExtension} from "@/UtilityFunctions";
import {AsyncCache} from "@/Utilities/AsyncCache";
import {useSelector} from "react-redux";
import {Feature, hasFeature} from "@/Features";

const Fork: React.FunctionComponent = () => {
    if (!hasFeature(Feature.COPY_APP_MOCKUP)) return null;

    const location = useLocation();
    const name = getQueryParam(location.search, "name");
    const version = getQueryParam(location.search, "version");

    const [title, setTitle] = useState("Fork");
    const [baseApplication, setBaseApplication] = useState<Application | null>(null);
    const codeView = useRef<HTMLDivElement>(null);

    usePage(title, SidebarTabId.APPLICATIONS);
    useEffect(() => {
        if (!name || !version) return;
        let didCancel = false;

        (async () => {
            const app = await callAPI(AppStore.findByNameAndVersion({appName: name, appVersion: version}));
            if (didCancel || !app) return;
            setBaseApplication(app);
        })();

        return () => {
            didCancel = true;
        };
    }, [name, version]);

    useEffect(() => {
        if (baseApplication) {
            setTitle("Forking " + baseApplication.metadata.title);
        }
    }, [baseApplication]);

    React.useLayoutEffect(() => {
        const localStorageKey = `fork-${name}`;
        let initialValue = localStorage.getItem(localStorageKey);
        if (!initialValue) {
            initialValue = `name: My fork

features:
  multiNode: true # NOTE: Without this, you will not be able to select multiple nodes

parameters:
  input_dir:
    type: Directory
    optional: false
    title: "Input directory"
    description: |
      The input directory must contain the following files:

      - \`pw.Si.in\`
      - \`ph.Si.in\`
      - \`dynmat.Si.in\`

sbatch:
  ntasks: "{{ ucloud.machine.cpu * ucloud.nodes }}"
  cpus-per-task: 1

# TODO Tweak this example to be more realistic. We have all the tools to do it,
# I am just not an expert in how to run these tools.
invocation: |
  echo Running on "$(hostname)" with {{ ucloud.machine.name }}
  echo Available nodes: "$SLURM_NODELIST"
  echo Slurm_submit_dir: "$SLURM_SUBMIT_DIR"
  echo Start time: "$(date)"

  export OMP_NUM_THREADS=1

  cd {{ input_dir }}
  srun -n $SLURM_NTASKS pw.x < pw.Si.in | tee pw.Si.out
  srun -n $SLURM_NTASKS ph.x < ph.Si.in | tee ph.Si.out
  srun -n $SLURM_NTASKS dynmat.x < dynmat.Si.in | tee dynmat.Si.out
            
`;
        }

        // NOTE(Dan): Multiple tries are needed due to the way the tabbed cards work. This will delay the
        // initialization by some semi-unpredictable amount of time.
        let didInit = false;
        function initMonaco(attemptsRemaining: number = 100) {
            const node = codeView.current;
            if (!node) {
                if (attemptsRemaining <= 0) return;
                window.setTimeout(() => initMonaco(attemptsRemaining - 1), 0);
                return;
            }
            didInit = true;
            getMonaco().then(monaco => {
                const count = monaco.editor.getEditors().length;
                if (count > 0) return;
                monaco.editor.create(node, {
                    value: initialValue,
                    language: "yaml",
                    readOnly: false,
                    minimap: {enabled: false},
                    theme: isLightThemeStored() ? "light" : "vs-dark",
                });
                window.onresize = () => {
                    monaco.editor.getEditors().forEach(it => it.layout());
                };
            });
        }

        initMonaco();

        async function save() {
            if (!didInit) return;

            const monaco = await getMonaco();
            const editors = monaco.editor.getEditors();
            if (editors.length !== 1) return;

            const value = editors[0].getValue();
            localStorage.setItem(localStorageKey, value);
        }

        const saveToDisk = window.setInterval(async () => {
            save();
        }, 5000);

        return () => {
            getMonaco().then(monaco => monaco.editor.getEditors().forEach(it => it.dispose()));
            save();
            window.clearInterval(saveToDisk);
        };
    }, [name]);

    const lightTheme = useSelector((red: ReduxObject) => red.sidebar.theme)
    React.useEffect(() => {
        const theme = lightTheme === "light" ? "light" : "vs-dark";
        getMonaco().then(monaco => monaco.editor.setTheme(theme));
    }, [lightTheme]);


    if (!name || !version) {
        return null;
    }

    if (baseApplication == null) {
        return <MainContainer
            main={<HexSpin/>}
        />
    }

    return <MainContainer
        main={<Flex flexDirection={"column"} gap={"32px"}>
            <Flex flexDirection={"row"} gap={"16px"} alignItems={"end"}>
                <SafeLogo type={"APPLICATION"} name={baseApplication.metadata.name} size={"64px"}/>
                <Flex flexDirection={"column"}>
                    <Text verticalAlign="center" alignItems="center" fontSize={20}>
                        Creating fork of
                    </Text>
                    <Text verticalAlign="center" alignItems="center" fontSize={30}>
                        {baseApplication.metadata.title} ({baseApplication.metadata.version})
                    </Text>
                </Flex>
                <Box flexGrow={1}/>
                <Button color={"successMain"}>
                    <Icon name={"heroCheck"} mr={8}/>
                    Save
                </Button>
            </Flex>

            <TabbedCard style={{minHeight: "1000px", height: "calc(100vh - 155px)"}}>
                <TabbedCardTab icon={"heroCodeBracket"} name={"Code"}>
                    <div
                        style={{
                            borderBottomLeftRadius: "10px",
                            borderBottomRightRadius: "10px",
                            height: "calc(100vh - 155px - 48px)",
                            width: "calc(100% + 40px)",
                            marginTop: "-8px",
                            marginLeft: "-20px",
                            overflow: "hidden",
                        }}
                    >
                        <div style={{width: "100%", height: "100%"}} ref={codeView}/>
                    </div>
                </TabbedCardTab>

                <TabbedCardTab icon={"heroMagnifyingGlass"} name={"Preview"}>
                    Placeholder. A preview of running the application goes here.
                </TabbedCardTab>

                <TabbedCardTab icon={"heroPlay"} name={"Dry run"}>
                    Placeholder. A preview of what would happen if you run the application goes here.
                </TabbedCardTab>
            </TabbedCard>
        </Flex>}
    />;
};

// TODO(Dan): This has been copy & pasted from the file preview. We probably need a more common location for this.
const monacoCache = new AsyncCache<any>();

async function getMonaco(): Promise<any> {
    return monacoCache.retrieve("", async () => {
        const monaco = await (import("monaco-editor"));
        self.MonacoEnvironment = {
            getWorker: function (workerId, label) {
                switch (label) {
                    case 'json':
                        return getWorkerModule('/monaco-editor/esm/vs/language/json/json.worker?worker', label);
                    case 'css':
                    case 'scss':
                    case 'less':
                        return getWorkerModule('/monaco-editor/esm/vs/language/css/css.worker?worker', label);
                    case 'html':
                    case 'handlebars':
                    case 'razor':
                        return getWorkerModule('/monaco-editor/esm/vs/language/html/html.worker?worker', label);
                    case 'typescript':
                    case 'javascript':
                        return getWorkerModule('/monaco-editor/esm/vs/language/typescript/ts.worker?worker', label);
                    default:
                        return getWorkerModule('/monaco-editor/esm/vs/editor/editor.worker?worker', label);
                }


                function getWorkerModule(moduleUrl, label) {
                    return new Worker(self.MonacoEnvironment!.getWorkerUrl!(moduleUrl, label), {
                        name: label,
                        type: 'module'
                    });
                }
            }
        };

        console.log("Got monaco!");
        return monaco;
    })
}

export default Fork;
