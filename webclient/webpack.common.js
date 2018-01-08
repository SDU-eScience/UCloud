var webpack = require('webpack');
var CopyWebpackPlugin = require('copy-webpack-plugin');
var HtmlWebpackPlugin = require('html-webpack-plugin');
var ExtractTextPlugin = require('extract-text-webpack-plugin');
var path = require('path');

var baseHref = process.env.REACT_BASE_HREF ? process.env.REACT_BASE_HREF : '/';

module.exports = {

    entry: {
        'vendor': './app/Vendor.jsx',
        'app': './app/App.jsx'
    },

    resolve: {
        root: path.join(__dirname, ''),
        modulesDirectories: ['node_modules', 'bower_components'],
        extensions: ['', '.js', '.jsx']
    },

    module: {
        loaders: [{
                test: /jquery\.flot\.resize\.js$/,
                loader: 'imports?this=>window'
            }, {
                test: /\.js/,
                loader: 'imports?define=>false'
            }, {
                test: /\.jsx?$/,
                exclude: /(node_modules|bower_components)/,
                loaders: ['react-hot']
            }, {
                test: /\.jsx?$/,
                exclude: /(node_modules|bower_components)/,
                loader: 'babel',
                query: {
                    presets: ['es2015', 'react'],
                    compact: false
                }
            }, {
                test: /\.css$/,
                exclude: path.join(process.cwd(), '/app'),
                loader: ExtractTextPlugin.extract('style', 'css?sourceMap')
            }, {
                test: /\.css$/,
                include: path.join(process.cwd(), '/app'),
                loader: 'raw'
            }, {
                test: /\.woff|\.woff2|\.svg|.eot|\.ttf/,
                loader: 'url?prefix=font/&limit=10000'
            }, {
                test: /\.(png|jpg|gif)$/,
                loader: 'url?limit=10000'
            }, {
                test: /\.scss$/,
                loader: 'style!css!sass?outputStyle=expanded'
            }]
            // , noParse: [/\.min\.js/]
    },

    devServer: {
        outputPath: path.join(__dirname, 'dist')
    },

    plugins: [
        new webpack.optimize.CommonsChunkPlugin('vendor', 'vendor[hash:6].js'),
        new HtmlWebpackPlugin({
            template: 'app/index.html',
            baseUrl: baseHref
        }),
        new webpack.ResolverPlugin([
            // new webpack.ResolverPlugin.DirectoryDescriptionFilePlugin('package.json', ['main']),
            new webpack.ResolverPlugin.DirectoryDescriptionFilePlugin('.bower.json', ['main'])
        ]),
        new CopyWebpackPlugin([{
            from: 'img',
            to: 'img',
            context: path.join(__dirname, 'app')
        }, {
            from: 'server',
            to: 'server',
            context: path.join(__dirname, 'app')
        }, {
            from: 'fonts',
            to: 'fonts',
            context: path.join(__dirname, 'app')
        }]),
        new webpack.ProvidePlugin({
            $: 'jquery',
            jQuery: 'jquery',
            'window.jQuery': 'jquery'
        }),
        // https://github.com/moment/moment/issues/2979#issuecomment-189899510
        new webpack.ContextReplacementPlugin(/\.\/locale$/, 'empty-module', false, /js$/),
        new webpack.DefinePlugin({
            REACT_BASE_HREF: JSON.stringify(baseHref)
        })
    ]
};
