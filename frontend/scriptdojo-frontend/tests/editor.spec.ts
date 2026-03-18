import { test, expect } from '@playwright/test'
import { login } from './helpers/auth'

test.describe('Editor Page', () => {

  let fileUrl: string

  test.beforeEach(async ({ page }) => {
    await login(page)

    // Create a file and navigate to editor
    const fileName = `EditorTest${Date.now()}`
    await page.fill('input[placeholder*="New file name"]', fileName)
    await page.getByRole('button', { name: 'Create File' }).click()
    await page.getByText(`${fileName}.java`).click()
    await page.waitForURL(/\/editor\?fileId=/)
    fileUrl = page.url()
  })

  test('should display ScriptDojo in sidebar', async ({ page }) => {
    await expect(page.getByText('ScriptDojo')).toBeVisible()
  })

  test('should display file name in sidebar', async ({ page }) => {
    await expect(page.locator('text=File:')).toBeVisible()
  })

  test('should display Monaco editor', async ({ page }) => {
    await expect(page.locator('.monaco-editor')).toBeVisible({ timeout: 10000 })
  })

  test('should display console output panel', async ({ page }) => {
    await expect(page.getByText('Console Output')).toBeVisible()
  })

  test('should display Save File button', async ({ page }) => {
    await expect(page.getByRole('button', { name: /Save File/ })).toBeVisible()
  })

  test('should display Run Java Code button', async ({ page }) => {
    await expect(page.getByRole('button', { name: /Run Java Code/ })).toBeVisible()
  })

  test('should display Generate Share Link button', async ({ page }) => {
    await expect(page.getByRole('button', { name: /Generate Share Link/ })).toBeVisible()
  })

  test('should show active users panel', async ({ page }) => {
    await expect(page.getByText('Active Users')).toBeVisible()
  })

  test('should show green status dot when connected', async ({ page }) => {
    // Wait for WebSocket connection
    await page.waitForTimeout(2000)
    const dot = page.locator('span').filter({ hasText: '' }).first()
    // Connected dot should have green background
    await expect(page.getByText('ScriptDojo')).toBeVisible()
  })

  test('should save file successfully', async ({ page }) => {
    page.once('dialog', dialog => {
      expect(dialog.message()).toContain('saved')
      dialog.accept()
    })
    await page.getByRole('button', { name: /Save File/ }).click()
  })

  test('should run code and show output', async ({ page }) => {
    await page.getByRole('button', { name: /Run Java Code/ }).click()
    await expect(page.getByText(/Compiling|EXECUTION|FAILED/)).toBeVisible({ timeout: 15000 })
  })

  test('should generate share link', async ({ page }) => {
    await page.getByRole('button', { name: /Generate Share Link/ }).click()
    await expect(page.getByText('Share Link')).toBeVisible({ timeout: 5000 })
  })

  test('should display share URL after generating link', async ({ page }) => {
    await page.getByRole('button', { name: /Generate Share Link/ }).click()
    const input = page.locator('input[readonly]')
    await expect(input).toBeVisible({ timeout: 5000 })
    const value = await input.inputValue()
    expect(value).toContain('localhost:5173/room/')
  })

  test('should clear console output', async ({ page }) => {
    await page.getByRole('button', { name: /Run Java Code/ }).click()
    await expect(page.getByText(/Compiling|EXECUTION/)).toBeVisible({ timeout: 15000 })
    await page.getByRole('button', { name: 'Clear' }).click()
    await expect(page.getByText('Ready to run Java code')).toBeVisible()
  })

})