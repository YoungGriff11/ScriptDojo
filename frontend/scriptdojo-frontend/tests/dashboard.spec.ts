import { test, expect } from '@playwright/test'
import { login, TEST_FILE_NAME } from './helpers/auth'

test.describe('Dashboard Page', () => {

  test.beforeEach(async ({ page }) => {
    await login(page)
  })

  test('should display ScriptDojo heading', async ({ page }) => {
    await expect(page.getByText('ScriptDojo')).toBeVisible()
  })

  test('should display logged in username', async ({ page }) => {
    await expect(page.getByText(/Logged in as/)).toBeVisible()
  })

  test('should display file creation input', async ({ page }) => {
    await expect(page.getByPlaceholder(/New file name/)).toBeVisible()
  })

  test('should display Create File button', async ({ page }) => {
    await expect(page.getByRole('button', { name: 'Create File' })).toBeVisible()
  })

  test('should create a new file', async ({ page }) => {
    const fileName = `${TEST_FILE_NAME}${Date.now()}`
    await page.fill('input[placeholder*="New file name"]', fileName)
    await page.getByRole('button', { name: 'Create File' }).click()
    await expect(page.getByText(fileName)).toBeVisible({ timeout: 5000 })
  })

  test('should create file on Enter key press', async ({ page }) => {
    const fileName = `EnterTest${Date.now()}`
    await page.fill('input[placeholder*="New file name"]', fileName)
    await page.keyboard.press('Enter')
    await expect(page.getByText(fileName)).toBeVisible({ timeout: 5000 })
  })

  test('should auto-append .java to file name', async ({ page }) => {
    const baseName = `AutoJava${Date.now()}`
    await page.fill('input[placeholder*="New file name"]', baseName)
    await page.getByRole('button', { name: 'Create File' }).click()
    await expect(page.getByText(`${baseName}.java`)).toBeVisible({ timeout: 5000 })
  })

  test('should navigate to editor when file is clicked', async ({ page }) => {
    // Create a file first
    const fileName = `ClickTest${Date.now()}`
    await page.fill('input[placeholder*="New file name"]', fileName)
    await page.getByRole('button', { name: 'Create File' }).click()
    await page.getByText(`${fileName}.java`).click()
    await expect(page).toHaveURL(/\/editor\?fileId=/)
  })

 test('should delete a file', async ({ page }) => {
  const fileName = `DeleteMe${Date.now()}`
  await page.fill('input[placeholder*="New file name"]', fileName)
  await page.getByRole('button', { name: 'Create File' }).click()
  await expect(page.getByText(`${fileName}.java`)).toBeVisible({ timeout: 5000 })

  // Register dialog handler BEFORE the click that triggers it
  page.once('dialog', dialog => dialog.accept())
  await page.getByRole('button', { name: 'Delete' }).last().click()

  // Wait for the DELETE request to complete before checking
  await page.waitForResponse(res =>
    res.url().includes('/api/files/') && res.request().method() === 'DELETE'
  )

  await expect(page.getByText(`${fileName}.java`)).not.toBeVisible({ timeout: 5000 })
})

  test('should show logout button', async ({ page }) => {
    await expect(page.getByRole('button', { name: 'Logout' })).toBeVisible()
  })

  test('should logout and redirect to login', async ({ page }) => {
    await page.getByRole('button', { name: 'Logout' }).click()
    await expect(page).toHaveURL(/\/login/)
  })

})