const webpack = require('webpack');
const CopyWebpackPlugin = require('copy-webpack-plugin');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const ExtractTextPlugin = require('extract-text-webpack-plugin');
const path = require('path');
//var baseHref = process.env.REACT_BASE_HREF ? process.env.REACT_BASE_HREF : '/';
const baseHref = "/app";

module.exports = {

    entry: {
        vendor: './app/Vendor.tsx',
        app: './app/App.jsx'
    },

    resolve: {
        //root: path.join(__dirname, ''),
        modules: ["node_modules"],
        extensions: ['.js', '.jsx', ".json", ".ts", ".tsx"]
    },

    module: {
        rules: [
            {
                test: /\.tsx?$/,
                use: "ts-loader",
                exclude: /node_modules/
            },
            {
                test: /jquery\.flot\.resize\.js$/,
                use: 'imports-loader?this=>window'
            },
            {
                test: /\.js/,
                use: 'imports-loader?define=>false'
            },
            {
                test: /\.jsx?$/,
                exclude: /(node_modules)/,
                loader: "babel-loader",
                query: {
                    presets: ['es2015', 'react'],
                    compact: false
                }
            }, {
                test: /\.css$/,
                exclude: path.join(process.cwd(), '/app'),
                use: ExtractTextPlugin.extract({ fallback: "style-loader", use: "css-loader" })
            }, {
                test: /\.css$/,
                include: path.join(process.cwd(), '/app'),
                use: "raw-loader"
            }, {
                test: /\.woff|\.woff2|\.svg|.eot|\.ttf/,
                use: 'url-loader?prefix=font/&limit=10000'
            }, {
                test: /\.(png|jpg|gif)$/,
                use: 'url-loader?limit=10000'
            }, {
                test: /\.scss$/,
                use: [
                    "style-loader",
                    "css-loader",
                    "sass-loader",
                ]
            },
            {
                test: /\.json/,
                loader: "json-loader"
            },
        ]
    },

    plugins: [
        new webpack.optimize.CommonsChunkPlugin({ name: 'vendor', filename: 'vendor[hash:6].js' }),
        new HtmlWebpackPlugin({
            template: 'app/index.html',
            baseUrl: baseHref
        }),
        // https://github.com/moment/moment/issues/2979#issuecomment-189899510
        new webpack.ContextReplacementPlugin(/\.\/locale$/, 'empty-module', false, /js$/),
        new webpack.DefinePlugin({
            REACT_BASE_HREF: JSON.stringify(baseHref)
        })
    ]
};
