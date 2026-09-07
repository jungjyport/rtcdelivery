export interface SignupForm {
  username: string
  password: string
  passwordConfirm: string
  nickname: string
  email: string
}

export type SignupField = 'username' | 'password' | 'passwordConfirm' | 'nickname' | 'email'

export interface LoginForm {
  username: string
  password: string
}

export type LoginField = 'username' | 'password'

const USERNAME_PATTERN = /^[a-z0-9_]+$/
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

const SIGNUP_FIELDS: readonly SignupField[] = [
  'username',
  'password',
  'passwordConfirm',
  'nickname',
  'email',
]

const DUPLICATE_FIELD: Partial<Record<string, SignupField>> = {
  DUPLICATE_USERNAME: 'username',
  DUPLICATE_EMAIL: 'email',
  DUPLICATE_NICKNAME: 'nickname',
}

function isSignupField(key: string): key is SignupField {
  return (SIGNUP_FIELDS as readonly string[]).includes(key)
}

function isLoginField(key: string): key is LoginField {
  return key === 'username' || key === 'password'
}

/** 통과하면 빈 객체. 실패하면 필드 → i18n 키 */
export function validateSignup(form: SignupForm): Partial<Record<SignupField, string>> {
  const errors: Partial<Record<SignupField, string>> = {}

  if (!form.username) {
    errors.username = 'auth.validation.usernameRequired'
  }
  else if (form.username.length < 4 || form.username.length > 20) {
    errors.username = 'auth.validation.usernameLength'
  }
  else if (!USERNAME_PATTERN.test(form.username)) {
    errors.username = 'auth.validation.usernamePattern'
  }

  if (!form.password) {
    errors.password = 'auth.validation.passwordRequired'
  }
  else if (form.password.length < 4 || form.password.length > 64) {
    errors.password = 'auth.validation.passwordLength'
  }

  if (!form.passwordConfirm) {
    errors.passwordConfirm = 'auth.validation.passwordConfirmRequired'
  }
  else if (form.passwordConfirm !== form.password) {
    errors.passwordConfirm = 'auth.validation.passwordMismatch'
  }

  const nickname = form.nickname.trim()
  if (!nickname) {
    errors.nickname = 'auth.validation.nicknameRequired'
  }
  else if (nickname.length < 2 || nickname.length > 20) {
    errors.nickname = 'auth.validation.nicknameLength'
  }

  const email = form.email.trim()
  if (email) {
    if (email.length > 100) {
      errors.email = 'auth.validation.emailLength'
    }
    else if (!EMAIL_PATTERN.test(email)) {
      errors.email = 'auth.validation.emailInvalid'
    }
  }

  return errors
}

export function validateLogin(form: LoginForm): Partial<Record<LoginField, string>> {
  const errors: Partial<Record<LoginField, string>> = {}
  if (!form.username.trim()) errors.username = 'auth.validation.usernameRequired'
  if (!form.password.trim()) errors.password = 'auth.validation.passwordRequired'
  return errors
}

/** 오픈 리다이렉트 방지. 같은 오리진의 상대 경로만 허용한다. */
export function getSafeRedirect(value: unknown): string | null {
  if (typeof value !== 'string' || value.length === 0) return null
  if (value.startsWith('http://') || value.startsWith('https://')) return null
  if (!value.startsWith('/') || value.startsWith('//')) return null
  if (value.includes('\\')) return null
  return value
}

export function signupFieldErrorsFromApi(
  code: string,
  data: unknown,
  form: SignupForm,
): Partial<Record<SignupField, string>> {
  const errors: Partial<Record<SignupField, string>> = {}
  const duplicateField = DUPLICATE_FIELD[code]
  if (duplicateField) {
    errors[duplicateField] = `error.${code}`
  }

  if (code === 'VALIDATION_ERROR' && isPlainObject(data)) {
    const clientErrors = validateSignup(form)
    for (const key of Object.keys(data)) {
      if (!isSignupField(key) || key === 'passwordConfirm') continue
      errors[key] = clientErrors[key] ?? 'error.VALIDATION_ERROR'
    }
  }

  return errors
}

export function loginFieldErrorsFromApi(
  code: string,
  data: unknown,
): Partial<Record<LoginField, string>> {
  const errors: Partial<Record<LoginField, string>> = {}
  if (code !== 'VALIDATION_ERROR' || !isPlainObject(data)) return errors

  for (const key of Object.keys(data)) {
    if (!isLoginField(key)) continue
    errors[key] = key === 'username'
      ? 'auth.validation.usernameRequired'
      : 'auth.validation.passwordRequired'
  }
  return errors
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}
