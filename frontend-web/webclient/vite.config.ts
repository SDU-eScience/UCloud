import {defineConfig, UserConfigExport} from "vite";
import reactRefresh from "@vitejs/plugin-react-refresh";
import tsconfigPaths from "vite-tsconfig-paths";
import CONF from "./site.config.json";

// https://vitejs.dev/config/
export default ({mode, ...rest}: {mode: "development" | "local-dev" | "production"}): UserConfigExport => {

    const target = mode === "local-dev" ? "http://localhost:8080" : `https://${CONF.DEV_SITE}`;

    return defineConfig({
        clearScreen: true,
        assetsInclude: "./app/Assets",
        define: {
            DEVELOPMENT_ENV: mode !== "production",
        },
        plugins: [reactRefresh(), tsconfigPaths()],
        server: mode === "production" ? undefined : {
            port: 9000,
            cors: {
                origin: "*",
                methods: ["GET", "HEAD", "PUT", "PATCH", "POST", "DELETE"],
                allowedHeaders: ["X-Requested-With", "content-type", "Authorization"]
            },
            /* Use regex instead? These all match. */
            proxy: {
                "/auth/": {
                    target,
                    ws: true,
                    changeOrigin: true,
                    headers: {
                        "Access-Control-Allow-Origin": "*",
                        "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, PATCH, OPTIONS",
                        "Access-Control-Allow-Headers": "X-Requested-With, content-type, Authorization"
                    },
                },
                "/api/": {
                    target,
                    ws: true,
                    changeOrigin: true,
                    headers: {
                        "Access-Control-Allow-Origin": "*",
                        "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, PATCH, OPTIONS",
                        "Access-Control-Allow-Headers": "X-Requested-With, content-type, Authorization"
                    },
                },
                "/ucloud/": {
                    target,
                    ws: true,
                    changeOrigin: true,
                    headers: {
                        "Access-Control-Allow-Origin": "*",
                        "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, PATCH, OPTIONS",
                        "Access-Control-Allow-Headers": "X-Requested-With, content-type, Authorization"
                    },
                },
            }
        }
    });
}