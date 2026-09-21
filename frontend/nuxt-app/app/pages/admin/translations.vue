<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useTranslationAdmin, type TranslationJobAdminItem } from '~/composables/useTranslationAdmin'

definePageMeta({
  middleware: 'admin',
})

const { t } = useI18n()

useHead({
  title: () => `${t('adminTranslations.title')} - RTC Delivery`,
})

const {
  jobs,
  totalElements,
  totalPages,
  currentPage,
  isLoading,
  isActionLoading,
  errorMessage,
  actionMessage,
  fetchJobs,
  retryJob,
  retryAllFailedJobs,
} = useTranslationAdmin()

const selectedStatus = ref<string>('ALL')
const selectedJobForDetail = ref<TranslationJobAdminItem | null>(null)

const statusTabs = [
  { label: '전체', value: 'ALL' },
  { label: '실패 (FAILED)', value: 'FAILED' },
  { label: '대기 (PENDING)', value: 'PENDING' },
  { label: '진행중 (IN_PROGRESS)', value: 'IN_PROGRESS' },
  { label: '완료 (COMPLETED)', value: 'COMPLETED' },
  { label: '대체됨 (SUPERSEDED)', value: 'SUPERSEDED' },
]

onMounted(() => {
  loadData(0)
})

watch(selectedStatus, () => {
  loadData(0)
})

function loadData(page = 0) {
  fetchJobs(selectedStatus.value, page, 15)
}

async function handleRetry(job: TranslationJobAdminItem) {
  if (job.status === 'SUPERSEDED') {
    if (!confirm('이 잡은 이미 최신 요청으로 무효화(SUPERSEDED)된 건입니다. 강제 재시도 시 최신 번역을 덮어쓸 수 있습니다. 계속 진행하시겠습니까?')) {
      return
    }
  }

  const success = await retryJob(job.id)
  if (success) {
    loadData(currentPage.value)
  }
}

async function handleRetryAllFailed() {
  if (!confirm('FAILED 상태의 모든 번역 작업을 재시도 대기(PENDING) 상태로 초기화하시겠습니까?')) {
    return
  }

  const count = await retryAllFailedJobs()
  if (count > 0) {
    loadData(0)
  }
}

function openDetailModal(job: TranslationJobAdminItem) {
  selectedJobForDetail.value = job
}

function closeDetailModal() {
  selectedJobForDetail.value = null
}

function formatDate(dateStr?: string) {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return d.toLocaleString('ko-KR', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

function getStatusBadgeClass(status: string) {
  switch (status) {
    case 'FAILED':
      return 'bg-red-50 text-red-700 border-red-200'
    case 'PENDING':
      return 'bg-amber-50 text-amber-700 border-amber-200'
    case 'IN_PROGRESS':
      return 'bg-blue-50 text-blue-700 border-blue-200 animate-pulse'
    case 'COMPLETED':
      return 'bg-emerald-50 text-emerald-700 border-emerald-200'
    case 'SUPERSEDED':
      return 'bg-surface-100 text-surface-600 border-surface-200'
    default:
      return 'bg-surface-100 text-surface-700 border-surface-200'
  }
}
</script>

<template>
  <div class="min-h-screen bg-surface-50 pt-20 pb-20">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
      <!-- 페이지 헤더 -->
      <div class="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-8">
        <div>
          <div class="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-purple-50 text-purple-700 text-xs font-bold mb-2">
            🛡️ 최고 관리자(ADMIN) 전용
          </div>
          <h1 class="text-3xl font-black text-surface-900">
            {{ t('adminTranslations.title') }}
          </h1>
          <p class="text-surface-500 text-sm mt-1">
            {{ t('adminTranslations.subtitle') }}
          </p>
        </div>

        <!-- 상단 액션 버튼 -->
        <div class="flex items-center gap-3">
          <button
            type="button"
            class="px-4 py-2 rounded-xl text-sm font-bold bg-white border border-surface-200 text-surface-700 hover:bg-surface-50 transition-all flex items-center gap-1.5 shadow-sm"
            :disabled="isLoading || isActionLoading"
            @click="loadData(currentPage)"
          >
            <span :class="{ 'animate-spin': isLoading }">🔄</span>
            새로고침
          </button>
          <button
            type="button"
            class="px-4 py-2 rounded-xl text-sm font-bold bg-amber-500 text-white hover:bg-amber-600 transition-all flex items-center gap-1.5 shadow-sm shadow-amber-500/20 disabled:opacity-50"
            :disabled="isLoading || isActionLoading"
            @click="handleRetryAllFailed"
          >
            ⚡ 실패 작업 일괄 재시도
          </button>
        </div>
      </div>

      <!-- 안내 및 알림 메시지 -->
      <div v-if="actionMessage" class="mb-6 p-4 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-800 text-sm flex items-center justify-between">
        <div class="flex items-center gap-2">
          <span>✅</span>
          <span>{{ actionMessage }}</span>
        </div>
        <button type="button" class="text-emerald-600 hover:text-emerald-900 font-bold" @click="actionMessage = null">✕</button>
      </div>

      <div v-if="errorMessage" class="mb-6 p-4 rounded-xl bg-red-50 border border-red-200 text-red-800 text-sm flex items-center justify-between">
        <div class="flex items-center gap-2">
          <span>⚠️</span>
          <span>{{ errorMessage }}</span>
        </div>
        <button type="button" class="text-red-600 hover:text-red-900 font-bold" @click="errorMessage = null">✕</button>
      </div>

      <!-- 상태별 탭 필터 -->
      <div class="flex flex-wrap gap-2 mb-6 border-b border-surface-200 pb-3">
        <button
          v-for="tab in statusTabs"
          :key="tab.value"
          type="button"
          class="px-3.5 py-1.5 rounded-lg text-xs font-bold transition-all"
          :class="selectedStatus === tab.value
            ? 'bg-surface-900 text-white shadow-sm'
            : 'bg-white text-surface-600 hover:bg-surface-100 border border-surface-200'"
          @click="selectedStatus = tab.value"
        >
          {{ tab.label }}
        </button>
      </div>

      <!-- 잡 목록 카드 / 테이블 -->
      <div class="card bg-white rounded-2xl shadow-xl shadow-surface-900/5 border border-surface-200 overflow-hidden">
        <div class="p-4 border-b border-surface-100 flex items-center justify-between bg-surface-50/50">
          <span class="text-xs font-bold text-surface-600">
            총 <span class="text-primary-600 font-extrabold">{{ totalElements }}</span>건의 작업
          </span>
          <span class="text-xs text-surface-400">
            페이지 {{ totalPages > 0 ? currentPage + 1 : 0 }} / {{ totalPages }}
          </span>
        </div>

        <div class="overflow-x-auto">
          <table class="w-full text-left text-xs border-collapse">
            <thead>
              <tr class="border-b border-surface-200 bg-surface-50 text-surface-500 font-bold uppercase tracking-wider">
                <th class="py-3 px-4">Job ID</th>
                <th class="py-3 px-4">매장 ID</th>
                <th class="py-3 px-4">언어쌍</th>
                <th class="py-3 px-4">상태</th>
                <th class="py-3 px-4">재시도</th>
                <th class="py-3 px-4">마지막 에러</th>
                <th class="py-3 px-4">생성일시</th>
                <th class="py-3 px-4 text-center">액션</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-surface-100 font-sans">
              <tr v-if="isLoading" class="text-center">
                <td colspan="8" class="py-12 text-surface-400">
                  <div class="inline-block animate-spin text-xl mb-2">🔄</div>
                  <p>데이터를 불러오는 중입니다...</p>
                </td>
              </tr>
              <tr v-else-if="jobs.length === 0" class="text-center">
                <td colspan="8" class="py-12 text-surface-400">
                  <p class="text-base mb-1">🔍</p>
                  <p>해당 상태의 번역 작업이 없습니다.</p>
                </td>
              </tr>
              <tr
                v-for="job in jobs"
                :key="job.id"
                class="hover:bg-surface-50/80 transition-colors"
              >
                <!-- Job ID -->
                <td class="py-3 px-4 font-mono font-bold text-surface-900">
                  #{{ job.id }}
                </td>

                <!-- 매장 ID -->
                <td class="py-3 px-4 font-medium text-surface-700">
                  매장 #{{ job.restaurantId }}
                </td>

                <!-- 언어쌍 -->
                <td class="py-3 px-4 font-mono">
                  <span class="px-2 py-0.5 rounded bg-surface-100 text-surface-700 text-[11px] font-semibold">
                    {{ job.sourceLocale }} ➔ {{ job.targetLocale }}
                  </span>
                </td>

                <!-- 상태 -->
                <td class="py-3 px-4">
                  <span
                    class="inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-bold border"
                    :class="getStatusBadgeClass(job.status)"
                  >
                    {{ job.status }}
                  </span>
                </td>

                <!-- 재시도 횟수 -->
                <td class="py-3 px-4 font-mono">
                  <span :class="job.retryCount > 0 ? 'text-amber-600 font-bold' : 'text-surface-400'">
                    {{ job.retryCount }} 회
                  </span>
                </td>

                <!-- 마지막 에러 -->
                <td class="py-3 px-4 max-w-xs">
                  <div
                    v-if="job.lastError"
                    class="truncate font-mono text-[11px] text-red-600 cursor-pointer hover:underline"
                    :title="job.lastError"
                    @click="openDetailModal(job)"
                  >
                    {{ job.lastError }}
                  </div>
                  <span v-else class="text-surface-300">-</span>
                </td>

                <!-- 생성일시 -->
                <td class="py-3 px-4 text-surface-500 whitespace-nowrap">
                  {{ formatDate(job.createdAt) }}
                </td>

                <!-- 액션 -->
                <td class="py-3 px-4 text-center whitespace-nowrap">
                  <div class="flex items-center justify-center gap-1.5">
                    <button
                      type="button"
                      class="px-2.5 py-1 rounded-lg text-xs font-bold border transition-all"
                      :class="job.status === 'FAILED' || job.status === 'SUPERSEDED'
                        ? 'bg-primary-50 text-primary-600 border-primary-200 hover:bg-primary-100'
                        : 'bg-surface-50 text-surface-300 border-surface-200 cursor-not-allowed'"
                      :disabled="job.status !== 'FAILED' && job.status !== 'SUPERSEDED' || isActionLoading"
                      @click="handleRetry(job)"
                    >
                      재시도
                    </button>
                    <button
                      type="button"
                      class="px-2 py-1 rounded-lg text-xs font-bold bg-surface-100 text-surface-600 hover:bg-surface-200 transition-all"
                      title="상세 보기"
                      @click="openDetailModal(job)"
                    >
                      상세
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 페이징 컨트롤 -->
        <div v-if="totalPages > 1" class="p-4 border-t border-surface-100 flex items-center justify-between bg-surface-50/30">
          <button
            type="button"
            class="px-3 py-1.5 rounded-lg border border-surface-200 text-xs font-bold bg-white text-surface-700 hover:bg-surface-50 disabled:opacity-40"
            :disabled="currentPage === 0 || isLoading"
            @click="loadData(currentPage - 1)"
          >
            ◀ 이전
          </button>

          <span class="text-xs text-surface-600 font-medium">
            {{ currentPage + 1 }} / {{ totalPages }}
          </span>

          <button
            type="button"
            class="px-3 py-1.5 rounded-lg border border-surface-200 text-xs font-bold bg-white text-surface-700 hover:bg-surface-50 disabled:opacity-40"
            :disabled="currentPage >= totalPages - 1 || isLoading"
            @click="loadData(currentPage + 1)"
          >
            다음 ▶
          </button>
        </div>
      </div>
    </div>

    <!-- 상세 보기 모달 -->
    <div
      v-if="selectedJobForDetail"
      class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm animate-fade-in"
      @click.self="closeDetailModal"
    >
      <div class="card bg-white rounded-2xl max-w-2xl w-full p-6 shadow-2xl border border-surface-200 max-h-[90vh] flex flex-col">
        <div class="flex items-center justify-between pb-4 border-b border-surface-100">
          <div>
            <h3 class="text-lg font-black text-surface-900">
              번역 잡 #{{ selectedJobForDetail.id }} 상세 정보
            </h3>
            <p class="text-xs text-surface-500 mt-0.5 font-mono">
              Event ID: {{ selectedJobForDetail.eventId }}
            </p>
          </div>
          <button
            type="button"
            class="w-8 h-8 rounded-full bg-surface-100 hover:bg-surface-200 flex items-center justify-center text-surface-500 font-bold"
            @click="closeDetailModal"
          >
            ✕
          </button>
        </div>

        <div class="py-4 space-y-4 overflow-y-auto text-xs">
          <div class="grid grid-cols-2 sm:grid-cols-4 gap-3 bg-surface-50 p-3 rounded-xl border border-surface-200">
            <div>
              <span class="text-surface-400 block text-[10px]">매장 ID</span>
              <span class="font-bold text-surface-800">#{{ selectedJobForDetail.restaurantId }}</span>
            </div>
            <div>
              <span class="text-surface-400 block text-[10px]">언어쌍</span>
              <span class="font-bold text-surface-800 font-mono">{{ selectedJobForDetail.sourceLocale }} ➔ {{ selectedJobForDetail.targetLocale }}</span>
            </div>
            <div>
              <span class="text-surface-400 block text-[10px]">상태</span>
              <span class="font-bold" :class="getStatusBadgeClass(selectedJobForDetail.status)">
                {{ selectedJobForDetail.status }}
              </span>
            </div>
            <div>
              <span class="text-surface-400 block text-[10px]">재시도 횟수</span>
              <span class="font-bold text-surface-800">{{ selectedJobForDetail.retryCount }} 회</span>
            </div>
          </div>

          <!-- 마지막 에러 내용 -->
          <div v-if="selectedJobForDetail.lastError">
            <span class="font-bold text-red-600 block mb-1">⚠️ 마지막 에러 (Last Error)</span>
            <pre class="bg-red-50 text-red-800 p-3 rounded-xl border border-red-200 font-mono text-[11px] whitespace-pre-wrap break-all max-h-40 overflow-y-auto">{{ selectedJobForDetail.lastError }}</pre>
          </div>

          <!-- 번역 대상 원본 데이터 (JSON) -->
          <div>
            <span class="font-bold text-surface-700 block mb-1">📦 번역 대상 항목 (Entries JSON)</span>
            <pre class="bg-surface-900 text-surface-100 p-3 rounded-xl font-mono text-[11px] whitespace-pre-wrap break-all max-h-60 overflow-y-auto">{{ selectedJobForDetail.entries }}</pre>
          </div>
        </div>

        <div class="pt-4 border-t border-surface-100 flex items-center justify-end gap-2">
          <button
            v-if="selectedJobForDetail.status === 'FAILED' || selectedJobForDetail.status === 'SUPERSEDED'"
            type="button"
            class="px-4 py-2 rounded-xl text-xs font-bold bg-primary-600 text-white hover:bg-primary-700 shadow-sm transition-all"
            @click="handleRetry(selectedJobForDetail); closeDetailModal();"
          >
            ⚡ 이 잡 재시도 실행
          </button>
          <button
            type="button"
            class="px-4 py-2 rounded-xl text-xs font-bold bg-surface-100 text-surface-700 hover:bg-surface-200 transition-all"
            @click="closeDetailModal"
          >
            닫기
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
