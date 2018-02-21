var webpack = require('webpack');
var webpackMerge = require('webpack-merge');
const CopyWebpackPlugin = require('copy-webpack-plugin');
var ExtractTextPlugin = require('extract-text-webpack-plugin');
var commonConfig = require('./webpack.config.js');
var path = require('path');

module.exports = webpackMerge(commonConfig, {
    devtool: '#source-map',

    entry: {
        vendor: './app/Vendor.jsx',
        app: './app/App.jsx'
    },

    output: {
        path: path.join(process.cwd(), '/dist'),
        publicPath: 'http://localhost:9000/',
        filename: '[name].js',
    },

    plugins: [
        new webpack.HotModuleReplacementPlugin(),
        new ExtractTextPlugin('[name].css'),
        new CopyWebpackPlugin([{
            from: 'mock-api',
            to: 'mock-api',
            context: path.join(__dirname, 'app')
        }])
    ],

    devServer: {
        historyApiFallback: true,
        stats: 'minimal',
        index: "app/index.html",
        inline: true,
        hot: true,
        headers: {
            'Access-Control-Allow-Origin': '*'
        },
        proxy: [{
            context: ["/auth/login", "/auth/request", "/auth/login-redirect", "/api", "/auth/css/", "/auth/refresh", "/auth/fonts/", "/auth/sdu_plain_white.png", "/auth/wayf_logo.png", "/auth/saml/"],
            target: "https://cloud.sdu.dk",
            secure: false, // FIXME HTTPS Should be secure
        }, {
            context: "/auth",
            target: "http://localhost:8080",
        }],
    }
});
