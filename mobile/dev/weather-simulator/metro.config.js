const path = require('node:path');
const { getDefaultConfig } = require('expo/metro-config');
const config = getDefaultConfig(__dirname);
const mobileRoot = path.resolve(__dirname, '../..');
config.watchFolders = [mobileRoot];
config.resolver.nodeModulesPaths = [path.join(mobileRoot, 'node_modules')];
module.exports = config;
