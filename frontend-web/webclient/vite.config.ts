import {defineConfig, UserConfigExport} from "vite";
import reactRefresh from "@vitejs/plugin-react-refresh";
import path from "path";
import CONF from "./site.config.json";

// https://vitejs.dev/config/
export default ({mode, ...rest}: {mode: "development" | "local-dev" | "production"}): UserConfigExport => {

    const target = "https://cloud.sdu.dk";

    const sharedProxySetting = {
        target,
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
            DEVELOPMENT_ENV: mode !== "production",
        },
        mode,
        build: {
            minify: false,
        },
        assetsInclude: "./app/Assets/",
        plugins: [reactRefresh()],
        resolve: {
            alias: {
                "@": path.resolve(__dirname, "./app")
            }
        },
        server: {
            port: 9000,
            cors: {
                origin: "*",
                methods: ["GET", "HEAD", "PUT", "PATCH", "POST", "DELETE"],
                allowedHeaders: ["X-Requested-With", "content-type", "Authorization"]
            },
            // Use regex instead? These all match.
            proxy: {
                "/auth/": sharedProxySetting,
                "/api/": sharedProxySetting,
                "/ucloud/": sharedProxySetting
            }
        }
    });
};
