const Store = require('electron-store');
const { defaults } = require('./defaults');

const store = new Store({ defaults });

module.exports = store;
