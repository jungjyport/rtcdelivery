<script setup lang="ts">
import { ApiError } from '~/types/api'
import type { LoginField, LoginForm } from '~/utils/auth-validation'
import { loginFieldErrorsFromApi, validateLogin } from '~/utils/auth-validation'
import AuthFormShell from '~/components/auth/AuthFormShell.vue'

definePageMeta({
  middleware: 'guest',
})

const { t, te } = useI18n()
const route = useRoute()
const { login, isSubmitting, formError } = useAuth()

const form = reactive<LoginForm>({
  username: '',
  password: '',
})
const fieldErrors = ref<Partial<Record<LoginField, string>>>({})
const showPassword = ref(false)

const showRegistered = computed(() => route.query.registered === '1')

useHead({
  title: () => t('auth.loginTitle'),
})

function tOrFallback(key: string) {
  return te(key) ? t(key) : t('error.UNKNOWN_ERROR')
}

function inputClass(hasError: boolean, extra = '') {
  return [
    'w-full rounded-xl border bg-white px-4 py-2.5 text-surface-900 placeholder-surface-400 outline-none transition-colors',
    'focus:ring-2 focus:ring-primary-500/20',
    hasError ? 'border-red-500 focus:border-red-500' : 'border-surface-200 focus:border-primary-500',
    extra,
  ].join(' ')
}

function onBlur(field: LoginField) {
  const key = validateLogin(form)[field]
  if (key) fieldErrors.value[field] = key
  else delete fieldErrors.value[field]
}

async function onSubmit() {
  formError.value = null
  const errors = validateLogin(form)
  fieldErrors.value = errors
  if (Object.keys(errors).length > 0) return

  try {
    await login({
      username: form.username.trim(),
      password: form.password,
    })
  }
  catch (error) {
    if (error instanceof ApiError) {
      fieldErrors.value = loginFieldErrorsFromApi(error.code, error.data)
    }
  }
}
</script>

<template>
  <AuthFormShell :title="t('auth.loginTitle')">
    <form
      class="space-y-4"
      novalidate
      @submit.prevent="onSubmit"
    >
      <div
        v-if="showRegistered"
        class="rounded-xl bg-emerald-50 px-4 py-3 text-sm text-emerald-800"
        role="status"
      >
        {{ t('auth.signupSuccess') }}
      </div>

      <p
        v-if="formError"
        class="rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700"
        role="alert"
      >
        {{ tOrFallback(formError) }}
      </p>

      <div>
        <label for="login-username" class="mb-1.5 block text-sm font-medium text-surface-700">
          {{ t('auth.usernameLabel') }}
        </label>
        <input
          id="login-username"
          v-model="form.username"
          type="text"
          name="username"
          autocomplete="username"
          autocapitalize="off"
          autocorrect="off"
          spellcheck="false"
          :class="inputClass(!!fieldErrors.username)"
          @blur="onBlur('username')"
        >
        <p v-if="fieldErrors.username" class="mt-1.5 text-sm text-red-600">
          {{ tOrFallback(fieldErrors.username) }}
        </p>
      </div>

      <div>
        <label for="login-password" class="mb-1.5 block text-sm font-medium text-surface-700">
          {{ t('auth.passwordLabel') }}
        </label>
        <div class="relative">
          <input
            id="login-password"
            v-model="form.password"
            :type="showPassword ? 'text' : 'password'"
            name="password"
            autocomplete="current-password"
            :class="inputClass(!!fieldErrors.password, 'pr-12')"
            @blur="onBlur('password')"
          >
          <button
            type="button"
            class="absolute top-1/2 right-3 -translate-y-1/2 text-surface-400 hover:text-surface-600"
            :aria-label="showPassword ? t('auth.hidePassword') : t('auth.showPassword')"
            @click="showPassword = !showPassword"
          >
            <svg
              v-if="showPassword"
              xmlns="http://www.w3.org/2000/svg"
              fill="none"
              viewBox="0 0 24 24"
              stroke-width="1.5"
              stroke="currentColor"
              class="h-5 w-5"
            >
              <path stroke-linecap="round" stroke-linejoin="round" d="M3.98 8.223A10.477 10.477 0 0 0 1.934 12C3.226 16.338 7.244 19.5 12 19.5c.993 0 1.953-.138 2.863-.395M6.228 6.228A10.451 10.451 0 0 1 12 4.5c4.756 0 8.773 3.162 10.065 7.498a10.522 10.522 0 0 1-4.293 5.774M6.228 6.228 3 3m3.228 3.228 3.65 3.65m7.894 7.894L21 21m-3.228-3.228-3.65-3.65m0 0a3 3 0 1 0-4.243-4.243m4.242 4.242L9.88 9.88" />
            </svg>
            <svg
              v-else
              xmlns="http://www.w3.org/2000/svg"
              fill="none"
              viewBox="0 0 24 24"
              stroke-width="1.5"
              stroke="currentColor"
              class="h-5 w-5"
            >
              <path stroke-linecap="round" stroke-linejoin="round" d="M2.036 12.322a1.012 1.012 0 0 1 0-.639C3.423 7.51 7.36 4.5 12 4.5c4.638 0 8.573 3.007 9.963 7.178.07.207.07.431 0 .639C20.577 16.49 16.64 19.5 12 19.5c-4.638 0-8.573-3.007-9.963-7.178Z" />
              <path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z" />
            </svg>
          </button>
        </div>
        <p v-if="fieldErrors.password" class="mt-1.5 text-sm text-red-600">
          {{ tOrFallback(fieldErrors.password) }}
        </p>
      </div>

      <button
        type="submit"
        class="btn-primary w-full disabled:cursor-not-allowed disabled:opacity-60 disabled:shadow-none"
        :disabled="isSubmitting"
        :aria-busy="isSubmitting"
      >
        {{ t('auth.submitLogin') }}
      </button>
    </form>

    <template #footer>
      <NuxtLink
        to="/auth/signup"
        class="font-medium text-primary-600 transition-colors hover:text-primary-500"
      >
        {{ t('auth.goSignup') }}
      </NuxtLink>
    </template>
  </AuthFormShell>
</template>
