<script setup lang="ts">
defineProps<{
  title: string
}>()
</script>

<template>
  <div class="relative flex flex-1 flex-col items-center justify-center overflow-hidden bg-surface-50 px-4 pt-16 pb-12">
    <div class="absolute inset-0 bg-gradient-to-br from-primary-500/5 via-transparent to-accent-500/5" />
    <div class="absolute top-20 left-10 h-72 w-72 rounded-full bg-primary-400/10 blur-3xl" />
    <div class="absolute top-40 right-10 h-96 w-96 rounded-full bg-accent-400/10 blur-3xl" />

    <div class="relative w-full max-w-md py-12">
      <div class="rounded-2xl border border-surface-100 bg-white p-6 shadow-sm shadow-surface-900/5 sm:p-8">
        <h1 class="mb-6 text-center text-2xl font-bold text-surface-900">
          {{ title }}
        </h1>
        <slot />
      </div>

      <p class="mt-6 text-center text-sm text-surface-500">
        <slot name="footer" />
      </p>
    </div>
  </div>
</template>
