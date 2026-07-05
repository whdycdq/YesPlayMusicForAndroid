const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');

const projectRoot = path.resolve(__dirname, '..');
const androidRoot = path.join(projectRoot, 'android');
const localJdkRoot = path.join(projectRoot, '.jdk');
const task = process.argv[2] || 'assembleDebug';
const env = { ...process.env };

function findLocalJdk() {
  if (!fs.existsSync(localJdkRoot)) return null;
  return fs
    .readdirSync(localJdkRoot, { withFileTypes: true })
    .filter(entry => entry.isDirectory())
    .map(entry => path.join(localJdkRoot, entry.name))
    .find(directory =>
      fs.existsSync(
        path.join(
          directory,
          'bin',
          process.platform === 'win32' ? 'java.exe' : 'java'
        )
      )
    );
}

const localJdk = findLocalJdk();
if (localJdk) {
  env.JAVA_HOME = localJdk;
  env.PATH = `${path.join(localJdk, 'bin')}${path.delimiter}${env.PATH}`;
}

if (!env.JAVA_HOME) {
  console.error(
    'Android 构建需要 JDK 21。请设置 JAVA_HOME，或把 JDK 解压到项目的 .jdk 目录。'
  );
  process.exit(1);
}

const wrapper = process.platform === 'win32' ? 'gradlew.bat' : './gradlew';
const args = ['-I', 'china-mirrors.init.gradle', task, '--no-daemon'];
const result = spawnSync(wrapper, args, {
  cwd: androidRoot,
  env,
  stdio: 'inherit',
  shell: process.platform === 'win32',
});

process.exit(result.status === null ? 1 : result.status);
