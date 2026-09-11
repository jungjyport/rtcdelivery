<script setup lang="ts">
import { ref, computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '~/stores/authStore'
import type { MemberResponse } from '~/types/catalog'

definePageMeta({
  middleware: 'admin',
})

const { t } = useI18n()
const authStore = useAuthStore()
const { user } = storeToRefs(authStore)
const { updateMemberRole, isSubmitting, errorMessage, successResult } = useRoleManagement()

useHead({
  title: () => `${t('admin.title')} - RTC Delivery`,
})

const targetMemberId = ref<number | null>(null)
const selectedRole = ref<string>('ROLE_OWNER')
const successFeedback = ref<MemberResponse | null>(null)

// 본인 ID 변경 시도 검출
const isSelfTarget = computed(() => {
  if (!user.value?.id || !targetMemberId.value) return false
  return Number(user.value.id) === Number(targetMemberId.value)
})

async function onSubmitRoleChange() {
  if (!targetMemberId.value || isSelfTarget.value) return

  try {
    const res = await updateMemberRole(targetMemberId.value, selectedRole.value)
    successFeedback.value = res
  } catch (e) {
    successFeedback.value = null
  }
}
</script>

<template>
  <div class="min-h-screen bg-surface-50 pt-20 pb-20">
    <div class="max-w-3xl mx-auto px-4 sm:px-6 lg:px-8">
      <!-- 페이지 헤더 -->
      <div class="mb-8">
        <div class="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-purple-50 text-purple-700 text-xs font-bold mb-2">
          🛡️ 최고 관리자(ADMIN) 전용
        </div>
        <h1 class="text-3xl font-black text-surface-900">
          {{ t('admin.title') }}
        </h1>
        <p class="text-surface-500 text-sm mt-1">
          {{ t('admin.subtitle') }}
        </p>
      </div>

      <!-- 정책 및 토큰 안내 배너 -->
      <div class="mb-8 p-4 rounded-2xl bg-amber-50 border border-amber-200/80 flex items-start gap-3 text-amber-900 text-xs sm:text-sm">
        <span class="text-lg shrink-0">⚠️</span>
        <div class="space-y-1">
          <p class="font-bold">역할 변경 및 토큰 반영 정책</p>
          <p class="text-amber-800 leading-relaxed">
            {{ t('admin.tokenNotice') }}
            이미 발급된 회원의 Access Token은 수명(30분) 만료 전까지 이전 권한을 유지하므로, 즉시 점주 기능을 적용하려면 재로그인이 권장됩니다.
          </p>
        </div>
      </div>

      <!-- 역할 변경 카드 -->
      <div class="card bg-white p-6 sm:p-8 shadow-xl shadow-surface-900/5 mb-8 border border-surface-200/80">
        <form @submit.prevent="onSubmitRoleChange" class="space-y-6">
          <!-- 회원 번호 입력 -->
          <div>
            <label class="block text-xs font-bold text-surface-700 mb-1.5">
              {{ t('admin.memberIdLabel') }} *
            </label>
            <input
              v-model.number="targetMemberId"
              type="number"
              min="1"
              placeholder="예: 2 (회원 고유 ID)"
              required
              class="w-full py-3 px-4 rounded-xl border border-surface-200 text-sm outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-500/10 transition-all font-mono"
            />
            <p v-if="isSelfTarget" class="mt-2 text-xs font-bold text-red-600 flex items-center gap-1">
              <span>🚫</span>
              <span>{{ t('admin.selfChangeWarning') }}</span>
            </p>
          </div>

          <!-- 변경할 권한 선택 -->
          <div>
            <label class="block text-xs font-bold text-surface-700 mb-2">
              {{ t('admin.roleLabel') }} *
            </label>
            <div class="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <!-- 점주 승격 라디오 -->
              <label
                class="flex flex-col p-4 rounded-xl border-2 cursor-pointer transition-all text-center"
                :class="selectedRole === 'ROLE_OWNER'
                  ? 'border-primary-500 bg-primary-50/50 shadow-sm'
                  : 'border-surface-200 hover:border-surface-300 bg-white'"
              >
                <input
                  v-model="selectedRole"
                  type="radio"
                  value="ROLE_OWNER"
                  class="sr-only"
                />
                <span class="text-2xl mb-1">👨‍🍳</span>
                <span class="font-bold text-sm text-surface-900">{{ t('admin.roleOwner') }}</span>
                <span class="text-[11px] text-surface-500 mt-1">매장·메뉴 등록/관리 가능</span>
              </label>

              <!-- 일반 회원 강등 라디오 -->
              <label
                class="flex flex-col p-4 rounded-xl border-2 cursor-pointer transition-all text-center"
                :class="selectedRole === 'ROLE_USER'
                  ? 'border-primary-500 bg-primary-50/50 shadow-sm'
                  : 'border-surface-200 hover:border-surface-300 bg-white'"
              >
                <input
                  v-model="selectedRole"
                  type="radio"
                  value="ROLE_USER"
                  class="sr-only"
                />
                <span class="text-2xl mb-1">👤</span>
                <span class="font-bold text-sm text-surface-900">{{ t('admin.roleUser') }}</span>
                <span class="text-[11px] text-surface-500 mt-1">일반 주문 고객</span>
              </label>

              <!-- 관리자 라디오 -->
              <label
                class="flex flex-col p-4 rounded-xl border-2 cursor-pointer transition-all text-center"
                :class="selectedRole === 'ROLE_ADMIN'
                  ? 'border-primary-500 bg-primary-50/50 shadow-sm'
                  : 'border-surface-200 hover:border-surface-300 bg-white'"
              >
                <input
                  v-model="selectedRole"
                  type="radio"
                  value="ROLE_ADMIN"
                  class="sr-only"
                />
                <span class="text-2xl mb-1">👑</span>
                <span class="font-bold text-sm text-surface-900">{{ t('admin.roleAdmin') }}</span>
                <span class="text-[11px] text-surface-500 mt-1">최고 시스템 관리자</span>
              </label>
            </div>
          </div>

          <!-- 에러 메시지 -->
          <div
            v-if="errorMessage"
            class="p-3 rounded-xl bg-red-50 text-red-600 text-xs font-semibold flex items-center gap-2"
            role="alert"
          >
            <span>⚠️</span>
            <span>{{ t(errorMessage) || errorMessage }}</span>
          </div>

          <!-- 제출 버튼 -->
          <div>
            <button
              type="submit"
              class="w-full btn-primary !rounded-xl !py-3 font-bold text-sm flex items-center justify-center gap-2"
              :disabled="isSubmitting || isSelfTarget || !targetMemberId"
            >
              <span>{{ isSubmitting ? '권한 변경 처리 중...' : t('admin.submitChange') }}</span>
            </button>
          </div>
        </form>
      </div>

      <!-- 성공 결과 카드 -->
      <Transition
        enter-active-class="transition duration-300 ease-out"
        enter-from-class="transform -translate-y-2 opacity-0"
        enter-to-class="transform translate-y-0 opacity-100"
      >
        <div
          v-if="successFeedback"
          class="card bg-emerald-50/80 border border-emerald-200 p-6 sm:p-8 rounded-2xl"
        >
          <div class="flex items-center gap-3 mb-4">
            <span class="w-8 h-8 rounded-full bg-emerald-500 text-white flex items-center justify-center font-bold text-sm">
              ✓
            </span>
            <h3 class="font-bold text-base text-emerald-900">
              {{ t('admin.changeSuccess') }}
            </h3>
          </div>

          <div class="bg-white p-4 rounded-xl space-y-2 text-xs sm:text-sm text-surface-700">
            <div class="flex justify-between border-b border-surface-100 pb-2">
              <span class="text-surface-500">회원 ID:</span>
              <span class="font-bold font-mono">{{ successFeedback.id }}</span>
            </div>
            <div class="flex justify-between border-b border-surface-100 pb-2">
              <span class="text-surface-500">아이디 (Username):</span>
              <span class="font-bold font-mono">{{ successFeedback.username }}</span>
            </div>
            <div class="flex justify-between border-b border-surface-100 pb-2">
              <span class="text-surface-500">닉네임:</span>
              <span class="font-bold">{{ successFeedback.nickname }}</span>
            </div>
            <div class="flex justify-between items-center pt-1">
              <span class="text-surface-500">적용된 새 권한:</span>
              <span class="px-3 py-1 rounded-full bg-primary-50 text-primary-700 font-extrabold text-xs">
                {{ successFeedback.role }}
              </span>
            </div>
          </div>
        </div>
      </Transition>
    </div>
  </div>
</template>
