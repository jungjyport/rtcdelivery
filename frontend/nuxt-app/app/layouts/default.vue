<script setup lang="ts">
import AppHeader from '~/components/common/AppHeader.vue'
import AppFooter from '~/components/common/AppFooter.vue'
</script>

<template>
  <div class="min-h-screen flex flex-col bg-surface-50">
    <AppHeader />
    <main class="flex flex-1 flex-col">
      <slot />
    </main>
    <AppFooter />
  </div>
</template>
