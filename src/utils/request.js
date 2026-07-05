import router from '@/router';
import { doLogout, getCookie } from '@/utils/auth';
import axios from 'axios';
import { getNeteaseApiBaseUrl } from '@/utils/apiEndpoint';

const ANDROID_NETWORK_RETRY_LIMIT = 2;
const RETRYABLE_HTTP_STATUS = new Set([408, 425, 429, 500, 502, 503, 504]);

let baseURL = '';
// Web 和 Electron 跑在不同端口避免同时启动时冲突
if (process.env.IS_ELECTRON) {
  if (process.env.NODE_ENV === 'production') {
    baseURL = process.env.VUE_APP_ELECTRON_API_URL;
  } else {
    baseURL = process.env.VUE_APP_ELECTRON_API_URL_DEV;
  }
} else {
  baseURL = getNeteaseApiBaseUrl();
}

const service = axios.create({
  baseURL,
  withCredentials: true,
  timeout: 15000,
});

function sleep(milliseconds) {
  return new Promise(resolve => window.setTimeout(resolve, milliseconds));
}

function isIdempotentRequest(config) {
  const method = (config?.method || 'get').toLowerCase();
  return ['get', 'head', 'options'].includes(method);
}

function shouldRetryAndroidRequest(config, status) {
  if (
    process.env.VUE_APP_PLATFORM !== 'android' ||
    !config ||
    !isIdempotentRequest(config)
  ) {
    return false;
  }

  const retryCount = config.__androidNetworkRetryCount || 0;
  if (retryCount >= ANDROID_NETWORK_RETRY_LIMIT) return false;

  return status === undefined || RETRYABLE_HTTP_STATUS.has(Number(status));
}

async function retryAndroidRequest(config) {
  const retryCount = config.__androidNetworkRetryCount || 0;
  config.__androidNetworkRetryCount = retryCount + 1;
  config.baseURL = getNeteaseApiBaseUrl();

  const baseDelay = retryCount === 0 ? 450 : 1200;
  const jitter = Math.floor(Math.random() * 250);
  await sleep(baseDelay + jitter);
  return service(config);
}

service.interceptors.request.use(function (config) {
  if (process.env.VUE_APP_PLATFORM === 'android') {
    config.baseURL = getNeteaseApiBaseUrl();
    baseURL = config.baseURL;
  }
  if (!config.params) config.params = {};
  if (baseURL.length) {
    if (
      baseURL[0] !== '/' &&
      !process.env.IS_ELECTRON &&
      getCookie('MUSIC_U') !== null
    ) {
      config.params.cookie = `MUSIC_U=${getCookie('MUSIC_U')};`;
    }
  } else {
    console.error("You must set up the baseURL in the service's config");
  }

  if (!process.env.IS_ELECTRON && !config.url.includes('/login')) {
    config.params.realIP = '211.161.244.70';
  }

  // Force real_ip
  const enableRealIP = JSON.parse(
    localStorage.getItem('settings')
  ).enableRealIP;
  const realIP = JSON.parse(localStorage.getItem('settings')).realIP;
  if (process.env.VUE_APP_REAL_IP) {
    config.params.realIP = process.env.VUE_APP_REAL_IP;
  } else if (enableRealIP) {
    config.params.realIP = realIP;
  }

  const proxy = JSON.parse(localStorage.getItem('settings')).proxyConfig;
  if (['HTTP', 'HTTPS'].includes(proxy.protocol)) {
    config.params.proxy = `${proxy.protocol}://${proxy.server}:${proxy.port}`;
  }

  return config;
});

service.interceptors.response.use(
  response => {
    const res = response.data;
    if (
      shouldRetryAndroidRequest(response.config, res?.code) &&
      RETRYABLE_HTTP_STATUS.has(Number(res?.code))
    ) {
      return retryAndroidRequest(response.config);
    }
    return res;
  },
  async error => {
    /** @type {import('axios').AxiosResponse | null} */
    let response;
    let data;
    if (error === 'TypeError: baseURL is undefined') {
      response = error;
      data = error;
      console.error("You must set up the baseURL in the service's config");
    } else if (error.response) {
      response = error.response;
      data = response.data;
    }

    if (shouldRetryAndroidRequest(error.config, response?.status)) {
      return retryAndroidRequest(error.config);
    }

    if (
      response &&
      typeof data === 'object' &&
      data.code === 301 &&
      data.msg === '需要登录'
    ) {
      console.warn('Token has expired. Logout now!');

      // 登出帳戶
      doLogout();

      // 導向登入頁面
      if (
        process.env.IS_ELECTRON === true ||
        process.env.VUE_APP_PLATFORM === 'android'
      ) {
        router.push({ name: 'loginAccount' });
      } else {
        router.push({ name: 'login' });
      }
    }

    return Promise.reject(error);
  }
);

export default service;
