import {defineConfig, UserConfigExport} from "vite";
import react from "@vitejs/plugin-react";
//@ts-ignore
import path from "path";
import {DEV_SITE, SANDBOX_SITE} from "./site.config.json";

// https://vitejs.dev/config/

type Mode = "development" | "local-dev" | "production" | "compose" | string;

function targetFromConfig(mode: Mode): string {
    if (mode.startsWith("http")) {
        return mode;
    }

    switch (mode) {
        case "development":
            return `https://${DEV_SITE}`;
        case "sandbox":
            return `https://${SANDBOX_SITE}`;
        case "compose":
            return "http://backend:8080";
        case "local-dev":
        case "production":
        default:
            return "http://localhost:8080";
    }
}

export default ({mode, port, ...rest}: {mode: Mode; port?: number;}): UserConfigExport => {
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
        build: {
            assetsInlineLimit: 0 
        },
        define: {
            /*
                Note(Jonas): Added because of React-Markdown using the `assert` function,
                which is why the assert package is installed.
            */
            "process.env": {},
            DEVELOPMENT_ENV: mode !== "production",
        },
        mode: mode === "production" ? "production" : "development",
        plugins: [react()],
        resolve: {
            alias: {
                //@ts-ignore
                "@": path.resolve(__dirname, "./app")
            }
        },
        server: {
            port: port ?? 9000,
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
                "/ucloud/development/": {
                    // NOTE(Dan): This always attempts to go to the integration module
                    target: "http://integration-module:8889",
                    ws: true,
                    changeOrigin: true,

                    headers: {
                        "Access-Control-Allow-Origin": "*",
                        "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, PATCH, OPTIONS",
                        "Access-Control-Allow-Headers": "X-Requested-With, content-type, Authorization"
                    }
                },
                "/ucloud/": sharedProxySetting,
                "/AppVersion.txt": sharedProxySetting
            },
            watch: {
                usePolling: true,
                interval: 1000,
                binaryInterval: 3000,
                alwaysStat: true,
                depth: 99,
            }
        }
    });
};
