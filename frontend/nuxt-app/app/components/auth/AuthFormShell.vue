<script setup lang="ts">
defineProps<{
  title: string
}>()
</script>

<template>
  <div class="relative min-h-screen overflow-hidden bg-surface-50 px-4">
    <div class="absolute inset-0 bg-gradient-to-br from-primary-500/5 via-transparent to-accent-500/5" />
    <div class="absolute top-20 left-10 h-72 w-72 rounded-full bg-primary-400/10 blur-3xl" />
    <div class="absolute top-40 right-10 h-96 w-96 rounded-full bg-accent-400/10 blur-3xl" />

    <div class="relative flex min-h-screen items-center justify-center py-12">
      <div class="w-full max-w-md">
        <NuxtLink
          to="/"
          class="mb-8 flex items-center justify-center gap-2"
        >
          <div class="flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-br from-primary-500 to-accent-500 shadow-lg shadow-primary-500/20">
            <span class="text-lg text-white">🔥</span>
          </div>
          <span class="bg-gradient-to-r from-primary-600 to-accent-500 bg-clip-text text-xl font-bold text-transparent">
            RTC Delivery
          </span>
        </NuxtLink>

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
  </div>
</template>
