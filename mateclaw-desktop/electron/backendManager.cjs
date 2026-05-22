const { spawn } = require('node:child_process');

class BackendManager {
  constructor() {
    this.process = null;
  }

  isRunning() {
    return Boolean(this.process && !this.process.killed);
  }

  async ensureStarted(config) {
    if (!config?.autoStartBackend || !config.backendCommand || this.isRunning()) {
      return false;
    }

    this.process = spawn(config.backendCommand, config.backendArgs || [], {
      cwd: config.backendCwd || process.cwd(),
      stdio: 'ignore',
      shell: true,
      detached: false,
    });

    this.process.on('exit', () => {
      this.process = null;
    });

    return true;
  }

  async stop() {
    if (!this.process) {
      return;
    }

    this.process.kill();
    this.process = null;
  }
}

module.exports = {
  BackendManager,
};
