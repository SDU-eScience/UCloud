import {defineConfig, UserConfigExport} from "vite";
import reactRefresh from "@vitejs/plugin-react-refresh";
import path from "path";
import {DEV_SITE} from "./site.config.json";

// https://vitejs.dev/config/

type Mode = "development" | "local-dev" | "production" | "compose";

function targetFromConfig(mode: Mode): string {
    switch (mode) {
        case "development":
            return `https://${DEV_SITE}`;
        case "compose":
            return "http://backend:8080";
        case "local-dev":
        case "production":
        default:
            return "localhost:8080";
    }
}

export default ({mode, ...rest}: {mode: Mode; command: string}): UserConfigExport => {
    const sharedProxySetting = {
        target: targetFromConfig(mode),
        ws: true,
        changeOrigin: true,
        headers: {
            "Access-Control-Allow-Origin": "*",
            "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, PATCH, OPTIONS",
            "Access-Control-Allow-Headers": "X-Requested-With, content-type, Authorization"
        }
    }

    return defineConfig({
        clearScreen: false,
        define: {
            /*
                Note(Jonas): Added because of React-Markdown using the `assert` function,
                which is why the assert package is installed.
            */
            "process.env": {},
            DEVELOPMENT_ENV: mode !== "production",
        },
        mode: mode === "production" ? "production" : "developement",
        plugins: [reactRefresh()],
        resolve: {
            alias: {
                "@": path.resolve(__dirname, "./app")
            }
        },
        server: {
            port: 9000,
            host: "0.0.0.0",
            cors: {
                origin: "*",
                methods: ["GET", "HEAD", "PUT", "PATCH", "POST", "DELETE"],
                allowedHeaders: ["X-Requested-With", "content-type", "Authorization"]
            },
            // Use regex instead? These all match.
            proxy: {
                "/auth/": sharedProxySetting,
                "/api/": sharedProxySetting,
                "/ucloud/": sharedProxySetting,
                "/AppVersion.txt": sharedProxySetting
            }
        }
    });
};
