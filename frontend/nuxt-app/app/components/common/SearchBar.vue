<script setup lang="ts">
const props = withDefaults(defineProps<{
  placeholder?: string
  modelValue?: string
  size?: 'sm' | 'md'
}>(), {
  placeholder: '',
  modelValue: '',
  size: 'md',
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'search', value: string): void
}>()

const input = ref(props.modelValue)

watch(() => props.modelValue, (val) => {
  input.value = val
})

function onSubmit() {
  emit('search', input.value.trim())
}

function onClear() {
  input.value = ''
  emit('update:modelValue', '')
  emit('search', '')
}
</script>

<template>
  <form @submit.prevent="onSubmit" class="w-full">
    <div
      class="search-bar-wrap"
      :class="size === 'sm' ? 'search-bar-sm' : 'search-bar-md'"
    >
      <div class="search-bar-inner">
        <svg class="search-icon" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
        </svg>
        <input
          v-model="input"
          type="text"
          :placeholder="placeholder"
          class="search-input"
          @input="emit('update:modelValue', input)"
        />
        <button
          v-if="input"
          type="button"
          class="clear-btn"
          @click="onClear"
          aria-label="clear"
        >
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>
      <button
        type="submit"
        class="search-submit-btn"
        :class="size === 'sm' ? 'search-submit-sm' : 'search-submit-md'"
      >
        <slot name="button-content">
          <span>{{ $t('common.search') }}</span>
        </slot>
      </button>
    </div>
  </form>
</template>

<style scoped>
.search-bar-wrap {
  display: flex;
  align-items: center;
  background: #fff;
  border-radius: 1rem;
  box-shadow: 0 4px 24px 0 rgba(0, 0, 0, 0.06);
  border: 1px solid #f1f5f9;
  transition: box-shadow 0.25s, border-color 0.25s;
}
.search-bar-wrap:focus-within {
  box-shadow: 0 8px 32px 0 rgba(0, 0, 0, 0.1);
  border-color: #bfdbfe;
}
.search-bar-md {
  padding: 0.5rem;
}
.search-bar-sm {
  padding: 0.25rem 0.375rem;
}

.search-bar-inner {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0 1rem;
  flex: 1;
  min-width: 0;
}

.search-icon {
  width: 1.125rem;
  height: 1.125rem;
  color: #94a3b8;
  flex-shrink: 0;
}

.search-input {
  width: 100%;
  background: transparent;
  border: none;
  outline: none;
  color: #0f172a;
  font-size: 0.9375rem;
  padding: 0.375rem 0;
}
.search-input::placeholder {
  color: #94a3b8;
}

.clear-btn {
  display: flex;
  align-items: center;
  color: #cbd5e1;
  background: transparent;
  border: none;
  cursor: pointer;
  flex-shrink: 0;
  transition: color 0.15s;
  padding: 0;
}
.clear-btn:hover {
  color: #64748b;
}

.search-submit-btn {
  flex-shrink: 0;
  background: linear-gradient(135deg, #3b82f6, #6366f1);
  color: #fff;
  border: none;
  cursor: pointer;
  font-weight: 600;
  border-radius: 0.75rem;
  transition: opacity 0.2s, transform 0.15s;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.375rem;
  letter-spacing: -0.01em;
}
.search-submit-btn:hover {
  opacity: 0.9;
  transform: translateY(-1px);
}
.search-submit-btn:active {
  transform: translateY(0);
}
.search-submit-md {
  padding: 0.625rem 1.5rem;
  font-size: 0.875rem;
}
.search-submit-sm {
  padding: 0.5rem 1rem;
  font-size: 0.8125rem;
}
</style>
