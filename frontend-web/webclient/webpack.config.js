const webpack = require("webpack");
const HtmlWebpackPlugin = require("html-webpack-plugin");
const MiniCSSExtractPlugin = require("mini-css-extract-plugin");
const path = require("path");
//const UglifyJsPlugin = require("uglifyjs-webpack-plugin");
const baseHref = "/app";

module.exports = {

    entry: {
        // vendor: "./app/Vendor.tsx",
        app: "./app/App.tsx"
    },

    resolve: {
        //root: path.join(__dirname, ''),
        modules: [path.resolve(__dirname, "app"), "node_modules"],
        extensions: [".js", ".jsx", ".json", ".ts", ".tsx"]
    },

    module: {
        rules: [
            {
                test: /\.(jsx|tsx?)$/,
                exclude: /node_modules/,
                loader: 'babel-loader',
            },
            {
                test: /\.js$/,
                use: ["source-map-loader"],
                enforce: "pre"
            },
            {
                test: /\.css$/,
                exclude: path.join(process.cwd(), '/app'),
                use: ["style-loader", "css-loader"]
            },
            {
                test: /\.(woff|woff2|svg|ttf|eot)$/,
                use: "file-loader?limit=10000"
            }, {
                test: /\.(png|jpg|gif)$/,
                use: "url-loader?limit=10000"
            }, {
                test: /\.scss$/,
                use: [
                    "style-loader",
                    "css-loader",
                    "sass-loader",
                ]
            }
        ]
    },

    optimization: {
        splitChunks: {
            name: "vendor",
            filename: "vendor[hash:6].js",
            chunks: 'all',
        },
        // minimizer: [
        //     new UglifyJsPlugin()
        // ]
    },

    plugins: [
        // Simplifies creation of HTML files to serve your webpack bundles. 
        // Useful for webpack bundles including a hash in the filename which changes every compilation. 
        new HtmlWebpackPlugin({
            template: 'app/index.html',
            baseUrl: baseHref,
            hash: true
        }),
        new MiniCSSExtractPlugin("[name].[hash:6].css"),
        // Allows overriding inferred information.
        // https://github.com/moment/moment/issues/2979#issuecomment-189899510
        new webpack.ContextReplacementPlugin(/\.\/locale$/, "empty-module", false, /js$/),
        //new webpack.DefinePlugin({ REACT_BASE_HREF: JSON.stringify(baseHref) })
    ]
};
