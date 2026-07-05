<template>
  <div class="login">
    <div class="login-container">
      <div class="section-1">
        <img src="/img/logos/netease-music.png" />
      </div>
      <div class="title">{{ $t('login.loginText') }}</div>
      <div class="section-2">
        <div v-show="mode === 'phone'" class="input-box">
          <div
            class="container"
            :class="{ active: ['phone', 'countryCode'].includes(inputFocus) }"
          >
            <svg-icon icon-class="mobile" />
            <div class="inputs">
              <input
                id="countryCode"
                v-model="countryCode"
                :placeholder="
                  inputFocus === 'countryCode' ? '' : $t('login.countryCode')
                "
                @focus="inputFocus = 'countryCode'"
                @blur="inputFocus = ''"
                @keyup.enter="login"
              />
              <input
                id="phoneNumber"
                v-model="phoneNumber"
                :placeholder="inputFocus === 'phone' ? '' : $t('login.phone')"
                @focus="inputFocus = 'phone'"
                @blur="inputFocus = ''"
                @keyup.enter="login"
              />
            </div>
          </div>
        </div>

        <div v-show="mode === 'email'" class="input-box">
          <div class="container" :class="{ active: inputFocus === 'email' }">
            <svg-icon icon-class="mail" />
            <div class="inputs">
              <input
                id="email"
                v-model="email"
                type="email"
                :placeholder="inputFocus === 'email' ? '' : $t('login.email')"
                @focus="inputFocus = 'email'"
                @blur="inputFocus = ''"
                @keyup.enter="login"
              />
            </div>
          </div>
        </div>
        <div v-show="['phone', 'email'].includes(mode)" class="input-box">
          <div class="container" :class="{ active: inputFocus === 'password' }">
            <svg-icon icon-class="lock" />
            <div class="inputs">
              <input
                id="password"
                v-model="password"
                type="password"
                :placeholder="
                  inputFocus === 'password' ? '' : $t('login.password')
                "
                @focus="inputFocus = 'password'"
                @blur="inputFocus = ''"
                @keyup.enter="login"
              />
            </div>
          </div>
        </div>

        <div v-show="mode == 'qrCode' && !isAndroid">
          <div v-show="qrCodeSvg" class="qr-code-container">
            <img :src="qrCodeSvg" loading="lazy" />
          </div>
          <div class="qr-code-info">
            {{ qrCodeInformation }}
          </div>
        </div>
        <div v-show="mode == 'qrCode' && isAndroid" class="native-qr-login">
          <button
            v-show="!processing"
            type="button"
            @click="startOfficialQrLogin"
          >
            打开网易云扫码登录
          </button>
          <button v-show="processing" class="loading" disabled>
            <span></span>
            <span></span>
            <span></span>
          </button>
          <div class="qr-code-info">{{ qrCodeInformation }}</div>
        </div>
      </div>
      <div v-show="mode !== 'qrCode'" class="confirm">
        <button v-show="!processing" @click="login">
          {{ $t('login.login') }}
        </button>
        <button v-show="processing" class="loading" disabled>
          <span></span>
          <span></span>
          <span></span>
        </button>
      </div>
      <div v-if="loginError" class="login-error">{{ loginError }}</div>
      <div class="other-login">
        <template v-for="(item, index) in otherLoginModes">
          <span v-if="index > 0" :key="`${item.mode}-separator`">|</span>
          <a :key="item.mode" @click="changeMode(item.mode)">
            {{ item.label }}
          </a>
        </template>
      </div>
      <div
        v-if="mode !== 'qrCode'"
        class="notice"
        v-html="isElectron ? $t('login.noticeElectron') : $t('login.notice')"
      ></div>
    </div>
  </div>
</template>

<script>
import QRCode from 'qrcode';
import md5 from 'crypto-js/md5';
import NProgress from 'nprogress';
import { Capacitor, registerPlugin } from '@capacitor/core';
import { mapMutations } from 'vuex';
import { getCookie, removeCookie, setCookies } from '@/utils/auth';
import nativeAlert from '@/utils/nativeAlert';
import {
  loginWithPhone,
  loginWithEmail,
  loginQrCodeKey,
  loginQrCodeCreate,
  loginQrCodeCheck,
} from '@/api/auth';

const NeteaseWebLogin = registerPlugin('NeteaseWebLogin');

export default {
  name: 'Login',
  data() {
    return {
      processing: false,
      mode: 'qrCode',
      countryCode: '+86',
      phoneNumber: '',
      email: '',
      password: '',
      smsCode: '',
      inputFocus: '',
      qrCodeKey: '',
      qrCodeSvg: '',
      qrCodeCheckInterval: null,
      qrCodeChecking: false,
      loginError: '',
      qrCodeInformation: '打开网易云音乐APP扫码登录',
    };
  },
  computed: {
    isElectron() {
      return process.env.IS_ELECTRON;
    },
    isAndroid() {
      return (
        process.env.VUE_APP_PLATFORM === 'android' &&
        Capacitor.getPlatform() === 'android'
      );
    },
    otherLoginModes() {
      return [
        { mode: 'qrCode', label: '二维码登录' },
        { mode: 'phone', label: this.$t('login.loginWithPhone') },
        { mode: 'email', label: this.$t('login.loginWithEmail') },
      ].filter(item => item.mode !== this.mode);
    },
  },
  created() {
    if (['phone', 'email', 'qrCode'].includes(this.$route.query.mode)) {
      this.mode = this.$route.query.mode;
    }
    if (this.mode === 'qrCode') {
      if (this.isAndroid) {
        this.startOfficialQrLogin();
      } else {
        this.getQrCodeKey();
      }
    }
  },
  beforeDestroy() {
    clearInterval(this.qrCodeCheckInterval);
  },
  methods: {
    ...mapMutations(['updateData']),
    validatePhone() {
      if (
        this.countryCode === '' ||
        this.phoneNumber === '' ||
        this.password === ''
      ) {
        nativeAlert('国家区号或手机号不正确');
        this.processing = false;
        return false;
      }
      return true;
    },
    validateEmail() {
      const emailReg =
        /^(([^<>()[\]\\.,;:\s@"]+(\.[^<>()[\]\\.,;:\s@"]+)*)|(".+"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/;
      if (
        this.email === '' ||
        this.password === '' ||
        !emailReg.test(this.email)
      ) {
        nativeAlert('邮箱不正确');
        return false;
      }
      return true;
    },
    login() {
      this.loginError = '';
      if (this.mode === 'phone') {
        this.processing = this.validatePhone();
        if (!this.processing) return;
        loginWithPhone({
          countrycode: this.countryCode.replace('+', '').replace(/\s/g, ''),
          phone: this.phoneNumber.replace(/\s/g, ''),
          password: 'fakePassword',
          md5_password: md5(this.password).toString(),
        })
          .then(this.handleLoginResponse)
          .catch(error => {
            this.processing = false;
            if (error.response?.data) {
              this.handleLoginResponse(error.response.data);
              return;
            }
            this.loginError =
              '登录请求失败。网易云可能要求安全验证，请改用二维码登录。';
            console.error(error);
          });
      } else if (this.mode === 'email') {
        this.processing = this.validateEmail();
        if (!this.processing) return;
        loginWithEmail({
          email: this.email.replace(/\s/g, ''),
          password: 'fakePassword',
          md5_password: md5(this.password).toString(),
        })
          .then(this.handleLoginResponse)
          .catch(error => {
            this.processing = false;
            if (error.response?.data) {
              this.handleLoginResponse(error.response.data);
              return;
            }
            this.loginError =
              '登录请求失败。账号密码登录目前容易触发网易云风控，请改用二维码登录。';
            console.error(error);
          });
      }
    },
    async startOfficialQrLogin() {
      this.loginError = '';
      this.processing = true;
      this.qrCodeInformation = '正在打开网易云官方扫码登录...';
      try {
        const result = await NeteaseWebLogin.start();
        if (!result?.cookie) {
          throw new Error('网易云登录成功，但没有读取到 Cookie');
        }
        this.qrCodeInformation = '扫码成功，正在自动同步登录状态...';
        await this.completeAccountLogin(result.cookie, true);
      } catch (error) {
        console.error(error);
        this.processing = false;
        this.qrCodeInformation =
          error?.message === '已取消扫码登录'
            ? '已取消扫码登录'
            : '扫码登录未完成，请点击按钮重试';
      }
    },
    handleLoginResponse(data) {
      if (!data) {
        this.processing = false;
        return;
      }
      if (data.code === 200) {
        if (!data.cookie) {
          this.processing = false;
          this.loginError =
            '登录成功，但服务器没有返回登录凭证，请使用二维码重试。';
          return;
        }
        this.completeAccountLogin(data.cookie);
      } else {
        this.processing = false;
        if (data.code === -462 || data.data?.verifyUrl) {
          this.loginError =
            '网易云要求额外安全验证，账号密码登录暂不可用，请使用二维码登录。';
          return;
        }
        this.loginError =
          data.msg ?? data.message ?? '账号或密码错误，请检查后重试。';
      }
    },
    async completeAccountLogin(cookie, authOnly = false) {
      this.processing = true;
      if (authOnly) {
        removeCookie('MUSIC_U');
        removeCookie('__csrf');
        setCookies(cookie, ['MUSIC_U', '__csrf']);
      } else {
        setCookies(cookie);
      }
      if (!getCookie('MUSIC_U')) {
        this.processing = false;
        this.loginError = '未能保存网易云登录凭证，请重新扫码。';
        return;
      }

      this.updateData({ key: 'user', value: {} });
      this.updateData({ key: 'loginMode', value: 'account' });
      try {
        await this.$store.dispatch('fetchUserProfile');
        if (!this.$store.state.data.user?.userId) {
          throw new Error('网易云账号资料为空');
        }
        this.$store.dispatch('fetchLikedPlaylist');
        this.$router.push({ path: '/library' });
      } catch (error) {
        console.error(error);
        this.processing = false;
        this.updateData({ key: 'loginMode', value: null });
        this.loginError =
          'Cookie 已保存，但读取账号资料失败。请确认 Cookie 未过期，并检查 API 地址。';
      }
    },
    async getQrCodeKey() {
      clearInterval(this.qrCodeCheckInterval);
      this.qrCodeKey = '';
      this.qrCodeSvg = '';
      this.qrCodeInformation = '正在生成登录二维码...';
      try {
        const result = await loginQrCodeKey();
        if (result?.code !== 200 || !result.data?.unikey) {
          throw new Error(result?.message || '二维码 key 生成失败');
        }

        this.qrCodeKey = result.data.unikey;
        const qrResult = await loginQrCodeCreate({
          key: this.qrCodeKey,
          qrimg: true,
          ua: 'pc',
        });
        if (qrResult?.data?.qrimg) {
          this.qrCodeSvg = qrResult.data.qrimg;
        } else {
          QRCode.toString(
            qrResult?.data?.qrurl ||
              `https://music.163.com/login?codekey=${this.qrCodeKey}`,
            {
              width: 192,
              margin: 0,
              color: {
                dark: '#335eea',
                light: '#00000000',
              },
              type: 'svg',
            }
          )
            .then(svg => {
              this.qrCodeSvg = `data:image/svg+xml;utf8,${encodeURIComponent(
                svg
              )}`;
            })
            .catch(err => {
              console.error(err);
            });
        }
        this.qrCodeInformation = '打开网易云音乐APP扫码并确认登录';
        this.checkQrCodeLogin();
      } catch (error) {
        console.error(error);
        this.qrCodeInformation = '二维码生成失败，请检查网络或 API 地址后重试';
      } finally {
        NProgress.done();
      }
    },
    checkQrCodeLogin() {
      clearInterval(this.qrCodeCheckInterval);
      const check = async () => {
        if (this.qrCodeKey === '' || this.qrCodeChecking) return;
        this.qrCodeChecking = true;
        try {
          const result = await loginQrCodeCheck(this.qrCodeKey);
          if (!result) throw new Error('二维码状态查询失败');
          if (result.code === 800) {
            clearInterval(this.qrCodeCheckInterval);
            this.qrCodeInformation = '二维码已失效，正在刷新...';
            setTimeout(() => this.getQrCodeKey(), 800);
          } else if (result.code === 802) {
            this.qrCodeInformation = '扫描成功，请在网易云音乐中确认登录';
          } else if (result.code === 801) {
            this.qrCodeInformation = '打开网易云音乐APP扫码并确认登录';
          } else if (result.code === 803) {
            clearInterval(this.qrCodeCheckInterval);
            this.qrCodeInformation = '登录成功，正在读取账号资料...';
            result.code = 200;
            result.cookie = (result.cookie || '').replace(/ HTTPOnly/g, '');
            this.handleLoginResponse(result);
          }
        } catch (error) {
          console.error(error);
          if (error.response?.status === 404) {
            clearInterval(this.qrCodeCheckInterval);
            this.qrCodeInformation = '二维码状态已失效，正在重新生成...';
            setTimeout(() => this.getQrCodeKey(), 800);
          } else {
            this.qrCodeInformation = '登录状态查询失败，正在自动重试...';
          }
        } finally {
          this.qrCodeChecking = false;
        }
      };
      check();
      this.qrCodeCheckInterval = setInterval(check, 3000);
    },
    changeMode(mode) {
      this.loginError = '';
      this.mode = mode;
      if (mode === 'qrCode') {
        if (this.isAndroid) {
          this.startOfficialQrLogin();
        } else if (this.qrCodeKey) {
          this.checkQrCodeLogin();
        } else {
          this.getQrCodeKey();
        }
      } else {
        clearInterval(this.qrCodeCheckInterval);
      }
    },
  },
};
</script>

<style lang="scss" scoped>
.login {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  margin-top: 32px;
}

.login-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.title {
  font-size: 24px;
  font-weight: 700;
  margin-bottom: 48px;
  color: var(--color-text);
}

.section-1 {
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  img {
    height: 64px;
    margin: 20px;
    user-select: none;
  }
}

.section-2 {
  display: flex;
  align-items: center;
  flex-direction: column;
}

.input-box {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 16px;
  color: var(--color-text);

  .container {
    display: flex;
    align-items: center;
    height: 46px;
    background: var(--color-secondary-bg);
    border-radius: 8px;
    width: 300px;
  }

  .svg-icon {
    height: 18px;
    width: 18px;
    color: #aaaaaa;
    margin: {
      left: 12px;
      right: 6px;
    }
  }

  .inputs {
    display: flex;
    width: 85%;
  }

  input {
    font-size: 20px;
    border: none;
    background: transparent;
    width: 100%;
    font-weight: 600;
    margin-top: -1px;
    color: var(--color-text);
  }

  input::placeholder {
    color: var(--color-text);
    opacity: 0.38;
  }

  input#countryCode {
    flex: 3;
  }
  input#phoneNumber {
    flex: 12;
  }

  .active {
    background: var(--color-primary-bg);
    input,
    .svg-icon {
      color: var(--color-primary);
    }
  }
}

.confirm button {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  font-weight: 600;
  background-color: var(--color-primary-bg);
  color: var(--color-primary);
  border-radius: 8px;
  margin-top: 24px;
  transition: 0.2s;
  padding: 8px;
  width: 100%;
  width: 300px;
  &:hover {
    transform: scale(1.06);
  }
  &:active {
    transform: scale(0.94);
  }
}

.other-login {
  margin-top: 24px;
  font-size: 13px;
  color: var(--color-text);
  opacity: 0.68;
  a {
    padding: 0 8px;
  }
}

.notice {
  width: 300px;
  border-top: 1px solid rgba(128, 128, 128);
  margin-top: 48px;
  padding-top: 12px;
  font-size: 12px;
  color: var(--color-text);
  opacity: 0.48;
}

.login-error {
  box-sizing: border-box;
  width: 300px;
  margin-top: 16px;
  color: #d33a31;
  font-size: 13px;
  line-height: 1.5;
  text-align: center;
}

.native-qr-login {
  display: flex;
  width: 300px;
  flex-direction: column;
  align-items: center;

  > button {
    display: flex;
    width: 300px;
    min-height: 44px;
    align-items: center;
    justify-content: center;
    border-radius: 8px;
    background: var(--color-primary-bg);
    color: var(--color-primary);
    font-size: 17px;
    font-weight: 600;
  }

  .qr-code-info {
    margin-top: 16px;
  }
}

@keyframes loading {
  0% {
    opacity: 0.2;
  }
  20% {
    opacity: 1;
  }
  100% {
    opacity: 0.2;
  }
}

button.loading {
  height: 44px;
  cursor: unset;
  &:hover {
    transform: none;
  }
}
.loading span {
  width: 6px;
  height: 6px;
  background-color: var(--color-primary);
  border-radius: 50%;
  margin: 0 2px;
  animation: loading 1.4s infinite both;
}

.loading span:nth-child(2) {
  animation-delay: 0.2s;
}

.loading span:nth-child(3) {
  animation-delay: 0.4s;
}

.qr-code-container {
  background-color: var(--color-primary-bg);
  padding: 24px 24px 21px 24px;
  border-radius: 1.25rem;
  margin-bottom: 12px;
}
.qr-code-info {
  color: var(--color-text);
  text-align: center;
  margin-bottom: 28px;
}
</style>
