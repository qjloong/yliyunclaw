import { defineStore } from 'pinia'
import { ref } from 'vue'
import { cronJobApi } from '@/api/index'
import type { CronJob } from '@/types/index'

export const useCronJobStore = defineStore('cronJob', () => {
  const jobs = ref<CronJob[]>([])
  const loading = ref(false)

  async function fetchJobs() {
    loading.value = true
    try {
      const res: any = await cronJobApi.list()
      jobs.value = res.data || res || []
    } catch (e) {
      console.error('Failed to fetch cron jobs', e)
    } finally {
      loading.value = false
    }
  }

  async function createJob(data: Partial<CronJob>) {
    const res: any = await cronJobApi.create(data)
    const job = res.data || res
    jobs.value.unshift(job)
    return job
  }

  async function updateJob(id: string | number, data: Partial<CronJob>) {
    const res: any = await cronJobApi.update(id, data)
    const updated = res.data || res
    const idx = jobs.value.findIndex((j) => String(j.id) === String(id))
    if (idx !== -1) jobs.value[idx] = updated
    return updated
  }

  async function deleteJob(id: string | number) {
    await cronJobApi.delete(id)
    jobs.value = jobs.value.filter((j) => String(j.id) !== String(id))
  }

  async function toggleJob(id: string | number, enabled: boolean) {
    await cronJobApi.toggle(id, enabled)
    const job = jobs.value.find((j) => String(j.id) === String(id))
    if (job) job.enabled = enabled
  }

  async function runNow(id: string | number) {
    await cronJobApi.runNow(id)
  }

  return { jobs, loading, fetchJobs, createJob, updateJob, deleteJob, toggleJob, runNow }
})
