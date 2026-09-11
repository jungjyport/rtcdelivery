// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: '2025-07-15',
  devtools: { enabled: true },

  modules: [
    '@pinia/nuxt',
    '@nuxtjs/tailwindcss',
    '@nuxtjs/i18n',
  ],

  // Pinia 설정
  pinia: {
    storesDirs: ['./app/stores/**'],
  },

  // Tailwind CSS 설정
  tailwindcss: {
    cssPath: '~/assets/css/tailwind.css',
    configPath: 'tailwind.config.js',
  },

  // i18n 설정
  i18n: {
    locales: [
      { code: 'ko', name: '한국어', file: 'ko.json' },
      { code: 'ja', name: '日本語', file: 'ja.json' },
    ],
    defaultLocale: 'ko',
    langDir: 'locales',
    strategy: 'no_prefix',
    detectBrowserLanguage: {
      useCookie: true,
      cookieKey: 'i18n_locale',
      redirectOn: 'root',
      alwaysRedirect: false,
    },
    vueI18n: './i18n.config.ts',
  },

  // Runtime Config (환경변수)
  // Nuxt는 NUXT_API_BASE_URL, NUXT_PUBLIC_API_BASE_URL 환경변수로 런타임에 자동 오버라이드합니다.
  runtimeConfig: {
    // SSR 서버 전용 (Docker 컨테이너 내부에서는 NUXT_API_BASE_URL=http://api-gateway:8080/api/v1 로 통신)
    apiBaseUrl: 'http://localhost:8080/api/v1',
    // 클라이언트 브라우저 전용 (사용자 PC 호스트에서 http://localhost:8080/api/v1 로 통신)
    public: {
      apiBaseUrl: 'http://localhost:8080/api/v1',
    },
  },

  // 앱 설정
  app: {
    head: {
      meta: [
        { charset: 'utf-8' },
        { name: 'viewport', content: 'width=device-width, initial-scale=1' },
      ],
      link: [
        { rel: 'preconnect', href: 'https://fonts.googleapis.com' },
        { rel: 'preconnect', href: 'https://fonts.gstatic.com', crossorigin: '' },
        { rel: 'stylesheet', href: 'https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800;900&family=Noto+Sans+KR:wght@300;400;500;600;700;900&family=Noto+Sans+JP:wght@300;400;500;600;700;900&display=swap' },
      ],
    },
  },
})
